package com.aws.alphabetuniverse.game.dto.admin;

public record GameConfigUpdateRequest(
        String configValue,
        Boolean active
) {
}