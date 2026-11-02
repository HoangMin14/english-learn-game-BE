package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.auth.dto.RegisterRequest;
import com.aws.alphabetuniverse.user.dto.UserResponse;
import com.aws.alphabetuniverse.user.service.UserSyncService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.SignUpRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthProfileIntegrationTest {

    private final CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
    private final UserSyncService sync = mock(UserSyncService.class);
    private final AuthServiceImpl service = new AuthServiceImpl();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "cognitoClient", client);
        ReflectionTestUtils.setField(service, "userSyncService", sync);
        ReflectionTestUtils.setField(service, "clientId", "test-client");
    }

    @Test
    void syncDelegatesTokenWithoutReadingStaleCognitoAttributes() {
        Jwt jwt = Jwt.withTokenValue("access-token").header("alg", "RS256").subject("sub-7").build();
        var expected = UserResponse.builder().id(7L).status("ACTIVE").build();
        when(sync.syncUser("sub-7", "access-token")).thenReturn(expected);

        assertSame(expected, service.syncUser(jwt));
        verifyNoInteractions(client);
    }

    @Test
    void registerUsesTheSameNormalizedNameAsPatch() {
        var request = RegisterRequest.builder().email("user@example.com").password("test-password")
                .displayName("  Cafe\u0301  ").build();

        service.register(request);

        var sent = ArgumentCaptor.forClass(SignUpRequest.class);
        verify(client).signUp(sent.capture());
        assertEquals("Café", sent.getValue().userAttributes().stream()
                .filter(attribute -> "name".equals(attribute.name())).findFirst().orElseThrow().value());
    }

    @Test
    void registerRejectsOverlongNameBeforeCreatingCognitoAccount() {
        var request = RegisterRequest.builder().displayName("x".repeat(101)).build();

        var error = assertThrows(ResponseStatusException.class, () -> service.register(request));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(client);
    }
}
