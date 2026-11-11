package com.aws.alphabetuniverse.auth.service;

import com.aws.alphabetuniverse.auth.dto.CognitoOAuthTokenResponse;
import com.aws.alphabetuniverse.auth.dto.GoogleAuthRequest;

public interface CognitoOAuthService {
    CognitoOAuthTokenResponse exchangeAuthorizationCode(GoogleAuthRequest request);

    CognitoOAuthTokenResponse refreshTokens(String refreshToken);
}
