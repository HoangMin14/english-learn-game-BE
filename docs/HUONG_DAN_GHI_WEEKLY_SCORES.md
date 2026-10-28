# Ghi tổng điểm vào weekly_scores

Ngày hướng dẫn: 28/10/2026.

Đây là code đề xuất để bạn tự áp dụng. Source Java của ứng dụng và database chưa được chỉnh sửa theo hướng dẫn này. Kết nối DB lúc kiểm tra bị timeout, nên cần xác nhận schema ở phần 2 trước khi áp dụng SQL. Ba class mới và bản sao GameAttemptServiceImpl đã ghép mẫu được biên dịch cách ly trong target/review-weekly-scores; các ca chuyển tuần của service cũng đã đạt. Chưa kiểm thử SQL ghi hoặc transaction thực tế trên DB.

## 1. Quy tắc và phạm vi

- `game_sessions.score` tiếp tục là điểm gốc của từng phiên.
- `weekly_scores` lưu một dòng cho mỗi cặp `(user_id, week_start)`.
- Tuần bắt đầu vào thứ Hai lúc 00:00 theo `Asia/Ho_Chi_Minh`.
- Xác định tuần bằng `session.endedAt`, không lấy thời điểm gọi GET hoặc một lần đọc đồng hồ mới.
- Chỉ cộng các phiên `CHECKPOINT`, `COMPLETED`, `is_won = true`, `score > 0` và có thời điểm kết thúc trong tuần.
- Mỗi lần đồng bộ, tính lại tổng từ các session đã lưu rồi ghi đè `total_score` bằng tổng đó. Đồng bộ lại nhiều lần không cộng trùng.
- CHECKPOINT nhóm cuối dưới 5 từ vẫn được tính nếu đã được thưởng điểm. Không thêm điều kiện số câu phải bằng 5.
- FREE_PLAY, REVIEW, phiên đang chơi, thất bại hoặc bỏ phiên không phát sinh điểm tuần.
- Nếu tắt điểm hoặc cấu hình thưởng bằng 0, chiến thắng không tạo thêm điểm tuần. Không tự tạo dòng 0 cho mọi user.
- Lưu tổng của cả learner và admin đã thực sự được thưởng điểm. API leaderboard hiện tại vẫn chỉ xếp hạng learner ACTIVE. Lọc role/status khi hiển thị, không xóa điểm đã lưu của tài khoản khi role/status thay đổi.
- Không cần API mới và không ghi dữ liệu trong GET leaderboard.
- Mỗi tuần có dòng riêng; không reset hoặc xóa điểm tuần trước vào thứ Hai.

Luồng ghi:

```text
submitAttempt() trong transaction hiện tại
  -> user và session đã được khóa theo luồng hiện có
  -> backend chấm đáp án
  -> lưu attempt / tiến độ từ vựng như hiện tại
  -> CHECKPOINT kết thúc và được thưởng điểm
  -> lưu trạng thái, score, endedAt của session
  -> đồng bộ weekly_scores trong cùng transaction
  -> xử lý streak theo quy tắc hiện tại
  -> commit toàn bộ, hoặc rollback toàn bộ khi lỗi
```

## 2. Kiểm tra DB trước khi ghép

Query sau chỉ đọc schema:

```sql
SELECT column_name, data_type, is_nullable,
       column_default, is_identity
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'weekly_scores'
ORDER BY ordinal_position;

SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND tablename = 'weekly_scores';

SELECT COUNT(*) AS null_keys
FROM public.weekly_scores
WHERE user_id IS NULL OR week_start IS NULL;

SELECT user_id, week_start, COUNT(*)
FROM public.weekly_scores
GROUP BY user_id, week_start
HAVING COUNT(*) > 1;
```

Cần bảo đảm:

1. `id` tự sinh bằng identity hoặc sequence/default. Query INSERT mẫu không truyền ID; `@GeneratedValue` không tự sinh ID cho câu native INSERT này.
2. `user_id` và `week_start` không NULL.
3. Có unique constraint hoặc unique index phù hợp trên `(user_id, week_start)`. Thứ tự hai cột trong index có thể ngược lại; đừng tạo thêm một index tương đương nếu đã có. [PostgreSQL: ON CONFLICT](https://www.postgresql.org/docs/current/sql-insert.html#SQL-ON-CONFLICT)
4. Các cột bắt buộc khác, nếu có ngoài Entity hiện tại, phải có default hoặc được bổ sung vào INSERT.

Nếu dữ liệu đã sạch và DB còn thiếu NOT NULL:

```sql
ALTER TABLE public.weekly_scores
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN week_start SET NOT NULL;
```

Chỉ chạy câu sau khi CHƯA có unique constraint/index tương đương:

```sql
ALTER TABLE public.weekly_scores
    ADD CONSTRAINT uq_weekly_scores_user_week
    UNIQUE (user_id, week_start);
```

Nếu `id` chưa tự sinh, cần migration phù hợp với cột/default hiện có và dữ liệu đang có; chưa thể chốt migration ID từ Entity Java. Không bỏ qua bước này.

Ứng dụng đang dùng `spring.jpa.hibernate.ddl-auto=none`, nên annotation trong Entity không tự tạo các ràng buộc trên Supabase.

Entity `WeeklyScore` hiện có có thể tiếp tục được dùng. Sau khi DB đã bảo đảm NOT NULL, nên đồng bộ quan hệ `user` thành `@ManyToOne(fetch = FetchType.LAZY, optional = false)` và `@JoinColumn(name = "user_id", nullable = false)`. Có thể thêm `@UniqueConstraint` tương ứng vào `@Table` để mô tả schema; annotation đó không thay thế migration.

## 3. Thêm WeeklyScoreRepository

File mới:

`D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/leaderboard/repository/WeeklyScoreRepository.java`

```java
package com.aws.alphabetuniverse.leaderboard.repository;

import com.aws.alphabetuniverse.leaderboard.entity.WeeklyScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface WeeklyScoreRepository
        extends JpaRepository<WeeklyScore, Long> {

    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO public.weekly_scores (
            user_id,
            week_start,
            total_score
        )
        SELECT
            :userId,
            :weekStart,
            COALESCE(SUM(s.score), 0)
        FROM public.game_sessions s
        WHERE s.user_id = :userId
          AND s.mode = 'CHECKPOINT'
          AND s.status = 'COMPLETED'
          AND s.is_won = TRUE
          AND s.score > 0
          AND s.ended_at >= :fromTime
          AND s.ended_at < :toTime
        ON CONFLICT (user_id, week_start)
        DO UPDATE SET
            total_score = EXCLUDED.total_score
        """, nativeQuery = true)
    int synchronizeWeek(
            @Param("userId") Long userId,
            @Param("weekStart") LocalDate weekStart,
            @Param("fromTime") LocalDateTime fromTime,
            @Param("toTime") LocalDateTime toTime
    );
}
```

`flushAutomatically = true` buộc JPA flush session vừa hoàn thành trước khi chạy SQL tổng hợp. Nếu session mới chỉ được `save()` nhưng chưa flush, tổng điểm có thể bỏ sót chính phiên vừa thắng. [Spring Data JPA: Modifying](https://docs.spring.io/spring-data/jpa/docs/current/api/org/springframework/data/jpa/repository/Modifying.html)

Query ghi `total_score = EXCLUDED.total_score`, không cộng tổng tính lại vào tổng cũ. Ví dụ hai session được thưởng 5 và 5 điểm thì tổng là 10; chạy đồng bộ lần nữa vẫn là 10.

Query này dùng quy ước hiện tại của source: `game_sessions.ended_at` là `timestamp without time zone` lưu giá trị UTC. Nếu schema đã đổi sang `timestamptz`, cần điều chỉnh kiểu parameter/mốc thời gian và SQL backfill tương ứng.

## 4. Thêm WeeklyScoreService

File mới:

`D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/leaderboard/service/WeeklyScoreService.java`

```java
package com.aws.alphabetuniverse.leaderboard.service;

import java.time.Instant;

public interface WeeklyScoreService {

    void synchronizeWeek(Long userId, Instant endedAt);
}
```

## 5. Thêm WeeklyScoreServiceImpl

File mới:

`D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/leaderboard/service/impl/WeeklyScoreServiceImpl.java`

```java
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class WeeklyScoreServiceImpl implements WeeklyScoreService {

    private static final ZoneId ZONE_ID =
            ZoneId.of("Asia/Ho_Chi_Minh");

    private final WeeklyScoreRepository weeklyScoreRepository;

    @Override
    @Transactional
    public void synchronizeWeek(Long userId, Instant endedAt) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(endedAt, "endedAt must not be null");

        LocalDate weekStart = endedAt.atZone(ZONE_ID)
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        LocalDateTime fromTime = LocalDateTime.ofInstant(
                weekStart.atStartOfDay(ZONE_ID).toInstant(),
                ZoneOffset.UTC
        );

        LocalDateTime toTime = LocalDateTime.ofInstant(
                weekStart.plusWeeks(1).atStartOfDay(ZONE_ID).toInstant(),
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
```

`@Transactional` mặc định REQUIRED nên service tham gia transaction của `submitAttempt()`. Không dùng REQUIRES_NEW và không bắt rồi bỏ qua lỗi cập nhật điểm tuần.

Luồng hiện tại đã khóa user rồi khóa session trước khi chấm. Giữ nguyên thứ tự khóa đó. Nó tuần tự hóa các lần ghi điểm của cùng user, giúp tránh hai lần tổng hợp đồng thời ghi đè một tổng cũ. Method này được thiết kế để gọi từ luồng submit đã giữ khóa user; nếu sau này có luồng ghi khác gọi service, luồng đó cũng cần cơ chế khóa tương ứng.

## 6. Ghép vào GameAttemptServiceImpl

File có sẵn:

`D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/game/service/impl/GameAttemptServiceImpl.java`

Thêm import:

```java
import com.aws.alphabetuniverse.leaderboard.service.WeeklyScoreService;
```

Thêm dependency cạnh các field `private final` hiện có:

```java
private final WeeklyScoreService weeklyScoreService;
```

Class đã có `@RequiredArgsConstructor`, nên không cần viết constructor thủ công.

Trong `submitAttempt()`, đặt đoạn sau SAU toàn bộ nhánh xử lý kết quả game và TRƯỚC đoạn xử lý streak. Lúc này session đã được đặt status/score/endedAt và đã gọi `gameSessionRepository.save(session)` trong nhánh hoàn thành:

```java
if (sessionCompleted
        && CHECKPOINT.equals(mode)
        && Boolean.TRUE.equals(sessionWon)
        && session.getScore() != null
        && session.getScore() > 0) {

    weeklyScoreService.synchronizeWeek(
            user.getId(),
            session.getEndedAt()
    );
}

// Đoạn xử lý streak hiện tại tiếp tục ở đây.
```

Giữ nguyên các phần khác của submit: xác thực, kiểm tra chủ sở hữu, khóa user/session, chấm từ snapshot, lưu attempt, `user_progress`, điểm thưởng và streak.

Không đọc lại `checkpoint_pass_points` trong service điểm tuần. Tổng phải lấy từ `game_sessions.score` đã lưu, để việc admin sửa cấu hình không làm đổi điểm các phiên cũ.

## 7. Bổ sung tổng điểm từ các phiên cũ

Code mới chỉ tự đồng bộ khi phát sinh chiến thắng mới. Nếu đã có điểm ở những tuần cũ, cần chạy backfill một lần.

Thực hiện sau khi kiểm tra schema/ràng buộc ở phần 2. Tạm dừng backend hoặc luồng ghi điểm trong lúc backfill để một bản tổng hợp cũ không ghi đè cập nhật mới đang chạy.

SQL dưới đây giả định `ended_at` là timestamp KHÔNG kèm múi giờ và giá trị lưu là UTC, như quy ước trong source hiện tại:

```sql
INSERT INTO public.weekly_scores (
    user_id,
    week_start,
    total_score
)
SELECT
    s.user_id,
    date_trunc(
        'week',
        (s.ended_at AT TIME ZONE 'UTC')
            AT TIME ZONE 'Asia/Ho_Chi_Minh'
    )::date AS week_start,
    SUM(s.score) AS total_score
FROM public.game_sessions s
WHERE s.user_id IS NOT NULL
  AND s.mode = 'CHECKPOINT'
  AND s.status = 'COMPLETED'
  AND s.is_won = TRUE
  AND s.score > 0
  AND s.ended_at IS NOT NULL
GROUP BY 1, 2
ON CONFLICT (user_id, week_start)
DO UPDATE SET
    total_score = EXCLUDED.total_score;
```

Backfill ghi đè bằng tổng chính xác, nên chạy lại không cộng thêm lần nữa. Nó không cộng điểm mới vào `game_sessions`, không sửa streak và không sửa `user_progress`.

Nếu không có CHECKPOINT thắng được thưởng điểm thì bảng vẫn chưa có dòng; đó là kết quả đúng theo điều kiện tổng hợp.

## 8. API GET leaderboard

Ở thay đổi này, có thể giữ nguyên `GET /api/leaderboard/weekly` và query tổng hợp hiện tại. `weekly_scores` là bảng tổng hợp để bạn xem trong DB; `game_sessions` vẫn là dữ liệu gốc.

Admin có thể có dòng điểm trong bảng tổng hợp do đã được thưởng khi chơi, nhưng không xuất hiện trong danh sách xếp hạng vì query GET hiện chỉ lấy learner ACTIVE. Đây là hai quy tắc khác nhau cần giải thích rõ cho FE.

Nếu sau này chuyển GET sang đọc `weekly_scores`, vẫn phải giữ lọc tuần/learner/ACTIVE, cách xử lý đồng điểm, top N và vị trí của chính user. Không đổi nguồn đọc trước khi backfill và kiểm tra sự khớp tổng.

## 9. Các ca cần kiểm tra sau khi triển khai

| Ca | Kết quả mong đợi |
| --- | --- |
| Thắng CHECKPOINT được 5 điểm | Một dòng đúng user/tuần, tổng bằng tổng các phiên đủ điều kiện |
| Thắng thêm một CHECKPOINT 5 điểm trong cùng tuần | Cùng dòng, tổng tăng đúng 5 |
| Đồng bộ lại cùng user/tuần | Tổng giữ nguyên, không cộng trùng |
| Gửi lại attempt của phiên đã kết thúc | Luồng hiện tại trả 409, điểm không tăng |
| Thua, bỏ phiên, FREE_PLAY hoặc REVIEW | Không phát sinh điểm tuần |
| CHECKPOINT thắng với điểm 0 | Không tạo thêm điểm/dòng từ hook mới |
| CHECKPOINT nhóm cuối có dưới 5 câu nhưng được thưởng | Tổng tuần vẫn cập nhật |
| Lỗi sau khi đã đồng bộ nhưng trước commit | Attempt, session, tiến độ và tổng tuần cùng rollback |
| Hai phiên của cùng user hoàn thành đồng thời | Khóa user hiện có giữ tổng cuối đúng |
| Phiên kết thúc ngay trước/sau 00:00 thứ Hai giờ Việt Nam | Điểm vào tuần theo endedAt, không theo lúc query chạy |
| Cấu hình thưởng đổi từ 5 sang 10 giữa hai lần thắng | Tổng lấy điểm đã lưu: 5 + 10 = 15 |
| Backfill chạy lại khi backend tạm dừng ghi | Không tăng điểm trùng |

Đối chiếu bằng tổng các session đủ điều kiện của cùng user và khoảng tuần; không dùng tổng điểm của tất cả mode hoặc tất cả thời gian để so với một dòng điểm tuần.

## 10. Kết quả kiểm tra và giới hạn

- Biên dịch cách ly thành công ba class mới và bản sao GameAttemptServiceImpl đã chèn import/dependency/hook từ hướng dẫn, với Java release 17 và các thư viện có sẵn của dự án.
- Chạy service mẫu với repository giả lập để kiểm tra các mốc endedAt:
  - `2026-11-01T16:59:59Z` (23:59:59 Chủ nhật giờ Việt Nam) thuộc tuần bắt đầu `2026-10-26`.
  - `2026-11-01T17:00:00Z` (00:00 thứ Hai giờ Việt Nam) thuộc tuần bắt đầu `2026-11-02`.
  - `2026-10-28T08:00:00Z` thuộc tuần bắt đầu `2026-10-26`.
- Các ca trên kiểm tra cả week_start và hai mốc UTC truyền vào repository; không mở kết nối DB và không ghi dữ liệu.

- Chưa thay đổi Java source theo mẫu này.
- Chưa tạo constraint/index, chạy backfill hoặc ghi dữ liệu trên Supabase.
- Chưa xác nhận schema live của weekly_scores do kết nối timeout.
- Chưa chạy query INSERT/ON CONFLICT trên schema thực tế; chưa kiểm thử transaction, rollback hoặc submit đồng thời bằng ứng dụng đang chạy.
