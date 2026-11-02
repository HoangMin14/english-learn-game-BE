# PATCH /api/users/me

API cập nhật tên hiển thị cho người đang đăng nhập, theo cấu trúc Controller → Service/ServiceImpl → Repository. Phần gọi AWS có interface `CognitoUserAttributeService` và implementation `CognitoUserAttributeServiceImpl` riêng.

## Request

```http
PATCH /api/users/me
Authorization: Bearer <access_token>
Content-Type: application/json

{"displayName":"Nguyễn Minh"}
```

Tên bắt buộc, được chuẩn hóa Unicode NFC và bỏ khoảng trắng hai đầu, tối đa 100 Unicode code point sau chuẩn hóa. Register dùng cùng quy tắc. Sync vẫn tiếp nhận tên cũ dài hơn giới hạn này, không tự cắt tên hoặc làm tài khoản cũ không đăng nhập được.

Response HTTP 200 dùng `UserResponse` hiện tại: `id`, `email`, `displayName`, `role`, `status`. JWT thiếu/sai/hết hạn hoặc chưa có User local: 401. User không ACTIVE: 403. Tên không hợp lệ: 400. AWS giới hạn request: 429. Lỗi kết nối/chưa xác minh được cập nhật AWS: 503.

## Các file triển khai

Các đường dẫn bên dưới tính từ `src/main/java/com/aws/alphabetuniverse/`:

| File | Thao tác và lý do |
| --- | --- |
| `user/controller/UserProfileController.java` | Thêm endpoint PATCH, nhận JWT và request, khai báo Bearer cho Swagger. |
| `user/dto/UpdateMyProfileRequest.java` | Thêm request chỉ nhận displayName, bắt buộc có giá trị. |
| `user/dto/CognitoUserProfile.java` | Thêm DTO nội bộ chứa email và tên đọc từ Cognito. |
| `user/service/UserProfileService.java` | Thêm interface nghiệp vụ cập nhật hồ sơ. |
| `user/service/impl/UserProfileServiceImpl.java` | Thêm transaction, khóa User, kiểm tra ACTIVE, gọi AWS rồi cập nhật DB. |
| `user/service/CognitoUserAttributeService.java` | Thêm interface giao tiếp Cognito. |
| `user/service/impl/CognitoUserAttributeServiceImpl.java` | Thêm implementation gọi GetUser/UpdateUserAttributes, phân loại lỗi và xác minh sau timeout. |
| `user/util/DisplayNameNormalizer.java` | Thêm quy tắc chuẩn hóa tên dùng chung, tránh Register và PATCH xử lý khác nhau. |
| `user/service/impl/UserSyncServiceImpl.java` | Sửa để tái sử dụng service Cognito, luôn đọc profile sau khi khóa User. |
| `auth/service/impl/AuthServiceImpl.java` | Sửa Sync chỉ truyền sub/token; Register dùng chung validation tên. |
| `config/CognitoConfig.java` | Bổ sung timeout vào client hiện có. |
| `common/exception/GlobalExceptionHandler.java` | Giữ đúng HTTP status của lỗi đã được service phân loại. |
| `auth/service/UserSyncService.java`, `auth/service/impl/UserSyncServiceImpl.java` | Bỏ interface cũ trùng package và implementation rỗng/sai chữ ký. |

`user/service/UserSyncService.java`, `UserRepository`, `UserResponse`, `SecurityConfig`, `ActiveUserFilter` và bảng `users` tiếp tục được sử dụng, không cần khai báo lại các method/field đã có. `pom.xml` chỉ bổ sung cấu hình Mockito agent cho test, không thêm dependency.

## Luồng lưu dữ liệu

1. Lấy Cognito sub và access token từ JWT, không nhận userId từ request.
2. Khóa User bằng `findByCognitoSubForUpdate()` trong transaction READ_COMMITTED, kiểm tra ACTIVE.
3. Cập nhật thuộc tính `name` trên Cognito, kể cả khi DB đã có tên giống request để khôi phục trường hợp hai phía đang lệch.
4. Nếu lỗi mạng/timeout, đọc lại Cognito. Chỉ tiếp tục khi xác nhận tên đúng với request; chưa xác nhận được thì trả 503.
5. Nếu tên local thay đổi, cập nhật `display_name` và `updated_at` theo UTC, flush và commit. PATCH không thay đổi email, role, status, cognito_sub hoặc created_at.
6. Auth Sync dùng cùng khóa và đọc Cognito sau khi lấy khóa, tránh dùng tên cũ đã đọc trước khi chờ khóa. Sync vẫn đồng bộ email theo nghiệp vụ cũ.

Cognito và PostgreSQL không cùng một transaction. Nếu AWS đã cập nhật nhưng DB rollback/commit lỗi, backend trả lỗi và ghi cảnh báo theo userId. FE có thể retry PATCH với cùng tên hoặc gọi `POST /api/auth/sync`, có giới hạn số lần retry. Không có job tự đồng bộ nền trong bản này.

## Điều kiện AWS và database

- Access token cần scope `aws.cognito.signin.user.admin`; App Client phải được ghi thuộc tính `name`, thuộc tính này phải mutable. Không dùng ID token.
- Tái sử dụng client Cognito và region hiện tại, timeout 3 giây mỗi attempt / 8 giây mỗi API call. Một request backend có thể gọi AWS nhiều lần nên tổng thời gian có thể lớn hơn 8 giây.
- Tái sử dụng bảng `users` và các query khóa/upsert hiện có. Không thêm bảng hoặc chạy migration. Cột `display_name` phải chứa được tên theo giới hạn đã chọn.
- Interface Sync và implementation chỉ giữ trong package `user`; bỏ hai file cũ trong package/thư mục `auth` gây trùng hoặc không khớp chữ ký.

## Kiểm thử cục bộ

Chạy riêng các test mới bằng Maven:

```powershell
mvn -o '-Dtest=UserProfileControllerTest,UserProfileServiceImplTest,CognitoUserAttributeServiceImplTest,UserSyncServiceImplTest,AuthProfileIntegrationTest' test
```

Các test này mock Cognito, repository và JWT decoder; không kết nối AWS/PostgreSQL. Không dùng chúng để kết luận quyền App Client, schema thực tế hoặc khóa đồng thời trên PostgreSQL đã được kiểm thử tích hợp. Test `BackendApplicationTests` hiện tại khởi động toàn bộ ứng dụng nên không nằm trong lệnh trên.

`pom.xml` cấu hình Mockito làm Java agent khi chạy Surefire để test không phụ thuộc cơ chế tự attach agent trên JDK mới. `GlobalExceptionHandler` giữ nguyên HTTP status của `ResponseStatusException`, tránh handler Cognito bắt exception nguyên nhân bên trong rồi đổi thành 400.

Kết quả kiểm thử ngày 02/11/2026: compile thành công; 39 test mới pass, 0 lỗi. Bộ test gồm `UserProfileControllerTest`, `UserProfileServiceImplTest`, `CognitoUserAttributeServiceImplTest`, `UserSyncServiceImplTest`, `AuthProfileIntegrationTest`.

## Test Swagger với hệ thống thật

1. Khởi động lại backend sau khi build.
2. Login, lấy access token mới và gọi Auth Sync nếu chưa có User local.
3. Nhấn Authorize bằng access token, mở `PATCH /api/users/me` trong nhóm User và gửi request mẫu.
4. Kiểm tra response 200 và `GET /api/users/me` trả tên mới; gọi `POST /api/auth/sync` để kiểm tra tên vẫn được giữ.
5. Đối chiếu thuộc tính `name` trên Cognito và `users.display_name` trên database nếu bạn có quyền xem. Đây là bước kiểm thử tích hợp bạn thực hiện sau khi triển khai, không nằm trong 39 test mock.
