package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.auth.config.CognitoOAuthSettings;
import com.aws.alphabetuniverse.auth.dto.CognitoOAuthTokenResponse;
import com.aws.alphabetuniverse.auth.dto.GoogleAuthRequest;
import com.aws.alphabetuniverse.auth.dto.RefreshTokenRequest;
import com.aws.alphabetuniverse.auth.service.CognitoOAuthService;
import com.aws.alphabetuniverse.common.exception.AccountBlockedException;
import com.aws.alphabetuniverse.common.exception.CognitoOAuthException;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthenticationResultType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthGoogleLoginTest {
    private static final String CALLBACK = "http://localhost:3000/callback";
    private static final String SCOPES = "openid email profile aws.cognito.signin.user.admin";
    private final UserRepository users = mock(UserRepository.class);
    private final CognitoOAuthService oauth = mock(CognitoOAuthService.class);
    private final UserSyncService sync = mock(UserSyncService.class);
    private final CognitoIdentityProviderClient sdk = mock(CognitoIdentityProviderClient.class);
    private final JwtDecoder decoder = mock(JwtDecoder.class);
    private final AuthServiceImpl service = new AuthServiceImpl();
    private final GoogleAuthRequest request = new GoogleAuthRequest("code", CALLBACK,
            "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG");
    private final User user = User.builder().id(7L).cognitoSub("sub-7").email("user@example.com")
            .displayName("Existing name").role("admin").status("ACTIVE").build();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "userRepository", users);
        ReflectionTestUtils.setField(service, "cognitoOAuthService", oauth);
        ReflectionTestUtils.setField(service, "userSyncService", sync);
        ReflectionTestUtils.setField(service, "cognitoClient", sdk);
        ReflectionTestUtils.setField(service, "jwtDecoder", decoder);
        ReflectionTestUtils.setField(service, "clientId", "client");
        ReflectionTestUtils.setField(service, "oauthSettings", new CognitoOAuthSettings(
                "https://example.auth.us-east-1.amazoncognito.com", "client", CALLBACK,
                "http://localhost:3000", "oauth"));
        when(oauth.exchangeAuthorizationCode(request)).thenReturn(tokens("new-refresh"));
        when(oauth.refreshTokens(anyString())).thenReturn(tokens("new-refresh"));
        when(decoder.decode("access")).thenReturn(jwt("sub-7", "access", "client", SCOPES, false));
        when(users.findByCognitoSub("sub-7")).thenReturn(Optional.of(user));
    }

    @Test
    void existingActiveUserKeepsRoleAndProfileWithoutDatabaseWrites() {
        var result = service.loginWithGoogle(request);
        assertEquals("access", result.getAccessToken());
        assertEquals("new-refresh", result.getRefreshToken());
        assertEquals("admin", user.getRole());
        assertEquals("Existing name", user.getDisplayName());
        verifyNoInteractions(sync, sdk);
        verify(users, never()).save(any());
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void firstGoogleUserCanExchangeBeforeSyncButDoesNotCreateLocalUser() {
        when(users.findByCognitoSub("sub-7")).thenReturn(Optional.empty());
        assertEquals("access", service.loginWithGoogle(request).getAccessToken());
        verify(users).findByCognitoSub("sub-7");
        verifyNoMoreInteractions(users);
        verifyNoInteractions(sync, sdk);
        verify(users, never()).save(any());
        verify(users, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void exchangeDefersEmailConflictChecksToSync() {
        when(users.findByCognitoSub("sub-7")).thenReturn(Optional.empty());
        when(users.findByEmailIgnoreCase("User@example.com")).thenReturn(Optional.of(
                User.builder().id(9L).cognitoSub("password-sub").status("BLOCKED").build()));
        assertEquals("access", service.loginWithGoogle(request).getAccessToken());
        verify(users, never()).findByEmailIgnoreCase(anyString());
        verifyNoInteractions(sync, sdk);
        verify(users, never()).save(any());
    }

    @Test
    void scopeOrderWhitespaceAndDuplicatesDoNotCauseFalseConfigurationErrors() {
        when(decoder.decode("access")).thenReturn(jwt("sub-7", "access", "client",
                "  profile\topenid email aws.cognito.signin.user.admin openid  ", false));
        assertDoesNotThrow(() -> service.loginWithGoogle(request));
    }

    @Test
    void blockedUserDoesNotReceiveTokensEvenWhenCognitoExchangeSucceeds() {
        user.setStatus("BLOCKED");
        assertThrows(AccountBlockedException.class, () -> service.loginWithGoogle(request));
        verifyNoInteractions(sync, sdk);
    }

    @Test
    void missingOrUnexpectedStatusIsForbidden() {
        user.setStatus(null);
        var error = assertThrows(CognitoOAuthException.class, () -> service.loginWithGoogle(request));
        assertEquals("ACCOUNT_INACTIVE", error.getCode());
        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void rejectsBadSignatureBeforeReadingAnyUser() {
        when(decoder.decode("access")).thenThrow(new BadJwtException("private-decoder-details"));
        var error = assertThrows(CognitoOAuthException.class, () -> service.loginWithGoogle(request));
        assertEquals("COGNITO_OAUTH_INVALID_RESPONSE", error.getCode());
        verifyNoInteractions(users, sync);
        assertNull(error.getCause());
    }

    @Test
    void rejectsWrongTokenTypeClientSubjectAndExpiredIdentity() {
        for (Jwt invalid : new Jwt[]{
                jwt("sub-7", "id", "client", SCOPES, false),
                jwt("sub-7", "access", "another-client", SCOPES, false),
                jwt("", "access", "client", SCOPES, false),
                jwt("sub-7", "access", "client", SCOPES, true)}) {
            when(decoder.decode("access")).thenReturn(invalid);
            assertThrows(CognitoOAuthException.class, () -> service.loginWithGoogle(request));
        }
        verifyNoInteractions(users, sync);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"openid email profile", "aws.cognito.signin.user.admin", "openid email"})
    void missingScopesAreConfigurationErrorsBeforeSync(String scopes) {
        when(decoder.decode("access")).thenReturn(jwt("sub-7", "access", "client", scopes, false));
        var error = assertThrows(CognitoOAuthException.class, () -> service.loginWithGoogle(request));
        assertEquals("COGNITO_OAUTH_CONFIG_ERROR", error.getCode());
        verifyNoInteractions(users, sync);
    }

    @Test
    void invalidProviderResponseDoesNotReadOrWriteLocalUsers() {
        when(oauth.exchangeAuthorizationCode(request)).thenReturn(null);
        assertEquals("COGNITO_OAUTH_INVALID_RESPONSE",
                assertThrows(CognitoOAuthException.class, () -> service.loginWithGoogle(request)).getCode());
        verifyNoInteractions(users, sync, sdk);
    }

    @Test
    void validatesCallbackAndVerifierBeforeCallingProvider() {
        assertThrows(CognitoOAuthException.class, () -> service.loginWithGoogle(null));
        assertThrows(CognitoOAuthException.class, () -> service.loginWithGoogle(
                new GoogleAuthRequest("code", CALLBACK, "short")));
        assertThrows(CognitoOAuthException.class, () -> service.loginWithGoogle(
                new GoogleAuthRequest("code", "https://evil.example/callback", request.codeVerifier())));
        verifyNoInteractions(oauth, users, sync, decoder);
    }

    @Test
    void databaseFailuresAreNotDisguisedAsCognitoOrNetworkErrors() {
        when(users.findByCognitoSub("sub-7")).thenThrow(new DataAccessResourceFailureException("test-db"));
        assertThrows(DataAccessResourceFailureException.class, () -> service.loginWithGoogle(request));
    }

    @Test
    void refreshReturnsRotatedRefreshTokenAndDoesNotCallLegacySdk() {
        var response = service.refreshToken(new RefreshTokenRequest("old-refresh"));
        assertEquals("new-refresh", response.refreshToken());
        verifyNoInteractions(sdk);
    }

    @Test
    void refreshRetainsOriginalTokenWhenRotationIsDisabledAndAllowsApiLoginScopes() {
        when(oauth.refreshTokens("old-refresh")).thenReturn(tokens(null));
        when(decoder.decode("access")).thenReturn(jwt("sub-7", "access", "client",
                "aws.cognito.signin.user.admin", false));
        assertEquals("old-refresh", service.refreshToken(new RefreshTokenRequest("old-refresh")).refreshToken());
    }

    @Test
    void oauthRefreshStillRejectsBlockedAndUnsynchronizedUsers() {
        user.setStatus("BLOCKED");
        assertThrows(AccountBlockedException.class,
                () -> service.refreshToken(new RefreshTokenRequest("old-refresh")));
        when(users.findByCognitoSub("sub-7")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(CognitoOAuthException.class,
                () -> service.refreshToken(new RefreshTokenRequest("old-refresh"))).getStatusCode());
    }

    @Test
    void sdkModePreservesLegacyRefreshForGoogleTokensWithoutOAuthSwitch() {
        ReflectionTestUtils.setField(service, "oauthSettings", new CognitoOAuthSettings(
                "https://example.auth.us-east-1.amazoncognito.com", "client", CALLBACK,
                "http://localhost:3000", "sdk"));
        when(sdk.initiateAuth(any(InitiateAuthRequest.class))).thenReturn(InitiateAuthResponse.builder()
                .authenticationResult(AuthenticationResultType.builder()
                        .accessToken("access").idToken("id").expiresIn(3600).build()).build());

        var result = service.refreshToken(new RefreshTokenRequest("old-refresh"));

        assertEquals("access", result.accessToken());
        assertEquals("old-refresh", result.refreshToken());
        verify(sdk).initiateAuth(argThat((InitiateAuthRequest sent) ->
                sent.authFlow() == AuthFlowType.REFRESH_TOKEN_AUTH
                        && "old-refresh".equals(sent.authParameters().get("REFRESH_TOKEN"))));
        verify(oauth, never()).refreshTokens(anyString());
    }

    @Test
    void logoutBuildsEncodedUrlWithoutDuplicateSlashes() {
        ReflectionTestUtils.setField(service, "cognitoDomain", "https://example.auth.us-east-1.amazoncognito.com/");
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:3000/");
        assertEquals("https://example.auth.us-east-1.amazoncognito.com/logout?client_id=client"
                        + "&logout_uri=http%3A%2F%2Flocalhost%3A3000%2Flogin",
                service.logout().getLogoutUrl());
    }

    private CognitoOAuthTokenResponse tokens(String refresh) {
        return new CognitoOAuthTokenResponse("access", "id", refresh, 3600, "Bearer");
    }

    private Jwt jwt(String sub, String type, String client, String scopes, boolean expired) {
        Instant now = Instant.now();
        var builder = Jwt.withTokenValue("access").header("alg", "RS256")
                .subject(sub).claim("token_use", type).claim("client_id", client)
                .issuedAt(now.minusSeconds(60)).expiresAt(now.plusSeconds(expired ? -30 : 3600));
        if (scopes != null) {
            builder.claim("scope", scopes);
        }
        return builder.build();
    }
}
