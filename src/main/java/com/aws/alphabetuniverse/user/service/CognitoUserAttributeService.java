package com.aws.alphabetuniverse.user.service;

import com.aws.alphabetuniverse.user.dto.CognitoUserProfile;

public interface CognitoUserAttributeService {

    CognitoUserProfile getProfile(String accessToken);

    void updateDisplayName(String accessToken, String displayName);
}
