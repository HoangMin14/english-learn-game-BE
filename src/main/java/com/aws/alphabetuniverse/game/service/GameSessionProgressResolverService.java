
package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.game.entity.GameQuestionSnapshot;
import com.aws.alphabetuniverse.game.entity.GameSession;
import com.aws.alphabetuniverse.game.entity.SessionAttempt;

import java.util.List;
import java.util.Map;

public interface GameSessionProgressResolverService {

    Progress validateAndResolve(
            GameSession session,
            List<SessionAttempt> loadedAttempts
    );

    interface Progress {

        String mode();

        List<Long> questionIds();

        Map<Long, GameQuestionSnapshot> snapshotById();

        int maxAttemptsPerWord();

        List<SessionAttempt> attempts();

        List<SessionAttempt> attemptsFor(Long vocabularyId);

        int attemptsUsed(Long vocabularyId);

        boolean hasCorrectAttempt(Long vocabularyId);

        boolean isResolved(Long vocabularyId);

        Long findNextUnresolved();

        int resolvedQuestions();

        String questionStatus(
                Long vocabularyId,
                Long currentVocabularyId
        );
    }
}
