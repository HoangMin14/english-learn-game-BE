package com.aws.alphabetuniverse.auth.dto;

import com.aws.alphabetuniverse.common.exception.CognitoOAuthException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;

public record GoogleAuthRequest(
        @Schema(description = "Authorization code do Cognito cấp, chỉ dùng một lần",
                example = "<authorization-code-from-Cognito>")
        @NotBlank(message = "Authorization code is required")
        @Size(max = 4096, message = "Authorization code is too long")
        String code,

        @Schema(description = "Phải khớp authorize request và callback đã đăng ký",
                example = "http://localhost:3000/callback")
        @NotBlank(message = "Redirect URI is required")
        @Size(max = 2048, message = "Redirect URI is too long")
        String redirectUri,

        @Schema(description = "Verifier gốc của giao dịch PKCE; không dùng giá trị mẫu khi chạy thật",
                example = "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG")
        @NotBlank(message = "PKCE code verifier is required")
        @Pattern(regexp = "[A-Za-z0-9._~-]{43,128}", message = "Invalid PKCE code verifier")
        String codeVerifier
) {
    public void validate() {
        if (code == null || code.isBlank() || code.length() > 4096
                || redirectUri == null || redirectUri.isBlank() || redirectUri.length() > 2048
                || codeVerifier == null || !codeVerifier.matches("[A-Za-z0-9._~-]{43,128}")) {
            throw new CognitoOAuthException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                    "Authorization code, redirect URI and a valid PKCE verifier are required");
        }
    }

    @Override
    public String toString() {
        return "GoogleAuthRequest[redacted]";
    }
}
