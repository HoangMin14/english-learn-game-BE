package com.aws.alphabetuniverse.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ResendConfirmationRequest(

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email

) {
}