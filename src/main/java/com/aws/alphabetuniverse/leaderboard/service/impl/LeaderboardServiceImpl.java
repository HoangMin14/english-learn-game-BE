package com.aws.alphabetuniverse.leaderboard.service.impl;

import com.aws.alphabetuniverse.leaderboard.dto.LeaderboardEntryResponse;
import com.aws.alphabetuniverse.leaderboard.dto.WeeklyLeaderboardResponse;
import com.aws.alphabetuniverse.leaderboard.repository.projection.WeeklyLeaderboardRow;
import com.aws.alphabetuniverse.leaderboard.service.LeaderboardService;
import com.aws.alphabetuniverse.game.repository.GameSessionRepository;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LeaderboardServiceImpl implements LeaderboardService {

    private static final ZoneId ZONE_ID =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final GameSessionRepository gameSessionRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public WeeklyLeaderboardResponse getWeeklyLeaderboard(
            Jwt jwt,
            int limit
    ) {

        // 1. Kiểm tra JWT
        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication token"
            );
        }

        // 2. Tìm user local bằng Cognito sub
        User user = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User has not been synchronized"
                ));

        // 3. Chỉ tài khoản ACTIVE được truy cập
        if (!"ACTIVE".equalsIgnoreCase(
                String.valueOf(user.getStatus()))) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "User is not active"
            );
        }

        // 4. Cho phép learner và admin xem bảng
        String role = String.valueOf(user.getRole());

        if (!"learner".equalsIgnoreCase(role)
                && !"admin".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Only learners and admins can access the leaderboard"
            );
        }

        // 5. Kiểm tra limit
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "limit must be between 1 and 100"
            );
        }

        // 6. Xác định tuần hiện tại theo giờ Việt Nam
        LocalDate today = LocalDate.now(
                clock.withZone(ZONE_ID)
        );

        LocalDate weekStart = today.with(
                TemporalAdjusters.previousOrSame(
                        DayOfWeek.MONDAY
                )
        );

        LocalDate weekEndExclusive = weekStart.plusWeeks(1);

        // 7. Chuyển hai mốc giờ Việt Nam sang UTC.
        // game_sessions.ended_at được quy ước lưu UTC
        // trong cột timestamp without time zone.
        LocalDateTime fromTime = LocalDateTime.ofInstant(
                weekStart.atStartOfDay(ZONE_ID).toInstant(),
                ZoneOffset.UTC
        );

        LocalDateTime toTime = LocalDateTime.ofInstant(
                weekEndExclusive.atStartOfDay(ZONE_ID).toInstant(),
                ZoneOffset.UTC
        );

        // 8. Truy vấn top N và user hiện tại
        List<WeeklyLeaderboardRow> rows =
                gameSessionRepository.findWeeklyLeaderboard(
                        fromTime,
                        toTime,
                        user.getId(),
                        limit
                );

        // 9. Tạo danh sách top N
        List<LeaderboardEntryResponse> entries = rows.stream()
                .filter(row ->
                        row.getPosition() != null
                                && row.getPosition() <= limit
                )
                .map(this::toResponse)
                .toList();

        // 10. Tìm thứ hạng người hiện tại
        WeeklyLeaderboardRow myRow = rows.stream()
                .filter(row ->
                        row.getUserId().equals(user.getId())
                )
                .findFirst()
                .orElse(null);

        LeaderboardEntryResponse me;

        if (myRow != null) {
            me = toResponse(myRow);
        } else {
            // Chưa có điểm hoặc là admin.
            // Admin không thuộc tập người được xếp hạng trong SQL.
            me = new LeaderboardEntryResponse(
                    user.getId(),
                    displayNameOrFallback(
                            user.getDisplayName(),
                            user.getId()
                    ),
                    null,
                    0L
            );
        }

        // 11. Trả response; không cập nhật Database
        return new WeeklyLeaderboardResponse(
                weekStart,
                weekEndExclusive,
                ZONE_ID.getId(),
                entries,
                me
        );
    }

    private LeaderboardEntryResponse toResponse(
            WeeklyLeaderboardRow row
    ) {
        return new LeaderboardEntryResponse(
                row.getUserId(),
                displayNameOrFallback(
                        row.getDisplayName(),
                        row.getUserId()
                ),
                row.getRank(),
                row.getTotalScore()
        );
    }

    private String displayNameOrFallback(
            String displayName,
            Long userId
    ) {
        if (displayName == null || displayName.isBlank()) {
            return "Người học #" + userId;
        }

        return displayName.trim();
    }
}