package com.aws.alphabetuniverse.security.config;

import com.aws.alphabetuniverse.security.filter.ActiveUserFilter;
import com.aws.alphabetuniverse.user.repository.UserRepository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserRepository userRepository
    ) throws Exception {

        http
                .cors(Customizer.withDefaults())

                .csrf(csrf -> csrf.disable())

                .authorizeHttpRequests(auth -> auth

                        .requestMatchers(
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/error"
                        ).permitAll()

                        .requestMatchers(
                                "/api/auth/register",
                                "/api/auth/confirm-register",
                                "/api/auth/login",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password",
                                "/api/auth/logout",
                                "/api/auth/resend-confirmation"
                        ).permitAll()

                        .requestMatchers("/api/auth/sync")
                        .authenticated()

                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/refresh"
                        ).permitAll()

                        .anyRequest()
                        .authenticated()
                )

                .oauth2ResourceServer(
                        oauth2 -> oauth2
                                .jwt(Customizer.withDefaults())
                );

        // Kiểm tra User ACTIVE sau khi JWT được xác thực.
        // Tạo trực tiếp Filter trong Security Chain,
        // không đăng ký thêm như Servlet Filter độc lập.
        http.addFilterAfter(
                new ActiveUserFilter(userRepository),
                BearerTokenAuthenticationFilter.class
        );

        return http.build();
    }
}