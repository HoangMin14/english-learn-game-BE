package com.aws.alphabetuniverse.auth.service;

import com.aws.alphabetuniverse.auth.dto.*;
import com.aws.alphabetuniverse.user.dto.UserResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {
    public UserResponse syncUser(Jwt jwt);
    public LogoutResponse logout();
    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest request);
    public ResetPasswordResponse resetPassword( ResetPasswordRequest request );
    public RegisterResponse register(RegisterRequest request);
    public ConfirmRegisterResponse confirmRegister (ConfirmRegisterRequest request);
    public LoginResponse login(LoginRequest request);
}
