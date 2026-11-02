package com.aws.alphabetuniverse.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMyProfileRequest(
        @NotBlank(message = "Display name is required") String displayName
) {
}
