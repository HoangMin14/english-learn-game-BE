package com.aws.alphabetuniverse.user.controller;

import com.aws.alphabetuniverse.security.config.SecurityConfig;
import com.aws.alphabetuniverse.common.exception.GlobalExceptionHandler;
import com.aws.alphabetuniverse.user.dto.UserResponse;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.UserProfileService;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class UserProfileControllerTest {

    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private UserRepository users;
    private UserProfileService profiles;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebTestConfig.class);
        context.refresh();
        users = context.getBean(UserRepository.class);
        profiles = context.getBean(UserProfileService.class);
        decoder = context.getBean(JwtDecoder.class);
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean("springSecurityFilterChain", Filter.class)).build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void validBearerTokenUpdatesOnlyAuthenticatedUsersProfile() throws Exception {
        activeUser();
        when(profiles.updateMyProfile("sub-7", "access-token", "New name"))
                .thenReturn(UserResponse.builder().id(7L).email("user@example.com")
                        .displayName("New name").role("learner").status("ACTIVE").build());

        mvc.perform(patch("/api/users/me").servletPath("/api/users/me")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New name\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.displayName").value("New name"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        verify(profiles).updateMyProfile("sub-7", "access-token", "New name");
    }

    @Test
    void missingBearerTokenReturns401() throws Exception {
        mvc.perform(patch("/api/users/me").servletPath("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New name\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void invalidBearerTokenReturns401() throws Exception {
        when(decoder.decode("invalid-token")).thenThrow(new BadJwtException("Invalid token"));
        mvc.perform(patch("/api/users/me").servletPath("/api/users/me")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New name\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void blockedUserReturns403BeforeReachingController() throws Exception {
        activeUser();
        when(users.findByCognitoSub("sub-7"))
                .thenReturn(Optional.of(User.builder().status("BLOCKED").build()));
        mvc.perform(patch("/api/users/me").servletPath("/api/users/me")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New name\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profiles);
    }

    @Test
    void unsynchronizedUserReturns401() throws Exception {
        activeUser();
        when(users.findByCognitoSub("sub-7")).thenReturn(Optional.empty());
        mvc.perform(patch("/api/users/me").servletPath("/api/users/me")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New name\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profiles);
    }

    @Test
    void blankOrMissingNameReturns400() throws Exception {
        activeUser();
        for (String body : new String[]{"{}", "{\"displayName\":\"   \"}"}) {
            mvc.perform(patch("/api/users/me").servletPath("/api/users/me")
                            .header("Authorization", "Bearer access-token")
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(profiles);
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 429, 503})
    void globalHandlerPreservesServiceStatusInsteadOfReturning400ForNestedAwsError(int httpStatus)
            throws Exception {
        activeUser();
        when(profiles.updateMyProfile("sub-7", "access-token", "New name"))
                .thenThrow(new ResponseStatusException(HttpStatusCode.valueOf(httpStatus), "Profile update failed",
                        CognitoIdentityProviderException.builder().statusCode(400).message("AWS error").build()));

        mvc.perform(patch("/api/users/me").servletPath("/api/users/me")
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\":\"New name\"}"))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.message").value("Profile update failed"));
    }

    private void activeUser() {
        when(decoder.decode("access-token")).thenReturn(Jwt.withTokenValue("access-token")
                .header("alg", "RS256").subject("sub-7").claim("token_use", "access")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());
        when(users.findByCognitoSub("sub-7"))
                .thenReturn(Optional.of(User.builder().id(7L).status("ACTIVE").build()));
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, UserProfileController.class, GlobalExceptionHandler.class})
    static class WebTestConfig {
        @Bean
        UserRepository users() {
            return mock(UserRepository.class);
        }

        @Bean
        UserProfileService profiles() {
            return mock(UserProfileService.class);
        }

        @Bean
        JwtDecoder decoder() {
            return mock(JwtDecoder.class);
        }
    }
}
