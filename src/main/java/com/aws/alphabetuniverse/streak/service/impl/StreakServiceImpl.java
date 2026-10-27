
package com.aws.alphabetuniverse.streak.service.impl;

import com.aws.alphabetuniverse.streak.dto.UserStreakResponse;
import com.aws.alphabetuniverse.streak.entity.UserStreak;
import com.aws.alphabetuniverse.streak.repository.UserStreakRepository;
import com.aws.alphabetuniverse.streak.service.StreakService;
import com.aws.alphabetuniverse.user.entity.User;
import com.aws.alphabetuniverse.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreakServiceImpl implements StreakService {

    private static final ZoneId ZONE_ID =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final UserStreakRepository userStreakRepository;
    private final UserRepository userRepository;
    private final Clock clock;

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

        LocalDate today = LocalDate.now(clock.withZone(ZONE_ID));
        Instant now = Instant.now(clock);
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

    @Override
    @Transactional(readOnly = true)
    public UserStreakResponse getMyStreak(Jwt jwt) {

        if (jwt == null
                || jwt.getSubject() == null
                || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid authentication token"
            );
        }

        // Chỉ tìm user local, không tự tạo user trong GET.
        var user = userRepository
                .findByCognitoSub(jwt.getSubject())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "User has not been synchronized"
                ));

        // Chỉ xác định ngày hiện tại một lần cho request.
        LocalDate today = LocalDate.now(clock.withZone(ZONE_ID));
        LocalDate yesterday = today.minusDays(1);

        // Query đọc riêng, không sử dụng findById có PESSIMISTIC_WRITE.
        Optional<UserStreak> streakOptional =
                userStreakRepository.findByUser_Id(user.getId());

        if (streakOptional.isEmpty()) {
            return new UserStreakResponse(
                    0, null, false, today, ZONE_ID.getId()
            );
        }

        UserStreak streak = streakOptional.get();

        // Entity thực tế dùng lastStreakDate.
        LocalDate lastStreakDate = streak.getLastStreakDate();
        Integer storedStreak = streak.getCurrentStreak();

        // Không có ngày ghi nhận: không có streak hiệu lực.
        if (lastStreakDate == null) {

            if (storedStreak == null || storedStreak != 0) {
                log.warn(
                        "Invalid streak data: userId={}, currentStreak={}, lastStreakDate=null",
                        user.getId(),
                        storedStreak
                );
            }

            return new UserStreakResponse(
                    0, null, false, today, ZONE_ID.getId()
            );
        }

        // Ngày đã được ghi nhưng số streak null hoặc <= 0:
        // coi là dữ liệu bất thường, không xác nhận hoạt động hôm nay.
        boolean invalidStreak =
                storedStreak == null || storedStreak <= 0;

        // Ngày trong tương lai cũng là dữ liệu bất thường.
        boolean futureDate = lastStreakDate.isAfter(today);

        if (invalidStreak) {
            log.warn(
                    "Invalid streak count: userId={}, currentStreak={}, lastStreakDate={}",
                    user.getId(),
                    storedStreak,
                    lastStreakDate
            );
        }

        if (futureDate) {
            log.warn(
                    "Future streak date: userId={}, lastStreakDate={}, today={}",
                    user.getId(),
                    lastStreakDate,
                    today
            );
        }

        if (invalidStreak || futureDate) {
            return new UserStreakResponse(
                    0,
                    lastStreakDate,
                    false,
                    today,
                    ZONE_ID.getId()
            );
        }

        // Hoạt động đủ điều kiện đã được ghi nhận hôm nay.
        if (lastStreakDate.equals(today)) {
            return new UserStreakResponse(
                    storedStreak,
                    lastStreakDate,
                    true,
                    today,
                    ZONE_ID.getId()
            );
        }

        // Hoạt động gần nhất là hôm qua: streak vẫn còn hiệu lực.
        if (lastStreakDate.equals(yesterday)) {
            return new UserStreakResponse(
                    storedStreak,
                    lastStreakDate,
                    false,
                    today,
                    ZONE_ID.getId()
            );
        }

        // Đã bỏ lỡ ít nhất một ngày.
        // Chỉ tính giá trị trả về, không reset Entity.
        return new UserStreakResponse(
                0,
                lastStreakDate,
                false,
                today,
                ZONE_ID.getId()
        );
    }
}
