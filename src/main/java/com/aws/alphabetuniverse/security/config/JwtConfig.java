package com.aws.alphabetuniverse.security.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;

@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
            String issuer,

            @Value("${aws.cognito.client-id}")
            String clientId
    ) {
        if (issuer.isBlank() || clientId.isBlank()) {
            throw new IllegalStateException(
                    "Cognito issuer and client ID must be configured"
            );
        }

        return new SupplierJwtDecoder(() -> {
            NimbusJwtDecoder decoder =
                    NimbusJwtDecoder.withIssuerLocation(issuer)
                            .build();

            OAuth2TokenValidator<Jwt> cognitoAccessTokenValidator =
                    jwt -> {
                        boolean validTokenUse =
                                "access".equals(
                                        jwt.getClaims().get("token_use")
                                );

                        boolean validClient =
                                clientId.equals(
                                        jwt.getClaims().get("client_id")
                                );

                        if (!validTokenUse || !validClient) {
                            return OAuth2TokenValidatorResult.failure(
                                    new OAuth2Error(
                                            "invalid_token",
                                            "A Cognito access token from the configured app client is required",
                                            null
                                    )
                            );
                        }

                        return OAuth2TokenValidatorResult.success();
                    };

            decoder.setJwtValidator(
                    new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefaultWithIssuer(issuer),
                            cognitoAccessTokenValidator
                    )
            );

            return decoder;
        });
    }
}