package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.auth.service.CognitoUserAdminService;
import com.aws.alphabetuniverse.common.exception.CognitoUserStatusSyncException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminDisableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminEnableUserRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.CognitoIdentityProviderException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.NotAuthorizedException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ResourceNotFoundException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.TooManyRequestsException;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserNotFoundException;

import java.net.URI;

@Service
public class CognitoUserAdminServiceImpl implements CognitoUserAdminService {

    private static final Logger log = LoggerFactory.getLogger(CognitoUserAdminServiceImpl.class);
    private final CognitoIdentityProviderClient cognitoClient;
    private final String userPoolId;

    public CognitoUserAdminServiceImpl(
            CognitoIdentityProviderClient cognitoClient,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri
    ) {
        this.cognitoClient = cognitoClient;
        // Use the same pool as the JWT verifier instead of hardcoding a second pool ID.
        String path = URI.create(issuerUri).getPath();
        if (path == null || !path.matches("/[\\w-]+_[a-zA-Z0-9]+")) {
            throw new IllegalArgumentException("Cognito issuer URI must identify one user pool");
        }
        this.userPoolId = path.substring(1);
    }

    @Override
    public void disableUser(String cognitoSub) {
        setEnabled(cognitoSub, false);
    }

    @Override
    public void enableUser(String cognitoSub) {
        setEnabled(cognitoSub, true);
    }

    private void setEnabled(String cognitoSub, boolean enabled) {
        if (cognitoSub == null || cognitoSub.isBlank()) {
            throw new CognitoUserStatusSyncException(HttpStatus.BAD_GATEWAY, "COGNITO_IDENTITY_MISSING",
                    "Tài khoản chưa có định danh Cognito hợp lệ.", null);
        }

        try {
            if (enabled) {
                cognitoClient.adminEnableUser(AdminEnableUserRequest.builder()
                        .userPoolId(userPoolId).username(cognitoSub).build());
            } else {
                cognitoClient.adminDisableUser(AdminDisableUserRequest.builder()
                        .userPoolId(userPoolId).username(cognitoSub).build());
            }
        } catch (TooManyRequestsException ex) {
            throw failure(HttpStatus.TOO_MANY_REQUESTS, "COGNITO_RATE_LIMITED",
                    "Cognito đang giới hạn số yêu cầu. Vui lòng thử lại sau.", enabled, ex);
        } catch (UserNotFoundException ex) {
            throw failure(HttpStatus.BAD_GATEWAY, "COGNITO_USER_NOT_FOUND",
                    "Không tìm thấy User trên Cognito. Kiểm tra cognitoSub và User Pool.", enabled, ex);
        } catch (ResourceNotFoundException ex) {
            throw failure(HttpStatus.BAD_GATEWAY, "COGNITO_POOL_NOT_FOUND",
                    "Không tìm thấy User Pool trên Cognito. Kiểm tra cấu hình User Pool và region.", enabled, ex);
        } catch (CognitoIdentityProviderException ex) {
            String providerCode = providerCode(ex);
            if (ex instanceof NotAuthorizedException
                    || "AccessDenied".equalsIgnoreCase(providerCode)
                    || "AccessDeniedException".equalsIgnoreCase(providerCode)) {
                throw failure(HttpStatus.BAD_GATEWAY, "COGNITO_ACCESS_DENIED",
                        "AWS từ chối thao tác quản trị Cognito. Kiểm tra quyền IAM và credentials của backend.",
                        enabled, ex);
            }
            throw failure(ex.statusCode() >= 500
                            ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY,
                    "COGNITO_SERVICE_ERROR",
                    "Chưa thể đồng bộ trạng thái tài khoản với Cognito. Vui lòng thử lại.", enabled, ex);
        } catch (SdkClientException ex) {
            throw failure(HttpStatus.SERVICE_UNAVAILABLE, "COGNITO_CLIENT_ERROR",
                    "Backend chưa gọi được AWS. Kiểm tra AWS credentials, kết nối mạng và timeout.", enabled, ex);
        }
    }

    private CognitoUserStatusSyncException failure(
            HttpStatus status, String code, String message, boolean enabled, RuntimeException ex
    ) {
        // No access tokens, credentials, email addresses or raw provider messages in the log.
        String providerCode = ex instanceof CognitoIdentityProviderException provider
                ? providerCode(provider) : "N/A";
        String requestId = ex instanceof CognitoIdentityProviderException provider
                ? provider.requestId() : null;
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        log.warn("Cognito account status sync failed. enabled={}, status={}, code={}, providerCode={}, "
                        + "requestId={}, exception={}, rootCause={}",
                enabled, status.value(), code, providerCode, requestId,
                ex.getClass().getSimpleName(), root.getClass().getSimpleName());
        return new CognitoUserStatusSyncException(status, code, message, ex);
    }

    private String providerCode(CognitoIdentityProviderException exception) {
        return exception.awsErrorDetails() == null ? "UNKNOWN" : exception.awsErrorDetails().errorCode();
    }
}
