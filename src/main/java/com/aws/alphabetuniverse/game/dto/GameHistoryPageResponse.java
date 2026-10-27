package com.aws.alphabetuniverse.game.dto;

import java.util.List;

public record GameHistoryPageResponse(
        List<GameHistoryItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}