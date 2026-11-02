package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.auth.dto.*;
import com.aws.alphabetuniverse.auth.dto.ForgotPasswordRequest;
import com.aws.alphabetuniverse.auth.dto.ForgotPasswordResponse;
import com.aws.alphabetuniverse.auth.service.AuthService;
import com.aws.alphabetuniverse.user.dto.UserResponse;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.UserSyncService;
import com.aws.alphabetuniverse.user.util.DisplayNameNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import org.springframework.beans.factory.annotation.Value;

import software.amazon.awssdk.services.cognitoidentityprovider.model.UnsupportedOperationException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {
    @Autowired
    private UserSyncService userSyncService;

    @Autowired
    private CognitoIdentityProviderClient cognitoClient;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Value("${aws.cognito.client-id}")
    private String clientId;

    @Value("${aws.cognito.domain}")
    private String cognitoDomain;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    private static final Logger log =
            LoggerFactory.getLogger(AuthServiceImpl.class);


    @Override
    public UserResponse syncUser(Jwt jwt) {

        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()
                || jwt.getTokenValue() == null
                || jwt.getTokenValue().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        // Read Cognito inside UserSyncService only after locking the local user.
        return userSyncService.syncUser(
                jwt.getSubject(),
                jwt.getTokenValue()
        );
    }



    @Override
    public LogoutResponse logout() {
        String logoutUrl = cognitoDomain
                        + "/logout"
                        + "?client_id="
                        + clientId
                        + "&logout_uri="
                        + frontendUrl
                        + "/login";
        return LogoutResponse.builder()
                .logoutUrl(logoutUrl)
                .build();
    }

    @Override
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request) {

        software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest cognitoRequest =
                software.amazon.awssdk.services.cognitoidentityprovider.model.ForgotPasswordRequest.builder()
                        .clientId(clientId)
                        .username(request.getEmail())
                        .build();
        cognitoClient.forgotPassword(cognitoRequest);
        return ForgotPasswordResponse.builder().message("Verification code has been sent to your email").build();
    }

    @Override
    public ResetPasswordResponse resetPassword(ResetPasswordRequest request) {
        ConfirmForgotPasswordRequest cognitoRequest = ConfirmForgotPasswordRequest.builder()
                        .clientId(clientId)
                        .username(request.getEmail())
                        .confirmationCode(request.getCode())
                        .password(request.getNewPassword())
                        .build();
        cognitoClient.confirmForgotPassword( cognitoRequest);

        return ResetPasswordResponse.builder().message("Password reset successfully").build();
    }

    @Override
    public RegisterResponse register(RegisterRequest request) {
        String displayName = DisplayNameNormalizer.normalizeRequired(request.getDisplayName());
        SignUpRequest cognitoRequest =
                SignUpRequest.builder()
                        .clientId(clientId)
                        .username(request.getEmail())
                        .password(request.getPassword())
                        .userAttributes(AttributeType.builder()
                                        .name("email")
                                        .value(request.getEmail())
                                        .build(),
                                AttributeType.builder()
                                        .name("name")
                                        .value(displayName)
                                        .build()).build();

        cognitoClient.signUp( cognitoRequest );
        return RegisterResponse.builder().message("Register successfully. Please check your email for verification code").build();
    }

    @Override
    public ConfirmRegisterResponse confirmRegister(ConfirmRegisterRequest request) {
        ConfirmSignUpRequest cognitoRequest =
                ConfirmSignUpRequest.builder()
                        .clientId(clientId)
                        .username(request.getEmail())
                        .confirmationCode(request.getCode())
                        .build();

        cognitoClient.confirmSignUp(cognitoRequest);

        return ConfirmRegisterResponse.builder()
                .message("Account verified successfully").build();
    }

    @Override
    public LoginResponse login(LoginRequest request) {

        // Bảo vệ cả trường hợp service được gọi trực tiếp.
        if (request == null
                || request.getEmail() == null
                || request.getEmail().isBlank()
                || request.getPassword() == null
                || request.getPassword().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email and password are required"
            );
        }

        String email = request.getEmail().trim();

        // Giữ nguyên mật khẩu, không trim().
        String password = request.getPassword();

        try {
            InitiateAuthResponse response =
                    cognitoClient.initiateAuth(
                            InitiateAuthRequest.builder()
                                    .clientId(clientId)
                                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                                    .authParameters(
                                            Map.of(
                                                    "USERNAME", email,
                                                    "PASSWORD", password
                                            )
                                    )
                                    .build()
                    );

            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Cognito returned an empty response"
                );
            }

            // Cognito có thể yêu cầu MFA hoặc đổi mật khẩu.
            String challenge = response.challengeNameAsString();

            if (challenge != null && !challenge.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Additional sign-in step is required: " + challenge
                );
            }

            AuthenticationResultType result =
                    response.authenticationResult();

            if (result == null
                    || result.accessToken() == null
                    || result.accessToken().isBlank()
                    || result.idToken() == null
                    || result.idToken().isBlank()) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Cognito returned an invalid authentication result"
                );
            }

            return LoginResponse.builder()
                    .accessToken(result.accessToken())
                    .idToken(result.idToken())
                    .refreshToken(result.refreshToken())
                    .expiresIn(result.expiresIn())
                    .build();

        } catch (NotAuthorizedException | UserNotFoundException ex) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Email or password is incorrect",
                    ex
            );

        } catch (UserNotConfirmedException ex) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Please confirm your account before signing in",
                    ex
            );

        } catch (PasswordResetRequiredException ex) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Password reset is required",
                    ex
            );

        } catch (TooManyRequestsException ex) {

            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many sign-in requests. Please try again later",
                    ex
            );

        } catch (CognitoIdentityProviderException ex) {

            throw new ResponseStatusException(
                    ex.statusCode() >= 500
                            ? HttpStatus.SERVICE_UNAVAILABLE
                            : HttpStatus.BAD_GATEWAY,
                    "Cognito could not complete the sign-in request",
                    ex
            );

        } catch (SdkClientException ex) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to connect to Cognito",
                    ex
            );
        }
    }

    @Override
    public RefreshTokenResponse refreshToken(
            RefreshTokenRequest request
    ) {

        // 1. Validate request
        if (request == null
                || request.refreshToken() == null
                || request.refreshToken().isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Refresh token is required"
            );
        }

        String refreshToken = request.refreshToken().trim();

        try {

            // 2. Build AuthParameters
            Map<String, String> authParameters = new HashMap<>();

            authParameters.put(
                    "REFRESH_TOKEN",
                    request.refreshToken().trim()
            );

            // 3. Gửi request đến AWS Cognito
            InitiateAuthRequest cognitoRequest =
                    InitiateAuthRequest.builder()
                            .authFlow(AuthFlowType.REFRESH_TOKEN_AUTH)
                            .clientId(clientId)
                            .authParameters(authParameters)
                            .build();

            InitiateAuthResponse cognitoResponse =
                    cognitoClient.initiateAuth(cognitoRequest);

            // 4. Kiểm tra AuthenticationResult
            if (cognitoResponse.authenticationResult() == null) {

                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Unable to refresh authentication session"
                );
            }

            var authenticationResult =
                    cognitoResponse.authenticationResult();

            String newAccessToken =
                    authenticationResult.accessToken();

            String newIdToken =
                    authenticationResult.idToken();

            if (newAccessToken == null
                    || newAccessToken.isBlank()
                    || newIdToken == null
                    || newIdToken.isBlank()) {

                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid authentication result"
                );
            }

            // 5. Verify chữ ký và các claim JWT
            Jwt jwt;

            try {

                jwt = jwtDecoder.decode(newAccessToken);

            } catch (JwtException ex) {

                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid access token"
                );
            }

            // Chỉ chấp nhận Access Token của Cognito.
            Object tokenUse =
                    jwt.getClaims().get("token_use");

            if (!"access".equals(tokenUse)) {

                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid token type"
                );
            }

            // Kiểm tra đúng App Client.
            Object tokenClientId =
                    jwt.getClaims().get("client_id");

            if (!clientId.equals(tokenClientId)) {

                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid token client"
                );
            }

            // 6. Lấy Cognito Sub từ JWT đã xác minh.
            String cognitoSub = jwt.getSubject();

            if (cognitoSub == null || cognitoSub.isBlank()) {

                throw new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid user identity"
                );
            }

            // 7. Kiểm tra user tồn tại trong PostgreSQL.
            User user = userRepository
                    .findByCognitoSub(cognitoSub)
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.UNAUTHORIZED,
                                    "User not found"
                            )
                    );

            // 8. Chỉ cho phép tài khoản ACTIVE.
            String userStatus =
                    String.valueOf(user.getStatus());

            if (!"ACTIVE".equalsIgnoreCase(userStatus)) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Account is not active"
                );
            }

            // 9. Trả token mới.
            // REFRESH_TOKEN_AUTH không cấp refresh token mới
            // khi Rotation bị tắt.
            return new RefreshTokenResponse(
                    newAccessToken,
                    newIdToken,
                    refreshToken,
                    authenticationResult.expiresIn()
            );

        } catch (ResponseStatusException ex) {
            throw ex;
        }

// Refresh Token sai, hết hạn hoặc không còn hợp lệ.
        catch (NotAuthorizedException ex) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Refresh token is invalid or expired"
            );
        }

// Cognito giới hạn số request.
        catch (TooManyRequestsException ex) {

            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many refresh requests"
            );
        }

// AWS WAF chặn request.
        catch (ForbiddenException ex) {

            log.error("Cognito request blocked by AWS WAF", ex);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cognito request was blocked by security policy"
            );
        }

// Request parameter không hợp lệ.
        catch (InvalidParameterException ex) {

            log.error("Invalid Cognito refresh parameters", ex);

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid refresh request"
            );
        }

        // App Client hoặc User Pool cấu hình sai.
        catch (InvalidUserPoolConfigurationException
               | UnsupportedOperationException ex) {

            log.error("Cognito refresh configuration error", ex);

            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Cognito refresh configuration error"
            );
        }

        // Cognito gặp lỗi nội bộ.
        catch (InternalErrorException ex) {

            log.error("Cognito internal error", ex);

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cognito service is temporarily unavailable"
            );
        }

        // Những lỗi Cognito khác.
        catch (CognitoIdentityProviderException ex) {

            log.error("Unexpected Cognito error", ex);

            if (ex.statusCode() >= 500) {

                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Cognito service is unavailable"
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cognito could not process refresh request"
            );
        }

        // Lỗi kết nối AWS SDK.
        catch (SdkClientException ex) {

            log.error("Cannot connect to Cognito", ex);

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Authentication service is temporarily unavailable"
            );
        }
    }

    @Override
    public ResendConfirmationResponse resendConfirmationCode(String email) {

        // 1. Validate email
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email is required"
            );
        }

        // 2. Normalize email
        String normalizedEmail = email.trim();

        // 3. Generic response
        String message =
                "If the account is eligible for confirmation, " +
                        "a new code will be sent to the registered contact method.";

        try {

            // 4. Build Cognito request
            ResendConfirmationCodeRequest request =
                    ResendConfirmationCodeRequest.builder()
                            .clientId(clientId)
                            .username(normalizedEmail)
                            .build();

            // 5. Call AWS Cognito
            cognitoClient.resendConfirmationCode(request);

            // 6. Return success
            return new ResendConfirmationResponse(message);

        } catch (TooManyRequestsException | LimitExceededException ex) {

            // Cognito rate limit
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Too many requests. Please try again later."
            );

        } catch (UserNotFoundException | NotAuthorizedException ex) {

            // Hide account existence and confirmation status
            return new ResendConfirmationResponse(message);

        } catch (InvalidParameterException ex) {

            // Do not hide possible AWS configuration errors
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cognito rejected the confirmation request"
            );

        } catch (CodeDeliveryFailureException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cognito could not deliver the confirmation code"
            );

        } catch (InternalErrorException ex) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Cognito service is temporarily unavailable"
            );

        } catch (SdkClientException ex) {

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to connect to AWS Cognito"
            );

        } catch (CognitoIdentityProviderException ex) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "AWS Cognito returned an unexpected error"
            );
        }
    }
}
