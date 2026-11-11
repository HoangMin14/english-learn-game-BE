# Kết quả triển khai Google Login V2 — Backend Alphabet Universe

**Ngày:** 10/11/2026  
**Phạm vi:** Backend và cấu hình local công khai; không sửa Frontend, database hoặc tài khoản/cấu hình AWS.

**Cập nhật V2:** Đã thực hiện phần BE theo bản `DAC_TA_THONG_NHAT_GOOGLE_LOGIN_BE_FE_V2 (1).md` người dùng cung cấp. Các mục kiểm thử AWS/FE thực tế vẫn cần nghiệm thu riêng.

API mới: **POST /api/auth/google**.

Hợp đồng chung: [Google Login BE/FE V2](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/docs/DAC_TA_THONG_NHAT_GOOGLE_LOGIN_BE_FE_V2.md). Báo cáo này mô tả code V2 đã sửa; tài liệu kế hoạch cũ không thay thế contract V2.

## 1. API đã triển khai

### Request

Không gửi Bearer token vào endpoint này. Nếu FE gửi một Bearer token sai/hết hạn, Resource Server vẫn có thể trả 401 trước Controller; permitAll không vô hiệu hóa việc xác minh token đã gửi.

~~~http
POST /api/auth/google
Content-Type: application/json
~~~

~~~json
{
  "code": "<code-Cognito-tra-ve-callback>",
  "redirectUri": "http://localhost:3000/callback",
  "codeVerifier": "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"
}
~~~

Verifier ví dụ chỉ minh họa định dạng. Code/verifier thực tế phải thuộc cùng giao dịch PKCE S256 do FE tạo.

### Response 200

~~~json
{
  "accessToken": "<Cognito access token>",
  "idToken": "<Cognito ID token>",
  "refreshToken": "<Cognito refresh token>",
  "expiresIn": 3600
}
~~~

Backend trả DTO trực tiếp và header Cache-Control: no-store, Pragma: no-cache. expiresIn lấy từ Cognito, không mặc định 3600 khi response bị thiếu.

FE tiếp tục gọi POST /api/auth/sync bằng accessToken, chờ profile ACTIVE rồi mới thiết lập phiên và điều hướng.

## 2. Logic đã có trong BE

1. Validate code, redirectUri và verifier 43–128 ký tự đúng tập ký tự PKCE.
2. Kiểm tra redirectUri khớp chính xác allowlist của server.
3. Gửi form POST tới Cognito /oauth2/token kèm code_verifier.
4. Kiểm tra token response: đủ các token, token_type là Bearer, expires_in dương.
5. Dùng JwtDecoder hiện tại kiểm tra access token; kiểm tra thêm sub, token_use, client_id và thời hạn.
6. Với Google login, yêu cầu đủ scope openid, email, profile, aws.cognito.signin.user.admin.
7. User local BLOCKED trả 403 ACCOUNT_BLOCKED; trạng thái không ACTIVE khác trả 403 ACCOUNT_INACTIVE.
8. User mới chưa Sync được phép tiếp tục. API Google không gọi GetUser, không kiểm tra email và không tạo user local.
9. Sync kiểm tra email trước INSERT. Email thuộc sub khác trả 409 EMAIL_ALREADY_EXISTS; không tự liên kết hoặc sửa danh tính.
10. Sync giữ transaction READ_COMMITTED, SELECT FOR UPDATE và role/status/createdAt của user cũ. INSERT trả 1 dùng lại hồ sơ vừa đọc; INSERT trả 0 đọc hồ sơ mới sau khi lấy khóa.
11. Cùng sub tìm thấy qua email vẫn đi qua INSERT ON CONFLICT trước khi lấy khóa. Native method hiện có clearAutomatically=true, giúp bỏ entity được precheck tải vào Persistence Context và đọc entity mới dưới khóa.
12. UNIQUE email vẫn bảo vệ race cuối cùng. Không bắt lỗi SQL rồi query trong transaction đã lỗi; để rollback và handler trả cùng 409/code/message như precheck.
13. Khi user mới thiếu name, Sync dùng phần trước @ đã chuẩn hóa, tối đa 100 code point; nếu phần đó không dùng được thì dùng Learner. User cũ giữ tên khi Cognito thiếu name.
14. Các lỗi Cognito khi Sync/PATCH hồ sơ trả code phù hợp: UNAUTHORIZED, TOO_MANY_REQUESTS, COGNITO_OAUTH_FORBIDDEN, COGNITO_UNAVAILABLE; không dùng fallback để bỏ qua lỗi token/WAF.

Thông thường Sync chỉ gọi GetUser một lần. Nhánh request khác đã tạo cùng sub cần đọc lại sau khóa, nên có thể gọi hai lần trong request đó.

HTTP client có connect timeout 3 giây, read timeout 8 giây và không theo redirect. Không có vòng lặp retry đổi code. Các record chứa code/verifier/token đã che dữ liệu khi gọi toString.

## 3. File mới

| File | Tại sao thêm |
|---|---|
| [GoogleAuthRequest.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/dto/GoogleAuthRequest.java) | DTO và validation PKCE, che dữ liệu trong toString |
| [CognitoOAuthTokenResponse.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/dto/CognitoOAuthTokenResponse.java) | Mapping response snake_case từ Cognito, giữ riêng với DTO public |
| [CognitoOAuthService.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/CognitoOAuthService.java) | Interface đổi code và refresh token |
| [CognitoOAuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/CognitoOAuthServiceImpl.java) | Impl gọi token endpoint và phân loại lỗi |
| [CognitoOAuthSettings.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/config/CognitoOAuthSettings.java) | Kiểm tra domain HTTPS, allowlist callback và chế độ refresh |
| [CognitoOAuthException.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/CognitoOAuthException.java) | Mang HTTP/code/message an toàn tới handler |
| [EmailAlreadyExistsException.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/EmailAlreadyExistsException.java) | Lỗi nghiệp vụ email có chung code/message cho precheck và UNIQUE race |

## 4. File hiện có đã sửa

| File | Thay đổi và mục đích |
|---|---|
| [AuthController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/controller/AuthController.java) | Endpoint Google; no-store cho Google/refresh; Swagger giải thích phải chờ Sync |
| [AuthService.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/AuthService.java) | Bổ sung loginWithGoogle |
| [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java) | Kiểm tra danh tính/status; V2 bỏ GetUser/email tại exchange; giữ refresh SDK mặc định |
| [RestClientConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/config/RestClientConfig.java) | Client có qualifier riêng cho OAuth, timeout rõ và không redirect |
| [SecurityConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/config/SecurityConfig.java) | Chỉ mở public POST /api/auth/google |
| [ActiveUserFilter.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/filter/ActiveUserFilter.java) | Bypass local-user filter cho POST Google; giữ chặn API nghiệp vụ |
| [GlobalExceptionHandler.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/GlobalExceptionHandler.java) | Lỗi OAuth có code; V2 đồng nhất precheck/UNIQUE email; validation không echo input |
| [UserRepository.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/repository/UserRepository.java) | Truy vấn chủ email không phân biệt hoa thường |
| [UserSyncServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/service/impl/UserSyncServiceImpl.java) | Precheck email, xử lý INSERT 1/0, tên dự phòng và giữ cơ chế khóa/rollback |
| [CognitoUserAttributeServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/service/impl/CognitoUserAttributeServiceImpl.java) | Bổ sung code lỗi cho Sync/PATCH, giữ nguyên HTTP status và cơ chế xác minh khi timeout |
| [application.properties](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/resources/application.properties) | Mapping allowlist callback và refresh mode qua biến môi trường |

Không thêm bảng hoặc migration. UserRepository đã có findByEmailIgnoreCase và insertIfAbsent trả int nên V2 không khai báo trùng hay sửa Repository. JwtConfig, SecurityConfig, ActiveUserFilter, CorsConfig và UserProfileService được tái sử dụng.

Riêng V2 sửa sáu file Java chính: AuthController, AuthServiceImpl, GoogleAuthRequest, UserSyncServiceImpl, GlobalExceptionHandler và CognitoUserAttributeServiceImpl; thêm EmailAlreadyExistsException. Các file khác trong bảng ghi lại cả phần triển khai Google trước V2.

## 5. Cấu hình để chạy

Tiếp tục dùng các biến đang có: AWS_COGNITO_CLIENT_ID, AWS_COGNITO_DOMAIN và FRONTEND_URL.

Cấu hình public dùng cho local V2 (hai biến OAuth và FRONTEND_URL hiện có):

~~~dotenv
FRONTEND_URL=http://localhost:3000
AWS_COGNITO_ALLOWED_REDIRECT_URIS=http://localhost:3000/callback
AWS_COGNITO_REFRESH_MODE=sdk
~~~

Đã đổi riêng FRONTEND_URL trong .env local từ 5173 sang 3000. Thêm [.env.google.example](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/.env.google.example) làm mẫu cấu hình công khai. Không sửa credential hoặc các biến khác. Cần khởi động lại BE bằng môi trường đã cập nhật; biến được IntelliJ/process ghi đè phải có cùng giá trị.

- Có nhiều callback: phân cách bằng dấu phẩy, tất cả phải đăng ký trên Cognito App Client.
- Nếu không cấu hình allowlist, Backend lấy FRONTEND_URL đã bỏ dấu / cuối và thêm /callback.
- Chỉ cho callback HTTPS; HTTP được phép với hostname localhost cho môi trường phát triển.
- Mặc định refresh-mode=sdk giữ REFRESH_TOKEN_AUTH hiện tại.
- Đặt refresh-mode=oauth để dùng /oauth2/token, hỗ trợ trả refresh token mới khi rotation bật.
- Trước khi chọn oauth cho cả email/password, xác nhận device remembering không bật. Không tự bật/tắt cấu hình AWS từ code này.
- Không bật rotation khi BE còn dùng sdk mode. Hai nhánh không tự fallback/retry sang nhau.

File application.properties hiện đang bị Git ignore trong dự án. Không force-add file chứa cấu hình local. CognitoOAuthSettings có fallback trực tiếp cho hai tên biến môi trường mới, nên team có thể cấu hình qua environment của process; kiểm thử đã kiểm tra cách này.

.env không tự được Spring Boot nạp nếu dự án chưa có loader. Đặt biến vào môi trường chạy của IntelliJ/systemd hoặc cơ chế cấu hình đang sử dụng, rồi khởi động lại Backend.

## 6. AWS/Google còn phải cấu hình và xác nhận

- Google là IdP được bật cho đúng Cognito User Pool/App Client.
- Dùng public App Client không secret theo phạm vi V1, cùng client ID BE đang xác minh.
- Authorization code grant được bật.
- Cognito App Client cho phép openid, email, profile và aws.cognito.signin.user.admin.
- Google OAuth callback tới https://<cognito-domain>/oauth2/idpresponse.
- Cognito Allowed callback URL là http://localhost:3000/callback cho local, khớp allowlist BE và FE.
- Allowed sign-out URL khớp FRONTEND_URL/login.
- Mapping email/name và quyền đọc/ghi thuộc tính đúng.
- Xác nhận device remembering/refresh rotation trước khi chọn chế độ refresh.

Chưa truy cập Console hoặc gọi các API AWS để xác nhận những cấu hình này. Kết quả mock không thay thế kiểm thử Google login thật.

## 7. Contract lỗi

| HTTP | code |
|---|---|
| 400 | VALIDATION_ERROR, OAUTH_REDIRECT_URI_INVALID, OAUTH_CODE_INVALID |
| 401 | REFRESH_TOKEN_INVALID hoặc UNAUTHORIZED cho refresh/phiên không hợp lệ |
| 403 | ACCOUNT_BLOCKED, ACCOUNT_INACTIVE, COGNITO_OAUTH_FORBIDDEN |
| 409 | EMAIL_ALREADY_EXISTS tại Sync, cho cả precheck và UNIQUE email |
| 429 | TOO_MANY_REQUESTS |
| 502 | COGNITO_OAUTH_CONFIG_ERROR, COGNITO_OAUTH_INVALID_RESPONSE |
| 503 | COGNITO_UNAVAILABLE |

User BLOCKED tiếp tục có message tiếng Việt hiện tại và không có token trong response.

## 8. Kiểm thử

**Kết quả V2: 183 kiểm thử đạt, 0 failure, 0 error, 0 skipped, trên 13 test suite liên quan.** Đã compile source và test với Java release 17. Các test dùng mock HTTP/Cognito/JWT/repository, transaction manager và Spring Security chain; không gọi Cognito/Google hoặc database thật.

| Suite | Số test đạt |
|---|---:|
| CognitoOAuthSettingsTest | 24 |
| CognitoOAuthWiringTest | 2 |
| CognitoOAuthServiceImplTest | 33 |
| AuthGoogleLoginTest | 21 |
| GoogleAuthControllerTest | 21 |
| AuthLoginBlockedTest | 12 |
| AuthProfileIntegrationTest | 3 |
| UserSyncServiceImplTest | 25 |
| CognitoUserAttributeServiceImplTest | 18 |
| UserProfileControllerTest | 9 |
| UserProfileServiceImplTest | 8 |
| ActiveUserBlockedFilterTest | 1 |
| CorsConfigTest | 6 |
| **Tổng** | **183** |

Lệnh chạy nhóm 181 test:

~~~powershell
mvn -o "-Dtest=CognitoOAuthSettingsTest,CognitoOAuthServiceImplTest,AuthGoogleLoginTest,GoogleAuthControllerTest,AuthLoginBlockedTest,AuthProfileIntegrationTest,UserSyncServiceImplTest,CognitoUserAttributeServiceImplTest,UserProfileControllerTest,UserProfileServiceImplTest,ActiveUserBlockedFilterTest,CorsConfigTest" test
~~~

Lệnh chạy thêm hai test khởi tạo cấu hình/bean:

~~~powershell
mvn -o "-Dtest=CognitoOAuthWiringTest" test
~~~

Trên môi trường sandbox Windows, hai test khởi tạo HTTP client cần quyền loopback nội bộ của JDK. Sau khi chạy ở môi trường cho phép loopback, cả hai đã đạt; không có request tới dịch vụ thật.

Các nhóm kiểm tra:

- Allowlist exact-match, domain/callback sai, biến môi trường và default refresh mode.
- Form encoding giữ nguyên code có ký tự + hoặc &, verifier chuyển đúng.
- Code sai/hết hạn, lỗi client, WAF, rate limit, timeout, HTML/JSON hỏng, response thiếu token/thời hạn.
- JWT sai signature thông qua decoder failure, token_use/client/sub/expiry/scope không phù hợp.
- BLOCKED, trạng thái khác ACTIVE; exchange không đọc profile/email hoặc tạo user; email trùng trả 409 tại Sync.
- Rotation bật/tắt ở nhánh OAuth, giữ nhánh SDK, user bị khóa và user chưa Sync khi refresh.
- Public POST Google, request không Bearer, Sync vẫn protected, callback lỗi không làm public mọi API.
- CORS preflight, lỗi validation/JSON không lộ code, token response no-store.
- Sync INSERT 1 chỉ đọc hồ sơ một lần, INSERT 0 đọc lại sau khóa, giữ tên/status user cũ; tên dự phòng cho user mới.
- UNIQUE INSERT/UPDATE race giả lập, không query sau lỗi SQL; transaction proxy kiểm tra READ_COMMITTED và rollback khi UNIQUE lỗi.
- Response HTTP 409 giống nhau giữa precheck và hai constraint email; UNIQUE khác/SQLState khác không bị đổi thành 409.
- Các regression login/hồ sơ hiện có.

Chưa kiểm chứng schema PostgreSQL hoặc race đồng thời trên PostgreSQL thật; test race ở đây mô phỏng nhánh INSERT và exception. Test transaction proxy kiểm tra lệnh rollback, không thay thế integration test DB. Ràng buộc UNIQUE và transaction Sync hiện tại được giữ nguyên. Chưa chạy browser Google Login trên App Client thật.

## 9. Handoff nhanh cho FE

1. Dùng đúng ba field code, redirectUri, codeVerifier.
2. Không gửi Authorization vào /auth/google và không auto-refresh/replay request đổi code.
3. Yêu cầu đủ bốn scope đã chốt.
4. Nhận token nhưng chờ Sync/profile ACTIVE trước khi lưu phiên thành công.
5. Dùng accessToken làm Bearer, không dùng idToken.
6. Nhận response DTO trực tiếp, không yêu cầu Backend bọc success/data hoặc thêm tokenType.
7. Đọc lỗi từ code/message; Sync 409 thống nhất EMAIL_ALREADY_EXISTS. Token tạm phải bị bỏ khi Sync 409/403/401, không điều hướng vào ứng dụng.
8. Logout response có logoutUrl; FE cần điều hướng trình duyệt tới đó sau khi dọn local.
9. Nếu Cognito chặn tài khoản tại Hosted UI trước khi cấp code, xử lý lỗi callback; không giả định luôn nhận 403 từ API Google.

Nguồn giao thức: [Cognito token endpoint](https://docs.aws.amazon.com/cognito/latest/developerguide/token-endpoint.html), [refresh tokens](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-the-refresh-token.html), [Google IdP](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-social-idp-google.html).
