package com.aws.alphabetuniverse.game.dto.admin;

import java.util.UUID;

public record GameConfigAuditContext(

        Long actorUserId,

        String actorCognitoSub,

        UUID operationId

) {

    public GameConfigAuditContext {

        if (actorUserId == null || actorUserId <= 0) {
            throw new IllegalArgumentException(
                    "Invalid actor user ID"
            );
        }

        if (actorCognitoSub == null
                || actorCognitoSub.isBlank()) {
            throw new IllegalArgumentException(
                    "Invalid Cognito sub"
            );
        }

        if (operationId == null) {
            throw new IllegalArgumentException(
                    "Operation ID is required"
            );
        }

        actorCognitoSub = actorCognitoSub.trim();
    }
}