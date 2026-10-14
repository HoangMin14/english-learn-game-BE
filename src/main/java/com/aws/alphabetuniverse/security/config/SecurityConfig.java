package com.aws.alphabetuniverse.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        System.out.println("SECURITY CONFIG LOADED");

        http
                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/error"
                        )
                        .permitAll()

                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/confirm-register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/logout"
                        ).permitAll()

                        .requestMatchers("/api/auth/sync")
                        .authenticated()

                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(
                        oauth2 -> oauth2
                                .jwt(Customizer.withDefaults())
                );

        return http.build();
    }
}
