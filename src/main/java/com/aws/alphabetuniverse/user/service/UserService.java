package com.aws.alphabetuniverse.user.service;

import com.aws.alphabetuniverse.user.dto.UserResponse;
import org.springframework.security.core.Authentication;

public interface UserService {
    public UserResponse getUserByCognitoSub(String cognitoSub);
    public UserResponse getCurrentUser(Authentication authentication);
}
