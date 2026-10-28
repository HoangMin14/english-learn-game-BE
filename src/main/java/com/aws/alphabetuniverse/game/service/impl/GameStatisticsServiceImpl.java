package com.aws.alphabetuniverse.game.service.impl;

import com.aws.alphabetuniverse.game.dto.GameStatisticsResponse;
import com.aws.alphabetuniverse.game.repository.GameSessionRepository;
import com.aws.alphabetuniverse.game.repository.SessionAttemptRepository;
import com.aws.alphabetuniverse.game.repository.projection.GameStatisticsSessionAggregate;
import com.aws.alphabetuniverse.game.repository.projection.GameStatisticsAttemptAggregate;
import com.aws.alphabetuniverse.game.service.GameStatisticsService;
import com.aws.alphabetuniverse.game.service.GameUserAuthorizationService;
import com.aws.alphabetuniverse.user.entity.User;

import lombok.RequiredArgsConstructor;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class GameStatisticsServiceImpl
        implements GameStatisticsService {

    private final GameUserAuthorizationService
            gameUserAuthorizationService;

    private final GameSessionRepository gameSessionRepository;

    private final SessionAttemptRepository sessionAttemptRepository;

    @Override
    @Transactional(
            readOnly = true,
            isolation = Isolation.REPEATABLE_READ
    )
    public GameStatisticsResponse getMyGameStatistics(Jwt jwt) {

        // 1. Xác thực người dùng và kiểm tra quyền.
        // Chỉ user ACTIVE có role LEARNER hoặc ADMIN được truy cập.
        User user = gameUserAuthorizationService
                .requireActiveGameUser(jwt);

        Long userId = user.getId();

        // 2. Lấy thống kê các phiên game.
        GameStatisticsSessionAggregate sessionStats =
                gameSessionRepository.getStatisticsByUserId(userId);

        // 3. Lấy thống kê các lượt trả lời.
        GameStatisticsAttemptAggregate attemptStats =
                sessionAttemptRepository.getStatisticsByUserId(userId);

        // 4. Chuẩn hóa kết quả null thành 0.
        long totalSessions = valueOrZero(
                sessionStats.getTotalSessions()
        );

        long wonSessions = valueOrZero(
                sessionStats.getWonSessions()
        );

        long totalCheckpointScore = valueOrZero(
                sessionStats.getTotalCheckpointScore()
        );

        long totalAttempts = valueOrZero(
                attemptStats.getTotalAttempts()
        );

        long correctAttempts = valueOrZero(
                attemptStats.getCorrectAttempts()
        );

        // 5. Tính tỷ lệ trả lời đúng.
        BigDecimal accuracyRate = BigDecimal.ZERO
                .setScale(2, RoundingMode.HALF_UP);

        if (totalAttempts > 0) {
            accuracyRate = BigDecimal.valueOf(correctAttempts)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(
                            BigDecimal.valueOf(totalAttempts),
                            2,
                            RoundingMode.HALF_UP
                    );
        }

        // 6. Trả DTO cho Controller.
        return new GameStatisticsResponse(
                totalSessions,
                wonSessions,
                totalCheckpointScore,
                totalAttempts,
                correctAttempts,
                accuracyRate
        );
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}