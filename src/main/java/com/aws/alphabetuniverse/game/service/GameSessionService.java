package com.aws.alphabetuniverse.game.service;

import com.aws.alphabetuniverse.game.dto.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Optional;

public interface GameSessionService {
    public GameSessionResponse createSession(Long topicId, CreateGameSessionRequest request, Jwt jwt);
    public void abandonActiveSession(Long topicId, Jwt jwt);
    public GameHistoryPageResponse getMyGameHistory(Jwt jwt, int page, int size);
    public Optional<GameSessionResumeResponse> getActiveSession(Long topicId, Jwt jwt);
    public GameProgressResponse getMyProgress(Jwt jwt);
    public GameHistoryDetailResponse getMyGameHistoryDetail(
            Long sessionId,
            Jwt jwt
    );
}
