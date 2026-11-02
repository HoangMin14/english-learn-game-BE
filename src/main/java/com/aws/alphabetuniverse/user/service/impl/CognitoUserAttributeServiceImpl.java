package com.aws.alphabetuniverse.user.service.impl;

import com.aws.alphabetuniverse.user.dto.CognitoUserProfile;
import com.aws.alphabetuniverse.user.service.CognitoUserAttributeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ForbiddenException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GetUserResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InvalidParameterException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserLambdaValidationException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class CognitoUserAttributeServiceImpl implements CognitoUserAttributeService {

    private final CognitoIdentityProviderClient cognitoClient;

    @Override
    public CognitoUserProfile getProfile(String accessToken) {
        validateAccessToken(accessToken);
        try {
            GetUserResponse response = cognitoClient.getUser(
                    GetUserRequest.builder().accessToken(accessToken).build());
            String email = null;
            String displayName = null;
            for (AttributeType attribute : response.userAttributes()) {
                if ("email".equals(attribute.name())) {
                    email = attribute.value();
                } else if ("name".equals(attribute.name())) {
                    displayName = attribute.value();
                }
            }
            return new CognitoUserProfile(email, displayName);
        } catch (CognitoIdentityProviderException ex) {
            throw translateCognitoException(ex);
        } catch (SdkClientException ex) {
            throw unavailable("Unable to read Cognito profile", ex);
        }
    }

    @Override
    public void updateDisplayName(String accessToken, String displayName) {
        validateAccessToken(accessToken);
        try {
            cognitoClient.updateUserAttributes(UpdateUserAttributesRequest.builder()
                    .accessToken(accessToken)
                    .userAttributes(AttributeType.builder().name("name").value(displayName).build())
                    .build());
        } catch (CognitoIdentityProviderException ex) {
            throw translateCognitoException(ex);
        } catch (SdkClientException ex) {
            // A lost response does not prove the write failed. Verify before changing PostgreSQL.
            try {
                if (Objects.equals(displayName, getProfile(accessToken).displayName())) {
                    return;
                }
            } catch (ResponseStatusException verificationFailure) {
                log.warn("Cognito display name verification failed; status={}",
                        verificationFailure.getStatusCode().value());
            }
            throw unavailable("Unable to confirm Cognito update; retry or sync your profile", ex);
        }
    }

    private void validateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is required");
        }
    }

    private ResponseStatusException translateCognitoException(CognitoIdentityProviderException ex) {
        HttpStatus status;
        String message;
        if (ex instanceof NotAuthorizedException || ex instanceof UserNotFoundException) {
            status = HttpStatus.UNAUTHORIZED;
            message = "Cognito access token is invalid, expired or revoked";
        } else if (ex instanceof TooManyRequestsException) {
            status = HttpStatus.TOO_MANY_REQUESTS;
            message = "Cognito request limit exceeded";
        } else if (ex instanceof ForbiddenException) {
            status = HttpStatus.FORBIDDEN;
            message = "Cognito request was blocked by security policy";
        } else if (ex instanceof InvalidParameterException || ex instanceof UserLambdaValidationException) {
            status = HttpStatus.BAD_REQUEST;
            message = "Cognito rejected the user attributes";
        } else {
            status = ex.statusCode() >= 500 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
            message = "Cognito service is temporarily unavailable";
        }
        return new ResponseStatusException(status, message, ex);
    }

    private ResponseStatusException unavailable(String message, SdkClientException ex) {
        log.warn("Cognito request could not be confirmed; exception={}", ex.getClass().getSimpleName());
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message, ex);
    }
}
