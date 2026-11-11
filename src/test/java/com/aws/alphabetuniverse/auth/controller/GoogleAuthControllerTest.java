package com.aws.alphabetuniverse.auth.controller;

import com.aws.alphabetuniverse.auth.dto.GoogleAuthRequest;
import com.aws.alphabetuniverse.auth.dto.LoginResponse;
import com.aws.alphabetuniverse.auth.service.AuthService;
import com.aws.alphabetuniverse.common.config.CorsConfig;
import com.aws.alphabetuniverse.common.exception.AccountBlockedException;
import com.aws.alphabetuniverse.common.exception.CognitoOAuthException;
import com.aws.alphabetuniverse.common.exception.EmailAlreadyExistsException;
import com.aws.alphabetuniverse.common.exception.GlobalExceptionHandler;
import com.aws.alphabetuniverse.security.config.SecurityConfig;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.sql.SQLException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GoogleAuthControllerTest {
    private static final String BODY = """
            {"code":"test-code","redirectUri":"http://localhost:3000/callback",
             "codeVerifier":"abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"}
            """;
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private AuthService auth;
    private UserRepository users;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MockPropertySource()
                .withProperty("app.cors.allowed-origins", "http://localhost:3000"));
        context.register(WebTestConfig.class);
        context.refresh();
        auth = context.getBean(AuthService.class);
        users = context.getBean(UserRepository.class);
        decoder = context.getBean(JwtDecoder.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
        when(auth.loginWithGoogle(any())).thenReturn(LoginResponse.builder().accessToken("access")
                .idToken("id").refreshToken("refresh").expiresIn(3600).build());
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void codeExchangeIsPublicAndReturnsUnwrappedContractWithNoStoreHeaders() throws Exception {
        mvc.perform(post("/api/auth/google").servletPath("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.idToken").value("id"))
                .andExpect(jsonPath("$.refreshToken").value("refresh"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Pragma", "no-cache"));
        verify(auth).loginWithGoogle(new GoogleAuthRequest("test-code", "http://localhost:3000/callback",
                "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"));
        verifyNoInteractions(users, decoder);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"code\":\"test\"}", "{\"code\":\"test\",\"redirectUri\":\"http://localhost:3000/callback\"}",
            "{\"code\":\"test\",\"redirectUri\":\"http://localhost:3000/callback\",\"codeVerifier\":\"short\"}",
            "{\"code\":\" \",\"redirectUri\":\"http://localhost:3000/callback\",\"codeVerifier\":\"abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG\"}"})
    void invalidBodiesReturn400WithoutCallingServices(String body) throws Exception {
        mvc.perform(post("/api/auth/google").servletPath("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verifyNoInteractions(auth, users);
    }

    @Test
    void malformedJsonNeverEchoesCodeOrBodyInError() throws Exception {
        mvc.perform(post("/api/auth/google").servletPath("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"private-code\",broken"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(content().string(not(containsString("private-code"))));
        verifyNoInteractions(auth, users);
    }

    @Test
    void blockedResponseContainsStableMessageAndNoTokens() throws Exception {
        when(auth.loginWithGoogle(any())).thenThrow(new AccountBlockedException());
        mvc.perform(post("/api/auth/google").servletPath("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"))
                .andExpect(jsonPath("$.message").value(AccountBlockedException.MESSAGE))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.refreshToken").doesNotExist());
    }

    @Test
    void oauthExceptionKeepsItsStatusAndMachineReadableCode() throws Exception {
        when(auth.loginWithGoogle(any())).thenThrow(new CognitoOAuthException(
                HttpStatus.BAD_GATEWAY, "COGNITO_OAUTH_CONFIG_ERROR", "Cognito sign-in configuration error"));
        mvc.perform(post("/api/auth/google").servletPath("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("COGNITO_OAUTH_CONFIG_ERROR"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void publicGoogleDoesNotApplyLocalUserFilterToAnOtherwiseValidOldBearer() throws Exception {
        when(decoder.decode("old-token")).thenReturn(Jwt.withTokenValue("old-token").header("alg", "RS256")
                .subject("not-yet-synced-old-sub").build());
        mvc.perform(post("/api/auth/google").servletPath("/api/auth/google")
                        .header("Authorization", "Bearer old-token")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
        verifyNoInteractions(users);
    }

    @Test
    void invalidBearerIsStillRejectedByResourceServerSoFrontendMustOmitIt() throws Exception {
        when(decoder.decode("expired")).thenThrow(new BadJwtException("test-expired"));
        mvc.perform(post("/api/auth/google").servletPath("/api/auth/google")
                        .header("Authorization", "Bearer expired")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auth, users);
    }

    @Test
    void syncAndOtherMethodsRemainProtected() throws Exception {
        mvc.perform(post("/api/auth/sync").servletPath("/api/auth/sync")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/google").servletPath("/api/auth/google")).andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }

    @Test
    void preflightFromFrontendWorksWithoutTokenButUnlistedOriginIsRejected() throws Exception {
        mvc.perform(options("/api/auth/google").servletPath("/api/auth/google")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));
        mvc.perform(options("/api/auth/google").servletPath("/api/auth/google")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auth, users);
    }

    @Test
    void syncAllowsValidJwtBeforeLocalUserExistsAndReturnsActiveProfile() throws Exception {
        authenticateSync();
        when(auth.syncUser(any())).thenReturn(com.aws.alphabetuniverse.user.dto.UserResponse.builder()
                .id(7L).email("user@example.com").displayName("Google user")
                .role("learner").status("ACTIVE").build());

        mvc.perform(post("/api/auth/sync").servletPath("/api/auth/sync")
                        .header("Authorization", "Bearer pending-access"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.role").value("learner"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(users);
    }

    @Test
    void syncPrecheckConflictReturnsContract409WithoutTokens() throws Exception {
        assertSyncEmailConflict(new EmailAlreadyExistsException());
    }

    @ParameterizedTest
    @ValueSource(strings = {"users_email_key", "users_email_lower_uidx"})
    void syncUniqueRaceReturnsSame409Contract(String constraint) throws Exception {
        assertSyncEmailConflict(constraintFailure("23505", constraint));
    }

    @ParameterizedTest
    @ValueSource(strings = {"users_cognito_sub_key", "unrelated_unique_key"})
    void unrelatedDatabaseConstraintDoesNotBecomeAnEmailConflict(String constraint) throws Exception {
        authenticateSync();
        when(auth.syncUser(any())).thenThrow(constraintFailure("23505", constraint));

        mvc.perform(post("/api/auth/sync").servletPath("/api/auth/sync")
                        .header("Authorization", "Bearer pending-access"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").doesNotExist())
                .andExpect(content().string(not(containsString("private-db-details"))));
    }

    @Test
    void nonUniqueViolationWithAnEmailConstraintNameDoesNotBecome409() throws Exception {
        authenticateSync();
        when(auth.syncUser(any())).thenThrow(constraintFailure("23502", "users_email_key"));

        mvc.perform(post("/api/auth/sync").servletPath("/api/auth/sync")
                        .header("Authorization", "Bearer pending-access"))
                .andExpect(status().isInternalServerError());
    }

    @Test
    void syncBlockedResponseContainsNoProfileOrTokens() throws Exception {
        authenticateSync();
        when(auth.syncUser(any())).thenThrow(new AccountBlockedException());

        mvc.perform(post("/api/auth/sync").servletPath("/api/auth/sync")
                        .header("Authorization", "Bearer pending-access"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
        verifyNoInteractions(users);
    }

    private void authenticateSync() {
        when(decoder.decode("pending-access")).thenReturn(Jwt.withTokenValue("pending-access")
                .header("alg", "RS256").subject("new-google-sub").build());
    }

    private void assertSyncEmailConflict(RuntimeException failure) throws Exception {
        authenticateSync();
        when(auth.syncUser(any())).thenThrow(failure);

        mvc.perform(post("/api/auth/sync").servletPath("/api/auth/sync")
                        .header("Authorization", "Bearer pending-access"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(EmailAlreadyExistsException.CODE))
                .andExpect(jsonPath("$.message").value(EmailAlreadyExistsException.MESSAGE))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(content().string(not(containsString("private-db-details"))));
        verifyNoInteractions(users);
    }

    private DataIntegrityViolationException constraintFailure(String state, String constraint) {
        return new DataIntegrityViolationException("private-db-details",
                new ConstraintViolationException("private-db-details",
                        new SQLException("private-db-details", state), constraint));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({AuthController.class, SecurityConfig.class, CorsConfig.class, GlobalExceptionHandler.class})
    static class WebTestConfig {
        @Bean AuthService auth() { return mock(AuthService.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
    }
}
