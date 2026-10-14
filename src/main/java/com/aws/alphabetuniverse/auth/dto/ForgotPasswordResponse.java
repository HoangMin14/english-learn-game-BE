package com.aws.alphabetuniverse.auth.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Data
@Builder
public class ForgotPasswordResponse {
    private String message;
}
