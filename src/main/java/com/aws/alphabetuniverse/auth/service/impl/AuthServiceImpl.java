package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.auth.dto.*;
import com.aws.alphabetuniverse.auth.dto.ForgotPasswordRequest;
import com.aws.alphabetuniverse.auth.dto.ForgotPasswordResponse;
import com.aws.alphabetuniverse.auth.service.AuthService;
import com.aws.alphabetuniverse.user.dto.UserResponse;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CognitoIdentityProviderClient cognitoClient;

    @Value("${aws.cognito.client-id}")
    private String clientId;

    @Value("${aws.cognito.domain}")
    private String cognitoDomain;

    @Value("${app.frontend-url}")
    private String frontendUrl;


    @Override
    @Transactional
    public UserResponse syncUser(Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required"
            );
        }

        // 1. Lấy danh tính từ Access Token đã được Spring Security xác thực
        String cognitoSub = jwt.getSubject();

        // 2. Gọi Cognito lấy thông tin tài khoản
        GetUserResponse cognitoUser = cognitoClient.getUser(
                GetUserRequest.builder()
                        .accessToken(jwt.getTokenValue())
                        .build()
        );

        // 3. Lấy email và tên từ thuộc tính Cognito
        String email = cognitoUser.userAttributes().stream()
                .filter(attr -> "email".equals(attr.name()))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);

        String displayName = cognitoUser.userAttributes().stream()
                .filter(attr -> "name".equals(attr.name()))
                .map(AttributeType::value)
                .findFirst()
                .orElse(null);

        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Email not found in Cognito"
            );
        }

        LocalDateTime now = LocalDateTime.now();

        // 4. Tìm người dùng hiện có; nếu chưa có thì tạo mới
        User user = userRepository.findByCognitoSub(cognitoSub)
                .orElseGet(() -> User.builder()
                        .cognitoSub(cognitoSub)
                        .email(email)
                        .displayName(displayName)
                        .role("learner")
                        .status("ACTIVE")
                        .createdAt(now)
                        .updatedAt(now)
                        .build());

        // 5. Đồng bộ lại thông tin từ Cognito
        user.setEmail(email);

        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }

        if (user.getRole() == null || user.getRole().isBlank()) {
            user.setRole("learner");
        }

        if (user.getStatus() == null || user.getStatus().isBlank()) {
            user.setStatus("ACTIVE");
        }

        user.setUpdatedAt(now);

        // 6. Lưu vào PostgreSQL
        user = userRepository.save(user);

        // 7. Trả response
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
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
                                        .value(request.getDisplayName())
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
        InitiateAuthRequest initiateAuthRequest = InitiateAuthRequest.builder()
                        .clientId(clientId)
                        .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                        .authParameters(Map.of("USERNAME", request.getEmail(), "PASSWORD", request.getPassword()))
                        .build();
        InitiateAuthResponse response = cognitoClient.initiateAuth(initiateAuthRequest);

        AuthenticationResultType result = response.authenticationResult();

        return LoginResponse.builder()
                .accessToken(result.accessToken())
                .idToken(result.idToken())
                .refreshToken(result.refreshToken())
                .expiresIn(result.expiresIn())
                .build();
    }
}
