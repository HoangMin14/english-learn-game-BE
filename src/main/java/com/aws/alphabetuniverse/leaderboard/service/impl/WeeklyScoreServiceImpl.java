
package com.aws.alphabetuniverse.leaderboard.service.impl;

import com.aws.alphabetuniverse.leaderboard.repository.WeeklyScoreRepository;
import com.aws.alphabetuniverse.leaderboard.service.WeeklyScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
public class WeeklyScoreServiceImpl
        implements WeeklyScoreService {

    private static final ZoneId ZONE_ID =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final WeeklyScoreRepository weeklyScoreRepository;

    @Override
    @Transactional
    public void synchronizeWeek(Long userId, Instant endedAt) {

        if (userId == null || endedAt == null) {
            throw new IllegalArgumentException(
                    "userId and endedAt must not be null"
            );
        }

        // Xác định thứ Hai đầu tuần theo giờ Việt Nam.
        LocalDate weekStart = endedAt
                .atZone(ZONE_ID)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(
                        DayOfWeek.MONDAY
                ));

        // Đổi mốc đầu tuần sang LocalDateTime UTC,
        // vì ended_at trong schema hiện tại là timestamp without time zone.
        LocalDateTime fromTime = LocalDateTime.ofInstant(
                weekStart.atStartOfDay(ZONE_ID).toInstant(),
                ZoneOffset.UTC
        );

        LocalDateTime toTime = LocalDateTime.ofInstant(
                weekStart.plusWeeks(1)
                        .atStartOfDay(ZONE_ID)
                        .toInstant(),
                ZoneOffset.UTC
        );

        weeklyScoreRepository.synchronizeWeek(
                userId,
                weekStart,
                fromTime,
                toTime
        );
    }
}
