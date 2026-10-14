package com.aws.alphabetuniverse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisterRequest {
    @Email
    @NotBlank
    private String email;


    @NotBlank
    private String password;


    @NotBlank
    private String displayName;
}
