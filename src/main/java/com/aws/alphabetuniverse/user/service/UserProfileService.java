package com.aws.alphabetuniverse.user.service;

import com.aws.alphabetuniverse.user.dto.UserResponse;

public interface UserProfileService {

    UserResponse updateMyProfile(String cognitoSub, String accessToken, String displayName);
}
