package com.aws.alphabetuniverse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.services.cognitoidentityprovider.endpoints.internal.Value;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResetPasswordRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String code;

    @NotBlank
    private String newPassword;
}
