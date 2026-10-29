package com.aws.alphabetuniverse.admin.service.impl;


import com.aws.alphabetuniverse.admin.service.AdminAccessService;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminAccessServiceImpl implements AdminAccessService {
    private final UserRepository userRepository;


    @Override
    public User requireActiveAdmin(Jwt jwt) {
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required"
            );
        }

        User user = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User has not been synchronized"
                ));

        if (!"ACTIVE".equalsIgnoreCase(
                String.valueOf(user.getStatus()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin account is not active"
            );
        }

        if (!"admin".equalsIgnoreCase(
                String.valueOf(user.getRole()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin role is required"
            );
        }

        return user;

    }
}
