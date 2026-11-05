package com.aws.alphabetuniverse.common.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/** An upstream failure that must not undo a local account block. */
public class CognitoUserStatusSyncException extends ResponseStatusException {

    private final String code;

    public CognitoUserStatusSyncException(HttpStatusCode status, String message, Throwable cause) {
        this(status, "COGNITO_STATUS_SYNC_FAILED", message, cause);
    }

    public CognitoUserStatusSyncException(HttpStatusCode status, String code, String message, Throwable cause) {
        super(status, message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
