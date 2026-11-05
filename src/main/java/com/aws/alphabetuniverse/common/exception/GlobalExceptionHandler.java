package com.aws.alphabetuniverse.common.exception;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccountBlockedException.class)
    public ResponseEntity<Map<String, String>> handleAccountBlocked(AccountBlockedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "code", AccountBlockedException.CODE,
                "message", AccountBlockedException.MESSAGE
        ));
    }

    @ExceptionHandler(CognitoUserStatusSyncException.class)
    public ResponseEntity<Map<String, String>> handleCognitoStatusSync(CognitoUserStatusSyncException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(Map.of("code", exception.getCode(), "message", exception.getReason()));
    }

    // Các constraint/index UNIQUE email đã xác nhận trên DB.
    private static final Set<String> EMAIL_UNIQUE_CONSTRAINTS =
            Set.of(
                    "users_email_key",
                    "users_email_lower_uidx"
            );

    // Preserve the service's HTTP status instead of matching a nested Cognito exception as 400.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request failed" : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(Map.of("message", message));
    }


    // ==========================================
    // COGNITO EXCEPTIONS
    // ==========================================

    @ExceptionHandler(CodeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleCodeMismatch() {

        return badRequest("Verification code is incorrect");
    }


    @ExceptionHandler(ExpiredCodeException.class)
    public ResponseEntity<Map<String, String>> handleExpiredCode() {

        return badRequest("Verification code has expired");
    }


    @ExceptionHandler(InvalidPasswordException.class)
    public ResponseEntity<Map<String, String>> handleInvalidPassword() {

        return badRequest(
                "Password does not meet Cognito password policy"
        );
    }


    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleUserNotFound() {

        return badRequest("User not found");
    }


    @ExceptionHandler(CognitoIdentityProviderException.class)
    public ResponseEntity<Map<String, String>> handleCognitoException(
            CognitoIdentityProviderException exception
    ) {
        HttpStatus status;
        String message;

        if (exception instanceof TooManyRequestsException
                || exception instanceof LimitExceededException) {

            status = HttpStatus.TOO_MANY_REQUESTS;
            message = "Request limit reached. Please try again later";

        } else if (exception instanceof UsernameExistsException
                || exception instanceof AliasExistsException) {

            status = HttpStatus.CONFLICT;
            message = "An account with these details already exists";

        } else if (exception instanceof CodeDeliveryFailureException) {

            status = HttpStatus.BAD_GATEWAY;
            message = "Unable to deliver the verification code";

        } else if (exception instanceof InvalidParameterException
                || exception instanceof UserLambdaValidationException) {

            status = HttpStatus.BAD_REQUEST;
            message = "Cognito rejected the request parameters";

        } else if (exception instanceof NotAuthorizedException) {

            // Ví dụ: thao tác xác nhận không phù hợp trạng thái tài khoản.
            // Login đã tự xử lý NotAuthorizedException thành 401.
            status = HttpStatus.BAD_REQUEST;
            message = "This operation is not allowed for the account";

        } else if (exception instanceof InternalErrorException
                || exception.statusCode() >= 500) {

            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Authentication service is temporarily unavailable";

        } else {

            status = HttpStatus.BAD_GATEWAY;
            message = "Cognito could not complete the request";
        }

        return ResponseEntity.status(status)
                .body(Map.of("message", message));
    }


    // ==========================================
    // RESOURCE NOT FOUND
    // ==========================================

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleResourceNotFound(
            ResourceNotFoundException exception
    ) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Map.of("message", exception.getMessage()));
    }


    // ==========================================
    // DATABASE CONSTRAINT VIOLATION
    // ==========================================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>>
    handleDataIntegrityViolation(
            DataIntegrityViolationException exception
    ) {

        // Chỉ lỗi UNIQUE email mới trả 409.
        if (isEmailUniqueViolation(exception)) {

            return ResponseEntity
                    .status(HttpStatus.CONFLICT)
                    .body(Map.of(
                            "message",
                            "Email is already used by another account"
                    ));
        }

        // Các lỗi database khác không được đánh đồng với lỗi email.
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "message",
                        "Database constraint violation"
                ));
    }


    // ==========================================
    // CHECK EMAIL UNIQUE VIOLATION
    // ==========================================

    private boolean isEmailUniqueViolation(
            Throwable exception
    ) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof ConstraintViolationException violation) {

                String sqlState = violation.getSQLState();

                String constraintName = violation.getConstraintName();

                // SQLState 23505 = unique_violation.
                if ("23505".equals(sqlState)
                        && constraintName != null
                        && EMAIL_UNIQUE_CONSTRAINTS.contains(
                        constraintName.toLowerCase(Locale.ROOT)
                )) {

                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }


    // ==========================================
    // COMMON RESPONSE
    // ==========================================

    private ResponseEntity<Map<String, String>> badRequest(
            String message
    ) {

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", message));
    }

    @ExceptionHandler(SdkClientException.class)
    public ResponseEntity<Map<String, String>> handleSdkClientException(
            SdkClientException exception
    ) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "message",
                        "Unable to connect to the external service"
                ));
    }
}
