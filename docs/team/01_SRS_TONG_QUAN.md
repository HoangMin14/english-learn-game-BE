# SRS V1 — Tổng quan yêu cầu hệ thống

Ngày đối chiếu: **04/11/2026**. Dự án: **Alphabet Universe / Signly**.

Tài liệu này mô tả phạm vi và yêu cầu hệ thống dựa trên source đang có. Phần actor, flow API, flow nghiệp vụ, flow chức năng và trạng thái được tách riêng để team dễ đọc; xem mục lục 00_HUONG_DAN_DOC.md.

## 1. Mục đích sản phẩm

Hệ thống giúp người dùng học từ vựng tiếng Anh theo chủ đề, nghe phát âm, luyện ghép chữ và theo dõi quá trình học. Người chơi có thể điều khiển chữ bằng chuột hoặc cử chỉ gắp/thả qua camera ở FE. BE nhận các chữ đã sắp xếp và tự chấm đáp án.

Admin quản lý nội dung học, cấu hình game, lịch sử thay đổi cấu hình và trạng thái tài khoản Learner.

## 2. Phạm vi V1

| Nhóm | Chức năng hiện có |
|---|---|
| Xác thực | Đăng ký, xác nhận email, gửi lại mã, đăng nhập, refresh, Sync, quên/đặt lại mật khẩu, trả URL đăng xuất. |
| Hồ sơ | Xem bản thân, đổi displayName đồng bộ Cognito và DB. |
| Nội dung | Topic active, học Vocabulary theo nhóm, nghe audio tiếng Anh. |
| Game | CHECKPOINT, FREE_PLAY, REVIEW; tạo/resume/bỏ phiên; gửi đáp án. |
| Theo dõi | Tiến độ học/checkpoint, lịch sử, chi tiết phiên, thống kê, streak. |
| Thi đua | Bảng xếp hạng tuần cho Learner ACTIVE có điểm hợp lệ. |
| Quản trị | Topic, Vocabulary, tra IPA, sinh audio thiếu, cấu hình global/Topic, audit, danh sách và khóa/mở Learner. |

Đường chơi core ở BE không yêu cầu truyền ảnh camera. Tương tác camera thuộc FE; request chấm bài chỉ gồm vocabularyId, submittedLetters và timeMs tùy chọn.

## 3. Actor và ranh giới hệ thống

- **Khách:** dùng các luồng xác thực công khai; chưa có quyền đọc dữ liệu học cần token.
- **Learner ACTIVE:** học/chơi, xem dữ liệu cá nhân và leaderboard.
- **Admin ACTIVE:** quản lý hệ thống; code cũng cho sử dụng các API học/chơi và xem dữ liệu của chính mình.
- **Cognito:** xác thực, token, thuộc tính tài khoản, disable/enable User.
- **Polly:** tổng hợp phát âm tiếng Anh. **S3:** lưu ảnh/MP3 và cung cấp URL ký.
- **Merriam-Webster:** tra phiên âm IPA.
- **PostgreSQL/Supabase:** lưu dữ liệu nghiệp vụ; là kho dữ liệu của hệ thống, không phải role người dùng.

## 4. Yêu cầu chức năng

| Mã | Yêu cầu và hành vi cần giữ |
|---|---|
| FR-AUTH-01 | Đăng ký tài khoản Cognito bằng email/password/displayName; xác nhận email qua mã. |
| FR-AUTH-02 | Login chỉ trả token khi xác thực thành công và danh tính local không bị khóa. |
| FR-AUTH-03 | Sync dùng Cognito sub để tìm/tạo User local; tài khoản mới có role learner, giữ role/status đã có. |
| FR-AUTH-04 | Refresh token không được vượt qua trạng thái BLOCKED local; client kết thúc phiên khi không refresh được. |
| FR-AUTH-05 | User bị khóa nhận 403 ACCOUNT_BLOCKED khi Login; FE hiển thị thông báo và không điều hướng vào màn chính. |
| FR-AUTH-06 | Logout FE xóa phiên phía client; BE hiện trả logoutUrl để kết thúc phiên trình duyệt Cognito khi áp dụng. |
| FR-PROFILE-01 | User chỉ đổi displayName của mình; đồng bộ thuộc tính name trên Cognito và DB, không đổi email/role qua API này. |
| FR-LEARN-01 | Danh sách người học chỉ hiển thị Topic/Vocabulary active theo thứ tự BE xác định. |
| FR-LEARN-02 | Học từ đầu và tiến từng từ; không được nhảy qua từ hoặc nhóm chưa qua checkpoint. |
| FR-LEARN-03 | Xem lại từ cũ không làm giảm tiến độ xa nhất. |
| FR-AUDIO-01 | Màn học, tạo game và resume có audioUrl theo đúng từ; thiếu audio không làm mất quyền học/chơi. |
| FR-GAME-01 | Mỗi User chỉ có tối đa một phiên IN_PROGRESS trong cùng Topic, không phân biệt mode. |
| FR-GAME-02 | CHECKPOINT chọn nhóm đã học đủ, chưa thắng sớm nhất; mỗi nhóm tối đa 5 từ. |
| FR-GAME-03 | FREE_PLAY dùng toàn bộ từ active trong Topic, không yêu cầu học trước; không mở khóa checkpoint thay luồng học. |
| FR-GAME-04 | REVIEW chỉ mở sau khi học hết từ active và thắng mọi checkpoint hiện tại của Topic. |
| FR-GAME-05 | BE giữ snapshot câu hỏi, thứ tự xáo trộn, số lượt và các cấu hình thưởng cho toàn bộ phiên. |
| FR-GAME-06 | Chỉ chủ phiên được gửi đáp án; BE chấm bằng word trong snapshot và chỉ nhận câu hiện tại. |
| FR-GAME-07 | CHECKPOINT sai hết lượt ở một từ thì FAILED ngay; FREE_PLAY/REVIEW bỏ qua từ đó và tiếp tục. |
| FR-GAME-08 | Resume phục hồi đúng câu, lượt còn lại, chữ xáo trộn; không tạo thêm phiên hoặc cộng điểm. |
| FR-GAME-09 | Bỏ phiên chuyển ABANDONED và giữ lịch sử attempts/tiến độ từ đã trả lời đúng. |
| FR-SCORE-01 | Điểm thưởng thuộc session CHECKPOINT thắng; mỗi attempt và mỗi từ không có điểm thưởng riêng. |
| FR-STREAK-01 | Chỉ CHECKPOINT thắng đủ 5 từ và bật streak mới ghi nhận ngày; tối đa một lần tăng trong một ngày nghiệp vụ. |
| FR-WEEK-01 | Tuần tính theo thứ Hai, múi giờ Asia/Ho_Chi_Minh; chỉ phiên CHECKPOINT thắng có score > 0 được tính điểm tuần. |
| FR-HISTORY-01 | User chỉ xem lịch sử/chi tiết của mình; list có phân trang, detail gồm cả câu chưa trả lời và các attempt đã lưu. |
| FR-ADMIN-01 | Admin tạo/sửa/ẩn/khôi phục Topic; tạo/sửa/ẩn Vocabulary, tra IPA và sinh audio thiếu. |
| FR-ADMIN-02 | Admin sửa bốn key được hỗ trợ ở global hoặc override theo Topic, bắt buộc mô tả. |
| FR-ADMIN-03 | Đọc audit cấu hình theo bộ lọc/phân trang; dữ liệu audit phụ thuộc trigger DB đã triển khai. |
| FR-ADMIN-04 | Admin chỉ khóa/mở Learner; kết hợp local status với Cognito và báo rõ khi đồng bộ AWS thất bại. |

## 5. Quy tắc dữ liệu cốt lõi

| Dữ liệu | Ý nghĩa |
|---|---|
| User | Danh tính local gắn với Cognito sub, role và trạng thái truy cập. |
| user_topic_learning_progress | Con trỏ từ xa nhất đã học trong một Topic. |
| user_progress | Từng từ đã từng trả lời đúng trong game; giữ first_completed_at đầu tiên. |
| game_sessions | Chế độ, trạng thái phiên, kết quả, điểm, thời gian, snapshot câu hỏi/cấu hình. |
| session_attempts | Mỗi lần gửi đáp án; một từ có thể có nhiều lần trả lời. |
| user_streaks | Chuỗi ngày đủ điều kiện và ngày ghi nhận gần nhất. |
| weekly_scores | Tổng hợp điểm tuần được đồng bộ thêm từ các session hợp lệ. |
| game_configs / game_config_audits | Cấu hình hiệu lực và lịch sử thay đổi cấu hình. |

“Đã học đến từ số 5”, “đã trả lời đúng 5 từ” và “đã thắng checkpoint 1–5” không thay thế cho nhau.

## 6. Yêu cầu chất lượng và giới hạn vận hành

| Mã | Yêu cầu / thực trạng |
|---|---|
| NFR-01 | JWT được kiểm tra chữ ký, issuer, hạn dùng, token_use=access và client_id đúng ứng dụng. |
| NFR-02 | Quyền lấy từ dữ liệu User local; không tin userId/role do FE tự gửi. |
| NFR-03 | Submit attempt và cập nhật điểm/streak/tiến độ thuộc cùng transaction DB; có khóa dữ liệu để tuần tự hóa các thao tác quan trọng. |
| NFR-04 | Thay đổi Cognito/S3 và DB không có transaction phân tán; UI/BE phải có cách xử lý kết quả chưa chắc chắn và đồng bộ lại. |
| NFR-05 | API cần thông báo lỗi và UI có loading/empty/error; 204 là body rỗng, không parse JSON. |
| NFR-06 | Ảnh Topic JPEG/PNG/WebP, service tối đa 2 MiB; cấu hình local hiện 2MB/file, 3MB/request. Cần đưa cấu hình tương ứng lên môi trường triển khai. |
| NFR-07 | URL ký S3 được cấu hình 15 phút; client phải có cách lấy URL mới khi hết hạn. |
| NFR-08 | Chưa chốt SLA hiệu năng/độ sẵn sàng hoặc ngưỡng tải production bằng đo lường; không xem các con số giao diện là SLA BE. |

## 7. Các giới hạn cần team hiểu

1. BE chưa hỗ trợ restore Vocabulary, sửa role User, đổi thứ tự nội dung hoặc cấu hình số câu checkpoint qua API Admin hiện tại.
2. Logout chưa triển khai danh sách thu hồi JWT ở BE. Thu hồi ở Cognito không tự được nhận biết bởi bộ kiểm tra JWT offline.
3. Tuần mới không cần xóa điểm tuần cũ: truy vấn dùng khoảng thời gian tuần hiện tại. Không có yêu cầu cron xóa bảng weekly_scores trong code này.
4. GET learning có nhánh sửa con trỏ cũ nếu từ đang trỏ đã bị ẩn; không mô tả mọi API GET trong hệ thống là tuyệt đối không ghi dữ liệu.
5. Adapter FE đang có giá trị dự phòng 3 lượt, 30 phút và nhánh chuyển CHECKPOINT lỗi sang FREE_PLAY. Những chi tiết này không phải quy tắc BE; team phải đối chiếu trước nghiệm thu.
6. Thêm/ẩn từ có thể đổi tập ID của nhóm checkpoint hiện tại. Việc đã thắng được so theo tập ID của nhóm, không chỉ theo số thứ tự checkpoint.

## 8. Điều kiện nghiệm thu V1 đề xuất

- [ ] Hai role hoạt động đúng; User BLOCKED không Login/đọc API nghiệp vụ được.
- [ ] Học tuần tự, khóa nhóm đúng, checkpoint cuối ít hơn 5 từ được xử lý rõ.
- [ ] Ba mode chơi có kết thúc/điểm/streak đúng bảng quy tắc.
- [ ] Reload/resume không đổi câu hỏi, không nhân đôi điểm hoặc attempt.
- [ ] Audio của câu hiện tại đúng từ ở học, game và REVIEW.
- [ ] Leaderboard không đưa Admin hoặc User BLOCKED vào tập xếp hạng.
- [ ] Lỗi AWS/từ điển/DB được hiển thị và không tạo thông báo thành công giả.
- [ ] Đã test tích hợp trên môi trường thử nghiệm với schema, IAM và dữ liệu thực tế trước khi công bố hoàn thành V1.
