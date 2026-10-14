package com.aws.alphabetuniverse.auth.controller;

import com.aws.alphabetuniverse.auth.dto.*;
import com.aws.alphabetuniverse.auth.service.AuthService;
import com.aws.alphabetuniverse.user.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
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

    @Operation(security = {@SecurityRequirement(name = "Bearer Authentication")})
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
            @RequestBody ForgotPasswordRequest request
    ){
        return authService.forgotPassword(request);
    }

    @PostMapping("/reset-password")
    public ResetPasswordResponse resetPassword(
            @RequestBody ResetPasswordRequest request
    ){
        return authService.resetPassword(request);
    }

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody RegisterRequest request){
        return authService.register(request);
    }

    @PostMapping("/confirm-register")
    public ConfirmRegisterResponse confirmRegister(@RequestBody ConfirmRegisterRequest request){
        return authService.confirmRegister(request);
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request){
        return authService.login(request);
    }

}
