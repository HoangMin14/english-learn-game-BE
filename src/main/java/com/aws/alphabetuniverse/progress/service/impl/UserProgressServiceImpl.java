package com.aws.alphabetuniverse.progress.service.impl;

import com.aws.alphabetuniverse.progress.repository.UserProgressRepository;
import com.aws.alphabetuniverse.progress.service.UserProgressService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserProgressServiceImpl
        implements UserProgressService {

    private final UserProgressRepository userProgressRepository;

    @Override
    @Transactional
    public void recordCorrectAnswer(
            Long userId,
            Long vocabularyId,
            LocalDateTime completedAt
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(
                vocabularyId,
                "vocabularyId must not be null"
        );
        Objects.requireNonNull(
                completedAt,
                "completedAt must not be null"
        );

        userProgressRepository.upsertCorrectAnswer(
                userId,
                vocabularyId,
                completedAt
        );
    }
}