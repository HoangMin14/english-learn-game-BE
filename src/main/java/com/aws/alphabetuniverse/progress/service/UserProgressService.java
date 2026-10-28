package com.aws.alphabetuniverse.progress.service;

import java.time.LocalDateTime;

public interface UserProgressService {

    public void recordCorrectAnswer(Long userId, Long vocabularyId, LocalDateTime completedAt);
}