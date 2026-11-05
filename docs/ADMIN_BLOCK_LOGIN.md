# Khóa/mở khóa Learner và chặn đăng nhập

## Hành vi của API

Giữ nguyên endpoint:

`PATCH /api/admin/users/{userId}/status`

```json
{"status":"BLOCKED"}
```

- Chỉ Admin ACTIVE được thao tác; chỉ quản lý tài khoản Learner.
- Không cho Admin tự khóa hoặc khóa Admin khác, giữ nguyên các kiểm tra hiện tại.
- Khóa bản ghi User trong transaction để các yêu cầu đổi trạng thái được xử lý tuần tự.
- Với BLOCKED: ghi/flush trạng thái local trước, sau đó gọi Cognito `AdminDisableUser`.
- Với ACTIVE: gọi `AdminEnableUser` trước; thành công mới ghi trạng thái local ACTIVE.
- Chỉ trả HTTP 200 khi thao tác Cognito thành công và transaction DB commit thành công.

Để mở khóa:

```json
{"status":"ACTIVE"}
```

Định danh gửi Cognito là `users.cognito_sub`, không dùng ID số trong PostgreSQL.
User Pool ID lấy từ cấu hình issuer JWT đang có; tái sử dụng Cognito client và timeout hiện tại.
Không tạo thêm Cognito client hoặc yêu cầu thêm biến môi trường chứa User Pool ID.

## Login khi bị khóa

`POST /api/auth/login` trả HTTP **403**:

```json
{
  "code": "ACCOUNT_BLOCKED",
  "message": "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."
}
```

Không có `accessToken`, `idToken` hoặc `refreshToken` trong response lỗi.

Login kiểm tra và khóa User theo email không phân biệt hoa thường ngay lần đọc đầu tiên.
Nếu đã BLOCKED thì trả lỗi trước khi gọi Cognito. Sau khi Cognito xác thực thành công,
backend xác minh access token bằng JwtDecoder hiện tại, lấy `sub` đã xác minh và kiểm tra lại
trạng thái User theo `sub`. Vì vậy email/alias chưa khớp dữ liệu local cũng không bỏ qua được
trạng thái BLOCKED của danh tính đã đăng nhập.

Login dùng transaction READ_COMMITTED. User chưa từng Sync, chưa có trong DB, vẫn được
đăng nhập lần đầu và tiếp tục gọi `/api/auth/sync` như trước. Login không tự tạo User hay đổi role/status.
Sai mật khẩu vẫn là 401; thiếu dữ liệu là 400; challenge Cognito vẫn là 409.

`/api/auth/sync`, refresh khi đọc được User BLOCKED và các API được ActiveUserFilter bảo vệ
cũng dùng thông báo khóa thống nhất. Khi Cognito đã từ chối refresh vì token bị thu hồi,
refresh vẫn trả 401 theo luồng hiện có.

## Khi AWS hoặc DB lỗi

AWS và PostgreSQL không có transaction chung. Cách xử lý hiện tại:

| Tình huống | Kết quả |
|---|---|
| AWS disable thất bại/timeout | Local vẫn commit BLOCKED; Admin nhận 429/502/503, không nhận 200 giả. Login backend và API nghiệp vụ vẫn chặn. |
| AWS enable thất bại/timeout | User trước đó BLOCKED vẫn BLOCKED local. |
| DB ghi BLOCKED lỗi trước khi gọi AWS | Rollback, không gọi Cognito; Admin nhận lỗi DB. |
| AWS thành công nhưng DB ghi/commit lỗi | Không trả 200; có thể lệch trạng thái. Gửi lại cùng yêu cầu để đồng bộ. |

`CognitoUserStatusSyncException` là ngoại lệ duy nhất được loại khỏi rollback trong API
đổi trạng thái. Không đổi thành `noRollbackFor = Exception.class` hoặc bỏ qua các lỗi DB.
Service Cognito không mở một transaction DB khác làm transaction đang chạy bị rollback-only.

Khi gặp lỗi AWS, gửi lại **cùng PATCH và cùng status** sau khi xử lý nguyên nhân.
Kể cả local đã đúng status, backend vẫn gọi lại Cognito; `updatedAt` chỉ đổi nếu status local thực sự đổi.
Không có job retry nền trong thay đổi này. Không chạy đồng thời hai status ngược nhau để thử lại.

Response lỗi đồng bộ có thêm `code` để xác định bước cần xử lý:

| code | Việc cần kiểm tra |
|---|---|
| `COGNITO_ACCESS_DENIED` | AWS từ chối thao tác: kiểm tra IAM principal/credentials của BE và hai quyền quản trị trên đúng User Pool. |
| `COGNITO_USER_NOT_FOUND` | `cognito_sub` và User Pool có khớp với User thực tế trên Cognito không. |
| `COGNITO_POOL_NOT_FOUND` | User Pool ID từ issuer và region của Cognito client. |
| `COGNITO_CLIENT_ERROR` | Credentials không tải được, lỗi mạng hoặc timeout. |
| `COGNITO_RATE_LIMITED` | Chờ rồi gửi lại yêu cầu; tránh gọi dồn liên tục. |
| `COGNITO_IDENTITY_MISSING` | User local chưa có `cognito_sub` hợp lệ. |
| `COGNITO_SERVICE_ERROR` / `COGNITO_STATUS_SYNC_FAILED` | Xem log backend để xác định lỗi còn lại. |

Log ghi thêm `code`, `providerCode`, `requestId`, `rootCause`; không ghi token, secret hoặc
nguyên văn thông báo từ AWS. API không tự cấp quyền IAM và không trả 200 để bỏ qua lỗi đồng bộ.

## Quyền AWS cần có

IAM principal mà backend đang sử dụng cần quyền quản trị trên đúng User Pool.
Cognito kiểm tra IAM cho [AdminDisableUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminDisableUser.html)
và [AdminEnableUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_AdminEnableUser.html).

Policy minh họa cho User Pool hiện cấu hình trong dự án; thay `AWS_ACCOUNT_ID` bằng tài khoản AWS thực tế:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "cognito-idp:AdminDisableUser",
        "cognito-idp:AdminEnableUser"
      ],
      "Resource": "arn:aws:cognito-idp:us-east-1:AWS_ACCOUNT_ID:userpool/us-east-1_i2fjAyM0t"
    }
  ]
}
```

Không cấp quyền quản trị này cho FE. Nếu backend chưa có quyền/credentials phù hợp,
API sẽ báo lỗi đồng bộ; trạng thái khóa local vẫn được giữ.

Khi BLOCKED, ActiveUserFilter chặn cả token cũ bằng trạng thái local. Sau khi mở khóa,
không dùng việc xác minh chữ ký JWT offline để kết luận token đã bị thu hồi ở Cognito có hợp lệ hay không;
[Cognito nêu rõ JWT offline không tự nhận biết việc thu hồi](https://docs.aws.amazon.com/cognito/latest/developerguide/token-revocation.html).
Thay đổi này không bổ sung danh sách thu hồi JWT cho nghiệp vụ Logout.

## FE hiển thị thông báo và không vào màn hình chính

FE chỉ lưu token và chuyển trang khi Login thành công. Khi HTTP 403 và `code = ACCOUNT_BLOCKED`:

1. Hiển thị `response.message` trên màn hình đăng nhập.
2. Xóa token và thông tin đăng nhập cũ khỏi state/storage đang dùng.
3. Giữ ở màn hình đăng nhập; không chuyển vào trang chính và không tiếp tục Sync.
4. Nếu đang ở trong web và một API trả lỗi này, kết thúc phiên FE và về màn hình đăng nhập.

Ví dụ luồng xử lý với fetch (ghép vào code FE đang dùng):

```javascript
const response = await fetch(`${apiBaseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
});
const data = await response.json();

if (!response.ok) {
    // Hiển thị data.message. Nếu ACCOUNT_BLOCKED, xóa trạng thái đăng nhập cũ.
    // Kết thúc xử lý ở đây; không lưu token hoặc điều hướng vào web.
    return;
}

// Chỉ nhánh thành công mới lưu token, Sync và chuyển trang theo flow hiện tại.
```

BE trả lỗi và chặn dữ liệu; hiển thị thông báo và điều hướng là trách nhiệm FE.
Đợt sửa này không bổ sung màn hình đăng nhập vào giao diện mẫu.

## Các file thay đổi

| File | Mục đích |
|---|---|
| [AdminUserServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/admin/service/impl/AdminUserServiceImpl.java) | Kết hợp khóa/mở local với Cognito, retry cùng status và giữ local BLOCKED nếu AWS lỗi. |
| [CognitoUserAdminService.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/CognitoUserAdminService.java) | Interface của service quản trị Cognito. |
| [CognitoUserAdminServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/CognitoUserAdminServiceImpl.java) | Gọi AdminDisableUser/AdminEnableUser, phân loại lỗi AWS. |
| [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java) | Kiểm tra trạng thái khi Login và Refresh, không trả token cho User BLOCKED. |
| [UserRepository.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/repository/UserRepository.java) | Thêm truy vấn khóa User theo email không phân biệt hoa thường. |
| [AccountBlockedException.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/AccountBlockedException.java) | Định nghĩa mã và thông báo khóa thống nhất. |
| [CognitoUserStatusSyncException.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/CognitoUserStatusSyncException.java) | Phân biệt lỗi đồng bộ AWS với lỗi DB để xử lý transaction đúng. |
| [GlobalExceptionHandler.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/GlobalExceptionHandler.java) | Trả JSON ACCOUNT_BLOCKED và thông báo tiếng Việt. |
| [ActiveUserFilter.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/filter/ActiveUserFilter.java) | Token đang dùng cũng nhận thông báo khóa thống nhất. |
| [UserSyncServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/service/impl/UserSyncServiceImpl.java) | Sync từ chối User BLOCKED bằng cùng mã lỗi. |

Không thêm bảng/cột và không thay đổi dữ liệu Supabase hay IAM/Cognito trong lúc sửa code.

## Kiểm thử

Các test mới mô phỏng repository/Cognito, kiểm tra thứ tự thao tác và dùng Spring transaction
interceptor thật để xác nhận quy tắc commit/rollback. Không dùng DB thật hoặc gọi AWS.
Đã có kiểm tra HTTP Login trả 403 đúng JSON, không trả token; token cũ bị filter chặn;
Login ACTIVE/lần đầu; email alias; mật khẩu sai; mở/khóa lặp lại; AWS timeout, thiếu quyền,
throttling và DB ghi lỗi. Kiểm tra khóa hàng cạnh tranh và commit thật trên PostgreSQL cần
được kiểm thử tiếp trên môi trường thử nghiệm của dự án.

Kết quả ngày 03/11/2026: compile thành công. Lần kiểm tra sau cùng có 37 test khóa/đăng nhập,
phân loại lỗi và response HTTP đều qua. Các nhóm Auth/Profile/Sync, CORS và âm thanh đã có
60 test hồi quy qua trong đợt kiểm tra trước. Không chạy `BackendApplicationTests` vì test đó
mở toàn bộ ứng dụng và có thể kết nối DB thật.

Chạy riêng các test mới:

```powershell
mvn "-Dtest=AdminUserStatusServiceTest,CognitoUserAdminServiceImplTest,AuthLoginBlockedTest,ActiveUserBlockedFilterTest" test
```
