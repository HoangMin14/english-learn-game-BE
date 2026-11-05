# Tài liệu nhập môn dự án Alphabet Universe / Signly

Phiên bản tài liệu: **V1 — 04/11/2026**.

Bộ tài liệu dành cho thành viên BE, FE, QA và thành viên mới trong team. Tên dự án backend là Alphabet Universe; giao diện FE hiện hiển thị thương hiệu Signly.

## 1. Mỗi mục là một file riêng

| Thứ tự đọc | File | Trả lời câu hỏi |
|---|---|---|
| 1 | [01_SRS_TONG_QUAN.md](01_SRS_TONG_QUAN.md) | Dự án làm gì, phạm vi V1 và yêu cầu chính là gì? |
| 2 | [02_ACTOR_VA_CHUC_NANG.md](02_ACTOR_VA_CHUC_NANG.md) | Ai được làm gì? Hệ thống nào tham gia? |
| 3 | [03_FLOW_NGHIEP_VU.md](03_FLOW_NGHIEP_VU.md) | Người dùng đi qua những màn và bước nghiệp vụ nào? |
| 4 | [04_FLOW_API.md](04_FLOW_API.md) | FE gọi endpoint nào, theo thứ tự nào, xử lý kết quả ra sao? |
| 5 | [05_FLOW_CHUC_NANG.md](05_FLOW_CHUC_NANG.md) | BE xử lý bên trong mỗi chức năng và ghi dữ liệu vào đâu? |
| 6 | [06_CHE_DO_VA_TRANG_THAI.md](06_CHE_DO_VA_TRANG_THAI.md) | CHECKPOINT/FREE_PLAY/REVIEW và từng trạng thái có nghĩa gì? |

File này là mục lục. Sáu nội dung chính ở sáu file độc lập; có thể giao từng file cho người phụ trách đọc.

## 2. Chọn tài liệu theo vai trò trong team

- **Thành viên mới:** đọc SRS → actor → flow nghiệp vụ → chế độ/trạng thái.
- **FE:** đọc flow nghiệp vụ → flow API → chế độ/trạng thái; đối chiếu DTO trước khi mapping dữ liệu.
- **BE:** đọc SRS → flow chức năng → flow API; kiểm tra tính nhất quán điểm, tiến độ, snapshot và transaction.
- **QA:** đọc ma trận actor, bảng trạng thái và các nhánh lỗi/tiêu chí nghiệm thu trong từng file.

## 3. Phạm vi đã đối chiếu

- Backend Java/Spring Boot hiện tại, gồm Controller, DTO, Service, Repository, Entity, Security và các service AWS.
- **46 endpoint**, trong đó **21 endpoint Admin**, theo các Controller trong source hiện tại.
- Có đọc thêm FE React/TypeScript đang dùng để nhận biết màn hình và cách nhập bằng chuột/cử chỉ. BE là nguồn quyết định cho việc chấm đáp án, quyền, điểm, streak và trạng thái phiên.
- Các tài liệu mô tả cách code hiện đang hoạt động; tiêu chí nghiệm thu là việc team cần kiểm tra, không phải cam kết mọi ca đã được test thật.
- Không kết nối để xác nhận schema/data Supabase, IAM, dịch vụ từ điển hoặc file S3 đang chạy trên môi trường thật trong đợt viết tài liệu này.

## 4. Những điểm cần nhớ trước khi làm việc

1. DB có **hai role**: `learner`, `admin`. Khách chưa đăng nhập không phải role thứ ba trong DB.
2. **Học từ**, **từng từ đã trả lời đúng**, **vượt checkpoint** là ba loại tiến độ khác nhau.
3. Chỉ CHECKPOINT thắng mới có thể có điểm thưởng; FREE_PLAY và REVIEW không có điểm thưởng.
4. Streak hiện chỉ ghi nhận khi thắng CHECKPOINT **đủ 5 từ**, và snapshot bật streak.
5. Nhóm checkpoint cuối Topic có thể ít hơn 5 từ; nhóm này vẫn có thể thưởng điểm, nhưng không đáp ứng điều kiện streak 5 từ.
6. Phiên bỏ dở trong source hiện tại mang trạng thái **ABANDONED**, không phải CANCELLED.
7. `mode` của màn học khác `mode` của game; `REVIEW` xuất hiện ở cả hai nơi nhưng không phải cùng một loại đối tượng.
8. Cấu hình, câu hỏi, chữ cái và key audio được chốt theo snapshot khi tạo game; không đọc lại cấu hình điểm mới để tính cho phiên cũ.
9. `GET /api/leaderboard/weekly` hiện tính từ game_sessions; weekly_scores là bảng được đồng bộ thêm, không phải nguồn đọc trực tiếp của API leaderboard.
10. Đăng xuất hiện trả logoutUrl; không suy ra BE đã có danh sách thu hồi toàn bộ JWT cũ.

## 5. Quy ước và nguồn

- ID/tên/timestamp/URL trong ví dụ là dữ liệu minh họa. Không dùng chúng làm dữ liệu thật.
- HTTP thành công không có envelope chung: có API trả object, array, page hoặc 204 rỗng.
- Đường dẫn kiểm tra BE trên máy hiện tại: `D:/AWS/PJ_AWS_1/aws-workshop-main/backend`.
- FE đang được đối chiếu: `D:/AWS/english-learn-game-FE-main`.
- Các file trong bộ tài liệu liên kết tương đối để giữ được liên kết khi gửi cả thư mục hoặc giải nén gói ZIP.
- Tài liệu Admin chi tiết đã có trong repo: `docs/FRONTEND_ADMIN_ISSUES.md`, `docs/ADMIN_BLOCK_LOGIN.md`; âm thanh: `docs/GAME_LEARNING_AUDIO.md`.

Khi BE thay đổi nghiệp vụ hoặc DTO, cập nhật file flow/trạng thái tương ứng trước khi xem các tài liệu này là bản mô tả mới nhất.
