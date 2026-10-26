
package com.aws.alphabetuniverse.vocabulary.dto;

import java.util.List;

public record AudioBackfillResponse(
        Long topicId,
        int totalMissing,
        int generated,
        int failed,
        List<Long> failedVocabularyIds
) {
}
