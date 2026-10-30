package com.aws.alphabetuniverse.game.dto.admin;

import java.util.List;

public record GameConfigAuditPageResponse(

        List<GameConfigAuditListItemResponse> content,

        int page,

        int size,

        long totalElements,

        int totalPages

) {
}