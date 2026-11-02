package com.aws.alphabetuniverse.user.service;

import com.aws.alphabetuniverse.user.dto.UserResponse;

public interface UserSyncService {

    UserResponse syncUser(
            String cognitoSub,
            String accessToken
    );
}