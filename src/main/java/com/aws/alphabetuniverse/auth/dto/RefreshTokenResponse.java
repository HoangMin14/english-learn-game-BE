package com.aws.alphabetuniverse.auth.dto;

public record RefreshTokenResponse(

        String accessToken,

        String idToken,

        String refreshToken,

        Integer expiresIn

) {
}