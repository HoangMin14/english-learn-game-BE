
package com.aws.alphabetuniverse.game.dto;

import com.aws.alphabetuniverse.game.enums.GameMode;

import jakarta.validation.constraints.NotNull;

public record CreateGameSessionRequest(
        @NotNull(message = "Game mode is required")
        GameMode mode
) {
}
