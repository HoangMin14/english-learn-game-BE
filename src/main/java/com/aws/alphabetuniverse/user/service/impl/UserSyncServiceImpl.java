package com.aws.alphabetuniverse.user.service.impl;

import com.aws.alphabetuniverse.common.exception.AccountBlockedException;
import com.aws.alphabetuniverse.common.exception.CognitoOAuthException;
import com.aws.alphabetuniverse.common.exception.EmailAlreadyExistsException;
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
        CognitoUserProfile currentProfile = null;

        // 3. First-time synchronization.
        if (user == null) {

            // Read Cognito profile to create local User.
            CognitoUserProfile initialProfile = readProfile(accessToken);
            String initialEmail = initialProfile.email().trim();

            // Precheck before INSERT; UNIQUE remains the final protection against races.
            User emailOwner = userRepository.findByEmailIgnoreCase(initialEmail).orElse(null);
            if (emailOwner != null && !cognitoSub.equals(emailOwner.getCognitoSub())) {
                throw new EmailAlreadyExistsException();
            }

            LocalDateTime now =
                    LocalDateTime.now(ZoneOffset.UTC);

            // Keep the existing 7-parameter repository method.
            int inserted = userRepository.insertIfAbsent(
                    cognitoSub,
                    initialEmail,
                    initialDisplayName(initialProfile),
                    "learner",
                    "ACTIVE",
                    now,
                    now
            );

            // insertIfAbsent clears the persistence context, including any emailOwner.
            // Lock a fresh entity even if another Sync created the same sub meanwhile.
            user = userRepository
                    .findByCognitoSubForUpdate(cognitoSub)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            "Unable to synchronize user"
                    ));

            // We own a newly inserted row and can reuse its initial profile.
            // INSERT 0 means another request won; reread Cognito after locking below.
            if (inserted == 1) {
                currentProfile = initialProfile;
            }
        }

        // 4. Check local account status.
        // Never automatically reactivate BLOCKED accounts.
        if ("BLOCKED".equalsIgnoreCase(user.getStatus())) {
            throw new AccountBlockedException();
        }
        if (!"ACTIVE".equalsIgnoreCase(
                String.valueOf(user.getStatus()))) {

            throw new CognitoOAuthException(
                    HttpStatus.FORBIDDEN,
                    "ACCOUNT_INACTIVE",
                    "Account is not active"
            );
        }

        // 5. Read Cognito AFTER acquiring the database lock.
        // This prevents stale profile data from overwriting newer data.
        if (currentProfile == null) {
            currentProfile = readProfile(accessToken);
        }

        String email = currentProfile.email().trim();

        String displayName =
                DisplayNameNormalizer.normalizeFromCognito(currentProfile.displayName());

        // 6. Check whether another account owns this email.
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(
                email,
                user.getId()
        )) {

            throw new EmailAlreadyExistsException();
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

    private CognitoUserProfile readProfile(String accessToken) {
        CognitoUserProfile profile = cognitoUserAttributeService.getProfile(accessToken);
        if (profile == null || profile.email() == null || profile.email().isBlank()) {

            throw new CognitoOAuthException(
                    HttpStatus.BAD_GATEWAY,
                    "COGNITO_OAUTH_INVALID_RESPONSE",
                    "Cognito profile does not contain a valid email"
            );
        }
        return profile;
    }

    private String initialDisplayName(CognitoUserProfile profile) {
        String name = DisplayNameNormalizer.normalizeFromCognito(profile.displayName());
        if (name != null) {
            return name;
        }
        String email = profile.email().strip();
        int separator = email.indexOf('@');
        String fallback = DisplayNameNormalizer.normalizeFromCognito(
                separator >= 0 ? email.substring(0, separator) : email);
        if (fallback == null) {
            return "Learner";
        }
        int end = fallback.offsetByCodePoints(0, Math.min(100, fallback.codePointCount(0, fallback.length())));
        return fallback.substring(0, end);
    }

}
