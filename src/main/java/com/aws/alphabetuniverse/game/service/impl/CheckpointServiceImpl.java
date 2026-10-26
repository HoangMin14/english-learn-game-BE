
package com.aws.alphabetuniverse.game.service.impl;

import com.aws.alphabetuniverse.game.entity.GameSession;
import com.aws.alphabetuniverse.game.repository.GameSessionRepository;
import com.aws.alphabetuniverse.game.service.CheckpointService;
import com.aws.alphabetuniverse.vocabulary.entity.Vocabulary;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CheckpointServiceImpl implements CheckpointService {

    private final GameSessionRepository gameSessionRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean hasWonCheckpoint(
            Long userId,
            Long topicId,
            int batchStart,
            int batchEnd,
            List<Vocabulary> activeVocabularies
    ) {
        if (batchStart < 1
                || batchEnd < batchStart
                || activeVocabularies == null
                || batchEnd > activeVocabularies.size()) {
            return false;
        }

        // Lấy tập ID từ vựng thuộc checkpoint cần kiểm tra.
        Set<Long> expectedIds = new HashSet<>();

        for (Vocabulary vocabulary :
                activeVocabularies.subList(batchStart - 1, batchEnd)) {
            expectedIds.add(vocabulary.getId());
        }

        // Lấy các phiên CHECKPOINT của người dùng trong topic.
        List<GameSession> previousCheckpoints =
                gameSessionRepository
                        .findAllByUser_IdAndTopic_IdAndModeOrderByStartedAtDesc(
                                userId,
                                topicId,
                                "CHECKPOINT"
                        );

        // Chỉ công nhận checkpoint đã thắng nếu trạng thái,
        // kết quả và tập ID câu hỏi đều khớp.
        return previousCheckpoints.stream()
                .anyMatch(session -> {
                    List<Long> savedIds = session.getQuestionVocabIds();

                    return "COMPLETED".equals(session.getStatus())
                            && Boolean.TRUE.equals(session.getWon())
                            && savedIds != null
                            && savedIds.size() == expectedIds.size()
                            && new HashSet<>(savedIds).equals(expectedIds);
                });
    }
}
