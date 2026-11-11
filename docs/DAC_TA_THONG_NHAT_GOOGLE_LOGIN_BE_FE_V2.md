# Đặc tả thống nhất Google Login — Backend và Frontend — V2

**Dự án:** Alphabet Universe  
**Ngày chốt tài liệu:** 10/11/2026  
**Đối tượng:** Member Backend, Frontend và người quản lý Google/Cognito.  
**Trạng thái:** Hợp đồng V2 và danh sách việc cần thực hiện, chưa phải báo cáo V2 đã triển khai thành công.

Hai bên dùng file này làm chuẩn cho các quyết định Google Login bên dưới. Các tài liệu Google Login trước đó phản ánh thiết kế/bản triển khai trước; những khác biệt được chốt lại tại mục 3.

Việc tạo file này không sửa Java/TypeScript, database, biến môi trường đang chạy hoặc cấu hình AWS. Mọi mục nghiệm thu cần có kết quả kiểm thử thực tế sau khi hai bên hoàn thành.

## 1. Phương án V1 của sản phẩm được chốt

| Hạng mục | Quyết định chung |
|---|---|
| Đăng nhập Google | Cognito làm trung gian giữa Google và ứng dụng |
| OAuth flow | Authorization Code + PKCE S256 |
| Bên khởi tạo state/verifier/challenge | FE |
| Bên kiểm tra state callback | FE, đối chiếu giao dịch đã lưu trong cùng tab |
| Bên đổi authorization code | BE qua POST /api/auth/google |
| Token gọi API | Cognito accessToken |
| Token/hồ sơ trước Sync | Chưa được coi là phiên đăng nhập ứng dụng thành công |
| Bên đọc hồ sơ và ghi user local | POST /api/auth/sync |
| Scope OAuth yêu cầu tới Cognito | openid email profile aws.cognito.signin.user.admin |
| API đọc hồ sơ | GetUser trong phạm vi đã chọn đủ scope |
| API cập nhật tên | PATCH /api/users/me hiện có, sử dụng UpdateUserAttributes |
| Refresh V1 | Giữ nhánh SDK REFRESH_TOKEN_AUTH, không bật rotation trong đợt tích hợp này |
| FE gọi BE khi dev | VITE_API_BASE_URL=/api qua Vite Proxy |
| Cổng local thống nhất | FE 3000, BE 8080 |
| Callback ứng dụng local | http://localhost:3000/callback |
| Trùng email khác Cognito sub | Sync trả 409; không tự liên kết tài khoản |
| Vai trò | User mới learner; user cũ giữ nguyên role/status |
| Cấu trúc file FE | Tái sử dụng file hiện có; không bắt buộc tạo bốn file riêng |
| Database | Không thêm bảng/cột cho chức năng cơ bản |

### Lưu ý về scope — điều kiện để áp dụng bản này

FE đã báo App Client hiện chỉ bật ba scope và gặp invalid_scope khi yêu cầu scope thứ tư. Đây là thông tin FE cung cấp; chưa được xác nhận trực tiếp từ AWS Console trong lần tạo tài liệu này.

**Người quản lý Cognito phải bật aws.cognito.signin.user.admin trên đúng App Client trước khi kiểm thử luồng bốn scope.** FE không tự bỏ scope; BE không tự bỏ kiểm tra để đưa người dùng vào một luồng hồ sơ chưa hỗ trợ.

Scope này phục vụ thao tác hồ sơ của chính user. Nó không cấp role admin của ứng dụng hoặc quyền IAM quản trị hệ thống. GetUser và UpdateUserAttributes đều yêu cầu scope tương ứng.

Nguồn: [GetUser](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_GetUser.html), [UpdateUserAttributes](https://docs.aws.amazon.com/cognito-user-identity-pools/latest/APIReference/API_UpdateUserAttributes.html).

Nếu nhóm không thể bật scope này, bản bốn scope chưa đủ điều kiện triển khai. Khi đó cần đặc tả khác cho userInfo và cách cập nhật hồ sơ, thay vì mỗi bên tự đổi một phần. Không thêm fallback chung khi token sai/hết hạn hoặc AWS WAF chặn.

## 2. Các giá trị phải giống nhau ở BE, FE và AWS

| Giá trị | Local đã chốt | Nơi sử dụng |
|---|---|---|
| FE origin | http://localhost:3000 | Vite, FRONTEND_URL, CORS |
| FE callback | http://localhost:3000/callback | FE authorize, BE allowlist, Cognito Allowed callback URLs |
| FE sign-out URL | http://localhost:3000/login | BE logout URL, Cognito Allowed sign-out URLs |
| BE target | http://localhost:8080 | Vite Proxy |
| FE API base URL | /api | Axios/apiClient |
| Cognito domain | https://<prefix>.auth.<region>.amazoncognito.com | Cùng domain ở BE và FE |
| Cognito App Client ID | <cognito-app-client-id> | Cùng giá trị ở BE, FE và App Client đang cấu hình |
| Google callback | https://<cognito-domain>/oauth2/idpresponse | Google OAuth Client Authorized redirect URIs |

Chọn cổng 3000 theo vite.config.ts của bản FE đã đọc, có strictPort=true. Nếu nhóm đổi cổng sau này, phải thay đồng bộ các URL liên quan; không dùng lẫn 3000 và 5173.

Callback Google → Cognito và callback Cognito → FE là hai URL khác nhau. Proxy /api chỉ áp dụng cho request API của FE, không thay thế callback OAuth.

## 3. Khác biệt giữa BE đang có và hợp đồng V2

| Nội dung | Code trước V2 | Mục tiêu V2 |
|---|---|---|
| API Google đọc profile user mới | Có gọi GetUser và kiểm tra email | Bỏ bước đọc profile/email tại API Google |
| Kiểm tra email trùng | Có ở API Google; Sync kiểm tra sau INSERT cho user mới | Chốt trách nhiệm tại Sync, kiểm tra sớm trước INSERT |
| Mã lỗi email | API Google có GOOGLE_ACCOUNT_EMAIL_CONFLICT; Sync có thể chỉ trả message | Sync thống nhất EMAIL_ALREADY_EXISTS với HTTP 409 |
| Hồ sơ user mới trong Sync | Có thể đọc hai lần | Một lần thông thường; đọc lại khi xử lý user do request khác tạo |
| Số lần đọc profile cho Google user mới | Có thể ba lần cho exchange + Sync | Một lần thông thường trong Sync |
| Callback trong ví dụ cũ | Thường là 5173 | Local chung dùng 3000 |
| FE base URL trong ví dụ cũ | Có ví dụ gọi thẳng BE | Giữ /api qua Vite Proxy |
| Refresh | BE đã có SDK mặc định và OAuth tùy cấu hình | Giữ sdk trong phạm vi tích hợp V1 này |
| Cấu trúc FE | Có đề xuất bốn file mới | Giữ callback/LoginForm hiện có nếu đáp ứng đúng hành vi |

**Mã EMAIL_ALREADY_EXISTS là quyết định contract V2**, chưa phải khẳng định BE đã sửa handler theo mã này. Cần sửa cả kiểm tra sớm lẫn handler UNIQUE email để FE nhận cùng cấu trúc khi có race.

Trong thời gian chuyển phiên bản, FE có thể nhận diện cả mã cũ và HTTP 409. Khi nghiệm thu V2, kiểm thử phải xác nhận code thống nhất tại Sync.

## 4. Flow liên thông

~~~mermaid
sequenceDiagram
    actor U as Người dùng
    participant FE as Frontend
    participant C as Cognito
    participant G as Google
    participant BE as Backend
    participant DB as PostgreSQL
    U->>FE: Tiếp tục với Google
    FE->>FE: Tạo state + verifier + challenge S256
    FE->>C: Điều hướng authorize với bốn scope
    C->>G: Xác thực Google
    G-->>C: Kết quả xác thực
    C-->>FE: /callback?code=...&state=...
    FE->>FE: Kiểm tra state và giao dịch
    FE->>BE: POST /api/auth/google
    BE->>C: Đổi code + verifier tại /oauth2/token
    C-->>BE: Bộ token Cognito
    BE->>BE: Verify access token và scope
    BE->>DB: Kiểm tra trạng thái user nếu đã tồn tại
    BE-->>FE: Token nếu hợp lệ
    FE->>FE: Giữ token tạm, chưa authenticated
    FE->>BE: POST /api/auth/sync bằng token tạm
    BE->>C: GetUser theo quy tắc transaction Sync
    BE->>DB: Kiểm tra email, tạo/cập nhật user
    BE-->>FE: UserResponse ACTIVE hoặc lỗi
    FE->>FE: Chỉ commit phiên nếu Sync thành công
~~~

HTTP 200 của /auth/google chỉ xác nhận đổi code và kiểm tra tương ứng đã thành công. Nó không có nghĩa PostgreSQL đã tạo user hoặc người dùng đã được vào ứng dụng.

## 5. Hợp đồng API

### 5.1. POST /api/auth/google

API công khai trước đăng nhập. Không gửi Authorization header.

~~~http
POST /api/auth/google
Content-Type: application/json
Accept: application/json
~~~

~~~json
{
  "code": "<authorization-code-do-Cognito-cap>",
  "redirectUri": "http://localhost:3000/callback",
  "codeVerifier": "abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG"
}
~~~

Verifier trong ví dụ chỉ minh họa định dạng, không được dùng làm hằng số khi chạy thật. Verifier thực phải thuộc chính authorize request đã tạo ra code.

| Field | Quy tắc |
|---|---|
| code | Bắt buộc, không rỗng; code Cognito vừa cấp, không phải Google ID token |
| redirectUri | Khớp chính xác authorize request, allowlist BE và callback đã đăng ký trên Cognito |
| codeVerifier | 43–128 ký tự A–Z, a–z, 0–9, -, ., _, ~; khớp challenge S256 |

state được FE kiểm tra với giao dịch đã lưu trước redirect; request BE chỉ cần ba field trên. FE không gửi email/userId/role/status để BE quyết định danh tính.

Response 200 trực tiếp trên đường truyền:

~~~json
{
  "accessToken": "<cognito-access-token>",
  "idToken": "<cognito-id-token>",
  "refreshToken": "<cognito-refresh-token>",
  "expiresIn": 3600
}
~~~

- expiresIn là số giây Cognito thực tế trả về, không hardcode khi response thiếu.
- Response chứa token phải có Cache-Control: no-store, Pragma: no-cache.
- BE không thêm vỏ success/data chỉ vì FE dùng ApiResponse; interceptor FE đang bọc DTO.
- FE không yêu cầu tokenType từ BE; dùng Bearer khi gọi protected API.
- idToken không dùng làm Bearer; V2 không cần thêm ID token decoder để kiểm tra email tại API Google.

### 5.2. POST /api/auth/sync

~~~http
POST /api/auth/sync
Authorization: Bearer <accessToken-vua-nhan>
~~~

Không cần body. Response 200:

~~~json
{
  "id": 123,
  "email": "learner@example.com",
  "displayName": "Người học",
  "role": "learner",
  "status": "ACTIVE"
}
~~~

FE chỉ thiết lập phiên từ response Sync ACTIVE. User mới có thể đã có trên Cognito nhưng chưa có local; /auth/google phải cho user đó tiếp tục tới Sync.

Nếu Google tạo sub mới nhưng email đã thuộc tài khoản local khác, Sync trả:

~~~json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "Email đã thuộc tài khoản khác. Vui lòng đăng nhập bằng phương thức đã đăng ký."
}
~~~

HTTP 409, không trả profile ACTIVE. FE bỏ token tạm, không vào ứng dụng. Không tự gán lại sub hoặc gộp dữ liệu chỉ vì email giống nhau.

### 5.3. POST /api/auth/refresh và POST /api/auth/logout

Giữ endpoint refresh, request có refreshToken và response hiện có. Chế độ dùng cho đợt tích hợp này:

~~~dotenv
AWS_COGNITO_REFRESH_MODE=sdk
~~~

Không đổi sang OAuth hoặc tự bật rotation trong đợt này. REFRESH_TOKEN_AUTH không tương thích rotation bật; một đợt triển khai rotation cần có kế hoạch BE/FE và test riêng.

Logout giữ response có logoutUrl:

~~~json
{
  "logoutUrl": "https://<cognito-domain>/logout?client_id=<client-id>&logout_uri=<encoded-sign-out-url>"
}
~~~

FE dọn phiên rồi điều hướng trình duyệt tới logoutUrl hợp lệ do BE trả về. Logout Cognito không cam kết đăng xuất Google; chức năng hiện tại cũng không đồng nghĩa đã thu hồi toàn bộ token.

### 5.4. Quy ước lỗi

| HTTP | code V2 | Bên phát hiện | FE cần làm |
|---|---|---|---|
| 400 | VALIDATION_ERROR | BE validation/JSON | Hiện lỗi, không tự đổi lại code |
| 400 | OAUTH_REDIRECT_URI_INVALID | BE allowlist | Báo sai cấu hình callback |
| 400 | OAUTH_CODE_INVALID | BE đổi code | Bắt đầu login mới |
| 401 | UNAUTHORIZED hoặc REFRESH_TOKEN_INVALID | Protected API/refresh | Xử lý phiên theo luồng hiện tại |
| 403 | ACCOUNT_BLOCKED | BE trạng thái local | Xóa phiên, hiện thông báo bị khóa |
| 403 | ACCOUNT_INACTIVE | BE trạng thái local | Không mở phiên |
| 403 | COGNITO_OAUTH_FORBIDDEN | Cognito/WAF | Báo yêu cầu bị chặn; không fallback bỏ qua |
| 409 | EMAIL_ALREADY_EXISTS | Sync/precheck/UNIQUE email | Bỏ token tạm, gợi ý phương thức đăng nhập cũ |
| 429 | TOO_MANY_REQUESTS | Provider/BE | Chờ, không lặp request liên tục |
| 502 | COGNITO_OAUTH_CONFIG_ERROR | Scope/client/grant không phù hợp | Hiện lỗi cấu hình, báo người phụ trách |
| 502 | COGNITO_OAUTH_INVALID_RESPONSE | Response/token không hợp lệ | Không mở phiên |
| 503 | COGNITO_UNAVAILABLE | Network/timeout/dịch vụ | Xử lý theo giai đoạn exchange hay Sync |

Các code này là contract ứng dụng. BE phải map nguyên nhân thực tế, không đổi mọi lỗi Cognito thành 401 hoặc trả nguyên error_description/stack trace.

## 6. Phần việc Backend

### 6.1. API Google sau khi điều chỉnh

1. Validate DTO, verifier và allowlist.
2. Đổi code bằng CognitoOAuthService; giữ form encoding, timeout và không tự retry.
3. Verify access token bằng decoder hiện tại, giữ issuer/signature/expiry/token_use/client_id/sub.
4. Kiểm tra đủ bốn scope cho luồng Google của bản này.
5. Nếu user local đã tồn tại theo sub: kiểm tra ACTIVE/BLOCKED.
6. Nếu chưa có local: cho qua tới Sync.
7. Trả token; không gọi GetUser, không đọc email từ FE, không tạo user ở method này.

Việc xung đột email chuyển về Sync. FE phải áp dụng điều kiện commit phiên ở mục 7.3, không điều hướng ngay khi exchange 200.

### 6.2. Sync — kiểm tra email và giảm gọi AWS

Giữ transaction READ_COMMITTED và các ràng buộc DB hiện tại.

**User đã tồn tại:**

1. Tìm theo sub bằng SELECT FOR UPDATE ngay lần đọc đầu.
2. Kiểm tra ACTIVE trước khi đồng bộ.
3. Đọc hồ sơ Cognito sau khi đã lấy khóa; không dùng snapshot đọc trước lúc chờ khóa để ghi đè.
4. Kiểm tra email thuộc user khác, cập nhật field hợp lệ, giữ role/status/createdAt.

**User chưa tồn tại:**

1. Đọc hồ sơ một lần, validate email, chuẩn hóa tên.
2. Kiểm tra chủ email trước INSERT. Chủ email có sub khác thì trả 409 EMAIL_ALREADY_EXISTS.
3. Nếu chủ email có cùng sub do request khác vừa Sync xong, xử lý như user hiện có: khóa bản ghi, kiểm tra trạng thái và đọc hồ sơ mới khi cần.
4. INSERT ON CONFLICT(cognito_sub) DO NOTHING; sử dụng số dòng insertIfAbsent trả về.
5. Nếu INSERT trả 1: transaction đang sở hữu bản ghi mới; lấy entity tương ứng và dùng hồ sơ vừa đọc, không gọi mạng thêm chỉ để lấy lại cùng hồ sơ.
6. Nếu INSERT trả 0: đã có request khác tạo cùng sub. Lấy khóa, kiểm tra trạng thái rồi đọc hồ sơ mới sau khi lấy khóa; không dùng dữ liệu cũ đã đọc trước lúc chờ.
7. Apply dữ liệu, commit và trả UserResponse.

UNIQUE email vẫn là lớp bảo vệ cuối cùng. Hai request có thể vượt qua precheck cùng lúc; handler UNIQUE email phải trả 409 EMAIL_ALREADY_EXISTS sau khi transaction rollback.

Không catch DataIntegrityViolationException rồi tiếp tục query trong transaction đã lỗi. Không loại bỏ UNIQUE để tránh exception.

Mục tiêu là một lần GetUser thông thường cho mỗi Sync; ngoại lệ race có thể cần đọc lại. Đây không phải lời hứa mọi request đều chỉ gọi mạng đúng một lần.

### 6.3. Hồ sơ, tên và quyền

- User mới mặc định learner, ACTIVE; tên thiếu dùng fallback đã thống nhất.
- User cũ giữ role/status; tên provider rỗng thì giữ tên local hiện có.
- Không tự reactivate BLOCKED trong Sync.
- Giữ PATCH /api/users/me cập nhật tên qua Cognito theo nghiệp vụ hiện tại.
- Mapping Google name có thể cập nhật lại thuộc tính Cognito khi login Google lần sau. V1 tiếp tục lấy Cognito/provider làm nguồn khi Sync; phải test và thống nhất UX, không hứa tên tự sửa sẽ luôn thắng mapping Google.
- Muốn ưu tiên tên riêng của ứng dụng vĩnh viễn là một thay đổi nguồn dữ liệu khác, không tự triển khai trong lần này.

### 6.4. File BE cần sửa/kiểm tra

| File | Việc cần làm |
|---|---|
| [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java) | Bỏ GetUser/email check tại API Google, giữ JWT/status và default refresh SDK |
| [UserSyncServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/service/impl/UserSyncServiceImpl.java) | Precheck email và nhánh INSERT 1/0, giữ đọc mới sau khóa cho trường hợp cần thiết |
| [UserRepository.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/repository/UserRepository.java) | Tái sử dụng findByEmailIgnoreCase, method khóa và số dòng insertIfAbsent; không khai báo trùng |
| [GlobalExceptionHandler.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/GlobalExceptionHandler.java) | Đồng nhất lỗi email sớm và UNIQUE thành 409 EMAIL_ALREADY_EXISTS |
| [CognitoOAuthSettings.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/config/CognitoOAuthSettings.java) | Cấu hình callback 3000, giữ sdk mặc định |
| [GoogleAuthRequest.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/dto/GoogleAuthRequest.java) | Giữ ba field và validation, cập nhật ví dụ Swagger nếu cần |
| [CognitoOAuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/CognitoOAuthServiceImpl.java) | Giữ đổi code PKCE, mapping lỗi, timeout và không replay |
| [CognitoUserAttributeServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/user/service/impl/CognitoUserAttributeServiceImpl.java) | GetUser/UpdateUserAttributes theo scope đã chốt; không catch-all fallback |
| [SecurityConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/config/SecurityConfig.java) | Giữ public riêng POST Google, protected Sync và nghiệp vụ |
| [ActiveUserFilter.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/filter/ActiveUserFilter.java) | Giữ bypass đúng endpoint public, vẫn kiểm tra local trên protected API |
| [CorsConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/config/CorsConfig.java) | Cho origin dev cần thiết, preflight đúng |
| [application.properties](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/resources/application.properties) | Mapping environment đúng callback/refresh; không commit bí mật local |

Không cần thêm bảng, endpoint profile thứ hai hoặc ID token decoder cho phương án này. Service HTTP hiện có tiếp tục giữ interface/impl đúng cấu trúc BE.

## 7. Phần việc Frontend

### 7.1. Khởi tạo Google login

1. Tạo state và verifier ngẫu nhiên bằng Web Crypto.
2. Tính challenge BASE64URL(SHA256(verifier)), bỏ padding, dùng S256.
3. Lưu giao dịch gồm state/verifier/redirectUri/createdAt/returnTo trong sessionStorage theo tab.
4. Điều hướng trình duyệt tới Cognito authorize với identity_provider=Google và đủ bốn scope.
5. Không đưa verifier vào URL, không lưu secret trong biến VITE_*.
6. Không dùng codeVerifier ngẫu nhiên do Swagger tự điền để đổi một code thuộc giao dịch khác.

### 7.2. Callback và chống xử lý trùng

- Có thể dùng /callback render LoginPage/LoginForm đang có; không bắt buộc tạo GoogleCallbackPage mới.
- Đối chiếu state, giao dịch, verifier, redirectUri và thời hạn local trước khi gọi BE.
- Chụp giá trị cần dùng rồi xóa code/state khỏi URL bằng replace; không log URL chứa code.
- Một giao dịch chỉ được POST đổi code một lần, kể cả StrictMode/remount/double click.
- processedOAuthRef có thể là một phần giải pháp; phải test remount và nhiều giao dịch, không mặc định rằng một ref xử lý mọi vòng đời.
- Không tự retry/replay exchange khi 400/401/timeout; code có thể đã được dùng. Bắt đầu giao dịch mới khi không còn xác định được kết quả exchange.
- Provider có thể trả error trước khi BE nhận request; callback phải hiển thị lỗi an toàn và kết thúc loading.

### 7.3. Token tạm, Sync và commit phiên

1. Exchange 200: chỉ giữ token trong bộ nhớ luồng login.
2. Gọi Sync với đúng accessToken tạm, không bị interceptor thay bằng token cũ.
3. Sync này không tự refresh/replay; timeout/retry có giới hạn chỉ áp dụng riêng Sync khi còn token tạm hợp lệ.
4. Chỉ khi Sync trả ACTIVE mới ghi token qua helper hiện có và thiết lập AuthContext.
5. Sync 409/403/401: xóa dữ liệu tạm, không vào protected route.
6. Sync 429/503: chưa authenticated; có thể cho thử lại Sync với giới hạn, không đổi code lại.
7. Callback phải render được, không bị AuthProvider boot GetMe bằng phiên cũ rồi che giao diện xử lý.
8. Logout/đổi user trong khi request cũ còn chạy phải có cơ chế revision để response cũ không khôi phục phiên.

### 7.4. Interceptor, proxy và cấu trúc file

- Thêm /auth/google vào publicAuthPaths; xóa Authorization khỏi request này.
- Không refresh khi /auth/google trả lỗi.
- Giữ VITE_API_BASE_URL=/api, VITE_BACKEND_URL=http://localhost:8080 và proxy hiện có.
- Type FE phải khớp DTO BE: tokenType không bắt buộc; logout response có logoutUrl.
- Giữ một nguồn auth types; không tạo interface trùng ở nhiều thư mục.

| File đang có | Việc cần làm |
|---|---|
| [authApi.ts](D:/AWS/english-learn-game-FE-main/src/features/auth/api/authApi.ts) | Google exchange ba field, explicit-token Sync, đúng kiểu response logout |
| [LoginForm.tsx](D:/AWS/english-learn-game-FE-main/src/features/auth/components/LoginForm.tsx) | PKCE/state/callback nếu đây là nơi nhóm đang xử lý; giữ login email ổn định |
| [LoginPage.tsx](D:/AWS/english-learn-game-FE-main/src/features/auth/pages/LoginPage.tsx) | Loading/error cho Google, không điều hướng sớm |
| [router.tsx](D:/AWS/english-learn-game-FE-main/src/app/router.tsx) | Public /callback, không bọc ProtectedRoute/AdminGuard |
| [apiClient.ts](D:/AWS/english-learn-game-FE-main/src/lib/apiClient.ts) | Public Google và tùy chọn Sync token tạm không auto-refresh |
| [AuthProvider.tsx](D:/AWS/english-learn-game-FE-main/src/features/auth/context/AuthProvider.tsx) | Callback boot/gating, commit phiên sau Sync ACTIVE |
| [tokenStorage.ts](D:/AWS/english-learn-game-FE-main/src/lib/tokenStorage.ts) | Giữ revision và dọn phiên, không lưu token tạm trước Sync |
| [auth.types.ts](D:/AWS/english-learn-game-FE-main/src/features/auth/types/auth.types.ts) | GoogleAuthRequest, AuthTokens và LogoutResponse khớp wire DTO |
| [normalizeApiError.ts](D:/AWS/english-learn-game-FE-main/src/lib/normalizeApiError.ts) | Đọc code/message, nhận EMAIL_ALREADY_EXISTS và tương thích HTTP 409 khi rollout |
| [vite.config.ts](D:/AWS/english-learn-game-FE-main/vite.config.ts) | Giữ proxy và port 3000 đã chốt |
| [env example](D:/AWS/english-learn-game-FE-main/.env.example) | Đồng nhất các biến public bên dưới |

Member FE có thể tách helper nếu giúp kiểm thử, nhưng số lượng/tên file không phải tiêu chí nghiệm thu. Hành vi và contract mới là tiêu chí.

## 8. Cấu hình mẫu dùng chung

### Backend environment

~~~dotenv
AWS_COGNITO_CLIENT_ID=<cognito-app-client-id>
AWS_COGNITO_DOMAIN=https://<prefix>.auth.us-east-1.amazoncognito.com
AWS_COGNITO_ALLOWED_REDIRECT_URIS=http://localhost:3000/callback
AWS_COGNITO_REFRESH_MODE=sdk
FRONTEND_URL=http://localhost:3000
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173
~~~

Đưa biến vào process/IntelliJ/systemd hoặc loader đang dùng. Spring Boot không mặc định tự đọc .env như Vite. Không đổi issuer URI User Pool thành Hosted UI domain.

### Frontend environment

~~~dotenv
VITE_API_BASE_URL=/api
VITE_BACKEND_URL=http://localhost:8080
VITE_COGNITO_CLIENT_ID=<cung-client-id-voi-BE>
VITE_COGNITO_DOMAIN=https://<prefix>.auth.us-east-1.amazoncognito.com
VITE_COGNITO_REDIRECT_URI=http://localhost:3000/callback
VITE_COGNITO_SCOPES="openid email profile aws.cognito.signin.user.admin"
~~~

Nếu FE đã dùng tên biến khác, mapping rõ về cùng giá trị; không tự tạo hai nguồn cấu hình cạnh tranh. VITE_* là public trong bundle, không chứa Google/Cognito Client Secret hoặc AWS credential.

### AWS/Google

- Cognito: đúng App Client không secret theo phạm vi V1; bật Google và Authorization code grant.
- Cognito: bật bốn scope trước khi FE yêu cầu chúng; kiểm tra thuộc tính email/name đọc/ghi và mapping phù hợp.
- Cognito callback: http://localhost:3000/callback.
- Cognito sign-out: http://localhost:3000/login.
- Google callback: https://<cognito-domain>/oauth2/idpresponse.
- Người phụ trách xác nhận trạng thái rotation/device remembering; không tự thay đổi các thiết lập này chỉ để thử.

Nguồn cấu hình: [Google IdP](https://docs.aws.amazon.com/cognito/latest/developerguide/cognito-user-pools-social-idp-google.html), [authorize](https://docs.aws.amazon.com/cognito/latest/developerguide/authorization-endpoint.html), [token endpoint](https://docs.aws.amazon.com/cognito/latest/developerguide/token-endpoint.html), [refresh](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-the-refresh-token.html).

## 9. Checklist nghiệm thu liên thông

Mỗi dòng cần ghi PASS/FAIL và bằng chứng sau khi chạy; không tick dựa trên đọc code hoặc test mock phiên bản trước.

| ID | Tình huống | Kết quả cần đạt | Phụ trách |
|---|---|---|---|
| G01 | Yêu cầu bốn scope trên App Client thật | Không invalid_scope; token có scope đúng | AWS + FE |
| G02 | Google user mới | Exchange 200, Sync tạo một learner ACTIVE rồi FE mới vào app | BE + FE |
| G03 | User cũ ACTIVE | Giữ id/role/status/createdAt; không nhân đôi dữ liệu | BE |
| G04 | User local BLOCKED nhưng provider vẫn cấp code | Không vào app, nhận lỗi trạng thái phù hợp | BE + FE |
| G05 | User đã disable trên Cognito | Callback xử lý được lỗi dù API Google chưa được gọi | AWS + FE |
| G06 | Email đã có tài khoản mật khẩu với sub khác | Exchange có thể 200; Sync trả 409 EMAIL_ALREADY_EXISTS, FE không commit phiên | BE + FE |
| G07 | Hai Sync cùng sub đồng thời | Một user, xử lý INSERT 1/0 đúng, không dùng hồ sơ cũ khi đã chờ khóa | BE |
| G08 | Hai user khác sub cùng email vượt qua precheck | UNIQUE bảo vệ, transaction lỗi rollback và handler trả 409 | BE |
| G09 | Code sai/hết hạn/đã dùng hoặc verifier không khớp | 400 OAUTH_CODE_INVALID, không auto-refresh/replay | BE + FE |
| G10 | state/verifier/giao dịch local thiếu hoặc sai | FE không gọi exchange và không vào app | FE |
| G11 | Callback 3000/5173 bị trộn hoặc URL dư path | Phát hiện rõ, không bỏ allowlist để cho qua | BE + FE |
| G12 | StrictMode/remount/double click/reload callback | Không đổi code cùng giao dịch hai lần | FE |
| G13 | Token cũ còn trong browser | Không mang vào exchange, không ghi đè Authorization token tạm của Sync | FE |
| G14 | Exchange timeout hoặc response thất lạc | Không tự dùng lại code, cho bắt đầu giao dịch mới | FE |
| G15 | Sync 503/429 | Chưa authenticated; chỉ retry Sync có giới hạn | BE + FE |
| G16 | Google token gọi PATCH tên và Sync tiếp theo | Đủ scope, quyền attribute đúng, hành vi nguồn tên được hiểu rõ | BE + AWS + FE |
| G17 | Đăng nhập Google lại sau PATCH tên | Kiểm tra tác động mapping Google, không hứa giữ tên trái nghiệp vụ | BE + FE |
| G18 | Refresh Google và email/password | SDK hiện tại chạy đúng theo cấu hình đã xác nhận | BE + FE |
| G19 | Logout rồi login lại | Dọn local và redirect Cognito logout; không nhầm với logout Google | BE + FE |
| G20 | Đăng nhập email/register/forgot/reset cũ | Không regression | BE + FE |
| G21 | Raw DTO và lỗi JSON | Không nhầm data wrapper/tokenType/code/message | BE + FE |
| G22 | DB lỗi/UNIQUE không phải email | Không biến mọi lỗi DB thành lỗi Cognito hoặc 409 giả | BE |
| G23 | User bị khóa sau khi đã login | Protected API tiếp tục chặn dù token chưa hết hạn | BE |
| G24 | Media/game sau khi login Google | User đã Sync, gọi được learning/game theo quyền hiện có | BE + FE |

### Kiểm thử tự động cần cập nhật

- BE: sửa AuthGoogleLoginTest để kiểm tra không gọi GetUser ở exchange.
- BE: sửa UserSyncServiceImplTest cho user hiện có, INSERT 1, INSERT 0, precheck email và giữ tên/status.
- BE: test handler UNIQUE email/code mới và rollback trong database test riêng khi kiểm thử race.
- FE: test state/PKCE, chống exchange trùng, explicit-token Sync, AuthProvider gating và lỗi 409 tại Sync.
- Giữ regression tests cho login/refresh/hồ sơ/blocked/CORS.
- Không chạy full context test kết nối database thật chỉ để xác nhận compile.

152 test mock đã báo trước thuộc bản BE trước V2. Cần cập nhật và chạy lại các test bị ảnh hưởng; con số đó không chứng minh thay đổi V2 hoặc cấu hình AWS đã hoạt động.

## 10. Trình tự thực hiện để hai bên không làm lệch nhau

1. Hai bên đọc mục 1–5 và thống nhất local port 3000, scope, response và thời điểm commit phiên.
2. Người quản lý Cognito bật scope còn thiếu, kiểm tra callback/mapping và cung cấp xác nhận cấu hình không chứa secret.
3. BE sửa API Google và Sync theo mục 6; cập nhật lỗi EMAIL_ALREADY_EXISTS, chạy test liên quan.
4. FE giữ cấu trúc hiện có, thêm PKCE/state và bảo đảm chỉ commit sau Sync; giữ Vite Proxy.
5. Kiểm thử Google user mới và email trùng trước, vì contract 409 đã chuyển sang Sync.
6. Kiểm thử BLOCKED, refresh, PATCH tên, logout và login email cũ.
7. Ghi kết quả G01–G24, chỉ coi chức năng hoàn thành khi cấu hình AWS và hai phía cùng qua.
8. Khi deploy, cấu hình domain HTTPS/callback/origin production riêng; Vite Proxy dev không tự xuất hiện trong bản build production.

## 11. Quy tắc không được hiểu nhầm

- Cognito có hỗ trợ Google; scope thiếu là cấu hình chưa đồng bộ, không chứng minh GetUser chỉ dành cho tài khoản native.
- Bốn scope là quyết định V1 để hỗ trợ cả đọc và cập nhật hồ sơ; phải bật thực tế trước khi dùng.
- HTTP 200 exchange chưa phải login ứng dụng thành công; Sync ACTIVE mới là điểm commit.
- Kiểm tra email trước INSERT không loại bỏ race; UNIQUE/rollback vẫn giữ.
- Một lần đọc hồ sơ là mục tiêu thông thường, không loại bỏ lần đọc mới cần thiết sau chờ khóa.
- Không gộp tài khoản bằng cách đổi cognito_sub theo email.
- Không tự bỏ PKCE/state để “test cho qua”.
- Không tự đổi refresh, bật rotation, bỏ Vite Proxy hoặc thêm bảng khi chưa có nhu cầu trong đặc tả này.
- Số file FE không quan trọng bằng hợp đồng, trạng thái và kết quả kiểm thử.
