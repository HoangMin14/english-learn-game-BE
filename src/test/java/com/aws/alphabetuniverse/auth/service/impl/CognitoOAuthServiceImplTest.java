package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.auth.config.CognitoOAuthSettings;
import com.aws.alphabetuniverse.auth.dto.CognitoOAuthTokenResponse;
import com.aws.alphabetuniverse.auth.dto.GoogleAuthRequest;
import com.aws.alphabetuniverse.common.exception.CognitoOAuthException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CognitoOAuthServiceImplTest {
    private static final String ENDPOINT = "https://example.auth.us-east-1.amazoncognito.com/oauth2/token";
    private static final String CALLBACK = "http://localhost:5173/callback";
    private static final String VERIFIER = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String TOKENS = """
            {"access_token":"test-access","id_token":"test-id","refresh_token":"test-refresh",
             "expires_in":7200,"token_type":"Bearer"}
            """;
    private MockRestServiceServer server;
    private CognitoOAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        var settings = new CognitoOAuthSettings(
                "https://example.auth.us-east-1.amazoncognito.com", "client", CALLBACK,
                "http://localhost:5173", "oauth");
        service = new CognitoOAuthServiceImpl(builder.build(), settings);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void postsFormEncodedCodeAndVerifierAndReadsActualSnakeCaseTokenResponse() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", "client");
        form.add("code", "opaque+code&value=one");
        form.add("redirect_uri", CALLBACK);
        form.add("code_verifier", VERIFIER);
        server.expect(requestTo(ENDPOINT)).andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(form))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(TOKENS, MediaType.APPLICATION_JSON));

        var response = service.exchangeAuthorizationCode(request("opaque+code&value=one"));

        assertEquals("test-access", response.accessToken());
        assertEquals("test-id", response.idToken());
        assertEquals("test-refresh", response.refreshToken());
        assertEquals(7200, response.expiresIn());
    }

    @ParameterizedTest
    @CsvSource({
            "invalid_grant,400,OAUTH_CODE_INVALID", "invalid_client,502,COGNITO_OAUTH_CONFIG_ERROR",
            "unauthorized_client,502,COGNITO_OAUTH_CONFIG_ERROR",
            "invalid_scope,502,COGNITO_OAUTH_CONFIG_ERROR",
            "unsupported_grant_type,502,COGNITO_OAUTH_CONFIG_ERROR",
            "invalid_request,400,VALIDATION_ERROR", "access_denied,403,COGNITO_OAUTH_FORBIDDEN",
            "temporarily_unavailable,503,COGNITO_UNAVAILABLE",
            "server_error,503,COGNITO_UNAVAILABLE", "unrecognized,502,COGNITO_OAUTH_INVALID_RESPONSE"
    })
    void classifiesProviderErrorsWithoutExposingRawDescriptions(String error, int status, String code) {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"" + error + "\",\"error_description\":\"private-upstream-details\"}"));

        var failure = assertThrows(CognitoOAuthException.class,
                () -> service.exchangeAuthorizationCode(request("code")));

        assertEquals(status, failure.getStatusCode().value());
        assertEquals(code, failure.getCode());
        assertFalse(failure.toString().contains("private-upstream-details"));
    }

    @ParameterizedTest
    @CsvSource({"429,429,TOO_MANY_REQUESTS", "500,503,COGNITO_UNAVAILABLE",
            "503,503,COGNITO_UNAVAILABLE", "403,403,COGNITO_OAUTH_FORBIDDEN",
            "302,502,COGNITO_OAUTH_INVALID_RESPONSE", "401,502,COGNITO_OAUTH_INVALID_RESPONSE"})
    void doesNotParseHtmlAsTokensOrFollowRedirectResponses(int upstream, int status, String code) {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.valueOf(upstream))
                .contentType(MediaType.TEXT_HTML).body("<html>private-upstream-details</html>"));
        var failure = assertThrows(CognitoOAuthException.class,
                () -> service.exchangeAuthorizationCode(request("code")));
        assertEquals(status, failure.getStatusCode().value());
        assertEquals(code, failure.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "null", "[]", "{}",
            "{\"access_token\":\"a\",\"id_token\":\"i\",\"expires_in\":3600,\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"a\",\"id_token\":\"i\",\"refresh_token\":\"r\",\"expires_in\":0,\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"a\",\"id_token\":\"i\",\"refresh_token\":\"r\",\"token_type\":\"Bearer\"}",
            "{\"access_token\":\"a\",\"id_token\":\"i\",\"refresh_token\":\"r\",\"expires_in\":3600,\"token_type\":\"Other\"}",
            "{\"access_token\":\"a\",\"id_token\":\"i\",\"refresh_token\":\"r\",\"expires_in\":\"bad\",\"token_type\":\"Bearer\"}"})
    void rejectsMalformedOrIncompleteSuccessWithoutDefaultingTokenLifetime(String body) {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var failure = assertThrows(CognitoOAuthException.class,
                () -> service.exchangeAuthorizationCode(request("code")));
        assertEquals(HttpStatus.BAD_GATEWAY, failure.getStatusCode());
        assertEquals("COGNITO_OAUTH_INVALID_RESPONSE", failure.getCode());
    }

    @Test
    void mapsNetworkTimeoutTo503WithoutRetryingSingleUseCode() {
        server.expect(requestTo(ENDPOINT)).andRespond(request -> {
            throw new SocketTimeoutException("private-transport-details");
        });
        var failure = assertThrows(CognitoOAuthException.class,
                () -> service.exchangeAuthorizationCode(request("code")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, failure.getStatusCode());
        assertNull(failure.getCause());
    }

    @Test
    void refreshPreservesProviderRotationResponseAndUsesRefreshGrant() {
        server.expect(requestTo(ENDPOINT)).andExpect(content().string(
                        "grant_type=refresh_token&client_id=client&refresh_token=old%2Brefresh"))
                .andRespond(withSuccess(TOKENS, MediaType.APPLICATION_JSON));
        assertEquals("test-refresh", service.refreshTokens("old+refresh").refreshToken());
    }

    @Test
    void refreshCanOmitRefreshTokenWhenRotationIsDisabled() {
        server.expect(requestTo(ENDPOINT)).andRespond(withSuccess(
                TOKENS.replace("\"refresh_token\":\"test-refresh\",", ""), MediaType.APPLICATION_JSON));
        assertNull(service.refreshTokens("old-refresh").refreshToken());
    }

    @Test
    void invalidRefreshTokenIs401RatherThanGoogleCodeError() {
        server.expect(requestTo(ENDPOINT)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"invalid_grant\"}"));
        var failure = assertThrows(CognitoOAuthException.class, () -> service.refreshTokens("bad-refresh"));
        assertEquals(HttpStatus.UNAUTHORIZED, failure.getStatusCode());
        assertEquals("REFRESH_TOKEN_INVALID", failure.getCode());
    }

    @Test
    void validatesBeforeAnyHttpRequest() {
        assertThrows(CognitoOAuthException.class, () -> service.exchangeAuthorizationCode(null));
        assertThrows(CognitoOAuthException.class,
                () -> service.exchangeAuthorizationCode(new GoogleAuthRequest("code", CALLBACK, "short")));
        assertThrows(CognitoOAuthException.class,
                () -> service.exchangeAuthorizationCode(new GoogleAuthRequest("code", "https://evil.example", VERIFIER)));
        assertThrows(CognitoOAuthException.class, () -> service.refreshTokens(" "));
    }

    @Test
    void recordDiagnosticsNeverContainCodesVerifiersOrTokens() {
        assertFalse(request("private-code").toString().contains("private-code"));
        assertFalse(request("private-code").toString().contains(VERIFIER));
        var tokens = new CognitoOAuthTokenResponse("private-access", "private-id", "private-refresh", 3600, "Bearer");
        assertFalse(tokens.toString().contains("private-access"));
        assertFalse(tokens.toString().contains("private-refresh"));
    }

    private GoogleAuthRequest request(String code) {
        return new GoogleAuthRequest(code, CALLBACK, VERIFIER);
    }
}
