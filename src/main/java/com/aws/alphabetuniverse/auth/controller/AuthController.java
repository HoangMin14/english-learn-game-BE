package com.aws.alphabetuniverse.auth.controller;

import com.aws.alphabetuniverse.auth.dto.*;
import com.aws.alphabetuniverse.auth.service.AuthService;
import com.aws.alphabetuniverse.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth")
public class AuthController {
    @Autowired
    private AuthService authService;

    @Operation(summary = "Đồng bộ hồ sơ Cognito vào database",
            description = "Trả 409 EMAIL_ALREADY_EXISTS nếu email thuộc Cognito sub khác. "
                    + "FE chỉ hoàn tất đăng nhập khi Sync trả user ACTIVE.",
            security = {@SecurityRequirement(name = "Bearer Authentication")})
    @PostMapping("/sync")
    public ResponseEntity<UserResponse> syncUser(
            @AuthenticationPrincipal Jwt jwt
    ){
        return ResponseEntity.ok(
                authService.syncUser(jwt)
        );
    }

    @PostMapping("/logout")
    public LogoutResponse logout(){
        return authService.logout();
    }

    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public ResetPasswordResponse resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        return authService.resetPassword(request);
    }

    @PostMapping("/register")
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }

    @PostMapping("/confirm-register")
    public ConfirmRegisterResponse confirmRegister(
            @Valid @RequestBody ConfirmRegisterRequest request
    ) {
        return authService.confirmRegister(request);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache").body(response);
    }

    @PostMapping("/google")
    public ResponseEntity<LoginResponse> loginWithGoogle(@Valid @RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.PRAGMA, "no-cache")
                .body(authService.loginWithGoogle(request));
    }

    @PostMapping("/resend-confirmation")
    public ResponseEntity<ResendConfirmationResponse> resendConfirmationCode(@Valid @RequestBody ResendConfirmationRequest request) {
        ResendConfirmationResponse response = authService.resendConfirmationCode(request.email());
        return ResponseEntity.ok(response);
    }

}
