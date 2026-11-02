package com.aws.alphabetuniverse.user.service.impl;

import com.aws.alphabetuniverse.user.dto.UserResponse;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;
import com.aws.alphabetuniverse.user.service.CognitoUserAttributeService;
import com.aws.alphabetuniverse.user.service.UserProfileService;
import com.aws.alphabetuniverse.user.util.DisplayNameNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserProfileServiceImpl implements UserProfileService {

    private final UserRepository userRepository;
    private final CognitoUserAttributeService cognitoUserAttributeService;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserResponse updateMyProfile(String cognitoSub, String accessToken, String displayName) {
        if (cognitoSub == null || cognitoSub.isBlank() || accessToken == null || accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        String normalizedName = DisplayNameNormalizer.normalizeRequired(displayName);
        User user = userRepository.findByCognitoSubForUpdate(cognitoSub)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User has not been synchronized"));
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is blocked or inactive");
        }

        // Always reconcile Cognito, even if PostgreSQL already contains the requested name.
        cognitoUserAttributeService.updateDisplayName(accessToken, normalizedName);

        if (!Objects.equals(user.getDisplayName(), normalizedName)) {
            warnIfLocalCommitFails(user.getId());
            user.setDisplayName(normalizedName);
            user.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
            user = userRepository.saveAndFlush(user);
        }
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .role(user.getRole())
                .status(user.getStatus())
                .build();
    }

    private void warnIfLocalCommitFails(Long userId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        log.warn("Profile transaction did not commit after Cognito update; userId={}. "
                                + "Retry PATCH /api/users/me or POST /api/auth/sync", userId);
                    }
                }
            });
        }
    }
}
