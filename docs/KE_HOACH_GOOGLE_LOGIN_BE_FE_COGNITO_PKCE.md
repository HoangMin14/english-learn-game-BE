# Kế hoạch Google Login cho Backend và Frontend — Cognito + PKCE

**Dự án:** Alphabet Universe  
**Ngày đối chiếu source:** 10/11/2026  
**Trạng thái:** Phần BE đã được triển khai trong workspace ngày 10/11/2026; cấu hình AWS và kiểm thử liên thông với FE vẫn cần thực hiện.  
**Phương án:** FE khởi tạo Authorization Code + PKCE; BE đổi code lấy token Cognito.

Tài liệu này là kế hoạch và hợp đồng chung cho BE/FE. Xem [kết quả triển khai BE](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/docs/GOOGLE_AUTH_BACKEND_IMPLEMENTATION.md) để biết file đã sửa, cấu hình thực tế và phạm vi kiểm thử. Những checklist bên dưới vẫn là tiêu chí nghiệm thu liên thông, không đồng nghĩa đã kiểm tra Google/Cognito thật.

## 1. Quyết định triển khai cho V1

| Hạng mục | Quyết định |
|---|---|
| API mới | POST /api/auth/google |
| Bên chuyển hướng tới Cognito | Frontend, bằng điều hướng trình duyệt |
| Bên đổi code | Backend gọi Cognito /oauth2/token |
| PKCE | Bắt buộc trong luồng dự án, dùng S256 |
| state | FE tạo ngẫu nhiên, lưu theo giao dịch đăng nhập và kiểm tra khi callback |
| Callback của ứng dụng | /callback trên Frontend |
| Token gọi API nghiệp vụ | Cognito accessToken |
| Đồng bộ PostgreSQL | Tiếp tục dùng POST /api/auth/sync |
| User mới | Tạo trong Sync; mặc định learner, ACTIVE |
| User cũ | Giữ nguyên role, status, id và createdAt |
| Tài khoản BLOCKED | BE chặn; FE không thiết lập phiên đăng nhập thành công |
| Trùng email nhưng khác Cognito sub | V1 trả 409; chưa tự liên kết tài khoản |
| Database | Chưa cần bảng/cột mới cho luồng cơ bản |
| Dịch vụ AWS mới | Không cần; sử dụng Backend hiện có và Cognito User Pool |

Token vẫn được trả về FE theo mô hình Bearer JWT hiện tại. Đây chưa phải mô hình Backend giữ toàn bộ token bằng session cookie HttpOnly.

### 1.1. Chốt App Client trước khi lập trình

Đề xuất cho phạm vi V1: cùng User Pool và cùng **public App Client không có client secret** mà các luồng đăng nhập hiện tại đang dùng, nếu cấu hình thực tế đáp ứng được.

- Bật Google, Authorization code grant và các scope cần thiết cho App Client này.
- BE đổi code vẫn dùng PKCE dù App Client không có secret.
- Không tạo App Client khác rồi mặc định rằng JwtConfig sẽ chấp nhận: code hiện tại chỉ chấp nhận client_id đã cấu hình.
- Nếu App Client thực tế có secret: cần thiết kế bổ sung xác thực client ở BE và kiểm tra cả các API đăng nhập/đăng ký hiện có. Không đưa secret vào FE.
- Google OAuth Client Secret là thông tin cấu hình Google IdP trong Cognito; không nhầm với Cognito App Client Secret.

Phân loại public/confidential client được AWS mô tả tại [App clients](https://docs.aws.amazon.com/cognito/latest/developerguide/user-pool-settings-client-apps.html).

## 2. Hiện trạng source trước khi triển khai

**Backend:** D:/AWS/PJ_AWS_1/aws-workshop-main/backend  
**Frontend:** D:/AWS/english-learn-game-FE-main  
**Tài liệu FE đề xuất ban đầu:** D:/BACKEND_GOOGLE_AUTH_IMPLEMENTATION.md

| Phần hiện tại | Kết quả đối chiếu |
|---|---|
| AuthController/AuthService | Chưa có loginWithGoogle hoặc POST /api/auth/google |
| Login email/mật khẩu | Backend gọi Cognito USER_PASSWORD_AUTH |
| JwtConfig | Kiểm tra issuer, thời hạn, token_use=access và client_id đúng cấu hình |
| UserSyncServiceImpl | Khóa user, gọi GetUser, insert nếu chưa có, giữ role/status của user cũ |
| CognitoUserAttributeServiceImpl | Dùng GetUser và UpdateUserAttributes |
| ActiveUserFilter | Chặn user local chưa Sync và user không ACTIVE trên API nghiệp vụ |
| Refresh hiện tại | InitiateAuth với REFRESH_TOKEN_AUTH |
| Logout hiện tại | BE trả logoutUrl; chưa có nghiệp vụ thu hồi refresh token trong method này |
| FE AuthProvider | Dùng profile.status=ACTIVE để xác định isAuthenticated |
| FE apiClient | Gắn access token, tự refresh khi API được bảo vệ trả 401 |
| FE tokenStorage | Hiện lưu access/refresh token vào localStorage; có cơ chế sessionRevision |
| FE router | Chưa có route /callback trong bản source đã đọc |
| FE auth types | AuthTokens đang yêu cầu tokenType, trong khi LoginResponse BE không có field này |

Các điểm trên là hiện trạng source đã đọc, không phải kết quả kiểm tra cấu hình trên Google Cloud/Cognito Console.

## 3. Flow tổng thể

~~~mermaid
sequenceDiagram
    actor U as Người dùng
    participant FE as Frontend
    participant C as Cognito
    participant G as Google
    participant BE as Backend
    participant DB as PostgreSQL
    U->>FE: Nhấn Tiếp tục với Google
    FE->>FE: Tạo state, verifier, challenge S256
    FE->>C: Điều hướng /oauth2/authorize
    C->>G: Chuyển tới Google
    G-->>C: Kết quả xác thực
    C-->>FE: /callback?code=...&state=...
    FE->>FE: Kiểm tra state và giao dịch đăng nhập
    FE->>BE: POST /api/auth/google
    BE->>C: POST /oauth2/token + code_verifier
    C-->>BE: Token Cognito
    BE->>BE: Xác minh access token
    BE->>DB: Kiểm tra user local và trạng thái
    BE-->>FE: Bộ token nếu hợp lệ
    FE->>BE: POST /api/auth/sync bằng access token vừa nhận
    BE->>C: GetUser
    BE->>DB: Tạo/cập nhật user trong transaction Sync
    BE-->>FE: UserResponse
    FE->>FE: Thiết lập AuthContext, lưu phiên và điều hướng
~~~

BE phải xử lý trường hợp hồ sơ mới trùng email trước khi báo đăng nhập Google thành công; UNIQUE tại Sync vẫn là lớp bảo vệ cuối cùng nếu có request đồng thời.

Nếu user đã bị disable trên Cognito, Cognito có thể chặn ngay ở bước đăng nhập và trả lỗi về callback. Khi đó request có thể chưa tới API Google của BE. FE phải xử lý cả lỗi callback, không chỉ HTTP 403 từ BE.

## 4. Phân công trách nhiệm

| Phần việc | FE | BE | Người cấu hình AWS/Google |
|---|---|---|---|
| Nút Google, trạng thái loading/lỗi | Chính | | |
| state, PKCE, lưu giao dịch trong sessionStorage | Chính | Nhận và chuyển verifier đúng cách | |
| Điều hướng trình duyệt tới Cognito | Chính | | |
| Google OAuth client, Cognito IdP, mapping | | Đối chiếu cấu hình | Chính |
| Đổi code tại token endpoint | | Chính | Bật đúng grant/callback |
| Kiểm tra JWT và trạng thái local | Không tự quyết định quyền | Chính | Đúng App Client |
| Tạo/cập nhật user DB | | UserSyncService hiện có | |
| Chỉ cho vào giao diện sau Sync | Chính | Trả trạng thái chính xác | |
| Refresh và logout | Dùng contract chung | Xử lý Cognito | Cấu hình phù hợp |

## 5. Cấu hình Google và Cognito

### 5.1. Phân biệt hai callback

| Nơi cấu hình | URL | Bên nhận kết quả |
|---|---|---|
| Google OAuth client — Authorized redirect URIs | https://<cognito-domain>/oauth2/idpresponse | Cognito |
| Cognito App Client — Allowed callback URLs | http://localhost:5173/callback | Frontend local |
| Cognito App Client — Allowed sign-out URLs | http://localhost:5173/login | Frontend local |

Không nhập FE callback thay cho idpresponse trong Google OAuth client. URL production phải cấu hình riêng; HTTPS dùng cho môi trường triển khai.

Hướng dẫn chính thức: [Google IdP trong Cognito](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-social-idp-google.html).

### 5.2. Các việc người quản lý cấu hình cần thực hiện

1. Trong Google Cloud, tạo/chọn OAuth client loại Web application và khai báo thông tin ứng dụng, callback tới Cognito.
2. Nếu Google app còn ở chế độ Testing, kiểm tra danh sách test users được phép đăng nhập.
3. Trong Cognito User Pool, thêm Google Identity Provider bằng Google Client ID/Secret.
4. Cấu hình mapping email, email_verified nếu Google cung cấp, và name theo nghiệp vụ.
5. Kiểm tra các thuộc tính được mapping là mutable và App Client được phép đọc/ghi cần thiết.
6. Bật Google cho App Client; giữ Cognito provider nếu vẫn dùng đăng nhập local.
7. Bật Authorization code grant, cấu hình chính xác callback/sign-out URL.
8. Thống nhất client ID, domain, scope, refresh rotation và device remembering với BE trước khi bật luồng cho FE.

Đối chiếu mapping tại [Mapping IdP attributes](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-specifying-attribute-mapping.html).

### 5.3. Scope chốt cho V1

Yêu cầu tới Cognito:

~~~text
openid email profile aws.cognito.signin.user.admin
~~~

- Bật các scope này trên Cognito App Client và đưa vào authorize request.
- Scope Google upstream được cấu hình riêng ở Google IdP.
- aws.cognito.signin.user.admin phục vụ API hồ sơ của chính user; không cấp role admin của Alphabet Universe.
- Scope này phù hợp với GetUser và UpdateUserAttributes đang có trong BE, do đó không mặc định thêm fallback userInfo.
- Nếu cấu hình không cấp được scope này, phải sửa thiết kế đọc/cập nhật hồ sơ trước khi triển khai; không lặng lẽ bỏ scope để đăng nhập rồi để các API sau bị lỗi.

Nguồn: [GetUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_GetUser.html), [UpdateUserAttributes](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_UpdateUserAttributes.html).

## 6. Hợp đồng API mới giữa BE và FE

### 6.1. POST /api/auth/google

API công khai trước đăng nhập. FE không gửi Authorization header vào request này.

~~~http
POST /api/auth/google
Content-Type: application/json
Accept: application/json
~~~

~~~json
{
  "code": "<authorization-code-do-Cognito-tra-ve>",
  "redirectUri": "http://localhost:5173/callback",
  "codeVerifier": "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"
}
~~~

Chuỗi verifier trong ví dụ chỉ minh họa hình dạng request. Khi chạy, verifier phải được tạo ngẫu nhiên và khớp challenge của chính giao dịch authorize đó; không dùng hằng số ví dụ.

| Field | Quy tắc |
|---|---|
| code | Bắt buộc, không rỗng; là code Cognito trả về callback |
| redirectUri | Bắt buộc; khớp chính xác URL dùng khi authorize và nằm trong allowlist cấu hình BE |
| codeVerifier | Bắt buộc; 43–128 ký tự thuộc tập A–Z, a–z, 0–9, dấu -, ., _, ~ |

BE lấy domain/client ID từ cấu hình server, không lấy từ JSON của FE. Không nhận userId, email, role hoặc status từ FE để quyết định danh tính.

state được kiểm tra tại FE callback vì FE là bên giữ giao dịch đăng nhập trong phương án này. Không thêm field state vào request BE rồi chỉ kiểm tra “có giá trị” và coi đó là đã xác minh state.

### 6.2. Response 200 trên đường truyền

Tái sử dụng LoginResponse:

~~~json
{
  "accessToken": "<cognito-access-token>",
  "idToken": "<cognito-id-token>",
  "refreshToken": "<cognito-refresh-token>",
  "expiresIn": 3600
}
~~~

- expiresIn là giá trị thực Cognito trả về, đơn vị giây.
- Code grant có yêu cầu openid phải được kiểm tra đủ các token mong đợi và thời hạn hợp lệ; không tự mặc định 3600 khi response bị thiếu.
- Backend trả Cache-Control: no-store và Pragma: no-cache cho response chứa token.
- Không thêm vỏ success/data vào BE chỉ vì FE có kiểu ApiResponse. apiClient FE hiện đã bọc DTO BE thành ApiResponse phía client.
- AuthTokens FE cần sửa tokenType thành tùy chọn hoặc loại khỏi giả định bắt buộc. Header gọi API vẫn là Bearer.
- idToken không được dùng làm Bearer cho các API BE hiện tại.

Nguồn giao thức: [Cognito token endpoint](https://docs.aws.amazon.com/cognito/latest/developerguide/token-endpoint.html).

### 6.3. POST /api/auth/sync tiếp tục được sử dụng

~~~http
POST /api/auth/sync
Authorization: Bearer <accessToken-vua-nhan>
~~~

Không cần request body. Response là UserResponse hiện tại:

~~~json
{
  "id": 123,
  "email": "learner@example.com",
  "displayName": "Người học",
  "role": "learner",
  "status": "ACTIVE"
}
~~~

FE chỉ thiết lập phiên thành công sau khi nhận profile ACTIVE từ Sync. User mới có thể đã tồn tại trên Cognito nhưng chưa có bản ghi local trước bước này; API Google không được từ chối user chỉ vì chưa Sync.

### 6.4. Contract lỗi cần bổ sung

Đề xuất các lỗi mới dùng JSON có code và message để khớp normalizeApiError FE đang có.

~~~json
{
  "code": "OAUTH_CODE_INVALID",
  "message": "Phiên đăng nhập Google không còn hợp lệ. Vui lòng đăng nhập lại."
}
~~~

| HTTP | code đề xuất | Trường hợp |
|---|---|---|
| 400 | VALIDATION_ERROR | Thiếu field, verifier sai định dạng hoặc JSON sai |
| 400 | OAUTH_REDIRECT_URI_INVALID | redirectUri không nằm trong allowlist |
| 400 | OAUTH_CODE_INVALID | invalid_grant: code hết hạn, đã dùng hoặc verifier không khớp |
| 401 | UNAUTHORIZED | Token gửi tới API được bảo vệ không hợp lệ/hết hạn |
| 403 | ACCOUNT_BLOCKED | User local BLOCKED, tái sử dụng exception/handler hiện có |
| 403 | ACCOUNT_INACTIVE | User local có trạng thái không ACTIVE khác BLOCKED |
| 409 | GOOGLE_ACCOUNT_EMAIL_CONFLICT | Email thuộc user local khác cognitoSub |
| 429 | TOO_MANY_REQUESTS | Cognito giới hạn request tương ứng |
| 502 | COGNITO_OAUTH_CONFIG_ERROR | Client/grant/scope cấu hình không phù hợp, invalid_client/unauthorized_client |
| 502 | COGNITO_OAUTH_INVALID_RESPONSE | Upstream trả token/body không hợp lệ hoặc JWT không qua xác minh |
| 503 | COGNITO_UNAVAILABLE | Không kết nối được, timeout hoặc Cognito trả lỗi dịch vụ tạm thời |

HTTP/code ở bảng là contract của ứng dụng, không phải cam kết mọi lỗi OAuth upstream có cùng HTTP. BE cần phân loại theo HTTP và error của Cognito.

Không trả nguyên error_description, HTML, stack trace hoặc exception message của upstream cho người dùng. Một lỗi cấu hình của ứng dụng không được biến thành “user đăng nhập sai” 401.

GlobalExceptionHandler hiện có thể trả lỗi Sync email dạng message mà chưa có code chuyên biệt; FE vẫn phải xử lý status 409. Có thể bổ sung code cho lỗi này trong phạm vi thống nhất contract, giữ nguyên HTTP và nghiệp vụ cũ.

## 7. Backend cần thêm/sửa những file nào?

### 7.1. File mới

| File đề xuất | Nhiệm vụ |
|---|---|
| D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/dto/GoogleAuthRequest.java | DTO code, redirectUri, codeVerifier và validation |
| D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/dto/CognitoOAuthTokenResponse.java | DTO nội bộ đọc snake_case response của Cognito; không dùng làm response public |
| D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/CognitoOAuthService.java | Interface cho exchangeAuthorizationCode và refreshTokens |
| D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/CognitoOAuthServiceImpl.java | Gọi token endpoint, kiểm tra response và phân loại lỗi |
| D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/CognitoOAuthException.java | Exception mang code an toàn để handler trả JSON đã thống nhất |

CognitoOAuthService phải có interface và impl đúng cấu trúc hiện tại. Không tạo thêm Entity/Repository chỉ để gọi HTTP Cognito.

### 7.2. File hiện có cần sửa

| File | Nội dung |
|---|---|
| [AuthController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/controller/AuthController.java) | Thêm POST /google, @Valid, response token no-store |
| [AuthService.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/AuthService.java) | Thêm loginWithGoogle(GoogleAuthRequest) |
| [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java) | Điều phối exchange, verify JWT, check status/email, mapping response; điều chỉnh refresh theo mục 11 |
| [SecurityConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/config/SecurityConfig.java) | Chỉ mở public POST /api/auth/google |
| [ActiveUserFilter.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/filter/ActiveUserFilter.java) | Đồng bộ danh sách auth bypass cho POST /api/auth/google; giữ chặn API nghiệp vụ |
| [RestClientConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/config/RestClientConfig.java) | Cấu hình HTTP client dành cho Cognito OAuth có timeout; không phá builder đang được IPA sử dụng |
| [GlobalExceptionHandler.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/GlobalExceptionHandler.java) | Map CognitoOAuthException và validation thành contract lỗi; tái sử dụng ACCOUNT_BLOCKED |
| [UserRepository.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/repository/UserRepository.java) | Bổ sung truy vấn email không phân biệt hoa thường nếu cần lấy chủ sở hữu email; không khai báo trùng method cũ |
| [UserSyncServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/service/impl/UserSyncServiceImpl.java) | Bổ sung quy tắc tên mặc định khi tạo user mới không có name, giữ khóa/role/status hiện tại |
| [application.properties](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/resources/application.properties) | Bổ sung mapping cấu hình OAuth và allowlist callback; dùng biến môi trường |

### 7.3. File tái sử dụng và kiểm tra, không mặc định phải viết lại

| File | Điều cần kiểm tra |
|---|---|
| [JwtConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/config/JwtConfig.java) | Dùng đúng issuer/client ID hiện có; giữ token_use=access |
| [CognitoUserAttributeServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/service/impl/CognitoUserAttributeServiceImpl.java) | GetUser/UpdateUserAttributes chạy được với scope đã chốt; không thêm catch-all fallback |
| [CognitoUserAttributeService.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/service/CognitoUserAttributeService.java) | Tái sử dụng interface đọc/cập nhật hồ sơ |
| [UserProfileServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/service/impl/UserProfileServiceImpl.java) | Test PATCH tên bằng Google token |
| [LoginResponse.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/dto/LoginResponse.java) | Giữ bốn field hiện tại |
| [RefreshTokenResponse.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/dto/RefreshTokenResponse.java) | Trả refresh token mới nếu rotation bật |
| [CorsConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/config/CorsConfig.java) | File nằm trong common/config; origin FE đúng, POST/OPTIONS và Content-Type được phép |
| [CognitoConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/config/CognitoConfig.java) | Tái sử dụng SDK client, không tạo bean Cognito trùng |

## 8. Logic Backend cần viết

### 8.1. Controller → AuthServiceImpl

1. Validate request: field bắt buộc, độ dài hợp lý, verifier hợp lệ.
2. So sánh redirectUri với danh sách URL cho phép. Dùng so khớp chính xác; không chấp nhận startsWith domain hoặc URL tùy ý.
3. Gọi CognitoOAuthService đổi code, không đưa request body chứa code/verifier vào log.
4. Xác minh accessToken bằng JwtDecoder đang có.
5. Kiểm tra sub không rỗng và scope hỗ trợ các API hồ sơ của V1.
6. Tra user local bằng sub đã xác minh.
7. User tồn tại: BLOCKED trả AccountBlockedException; trạng thái khác ACTIVE trả 403.
8. User chưa tồn tại: đọc hồ sơ qua CognitoUserAttributeService, kiểm tra email hợp lệ và kiểm tra email đã thuộc sub khác chưa. Nếu có, trả 409; nếu không, cho phép tiếp tục tới Sync.
9. Trả LoginResponse; chưa tự tạo user trong API Google.

Chỉ đọc profile từ token Cognito đã xác minh và dịch vụ tin cậy. Không đọc email do FE tự gửi để gán danh tính.

Kiểm tra trạng thái là tại thời điểm xử lý request. Admin có thể khóa tài khoản sau khi đăng nhập; ActiveUserFilter và service authorization vẫn phải kiểm tra mọi API cần bảo vệ.

### 8.2. Gọi token endpoint

Backend gửi HTTPS POST tới domain cố định trong cấu hình:

~~~text
<AWS_COGNITO_DOMAIN>/oauth2/token
~~~

Content-Type: application/x-www-form-urlencoded, với các field:

~~~text
grant_type=authorization_code
client_id=<client-id-cua-server>
code=<request.code>
redirect_uri=<request.redirectUri>
code_verifier=<request.codeVerifier>
~~~

Các giá trị phải được form-encode đúng. Không tự nối query string không encode. Không gửi JSON thay form.

Yêu cầu kỹ thuật:

- Inject HTTP client có cấu hình; ưu tiên tái sử dụng RestClient.Builder đang có, tạo client có qualifier riêng cho OAuth nếu cần.
- Timeout đề xuất cho lab: connect 3 giây, giới hạn mỗi request HTTP khoảng 8–10 giây; kiểm tra loại request factory khi hiện thực.
- Không bật tự động retry POST đổi code: code chỉ dùng một lần, response thất lạc không chứng minh code chưa được dùng.
- Xử lý status trước khi kỳ vọng JSON thành công. Body HTML/rỗng hoặc response sai định dạng phải thành lỗi upstream có kiểm soát.
- Không hardcode expiresIn, không trả 200 nếu thiếu token cần thiết.
- Không thêm new ObjectMapper rải rác; dùng converter/mapping phù hợp Spring Boot 4/Jackson đang có.
- Nếu bị InterruptedException, khôi phục interrupt flag trước khi chuyển lỗi; không gom toàn bộ exception thành lỗi mạng.
- Database exception phải đi theo handler DB; không catch Exception quanh cả method rồi biến lỗi DB thành 503.

Không bọc request HTTP đổi code trong transaction giữ khóa user. API Google chỉ đọc local; nếu cần SELECT FOR UPDATE cho phần kiểm tra riêng, phải đặt trong một transaction ngắn qua Spring proxy. Không gọi method ForUpdate khi không có transaction, và không giả định self-invocation tạo transaction mới.

### 8.3. Sync và tên hiển thị

Giữ các nguyên tắc hiện tại:

- User mới: insert có bảo vệ race bằng UNIQUE cognito_sub; mặc định learner, ACTIVE.
- User cũ: không đổi role/status; tài khoản BLOCKED trả 403.
- UNIQUE email vẫn được giữ; không sửa cognito_sub để gộp tài khoản.
- Tên mới không có từ provider: khi tạo user mới, dùng giá trị mặc định đã thống nhất, chẳng hạn phần trước @ của email, sau chuẩn hóa/giới hạn chiều dài.
- User cũ có tên hợp lệ mà provider không trả name: giữ tên cũ, không thay bằng tên fallback từ email.
- Giữ logic lấy profile sau khi đã lấy khóa để tránh ghi đè dữ liệu cũ.
- Profile/mapping thiếu email phải báo lỗi có kiểm soát, không INSERT NULL.

Google name được mapping vào Cognito có thể được cập nhật lại khi đăng nhập Google lần sau. V1 giữ Cognito/provider làm nguồn dữ liệu khi Sync theo logic hiện tại. Nếu muốn tên người dùng tự sửa luôn được ưu tiên hơn tên Google, đó là thay đổi nghiệp vụ riêng cần thiết kế trước, không tự sửa trong API Google.

### 8.4. Không thêm fallback sai nguyên nhân

Với scope đã chốt, GetUser là luồng đọc hồ sơ chính. Không bắt mọi NotAuthorizedException hoặc ForbiddenException rồi gọi userInfo.

ForbiddenException của GetUser có thể là AWS WAF chặn. Nếu sau này hỗ trợ token chỉ có scope OIDC, chọn cách đọc hồ sơ dựa trên token đã xác minh, giữ nguyên 401/403/429/503 và kiểm tra sub của userInfo phù hợp danh tính đang xử lý.

## 9. Frontend cần thêm/sửa những file nào?

### 9.1. File mới

| File đề xuất | Nhiệm vụ |
|---|---|
| D:/AWS/english-learn-game-FE-main/src/features/auth/utils/googleOAuth.ts | Tạo state/verifier/challenge, authorize URL, lưu/đọc/xóa giao dịch OAuth |
| D:/AWS/english-learn-game-FE-main/src/features/auth/hooks/useGoogleLogin.ts | Điều phối bắt đầu đăng nhập và trạng thái nút |
| D:/AWS/english-learn-game-FE-main/src/features/auth/pages/GoogleCallbackPage.tsx | Nhận callback, kiểm tra state, hiển thị loading/lỗi và hoàn tất đăng nhập |
| D:/AWS/english-learn-game-FE-main/src/features/auth/services/googleLoginFlow.ts | Điều phối exchange + Sync một lần cho mỗi giao dịch; tách khỏi vòng đời component |

Các đường dẫn trên là đề xuất cho source FE đã đọc. Nếu member đã có class/hook tương đương, sửa file đó, không tạo thêm luồng Google song song.

### 9.2. File hiện có cần sửa/kiểm tra

| File | Nội dung |
|---|---|
| [auth.types.ts](D:/AWS/english-learn-game-FE-main/src/features/auth/types/auth.types.ts) | Thêm GoogleAuthRequest; sửa giả định tokenType bắt buộc để khớp wire response |
| [authApi.ts](D:/AWS/english-learn-game-FE-main/src/features/auth/api/authApi.ts) | Thêm loginWithGoogle; thêm cách Sync bằng access token tạm mà chưa thiết lập phiên |
| [LoginPage.tsx](D:/AWS/english-learn-game-FE-main/src/features/auth/pages/LoginPage.tsx) | Thêm nút Google, loading/lỗi phù hợp, giữ form email/mật khẩu |
| [LoginForm.tsx](D:/AWS/english-learn-game-FE-main/src/features/auth/components/LoginForm.tsx) | Đối chiếu helper hoàn tất login dùng chung nếu refactor; không làm đổi luồng email hiện tại |
| [router.tsx](D:/AWS/english-learn-game-FE-main/src/app/router.tsx) | Thêm public route /callback, đặt ngoài ProtectedRoute và AdminGuard |
| [apiClient.ts](D:/AWS/english-learn-game-FE-main/src/lib/apiClient.ts) | Thêm /auth/google vào publicAuthPaths; hỗ trợ request Sync explicit-token không bị gắn token cũ/auto-refresh |
| [AuthProvider.tsx](D:/AWS/english-learn-game-FE-main/src/features/auth/context/AuthProvider.tsx) | Callback phải render được; không boot GetMe bằng phiên cũ rồi chặn callback; chỉ ACTIVE sau Sync |
| [tokenStorage.ts](D:/AWS/english-learn-game-FE-main/src/lib/tokenStorage.ts) | Tái sử dụng storage và sessionRevision; hỗ trợ luồng token tạm không ghi vào storage trước Sync |
| [normalizeApiError.ts](D:/AWS/english-learn-game-FE-main/src/lib/normalizeApiError.ts) | Đã đọc được code và errorCode; bổ sung test cho mã OAuth, không viết lại parser không cần thiết |
| [ProtectedRoute.tsx](D:/AWS/english-learn-game-FE-main/src/features/auth/components/ProtectedRoute.tsx) | Kiểm tra chỉ mở khi profile ACTIVE; không bảo vệ /callback |
| [types/auth.types.ts](D:/AWS/english-learn-game-FE-main/src/types/auth.types.ts) | Hiện re-export kiểu từ feature; giữ một nguồn kiểu, không khai báo trùng |
| [types/api.types.ts](D:/AWS/english-learn-game-FE-main/src/types/api.types.ts) | Bổ sung mã lỗi/LogoutResponse nếu cần type rõ; giữ phân biệt wire DTO và wrapper |
| [App.tsx](D:/AWS/english-learn-game-FE-main/src/app/App.tsx) | Chỉ sửa nếu cần điều chỉnh bootstrap AuthProvider vì Provider hiện bao RouterProvider |
| [env example](D:/AWS/english-learn-game-FE-main/.env.example) | Thêm biến public cho domain/client ID/callback/scope |

## 10. Logic FE cần viết

### 10.1. Khi nhấn nút Google

1. Khóa nút trong lúc bắt đầu; không tạo nhiều giao dịch từ double click.
2. Kết thúc phiên local cũ theo helper hiện có; hủy request/query có thể cập nhật lại phiên cũ.
3. Tạo verifier bằng crypto.getRandomValues; ví dụ 32 byte rồi base64url tạo chuỗi 43 ký tự.
4. Tính challenge = BASE64URL(SHA256(verifier)), bỏ padding dấu =.
5. Tạo state ngẫu nhiên độc lập.
6. Lưu state, verifier, redirectUri, thời điểm tạo và returnTo hợp lệ vào sessionStorage theo giao dịch.
7. Tạo URL bằng URL/URLSearchParams rồi điều hướng bằng window.location.assign.

Tham số authorize:

~~~text
response_type=code
identity_provider=Google
client_id=<cognito-app-client-id>
redirect_uri=http://localhost:5173/callback
scope=openid email profile aws.cognito.signin.user.admin
state=<random-state>
code_challenge_method=S256
code_challenge=<challenge-tinh-tu-verifier>
~~~

Không lưu verifier trong URL, localStorage dùng chung nhiều tab hoặc log. state và verifier là hai giá trị khác nhau. Nếu sessionStorage/Web Crypto không dùng được, báo lỗi và dừng, không fallback sang flow thiếu PKCE.

Tài liệu PKCE: [RFC 7636](https://datatracker.ietf.org/doc/html/rfc7636), [OAuth Security BCP](https://datatracker.ietf.org/doc/html/rfc9700).

### 10.2. Khi vào /callback

1. Đọc code/state hoặc error vào biến cục bộ.
2. Tìm giao dịch trong sessionStorage, kiểm tra state khớp chính xác.
3. Kiểm tra redirectUri là giá trị đã lưu lúc bắt đầu, không lấy tùy ý từ query callback.
4. Giới hạn tuổi giao dịch theo chính sách client; ví dụ 15 phút từ lúc bắt đầu. Đây là chính sách FE, khác thời hạn code do Cognito quyết định.
5. Nếu thiếu/sai state, thiếu verifier hoặc giao dịch đã hết hạn: không gọi BE; xóa giao dịch và yêu cầu đăng nhập lại.
6. Nếu Cognito/Google trả error: kết thúc giao dịch, hiển thị thông báo an toàn. Nếu có state thì vẫn phải đối chiếu; thiếu state không tiếp tục đổi code.
7. Lưu dữ liệu cần dùng rồi xóa code/state khỏi thanh địa chỉ bằng replace; không gửi nguyên URL callback vào analytics/log.
8. Chạy googleLoginFlow để đổi code đúng một lần.

### 10.3. Chống đổi code hai lần

Authorization code chỉ dùng một lần. React StrictMode hoặc remount có thể chạy effect lại.

- Dùng coordinator nằm ngoài component, lưu Promise đang chạy theo giao dịch/state.
- Hai effect cùng giao dịch phải dùng chung kết quả, không tạo hai POST đổi code.
- Một useRef trong component không đủ cho mọi trường hợp remount.
- Cleanup component không được tự abort/retry exchange rồi dùng lại code mà server có thể đã đổi thành công.
- Sau thành công/thất bại kết thúc, xóa giao dịch và dữ liệu nhạy cảm; giữ dấu hoàn tất không chứa token nếu cần chống xử lý lại.
- Sau reload làm mất kết quả exchange trong bộ nhớ, V1 có thể yêu cầu bắt đầu Google login mới. Không cố đổi lại code cũ.
- Nếu timeout lúc exchange, UI cho bắt đầu giao dịch mới; không tự replay POST dù chưa biết server đã đổi code hay chưa.

### 10.4. Token tạm → Sync → commit phiên

Đề xuất dùng token tạm trong bộ nhớ:

1. loginWithGoogle trả token; chưa gọi AuthContext.login, chưa ghi tokenStorage.
2. Gọi Sync với Authorization chứa đúng accessToken tạm.
3. Request Sync này không được interceptor thay bằng token cũ, tự refresh hay tự replay.
4. Sync trả profile ACTIVE: mới gọi AuthContext.login(tokens, profile) và lưu token qua helper hiện có.
5. Sync trả 403/409/401: bỏ token tạm, xóa giao dịch và hiển thị lỗi; không vào trang protected.
6. Sync trả 429/503: có thể cho thử lại riêng Sync có giới hạn bằng token tạm còn hạn; tuyệt đối không đổi code lại.

Để giữ API hiện có, có thể thêm authApi.syncUserWithToken(accessToken). Method này cần cơ chế request explicit-token và tắt auto-refresh. Nếu thêm option nội bộ vào Axios config, phải khai báo kiểu và truyền qua http wrapper để TypeScript compile; không chỉ gắn field lạ vào object.

FE apiClient hiện gắn tokenStorage vào mọi protected request và xử lý 401 bằng refresh. Vì vậy, chỉ truyền Authorization trong options nhưng giữ interceptor cũ chưa đủ nếu còn token local. Phải kiểm tra và sửa đúng nhánh gắn header/refresh.

Đổi token qua BE rồi trả JSON vẫn để token tiếp cận JavaScript. Kế hoạch này giữ mô hình Bearer hiện tại; không tự bật cookie, withCredentials hoặc thay đổi CSRF thành thiết kế BFF trong cùng công việc.

### 10.5. AuthProvider và điều hướng

- Callback phải được render kể cả khi chưa có user local.
- AuthProvider hiện bao RouterProvider; kiểm tra việc loading/sessionError của Provider có che callback hay không.
- Khi nhận diện đang có giao dịch callback, không boot GetMe bằng phiên cũ hoặc tạm thiết lập ACTIVE trước Sync.
- Sau Sync: admin đi /admin; learner đi /topics, hoặc returnTo nội bộ hợp lệ với quyền tương ứng.
- returnTo phải là đường dẫn nội bộ; từ chối URL tuyệt đối, //host, đường dẫn auth tạo vòng lặp và các dạng escape không hợp lệ.
- User mới mặc định learner; không lấy role từ Google hoặc query string.
- Request của phiên trước hoàn tất sau logout/login khác phải bị sessionRevision chặn như cơ chế hiện có.

### 10.6. Hiển thị lỗi và interceptor

| Tình huống | Hành vi FE |
|---|---|
| Người dùng hủy Google | Quay lại login, thông báo đã hủy, cho thử lại |
| state/PKCE local lỗi | Dừng, xóa giao dịch, tạo login mới |
| OAUTH_CODE_INVALID | Thông báo phiên Google hết hạn/không hợp lệ, không tự đổi code lại |
| ACCOUNT_BLOCKED | Xóa phiên, hiện thông báo tài khoản bị khóa |
| GOOGLE_ACCOUNT_EMAIL_CONFLICT hoặc Sync 409 | Gợi ý đăng nhập bằng phương thức đã đăng ký; không tạo tài khoản thay thế |
| 429 | Báo chờ, không lặp request liên tục |
| 502/503 khi exchange | Báo lỗi dịch vụ; nút bắt đầu Google login mới |
| 429/503 khi Sync | Chưa cho vào ứng dụng; thử lại Sync có giới hạn nếu còn token tạm |

/auth/google thuộc publicAuthPaths: không gửi access token cũ, không tự refresh khi exchange lỗi. ACCOUNT_BLOCKED có thể được xử lý tập trung như hiện tại, nhưng vẫn phải dọn giao dịch OAuth.

## 11. Refresh token phải đồng bộ với Google login

### 11.1. Hiện trạng và phương án

BE hiện dùng REFRESH_TOKEN_AUTH. Flow này không tương thích khi Cognito App Client bật refresh token rotation.

**Cập nhật BE:** đã có cả hai nhánh SDK và OAuth, chọn bằng AWS_COGNITO_REFRESH_MODE. Mặc định sdk để giữ hành vi hiện tại khi chưa xác nhận cấu hình User Pool. Chuyển oauth sau khi xác nhận điều kiện về device remembering dưới đây, và trước khi bật rotation. Không có cơ chế tự thử lại bằng nhánh còn lại khi refresh thất bại.

Đề xuất cho phần triển khai này:

1. Giữ POST /api/auth/refresh và DTO public hiện tại.
2. Dùng CognitoOAuthService.refreshTokens gọi /oauth2/token với grant_type=refresh_token.
3. Áp dụng cho cùng App Client của luồng Google và email/mật khẩu, sau khi xác nhận **device remembering không bật**. Nếu đang bật, cần nhánh SDK phù hợp cho các phiên đăng nhập API; không đổi chung một cách mù quáng.
4. Xác minh access token mới, kiểm tra user local ACTIVE rồi mới trả về.
5. Nếu Cognito cấp refresh_token mới, trả token mới; nếu không cấp vì rotation tắt, giữ token cũ.
6. FE lưu token mới trước khi tiếp tục request đang chờ.

Nguồn: [Refresh tokens](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-the-refresh-token.html).

### 11.2. Thứ tự bật rotation

- Rotation là mục tiêu cấu hình cho public client theo hướng dẫn OAuth hiện hành; triển khai tương thích BE/FE trước khi bật.
- Không bật rotation trên production rồi để BE tiếp tục gọi REFRESH_TOKEN_AUTH.
- Kiểm tra đồng thời refresh trong một tab: tái sử dụng refreshInFlight hiện có.
- FE hiện dùng localStorage chung nhiều tab; refreshInFlight chỉ trong một JavaScript context. Nếu cho phép nhiều tab, cần điều phối refresh giữa các tab, cập nhật token mới và logout qua cơ chế browser phù hợp, không ghi token vào log.
- Grace period có thể hỗ trợ request đồng thời nhưng không thay thế kiểm thử race; giá trị cần chọn theo cấu hình thực tế.
- Không dựa vào remembered devices/rotation/client secret mặc định; ghi lại cấu hình thực tế trước khi release.

Không cần thêm endpoint refresh riêng cho Google. Các phiên cũ và mới đều phải được kiểm thử sau thay đổi.

## 12. Logout cần sửa gì để Google không bị đăng nhập lại ngoài ý muốn?

### 12.1. Hiện trạng đã xác nhận

AuthServiceImpl.logout hiện chỉ trả logoutUrl, còn authApi.logout FE đang khai báo response null và AuthProvider chưa dùng URL này.

Không được dựa vào comment FE để kết luận Backend đã gọi GlobalSignOut hoặc thu hồi refresh token.

### 12.2. Phạm vi V1

1. Khai báo đúng LogoutResponse FE có logoutUrl.
2. Gọi POST /api/auth/logout để lấy URL như BE đang hỗ trợ.
3. Dọn token local, AuthContext, query cache và giao dịch Google trước khi rời ứng dụng.
4. Điều hướng trình duyệt tới logoutUrl do BE dựng từ domain/client ID/callback tin cậy.
5. Cognito xóa managed-login session của nó rồi chuyển về Allowed sign-out URL.
6. Nếu API logout lỗi, local logout vẫn phải hoàn tất; có thể dùng URL Cognito đã dựng từ cấu hình tin cậy hoặc hiển thị trạng thái chưa hoàn tất sign-out từ xa theo UX đã thống nhất.

Đăng xuất Cognito không cam kết đăng xuất phiên Google trong trình duyệt. Khi đăng nhập lại, có thể dùng prompt=select_account cho UX chọn tài khoản.

Luồng trên chưa bổ sung thu hồi refresh token ở server. Nếu yêu cầu là token đã phát phải bị thu hồi, cần bổ sung RevokeToken/GlobalSignOut phù hợp và kiểm thử riêng; không coi redirect logout là đã thu hồi mọi JWT.

Nguồn: [Cognito logout endpoint](https://docs.aws.amazon.com/cognito/latest/developerguide/logout-endpoint.html).

## 13. Biến môi trường và properties đề xuất

### 13.1. Backend

Không lưu bí mật thật trong tài liệu. Các property sau là mapping cần viết, không tự có hiệu lực chỉ vì thêm vào .env.

~~~properties
aws.cognito.client-id=${AWS_COGNITO_CLIENT_ID}
aws.cognito.domain=${AWS_COGNITO_DOMAIN}
aws.cognito.oauth.allowed-redirect-uris=${AWS_COGNITO_ALLOWED_REDIRECT_URIS}
aws.cognito.oauth.refresh-mode=${AWS_COGNITO_REFRESH_MODE:sdk}
app.frontend-url=${FRONTEND_URL}
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS}
~~~

~~~dotenv
AWS_COGNITO_CLIENT_ID=<cognito-app-client-id-dang-dung>
AWS_COGNITO_DOMAIN=https://<prefix>.auth.us-east-1.amazoncognito.com
AWS_COGNITO_ALLOWED_REDIRECT_URIS=http://localhost:5173/callback
FRONTEND_URL=http://localhost:5173
CORS_ALLOWED_ORIGINS=http://localhost:5173
~~~

Issuer URI vẫn là issuer User Pool, không đổi thành Hosted UI domain. Domain OAuth cần HTTPS, không lẫn path /oauth2/authorize hoặc /oauth2/token.

Spring Boot không mặc định tự nạp file .env theo cách Vite làm. Cần đưa biến vào đúng môi trường process/IDE/systemd hoặc cấu hình loader đã có.

### 13.2. Frontend

~~~dotenv
VITE_API_BASE_URL=http://localhost:8080/api
VITE_COGNITO_DOMAIN=https://<prefix>.auth.us-east-1.amazoncognito.com
VITE_COGNITO_CLIENT_ID=<cung-client-id-voi-BE>
VITE_COGNITO_REDIRECT_URI=http://localhost:5173/callback
VITE_COGNITO_SCOPES="openid email profile aws.cognito.signin.user.admin"
~~~

Mọi biến VITE_* được đưa vào bundle và có thể đọc từ trình duyệt. Chỉ đặt thông tin public; không đặt Google Client Secret, Cognito Client Secret hoặc AWS access key ở đây.

Allowlist callback và CORS là hai việc khác nhau:

- Callback là URL đầy đủ, ví dụ http://localhost:5173/callback.
- CORS origin là scheme + host + port, ví dụ http://localhost:5173.
- Cấu hình callback trên Cognito không tự cho phép CORS trên Backend.
- Deployment FE phải phục vụ /callback bằng SPA fallback. Refresh trực tiếp callback không được rơi vào 404 của hosting.

## 14. Dữ liệu được lưu ở đâu?

| Dữ liệu | Nơi lưu |
|---|---|
| Danh tính Google đã liên kết với User Pool | Cognito theo cấu hình provider |
| cognito_sub, email, displayName, role, status | public.users trong PostgreSQL |
| state/verifier/redirectUri/createdAt/returnTo của giao dịch | sessionStorage theo tab, xóa sau khi kết thúc |
| Token vừa đổi nhưng chưa Sync | Bộ nhớ của luồng callback |
| Token phiên sau Sync thành công | Helper tokenStorage hiện tại; việc chuyển sang mô hình lưu token khác là công việc riêng |
| Google Client Secret | Cấu hình Google IdP trong Cognito, không ở FE/DB ứng dụng |

Không lưu authorization code/verifier, password Google hoặc refresh token vào bảng users. Luồng này không yêu cầu tạo bảng google_users.

V1 không tự account-link. Nếu sau này liên kết tài khoản, phải xác minh phương thức tin cậy và giữ dữ liệu user hiện tại, không tự gộp chỉ dựa trên email giống nhau. [Cognito account linking](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-identity-federation-consolidate-users.html).

## 15. Checklist kiểm thử bắt buộc

Các dòng dưới đây là trường hợp cần kiểm thử sau triển khai, chưa phải kết quả đã chạy.

### 15.1. Backend

| ID | Trường hợp | Kết quả cần đạt |
|---|---|---|
| BE01 | Google code hợp lệ, user mới chưa có local | API Google 200; Sync tạo một learner ACTIVE |
| BE02 | User cũ ACTIVE | 200; giữ id, role, status, createdAt |
| BE03 | User local BLOCKED, Cognito vẫn phát được code | 403 ACCOUNT_BLOCKED, không trả token trong response |
| BE04 | Cognito đã disable user | Hosted UI/callback bị chặn; FE xử lý được dù BE chưa được gọi |
| BE05 | Thiếu code/verifier/redirectUri | 400 trước khi gọi AWS |
| BE06 | Verifier sai định dạng | 400; không gọi AWS |
| BE07 | Verifier khác challenge hoặc code hết hạn/đã dùng | 400 OAUTH_CODE_INVALID |
| BE08 | redirectUri không trong allowlist | 400; không đổi token |
| BE09 | JWT issuer/client/token_use/signature/expiry sai | Không trả token thành công |
| BE10 | Cognito 429, 5xx, timeout, HTML/rỗng | Đúng phân loại 429/502/503, không thành 200 hoặc 500 do parse |
| BE11 | Email đã thuộc tài khoản mật khẩu với sub khác | 409; không thay sub, role hoặc status |
| BE12 | Hai Sync đầu tiên đồng thời | Không tạo user trùng; DB giữ ràng buộc |
| BE13 | Google token gọi GetUser, Sync và PATCH name | Đủ scope và hoạt động đúng; name thiếu không gây NULL lỗi |
| BE14 | User bị khóa sau login | API nghiệp vụ và refresh tiếp theo bị chặn |
| BE15 | Refresh với rotation bật/tắt | Trả token mới/cũ đúng; không còn gọi flow không tương thích |
| BE16 | Đăng nhập email/mật khẩu, register/reset cũ | Không bị regression sau thay đổi App Client/refresh |
| BE17 | API public với request không có Bearer | Đổi code được; API protected vẫn yêu cầu JWT |
| BE18 | Preflight origin được phép / origin lạ | Origin hợp lệ qua; origin lạ bị chặn theo CORS |
| BE19 | Database lỗi trong kiểm tra/Sync | Không bị catch-all đổi sai thành lỗi Cognito; transaction rollback |
| BE20 | Response/token logging | Token response no-store; log không chứa code, verifier, token, secret |

### 15.2. Frontend

| ID | Trường hợp | Kết quả cần đạt |
|---|---|---|
| FE01 | Nhấn Google | Authorize URL đúng; mỗi lần có state/verifier mới |
| FE02 | Thiếu/sai state, verifier mất, giao dịch quá hạn | Không gọi BE; yêu cầu bắt đầu lại |
| FE03 | Hủy chọn Google, provider trả error | Không treo loading hoặc báo đăng nhập thành công |
| FE04 | StrictMode/double mount/double click | Chỉ một lần POST đổi code cho một giao dịch |
| FE05 | Exchange 400/401 | Không gọi auto-refresh và replay code |
| FE06 | Có token cũ trước khi login Google | Không gửi Bearer cũ vào /auth/google; không gán hồ sơ cũ cho token mới |
| FE07 | Token đã nhận nhưng Sync chưa xong | Chưa ACTIVE, chưa ghi token phiên thành công, không vào protected route |
| FE08 | Sync 403/409 | Xóa dữ liệu tạm; hiện đúng thông báo |
| FE09 | Exchange timeout | Không đổi lại code cũ; có nút bắt đầu đăng nhập mới |
| FE10 | Sync 503 | Chỉ retry Sync có giới hạn; không retry exchange |
| FE11 | Reload callback hoặc mở callback ở tab khác | Không bỏ qua kiểm tra state; không xử lý code cũ nhiều lần |
| FE12 | AuthProvider boot lỗi/phiên cũ | Callback vẫn có thể xử lý hoặc thoát có thông báo, không bị che vô hạn |
| FE13 | returnTo là URL ngoài hoặc không đủ quyền | Không điều hướng ra ngoài/không vào trang sai quyền |
| FE14 | Admin hợp lệ / learner mới | Điều hướng /admin hoặc /topics đúng profile từ BE |
| FE15 | Logout rồi Google login lại | Dọn local và đi Cognito logout; không tự coi phiên Google đã logout |
| FE16 | Hai tab cùng refresh khi rotation bật | Không ghi đè token mới bằng token cũ; phiên không bị lỗi lặp |
| FE17 | Raw LoginResponse không có tokenType | TypeScript và parser vẫn đúng hợp đồng |
| FE18 | Gọi API bằng idToken | Không sử dụng idToken; BE từ chối trường hợp test cố ý |

### 15.3. Cách chạy kiểm thử

- BE: unit/controller/security tests bằng mock HTTP/Cognito và JWT kiểm soát được; test race/constraint ở database test riêng.
- Không chạy toàn bộ mvn test khi context test đang có khả năng kết nối database dùng thật.
- FE: bổ sung Vitest cho PKCE, callback coordinator, apiClient public path/explicit-token, AuthProvider và refresh/logout.
- Sau đó chạy npm test theo phạm vi liên quan, npm run build và npm run lint trong thư mục FE.
- Kiểm thử Google/Cognito thực tế bằng tài khoản test sau khi người phụ trách đã cấu hình đúng; tránh tạo/xóa/khóa tài khoản đang dùng chỉ để thử.
- Authorization code lấy từ callback thực tế và chỉ dùng một lần; không thể lấy một code cũ để test Swagger lặp lại như JSON CRUD.

## 16. Thứ tự thực hiện cho hai member

1. Cùng xác nhận contract mục 6 và App Client/scopes/rotation/device remembering.
2. Người phụ trách AWS/Google cấu hình IdP, callback và mapping ở môi trường test.
3. BE viết DTO, CognitoOAuthService/interface/impl, error mapping và unit test.
4. BE thêm endpoint Google, validation local, public matcher và bộ test âm.
5. BE điều chỉnh refresh tương thích; giữ cơ chế chặn BLOCKED và Sync.
6. FE viết utility PKCE/state, nút Google, callback và coordinator một giao dịch.
7. FE bổ sung explicit-token Sync, public path, provider gating và commit phiên sau Sync.
8. FE sửa type LogoutResponse, điều hướng Cognito logout và kiểm thử phiên.
9. Hai bên chạy kiểm thử liên thông với Google user mới/cũ, trùng email, BLOCKED và token hết hạn.
10. Chỉ bật rotation sau khi cả BE/FE đã tương thích, kiểm thử nhiều tab và phiên email cũ.
11. Cấu hình production callback/origin/HTTPS riêng; triển khai BE trước khi FE bật nút Google cho người dùng.

## 17. Điều kiện hoàn thành

- [ ] Một API Google duy nhất, request/response khớp contract và có PKCE.
- [ ] Không đưa secret vào FE và không ghi token/code/verifier vào log.
- [ ] User mới có bản ghi local sau Sync; user cũ giữ dữ liệu/quyền/trạng thái.
- [ ] BLOCKED không vào được API nghiệp vụ; lỗi provider/callback cũng có UX rõ.
- [ ] Trùng email trả lỗi rõ; không tự account-link.
- [ ] Callback đổi code một lần, không tự refresh/replay request exchange.
- [ ] Refresh chạy được cho cả email/password và Google theo cấu hình thực tế.
- [ ] Logout dọn local và managed-login session theo phạm vi đã mô tả.
- [ ] Login email, register, reset password và các API protected cũ vẫn hoạt động.
- [ ] FE build/lint/test và BE checks liên quan đạt; lưu kết quả thực tế riêng.

## 18. Các khác biệt cần sửa so với tài liệu FE ban đầu

| Tài liệu ban đầu | Kế hoạch này |
|---|---|
| Request chỉ có code và redirectUri | Thêm codeVerifier, PKCE S256 và state ở FE |
| Mặc định Google chỉ được có ba scope | Bật scope Cognito cần cho GetUser/UpdateUserAttributes |
| Fallback khi NotAuthorized/Forbidden | Giữ đúng lỗi, không bypass scope/WAF bằng catch-all |
| Mọi token endpoint lỗi thành 401 | Phân biệt lỗi request, cấu hình, giới hạn và dịch vụ |
| Chỉ thêm API đổi code | Hoàn thiện Sync, callback, interceptor, refresh và logout |
| Chưa nói email trùng giữa hai phương thức | V1 trả 409, không gộp tài khoản tự động |
| Chưa xử lý React chạy callback lại | Coordinator đảm bảo một exchange mỗi giao dịch |
| FE giả định tokenType/LogoutResponse khác BE | Sửa type để khớp response thực tế |

**Kết quả mong đợi:** người dùng đăng nhập Google qua Cognito, Backend kiểm tra danh tính/trạng thái, PostgreSQL được Sync đúng và FE chỉ mở phiên sau khi profile ACTIVE. Đây là mục tiêu nghiệm thu liên thông; kết quả kiểm thử mock BE được ghi riêng và chưa thay thế kiểm thử Google/Cognito thực tế.
