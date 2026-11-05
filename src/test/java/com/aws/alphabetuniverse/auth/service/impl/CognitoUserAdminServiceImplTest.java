package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.common.exception.CognitoUserStatusSyncException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CognitoUserAdminServiceImplTest {

    private final CognitoIdentityProviderClient client = mock(CognitoIdentityProviderClient.class);
    private final CognitoUserAdminServiceImpl service = new CognitoUserAdminServiceImpl(
            client, "https://cognito-idp.us-east-1.amazonaws.com/us-east-1_TestPool"
    );

    @Test
    void disablesTheCognitoSubInTheSamePoolAsTheJwtIssuer() {
        service.disableUser("sub-7");

        var sent = ArgumentCaptor.forClass(AdminDisableUserRequest.class);
        verify(client).adminDisableUser(sent.capture());
        assertEquals("sub-7", sent.getValue().username());
        assertEquals("us-east-1_TestPool", sent.getValue().userPoolId());
    }

    @Test
    void enablesTheSameCognitoIdentity() {
        service.enableUser("sub-7");

        var sent = ArgumentCaptor.forClass(AdminEnableUserRequest.class);
        verify(client).adminEnableUser(sent.capture());
        assertEquals("sub-7", sent.getValue().username());
        assertEquals("us-east-1_TestPool", sent.getValue().userPoolId());
    }

    @Test
    void mapsThrottlingTo429() {
        when(client.adminDisableUser(any(AdminDisableUserRequest.class)))
                .thenThrow(TooManyRequestsException.builder().message("provider limit").build());

        var error = assertThrows(CognitoUserStatusSyncException.class, () -> service.disableUser("sub-7"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, error.getStatusCode());
        assertEquals("COGNITO_RATE_LIMITED", error.getCode());
    }

    @Test
    void mapsNetworkTimeoutTo503() {
        when(client.adminEnableUser(any(AdminEnableUserRequest.class)))
                .thenThrow(SdkClientException.create("network timeout"));

        var error = assertThrows(CognitoUserStatusSyncException.class, () -> service.enableUser("sub-7"));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, error.getStatusCode());
        assertEquals("COGNITO_CLIENT_ERROR", error.getCode());
    }

    @Test
    void mapsCognitoServerFailureTo503() {
        when(client.adminDisableUser(any(AdminDisableUserRequest.class)))
                .thenThrow(InternalErrorException.builder().statusCode(500).build());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(CognitoUserStatusSyncException.class,
                () -> service.disableUser("sub-7")).getStatusCode());
    }

    @Test
    void backendIamFailureIsNotReportedAsTheAdminsInvalidLogin() {
        when(client.adminDisableUser(any(AdminDisableUserRequest.class)))
                .thenThrow(NotAuthorizedException.builder().statusCode(403).message("IAM denied").build());

        var error = assertThrows(CognitoUserStatusSyncException.class, () -> service.disableUser("sub-7"));

        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertEquals("COGNITO_ACCESS_DENIED", error.getCode());
        assertFalse(error.getReason().contains("IAM denied"));
    }

    @Test
    void missingCloudUserIsAnUpstreamSyncFailure() {
        when(client.adminEnableUser(any(AdminEnableUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().statusCode(400).build());

        var error = assertThrows(CognitoUserStatusSyncException.class, () -> service.enableUser("sub-7"));
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertEquals("COGNITO_USER_NOT_FOUND", error.getCode());
    }

    @Test
    void classifiesGenericAwsAccessDeniedByProviderCodeWithoutLeakingTheProviderMessage() {
        when(client.adminDisableUser(any(AdminDisableUserRequest.class)))
                .thenThrow(CognitoIdentityProviderException.builder().statusCode(403).requestId("request-test-1")
                        .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDeniedException")
                                .errorMessage("sensitive provider details").build()).build());

        var error = assertThrows(CognitoUserStatusSyncException.class, () -> service.disableUser("sub-7"));

        assertEquals("COGNITO_ACCESS_DENIED", error.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
        assertTrue(error.getReason().contains("IAM"));
        assertFalse(error.getReason().contains("sensitive provider details"));
    }

    @Test
    void identifiesAMissingUserPoolSeparatelyFromAMissingUser() {
        when(client.adminEnableUser(any(AdminEnableUserRequest.class)))
                .thenThrow(ResourceNotFoundException.builder().statusCode(400).build());

        var error = assertThrows(CognitoUserStatusSyncException.class, () -> service.enableUser("sub-7"));

        assertEquals("COGNITO_POOL_NOT_FOUND", error.getCode());
        assertEquals(HttpStatus.BAD_GATEWAY, error.getStatusCode());
    }

    @Test
    void missingCognitoIdentityDoesNotMakeAnAwsRequest() {
        for (String sub : new String[]{null, "", "  "}) {
            assertThrows(CognitoUserStatusSyncException.class, () -> service.disableUser(sub));
        }
        verifyNoInteractions(client);
    }

    @Test
    void rejectsAnIssuerThatDoesNotIdentifyOnePool() {
        assertThrows(IllegalArgumentException.class,
                () -> new CognitoUserAdminServiceImpl(client, "https://example.test/"));
        assertThrows(IllegalArgumentException.class,
                () -> new CognitoUserAdminServiceImpl(client, "https://example.test/pool/extra"));
        verifyNoInteractions(client);
    }
}
