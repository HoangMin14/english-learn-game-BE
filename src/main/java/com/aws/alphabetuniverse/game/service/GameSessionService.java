package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.game.dto.CreateGameSessionRequest;
import com.aws.alphabetuniverse.game.dto.GameSessionResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface GameSessionService {
    public GameSessionResponse createSession(Long topicId, CreateGameSessionRequest request, Jwt jwt);
    public void abandonActiveSession(Long topicId, Jwt jwt);
}
