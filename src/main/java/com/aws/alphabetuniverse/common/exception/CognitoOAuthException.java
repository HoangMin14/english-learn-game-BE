package com.aws.alphabetuniverse.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class CognitoOAuthException extends ResponseStatusException {
    private final String code;

    public CognitoOAuthException(HttpStatus status, String code, String message) {
        super(status, message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static CognitoOAuthException invalidResponse() {
        return new CognitoOAuthException(HttpStatus.BAD_GATEWAY, "COGNITO_OAUTH_INVALID_RESPONSE",
                "Cognito returned an invalid authentication response");
    }

    public static CognitoOAuthException configurationError() {
        return new CognitoOAuthException(HttpStatus.BAD_GATEWAY, "COGNITO_OAUTH_CONFIG_ERROR",
                "Cognito sign-in configuration does not support this request");
    }
}
