package com.aws.alphabetuniverse.auth.dto;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Builder
public class ForgotPasswordRequest {
    private String email;
}
