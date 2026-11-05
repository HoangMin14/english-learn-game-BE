package com.aws.alphabetuniverse.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.DefaultCorsProcessor;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {
    @ParameterizedTest
    @CsvSource({
            "http://localhost:3000,/api/auth/login",
            "http://localhost:3000,/api/auth/register",
            "http://localhost:5173,/api/auth/login",
            "http://localhost:5173,/api/auth/register"
    })
    void defaultConfigurationAllowsFrontendPreflight(String origin, String path) throws Exception {
        var response = preflight(defaultOrigins(), origin, path);
        assertEquals(200, response.getStatus());
        assertEquals(origin, response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void unlistedOriginRemainsForbidden() throws Exception {
        var response = preflight(defaultOrigins(), "https://untrusted.example", "/api/auth/login");
        assertEquals(403, response.getStatus());
        assertNull(response.getHeader("Access-Control-Allow-Origin"));
    }

    @Test
    void explicitOriginConfigurationOverridesLocalDefaults() throws Exception {
        var response = preflight("https://frontend.example", "http://localhost:3000", "/api/auth/login");
        assertEquals(403, response.getStatus());
        assertEquals(200, preflight("https://frontend.example", "https://frontend.example",
                "/api/auth/login").getStatus());
    }

    private String defaultOrigins() throws Exception {
        var properties = new Properties();
        try (var stream = getClass().getResourceAsStream("/application.properties")) {
            assertNotNull(stream);
            properties.load(stream);
        }
        return new MockEnvironment().resolveRequiredPlaceholders(properties.getProperty("app.cors.allowed-origins"));
    }

    private MockHttpServletResponse preflight(String origins, String origin, String path) throws Exception {
        var source = new CorsConfig().corsConfigurationSource(origins);
        var request = new MockHttpServletRequest("OPTIONS", path);
        request.setServerName("localhost");
        request.setServerPort(8080);
        request.addHeader("Origin", origin);
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "content-type,authorization");
        var response = new MockHttpServletResponse();
        new DefaultCorsProcessor().processRequest(source.getCorsConfiguration(request), request, response);
        return response;
    }
}
