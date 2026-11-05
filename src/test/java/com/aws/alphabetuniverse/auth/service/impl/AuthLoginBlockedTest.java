package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.auth.controller.AuthController;
import com.aws.alphabetuniverse.auth.dto.LoginRequest;
import com.aws.alphabetuniverse.auth.dto.RefreshTokenRequest;
import com.aws.alphabetuniverse.common.exception.AccountBlockedException;
import com.aws.alphabetuniverse.common.exception.GlobalExceptionHandler;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthLoginBlockedTest {

    private final UserRepository users = mock(UserRepository.class);
    private final CognitoIdentityProviderClient cognito = mock(CognitoIdentityProviderClient.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final AuthServiceImpl service = new AuthServiceImpl();
    private final LoginRequest request = LoginRequest.builder()
            .email("User@example.com").password(" password-with-spaces ").build();
    private User user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "cognitoClient", cognito);
        ReflectionTestUtils.setField(service, "jwtDecoder", decoder);
        ReflectionTestUtils.setField(service, "clientId", "test-client");
        user = User.builder().id(7L).cognitoSub("sub-7").email("user@example.com")
                .role("learner").status("ACTIVE").build();
        when(users.findByEmailIgnoreCaseForUpdate("User@example.com"))
                .thenReturn(Optional.of(user));
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.of(user));
        when(decoder.decode("new-access-token")).thenReturn(Jwt.withTokenValue("new-access-token")
                .header("alg", "RS256").subject("sub-7")
                .claim("token_use", "access").claim("client_id", "test-client").build());
    }

    @Test
    void blockedLocalAccountIsDeniedBeforeCognitoEvenIfCognitoWouldIssueTokens() {
        user.setStatus("BLOCKED");

        var error = assertThrows(AccountBlockedException.class, () -> service.login(request));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals(AccountBlockedException.MESSAGE, error.getReason());
        verifyNoInteractions(cognito, decoder);
    }

    @Test
    void activeLoginLocksFirstAndChecksTheVerifiedSubBeforeReturningTokens() {
        successfulCognitoLogin();

        var response = service.login(request);

        var order = inOrder(users, cognito, decoder);
        order.verify(users).findByEmailIgnoreCaseForUpdate("User@example.com");
        order.verify(cognito).initiateAuth(any(InitiateAuthRequest.class));
        order.verify(decoder).decode("new-access-token");
        order.verify(users).findByCognitoSubForUpdate("sub-7");
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals("new-refresh-token", response.getRefreshToken());
        var sent = ArgumentCaptor.forClass(InitiateAuthRequest.class);
        verify(cognito).initiateAuth(sent.capture());
        assertEquals(" password-with-spaces ", sent.getValue().authParameters().get("PASSWORD"));
    }

    @Test
    void firstTimeUserCanLoginBeforeLocalAuthSync() {
        when(users.findByEmailIgnoreCaseForUpdate(any())).thenReturn(Optional.empty());
        when(users.findByCognitoSubForUpdate("sub-7")).thenReturn(Optional.empty());
        successfulCognitoLogin();

        assertEquals("new-access-token", service.login(request).getAccessToken());
        verify(users, never()).save(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void anEmailAliasCannotBypassTheBlockedStateOfTheVerifiedCognitoSub() {
        when(users.findByEmailIgnoreCaseForUpdate(any())).thenReturn(Optional.empty());
        user.setStatus("BLOCKED");
        successfulCognitoLogin();

        assertThrows(AccountBlockedException.class, () -> service.login(request));

        verify(decoder).decode("new-access-token");
        verify(users).findByCognitoSubForUpdate("sub-7");
    }

    @Test
    void rejectsNonActiveUnknownStatusWithoutCallingCognito() {
        user.setStatus(null);

        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.login(request)).getStatusCode());
        verifyNoInteractions(cognito, decoder);
    }

    @Test
    void wrongPasswordStillReturns401() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(NotAuthorizedException.builder().message("Incorrect credentials").build());

        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.login(request)).getStatusCode());
        verifyNoInteractions(decoder);
    }

    @Test
    void cognitoChallengeStillReturns409WithoutReturningTokens() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenReturn(InitiateAuthResponse.builder().challengeName(ChallengeNameType.NEW_PASSWORD_REQUIRED).build());

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.login(request)).getStatusCode());
        verifyNoInteractions(decoder);
    }

    @Test
    void unverifiedAccountRemainsASeparate403() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenThrow(UserNotConfirmedException.builder().build());

        var error = assertThrows(ResponseStatusException.class, () -> service.login(request));

        assertFalse(error instanceof AccountBlockedException);
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void anInvalidReturnedJwtIsNotSentToTheFrontend() {
        successfulCognitoLogin();
        when(decoder.decode("new-access-token")).thenThrow(new BadJwtException("invalid signature"));

        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(ResponseStatusException.class,
                () -> service.login(request)).getStatusCode());
        verify(users, never()).findByCognitoSubForUpdate(any());
    }

    @Test
    void missingCredentialsAreRejectedBeforeDatabaseOrAws() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.login(new LoginRequest())).getStatusCode());
        verifyNoInteractions(users, cognito, decoder);
    }

    @Test
    void blockedAccountGetsStableHttpCodeAndVietnameseMessageWithoutTokens() throws Exception {
        user.setStatus("BLOCKED");
        var controller = new AuthController();
        ReflectionTestUtils.setField(controller, "authService", service);
        var mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"User@example.com\",\"password\":\"example-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(AccountBlockedException.CODE))
                .andExpect(jsonPath("$.message").value(AccountBlockedException.MESSAGE))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.idToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
        verifyNoInteractions(cognito, decoder);
    }

    @Test
    void refreshDoesNotReturnNewTokensWhenTheLocalAccountIsBlocked() {
        user.setStatus("BLOCKED");
        successfulCognitoLogin();
        when(users.findByCognitoSub("sub-7")).thenReturn(Optional.of(user));

        assertThrows(AccountBlockedException.class,
                () -> service.refreshToken(new RefreshTokenRequest("old-refresh-token")));
    }

    private void successfulCognitoLogin() {
        when(cognito.initiateAuth(any(InitiateAuthRequest.class)))
                .thenReturn(InitiateAuthResponse.builder().authenticationResult(AuthenticationResultType.builder()
                        .accessToken("new-access-token").idToken("new-id-token")
                        .refreshToken("new-refresh-token").expiresIn(3600).build()).build());
    }
}
