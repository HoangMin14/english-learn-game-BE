package com.aws.alphabetuniverse.auth.service.impl;

import com.aws.alphabetuniverse.auth.dto.*;
import com.aws.alphabetuniverse.auth.dto.ForgotPasswordRequest;
import com.aws.alphabetuniverse.auth.dto.ForgotPasswordResponse;
import com.aws.alphabetuniverse.auth.service.AuthService;
import com.aws.alphabetuniverse.user.dto.UserResponse;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
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
    public UserResponse syncUser(Jwt jwt){

        String cognitoSub = jwt.getSubject();

        String email = jwt.getClaim("email");

        String name = jwt.getClaim("name");


        User user = userRepository
                .findByCognitoSub(cognitoSub)
                .orElseGet(() -> {

                    User newUser = User.builder()
                            .cognitoSub(cognitoSub)
                            .email(email)
                            .displayName(name)
                            .role("learner")
                            .status("ACTIVE")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    return userRepository.save(newUser);
                });


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
