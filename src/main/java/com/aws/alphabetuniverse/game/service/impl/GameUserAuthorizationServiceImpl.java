
package com.aws.alphabetuniverse.game.service.impl;

import com.aws.alphabetuniverse.game.service.GameUserAuthorizationService;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GameUserAuthorizationServiceImpl
        implements GameUserAuthorizationService {

    private static final Set<String> ALLOWED_ROLES =
            Set.of("LEARNER", "ADMIN");

    private final UserRepository userRepository;

    @Override
    public User findAuthenticatedUser(Jwt jwt) {
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication is required"
            );
        }

        return userRepository.findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));
    }

    @Override
    public User requireActiveGameUser(Jwt jwt) {
        User user = findAuthenticatedUser(jwt);
        assertActiveGameUser(user);
        return user;
    }

    @Override
    public void assertActiveGameUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authenticated user not found"
            );
        }

        String status = normalize(user.getStatus());

        if (!"ACTIVE".equals(status)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Account is not active"
            );
        }

        String role = normalize(user.getRole());

        if (role.startsWith("ROLE_")) {
            role = role.substring("ROLE_".length());
        }

        if (!ALLOWED_ROLES.contains(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User role is not allowed to play games"
            );
        }
    }

    private String normalize(Object value) {
        return value == null
                ? ""
                : value.toString()
                  .trim()
                  .toUpperCase(Locale.ROOT);
    }
}
