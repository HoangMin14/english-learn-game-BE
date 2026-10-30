package com.aws.alphabetuniverse.game.dto.admin;

public record GameConfigAdminResponse(
        Long topicId,
        String configKey,
        String globalValue,
        Boolean globalActive,
        String overrideValue,
        Boolean overrideActive,
        String effectiveValue,
        String source,
        String globalDescription,
        String overrideDescription
) {
}