package com.aws.alphabetuniverse.auth.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LogoutResponse {
    private String logoutUrl;
}
