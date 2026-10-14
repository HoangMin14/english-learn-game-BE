package com.aws.alphabetuniverse.user.service.impl;

import com.aws.alphabetuniverse.user.dto.UserResponse;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    @Autowired
    private  UserRepository userRepository;

    @Override
    public UserResponse getUserByCognitoSub(String cognitoSub) {
        User user = userRepository.findByCognitoSub(cognitoSub).orElseThrow(() -> new RuntimeException("User not found"));
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .build();
    }

    @Override
    public UserResponse getCurrentUser(Authentication authentication) {
        String cognitoSub = authentication.getName();

        User user = userRepository
                .findByCognitoSub(cognitoSub)
                .orElseThrow(
                        () -> new RuntimeException("User not found")
                );

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }
}
