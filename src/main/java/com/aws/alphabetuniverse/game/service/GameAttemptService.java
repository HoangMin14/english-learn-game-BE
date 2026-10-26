package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.game.dto.SubmitAttemptRequest;
import com.aws.alphabetuniverse.game.dto.SubmitAttemptResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface GameAttemptService {

    SubmitAttemptResponse submitAttempt(
            Long sessionId,
            SubmitAttemptRequest request,
            Jwt jwt
    );
}