package com.aws.alphabetuniverse.user.service.impl;

import com.aws.alphabetuniverse.common.exception.AccountBlockedException;
import com.aws.alphabetuniverse.user.dto.CognitoUserProfile;
import com.aws.alphabetuniverse.user.dto.UserResponse;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.UserSyncService;
import com.aws.alphabetuniverse.user.service.CognitoUserAttributeService;
import com.aws.alphabetuniverse.user.util.DisplayNameNormalizer;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserSyncServiceImpl implements UserSyncService {

    private final UserRepository userRepository;

    private final CognitoUserAttributeService cognitoUserAttributeService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse syncUser(
            String cognitoSub,
            String accessToken
    ) {

        // 1. Validate input.
        if (cognitoSub == null || cognitoSub.isBlank()
                || accessToken == null || accessToken.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication information"
            );
        }

        // 2. Find and lock User immediately.
        User user = userRepository
                .findByCognitoSubForUpdate(cognitoSub)
                .orElse(null);

        // 3. First-time synchronization.
        if (user == null) {

            // Read Cognito profile to create local User.
            CognitoUserProfile initialProfile =
                    cognitoUserAttributeService.getProfile(accessToken);

            validateEmail(initialProfile.email());

            LocalDateTime now =
                    LocalDateTime.now(ZoneOffset.UTC);

            // Keep the existing 7-parameter repository method.
            userRepository.insertIfAbsent(
                    cognitoSub,
                    initialProfile.email().trim(),
                    DisplayNameNormalizer.normalizeFromCognito(initialProfile.displayName()),
                    "learner",
                    "ACTIVE",
                    now,
                    now
            );

            // Read again and lock the created/existing User.
            user = userRepository
                    .findByCognitoSubForUpdate(cognitoSub)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Unable to synchronize user"
                    ));
        }

        // 4. Check local account status.
        // Never automatically reactivate BLOCKED accounts.
        if ("BLOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AccountBlockedException();
        }
        if (!"ACTIVE".equalsIgnoreCase(
                String.valueOf(user.getStatus()))) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Your account is blocked or inactive"
            );
        }

        // 5. Read Cognito AFTER acquiring the database lock.
        // This prevents stale profile data from overwriting newer data.
        CognitoUserProfile currentProfile =
                cognitoUserAttributeService.getProfile(accessToken);

        validateEmail(currentProfile.email());

        String email = currentProfile.email().trim();

        String displayName =
                DisplayNameNormalizer.normalizeFromCognito(currentProfile.displayName());

        // 6. Check whether another account owns this email.
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(
                email,
                user.getId()
        )) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email is already used by another account"
            );
        }

        // 7. Update only changed fields.
        boolean changed = false;

        if (!Objects.equals(user.getEmail(), email)) {
            user.setEmail(email);
            changed = true;
        }

        // Keep existing displayName if Cognito has no valid name.
        if (displayName != null
                && !displayName.equals(user.getDisplayName())) {

            user.setDisplayName(displayName);
            changed = true;
        }

        // Update timestamp only when data changes.
        if (changed) {
            user.setUpdatedAt(
                    LocalDateTime.now(ZoneOffset.UTC)
            );
        }

        // Never modify role, status or createdAt.
        user = userRepository.saveAndFlush(user);

        // 8. Return response.
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }

    // ==========================================
    // VALIDATION
    // ==========================================

    private void validateEmail(String email) {

        if (email == null || email.isBlank()) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cognito profile does not contain a valid email"
            );
        }
    }

}
