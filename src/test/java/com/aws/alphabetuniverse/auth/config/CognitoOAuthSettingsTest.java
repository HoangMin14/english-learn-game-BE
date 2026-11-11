package com.aws.alphabetuniverse.auth.config;

import com.aws.alphabetuniverse.common.exception.CognitoOAuthException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class CognitoOAuthSettingsTest {
    private static final String DOMAIN = "https://example.auth.us-east-1.amazoncognito.com";
    private static final String CALLBACK = "http://localhost:3000/callback";

    @Test
    void derivesExactLocalCallbackFromTrustedFrontendConfiguration() {
        var settings = new CognitoOAuthSettings(DOMAIN + "/", "client", "", "http://localhost:3000/", "sdk");
        assertEquals(URI.create(DOMAIN + "/oauth2/token"), settings.tokenEndpoint());
        assertDoesNotThrow(() -> settings.requireAllowedRedirectUri(CALLBACK));
        assertFalse(settings.useOAuthRefresh());
    }

    @Test
    void acceptsExplicitProductionAllowlistAndOAuthRefreshMode() {
        var settings = new CognitoOAuthSettings(DOMAIN, "client",
                CALLBACK + ", https://frontend.example/callback", "http://localhost:3000", "oauth");
        assertDoesNotThrow(() -> settings.requireAllowedRedirectUri("https://frontend.example/callback"));
        assertTrue(settings.useOAuthRefresh());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000/callback/extra", "http://localhost:3000/callback?next=attacker",
            "http://localhost:3000/callback/", "https://localhost:3000/callback",
            "http://localhost.evil.example:5173/callback", "http://localhost:3000@evil.example/callback",
            "http://localhost:3000/callback#fragment", "http://localhost:3000/callback ",
            "//evil.example/callback"
    })
    void rejectsRedirectsThatOnlyResembleTheAllowedUri(String redirect) {
        var settings = new CognitoOAuthSettings(DOMAIN, "client", CALLBACK, "http://localhost:3000", "sdk");
        var failure = assertThrows(CognitoOAuthException.class, () -> settings.requireAllowedRedirectUri(redirect));
        assertEquals(HttpStatus.BAD_REQUEST, failure.getStatusCode());
        assertEquals("OAUTH_REDIRECT_URI_INVALID", failure.getCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com", "https://user:secret@example.com", "https://example.com/oauth2",
            "https://example.com?x=1", "https://example.com#fragment", "https://example.com:8080", "bad origin"
    })
    void refusesMisconfiguredTokenEndpoints(String domain) {
        assertThrows(IllegalStateException.class,
                () -> new CognitoOAuthSettings(domain, "client", CALLBACK, "http://localhost:3000", "sdk"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://frontend.example/callback", "https://frontend.example/callback#fragment",
            "https://user@frontend.example/callback", "/callback", "https://*"})
    void refusesUnsafeCallbackConfiguration(String callback) {
        assertThrows(IllegalStateException.class,
                () -> new CognitoOAuthSettings(DOMAIN, "client", callback, "http://localhost:3000", "sdk"));
    }

    @Test
    void refusesUnknownRefreshModesRatherThanSilentlyChangingProtocol() {
        assertThrows(IllegalStateException.class,
                () -> new CognitoOAuthSettings(DOMAIN, "client", CALLBACK, "http://localhost:3000", "auto"));
    }
}
