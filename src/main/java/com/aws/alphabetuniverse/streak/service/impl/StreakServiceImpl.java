
package com.aws.alphabetuniverse.streak.service.impl;

import com.aws.alphabetuniverse.streak.entity.UserStreak;
import com.aws.alphabetuniverse.streak.repository.UserStreakRepository;
import com.aws.alphabetuniverse.streak.service.StreakService;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class StreakServiceImpl implements StreakService {

    private static final ZoneId ZONE_ID =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserStreakRepository userStreakRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public int recordActivity(Long userId) {

        if (userId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User ID is required"
            );
        }

        // Khóa User để tuần tự hóa việc cập nhật streak
        // của cùng một người dùng.
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        LocalDate today = LocalDate.now(ZONE_ID);
        Instant now = Instant.now();

        UserStreak streak = userStreakRepository
                .findById(userId)
                .orElse(null);

        // 2. Nếu chưa có bản ghi Streak, khởi tạo lần đầu.
        if (streak == null) {

            streak = new UserStreak();
            streak.setUser(user);
            streak.setCurrentStreak(1);
            streak.setLastStreakDate(today);
            streak.setUpdatedAt(now);

            return userStreakRepository.save(streak)
                    .getCurrentStreak();
        }

        // 3. Lấy dữ liệu Streak hiện tại.
        int currentStreak = streak.getCurrentStreak() == null
                ? 0
                : streak.getCurrentStreak();

        LocalDate lastStreakDate = streak.getLastStreakDate();

        // 4. Đã tính Streak hôm nay thì không cộng thêm.
        if (today.equals(lastStreakDate)) {
            return currentStreak;
        }

        // 5. Xử lý bản ghi cũ chưa có ngày Streak.
        // Giữ nguyên chuỗi lịch sử hiện có và lấy hôm nay
        // làm mốc tính tiếp theo, tránh tự động xóa dữ liệu.
        if (lastStreakDate == null) {
            // Không có lịch sử ngày để xác minh chuỗi cũ.
            // Bắt đầu chuỗi được xác minh từ hôm nay.
            currentStreak = 1;
        } else if (today.minusDays(1).equals(lastStreakDate)) {

            // Hoạt động liên tiếp sang ngày hôm nay.
            currentStreak++;

        } else {

            // Đã bỏ lỡ ít nhất một ngày.
            currentStreak = 1;
        }

        // 6. Cập nhật Streak.
        streak.setCurrentStreak(currentStreak);
        streak.setLastStreakDate(today);
        streak.setUpdatedAt(now);

        userStreakRepository.save(streak);

        return currentStreak;
    }
}
