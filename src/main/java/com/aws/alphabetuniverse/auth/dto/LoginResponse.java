package com.aws.alphabetuniverse.auth.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String accessToken;

    private String idToken;

    private String refreshToken;

    private Integer expiresIn;
}
