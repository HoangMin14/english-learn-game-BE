package com.aws.alphabetuniverse.user.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CognitoUserAttributeServiceImplTest {

    private final CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
    private final CognitoUserAttributeServiceImpl service = new CognitoUserAttributeServiceImpl(client);

    @Test
    void sendsOnlyNameAttributeWithTheUsersAccessToken() {
        service.updateDisplayName("access-token", "New name");
        verify(client).updateUserAttributes(UpdateUserAttributesRequest.builder()
                .accessToken("access-token")
                .userAttributes(AttributeType.builder().name("name").value("New name").build())
                .build());
        verify(client, never()).getUser(any(GetUserRequest.class));
    }

    @Test
    void acceptsUpdateWhenLostResponseIsConfirmedByGetUser() {
        when(client.updateUserAttributes(any(UpdateUserAttributesRequest.class)))
                .thenThrow(SdkClientException.create("Response lost"));
        when(client.getUser(any(GetUserRequest.class))).thenReturn(profile("New name"));

        assertDoesNotThrow(() -> service.updateDisplayName("access-token", "New name"));
        var order = inOrder(client);
        order.verify(client).updateUserAttributes(any(UpdateUserAttributesRequest.class));
        order.verify(client).getUser(any(GetUserRequest.class));
    }

    @Test
    void returns503WhenLostResponseCannotBeConfirmed() {
        when(client.updateUserAttributes(any(UpdateUserAttributesRequest.class)))
                .thenThrow(SdkClientException.create("Response lost"));
        when(client.getUser(any(GetUserRequest.class))).thenReturn(profile("Old name"));

        var error = assertThrows(ResponseStatusException.class,
                () -> service.updateDisplayName("access-token", "New name"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void returns503WhenBothUpdateAndVerificationHaveNetworkErrors() {
        when(client.updateUserAttributes(any(UpdateUserAttributesRequest.class)))
                .thenThrow(SdkClientException.create("Response lost"));
        when(client.getUser(any(GetUserRequest.class)))
                .thenThrow(SdkClientException.create("Network unavailable"));

        var error = assertThrows(ResponseStatusException.class,
                () -> service.updateDisplayName("access-token", "New name"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
    }

    @Test
    void readsCurrentEmailAndName() {
        when(client.getUser(any(GetUserRequest.class))).thenReturn(profile("New name"));
        var result = service.getProfile("access-token");
        assertEquals("user@example.com", result.email());
        assertEquals("New name", result.displayName());
    }

    @ParameterizedTest
    @MethodSource("cognitoErrors")
    void translatesAwsErrorsForBothReadAndWrite(CognitoIdentityProviderException failure, HttpStatus expected) {
        when(client.getUser(any(GetUserRequest.class))).thenThrow(failure);
        when(client.updateUserAttributes(any(UpdateUserAttributesRequest.class))).thenThrow(failure);

        assertEquals(expected, assertThrows(ResponseStatusException.class,
                () -> service.getProfile("access-token")).getStatusCode());
        assertEquals(expected, assertThrows(ResponseStatusException.class,
                () -> service.updateDisplayName("access-token", "New name")).getStatusCode());
    }

    static Stream<Arguments> cognitoErrors() {
        return Stream.of(
                Arguments.of(NotAuthorizedException.builder().build(), HttpStatus.UNAUTHORIZED),
                Arguments.of(UserNotFoundException.builder().build(), HttpStatus.UNAUTHORIZED),
                Arguments.of(TooManyRequestsException.builder().build(), HttpStatus.TOO_MANY_REQUESTS),
                Arguments.of(ForbiddenException.builder().build(), HttpStatus.FORBIDDEN),
                Arguments.of(InvalidParameterException.builder().build(), HttpStatus.BAD_REQUEST),
                Arguments.of(CognitoIdentityProviderException.builder().statusCode(500).build(),
                        HttpStatus.SERVICE_UNAVAILABLE),
                Arguments.of(CognitoIdentityProviderException.builder().statusCode(400).build(),
                        HttpStatus.BAD_GATEWAY));
    }

    @Test
    void rejectsMissingAccessTokenWithoutCallingAws() {
        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> service.getProfile(" ")).getStatusCode());
        verifyNoInteractions(client);
    }

    private GetUserResponse profile(String name) {
        return GetUserResponse.builder().userAttributes(
                AttributeType.builder().name("email").value("user@example.com").build(),
                AttributeType.builder().name("name").value(name).build()).build();
    }
}
