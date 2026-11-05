# Actor, chức năng và quyền truy cập

Ngày đối chiếu: **04/11/2026**. Đọc cùng SRS và bảng chế độ/trạng thái.

## 1. Actor nghiệp vụ

| Actor | Mục tiêu | Điều kiện |
|---|---|---|
| Khách chưa đăng nhập | Tạo tài khoản, xác minh email, đăng nhập, khôi phục mật khẩu. | Dùng API Auth công khai; chưa có quyền dữ liệu học. |
| Learner | Học, ghép chữ, nghe phát âm, theo dõi quá trình và điểm tuần. | JWT hợp lệ, User local đã Sync, role learner và status ACTIVE. |
| Admin | Quản lý nội dung, cấu hình, audit và Learner. | JWT hợp lệ, User local role admin và status ACTIVE. |

Hệ thống chỉ có hai role lưu DB: learner và admin. “Khách” là tình trạng chưa có phiên đăng nhập, không phải một role cần thêm vào bảng users.

## 2. Hệ thống hỗ trợ

| Thành phần | Tham gia chức năng nào? | Không được nhầm với |
|---|---|---|
| AWS Cognito | Mật khẩu, mã email, token, cập nhật name, khóa/mở tài khoản từ backend. | Role/status nghiệp vụ trong PostgreSQL. |
| Amazon Polly | Tạo file phát âm từ word tiếng Anh, hiện dùng giọng Joanna. | Dịch nghĩa tiếng Việt hoặc chấm bài. |
| Amazon S3 | Lưu ảnh Topic và audio MP3, phát qua URL ký. | Nơi tính điểm hay lưu tiến độ học. |
| Merriam-Webster | Cung cấp phiên âm IPA. | Cơ sở dữ liệu điểm hoặc nơi nhận request trực tiếp từ FE với API key bí mật. |
| Camera/MediaPipe tại FE | Theo dõi tay, chuyển động tác gắp/thả thành tương tác UI. | Một API BE nhận video rồi tự nhận diện từng chữ. |
| PostgreSQL/Supabase | Lưu User, nội dung, phiên, lượt trả lời, tiến độ, cấu hình và audit. | Actor con người hoặc role thứ ba. |

FE và BE là các thành phần trong sản phẩm. Không cấp các quyền quản trị AWS của BE cho Learner/Admin qua trình duyệt.

## 3. Ma trận quyền

“Có” nghĩa là source BE hiện cho phép khi đáp ứng điều kiện nghiệp vụ; giao diện có thể bố trí menu riêng.

| Chức năng | Khách | Learner ACTIVE | Admin ACTIVE |
|---|---|---|---|
| Đăng ký/xác nhận/gửi lại mã/quên mật khẩu | Có, theo điều kiện Cognito | Có thể dùng luồng Auth khi cần | Có thể dùng luồng Auth khi cần |
| Xem Topic active | Không | Có | Có |
| Học từ và lưu tiến độ bản thân | Không | Có | Có |
| CHECKPOINT / FREE_PLAY / REVIEW | Không | Có, tùy điều kiện mode | Có, tùy điều kiện mode |
| Resume/bỏ phiên bản thân | Không | Có | Có |
| Hồ sơ, lịch sử, thống kê, streak của bản thân | Không | Có | Có |
| Xem leaderboard tuần | Không | Có | Có |
| Được xếp hạng trên leaderboard | Không | Có nếu có điểm hợp lệ | Không; SQL loại role admin |
| Topic/Vocabulary Admin | Không | Không | Có |
| Cấu hình game và audit | Không | Không | Có |
| Danh sách User, khóa/mở Learner | Không | Không | Có |
| Khóa/mở Admin bằng API hiện có | Không | Không | Không |
| Xem lịch sử của người khác qua API /me | Không | Không | Không |

Token chưa Sync có thể dùng API Auth Sync; các API nghiệp vụ khác có thể trả 401 vì chưa có User local. Token hợp lệ không tự đồng nghĩa với có quyền Admin.

## 4. Danh mục use case

| Mã | Use case | Actor chính | Kết quả |
|---|---|---|---|
| UC-01 | Đăng ký và xác nhận email | Khách | Tài khoản Cognito đủ điều kiện đăng nhập. |
| UC-02 | Login và Sync | Khách/User | Nhận token, User local được đồng bộ, xác định role/status. |
| UC-03 | Quên và đặt lại mật khẩu | Chủ tài khoản | Mật khẩu đổi qua mã Cognito. |
| UC-04 | Đổi tên hiển thị | Learner/Admin | name Cognito và displayName DB được đồng bộ. |
| UC-05 | Mở Topic và học từ | Learner/Admin | Xem nội dung được mở, nghe MP3, lưu từ xa nhất đã học. |
| UC-06 | Chơi CHECKPOINT | Learner/Admin | Kiểm tra nhóm đã học, có thể nhận điểm và mở nhóm tiếp. |
| UC-07 | Chơi FREE_PLAY | Learner/Admin | Luyện toàn bộ từ active không yêu cầu học trước. |
| UC-08 | Chơi REVIEW | Learner/Admin | Ôn toàn bộ Topic đã học và vượt checkpoint. |
| UC-09 | Resume phiên | Chủ phiên | Khôi phục đúng tiến độ và câu hỏi đã lưu. |
| UC-10 | Bỏ phiên | Chủ phiên | Phiên thành ABANDONED, dữ liệu cũ vẫn được giữ. |
| UC-11 | Xem tiến độ/lịch sử/thống kê | Chủ dữ liệu | Đọc tiến trình và kết quả bản thân. |
| UC-12 | Xem streak và leaderboard | Learner/Admin | Đọc chuỗi ngày và bảng điểm tuần. |
| UC-13 | Quản lý Topic/Vocabulary | Admin | Nội dung được tạo/sửa/ẩn; Topic có thể khôi phục. |
| UC-14 | Quản lý cấu hình | Admin | Cập nhật global/override và xem hiệu lực. |
| UC-15 | Đọc audit | Admin | Xem thao tác, người thực hiện và dữ liệu trước/sau. |
| UC-16 | Khóa/mở Learner | Admin | Đổi status local, đồng bộ disable/enable Cognito. |

## 5. Trách nhiệm của từng thành phần

### FE

- Hiển thị đúng chức năng theo role và trạng thái User do BE trả.
- Thu nhận thao tác chuột/cử chỉ và gửi submittedLetters; không tự quyết định điểm cuối cùng.
- Dùng sessionId, currentVocabularyId/nextVocabularyId và trạng thái từ BE để chuyển câu.
- Hiển thị thông báo ACCOUNT_BLOCKED, lỗi dữ liệu và lỗi đồng bộ AWS; không điều hướng vào màn chính sau Login lỗi.
- Khi audioUrl null, vô hiệu hóa nút loa; khi URL hết hạn, lấy URL mới.

### BE

- Xác minh JWT, trạng thái local, role và quyền sở hữu dữ liệu.
- Chốt câu hỏi/cấu hình theo snapshot, tự chấm và lưu attempt.
- Ghi điểm/streak/tiến độ đúng điều kiện, giữ transaction và khóa dữ liệu cần thiết.
- Gọi Cognito, Polly, S3, từ điển bằng cấu hình/credentials phía server.

### QA và thành viên kiểm thử

QA là vai trò làm việc trong team, không phải role DB của ứng dụng. Cần kiểm thử cả nhánh sai, reload, gửi lặp, thiếu audio, checkpoint chưa mở và AWS lỗi; không chỉ kiểm tra HTTP 200.

## 6. Hai ví dụ để tránh nhầm quyền

**Admin chơi thử game:** Admin ACTIVE được tạo session và trả lời bằng dữ liệu của mình. Điểm phiên có thể được ghi như bình thường theo mode, nhưng SQL leaderboard chỉ xếp hạng Learner ACTIVE.

**Admin bị yêu cầu xem lịch sử Learner:** API `/api/users/me/game-history` luôn lấy User từ JWT. Đổi sessionId sang của người khác không được phép; vai trò Admin không biến API /me thành API quản trị lịch sử toàn hệ thống.
