package com.aws.alphabetuniverse.auth.config;

import com.aws.alphabetuniverse.auth.service.AuthService;
import com.aws.alphabetuniverse.auth.service.CognitoOAuthService;
import com.aws.alphabetuniverse.auth.service.impl.AuthServiceImpl;
import com.aws.alphabetuniverse.auth.service.impl.CognitoOAuthServiceImpl;
import com.aws.alphabetuniverse.config.RestClientConfig;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.CognitoUserAttributeService;
import com.aws.alphabetuniverse.user.service.UserSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CognitoOAuthWiringTest {
    @Test
    void productionOAuthBeansWireWithLegacySdkDefaultsAndNoExternalRequests() {
        try (var context = context(false)) {
            assertInstanceOf(AuthServiceImpl.class, context.getBean(AuthService.class));
            assertInstanceOf(CognitoOAuthServiceImpl.class, context.getBean(CognitoOAuthService.class));
            var settings = context.getBean(CognitoOAuthSettings.class);
            assertFalse(settings.useOAuthRefresh());
            assertDoesNotThrow(() -> settings.requireAllowedRedirectUri("http://localhost:3000/callback"));
            verifyNoInteractions(context.getBean(CognitoIdentityProviderClient.class),
                    context.getBean(UserRepository.class), context.getBean(JwtDecoder.class));
        }
    }

    @Test
    void explicitEnvironmentOverridesWorkWithoutEditingIgnoredApplicationProperties() {
        try (var context = context(true)) {
            var settings = context.getBean(CognitoOAuthSettings.class);
            assertTrue(settings.useOAuthRefresh());
            assertDoesNotThrow(() -> settings.requireAllowedRedirectUri("https://frontend.example/callback"));
            verifyNoInteractions(context.getBean(CognitoIdentityProviderClient.class),
                    context.getBean(UserRepository.class));
        }
    }

    private AnnotationConfigApplicationContext context(boolean oauth) {
        var environment = new MockEnvironment()
                .withProperty("aws.cognito.domain", "https://example.auth.us-east-1.amazoncognito.com")
                .withProperty("aws.cognito.client-id", "client")
                .withProperty("app.frontend-url", "http://localhost:3000");
        if (oauth) {
            environment.withProperty("AWS_COGNITO_ALLOWED_REDIRECT_URIS", "https://frontend.example/callback")
                    .withProperty("AWS_COGNITO_REFRESH_MODE", "oauth");
        }
        var context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        context.register(TestConfig.class);
        context.refresh();
        return context;
    }

    @Configuration
    @Import({CognitoOAuthSettings.class, RestClientConfig.class,
            CognitoOAuthServiceImpl.class, AuthServiceImpl.class})
    static class TestConfig {
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean CognitoIdentityProviderClient sdk() { return mock(CognitoIdentityProviderClient.class); }
        @Bean UserSyncService sync() { return mock(UserSyncService.class); }
        @Bean CognitoUserAttributeService profiles() { return mock(CognitoUserAttributeService.class); }
    }
}
