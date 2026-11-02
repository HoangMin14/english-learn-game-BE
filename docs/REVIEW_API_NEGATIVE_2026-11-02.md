# Kiểm thử các trường hợp sai của API — 02/11/2026

## Kết quả

Đã chạy **265 trường hợp kiểm thử**, **239 đạt**, **26 assertion chưa đạt**, thuộc **8 nhóm vấn đề** bên dưới. Đây không phải 26 API riêng biệt bị lỗi.

| Nhóm test | Tổng | Đạt | Chưa đạt |
| --- | ---: | ---: | ---: |
| Security/Auth | 179 | 156 | 23 |
| Game/Learning/lịch sử/giới hạn leaderboard | 34 | 34 | 0 |
| Admin/cấu hình/audit/từ vựng/IPA | 49 | 49 | 0 |
| CORS khi FE dùng origin khác | 3 | 0 | 3 |
| **Tổng** | **265** | **239** | **26** |

Các test giữ kỳ vọng đúng, vì vậy Maven báo BUILD FAILURE khi gặp những assertion chưa đạt. Không thay đổi kỳ vọng để làm bộ test xanh và không sửa code nghiệp vụ trong lần review này.

## Phạm vi và giới hạn

- Đọc `D:/AWS/PJ_AWS_1/aws-workshop-main/backend.zip`, bản cập nhật 02/11/2026 13:13:57. Source Java và pom trong ZIP khớp với dự án đang mở.
- Nạp toàn bộ controller hiện tại trong Spring MVC và dùng `SecurityConfig`, `ActiveUserFilter`, `GlobalExceptionHandler` thật của dự án. Kiểm tra ở cấp request HTTP bằng MockMvc.
- Các service nghiệp vụ của những nhánh được kiểm tra chạy code thực tế; repository, AWS Cognito, S3, Polly và HTTP client ngoài được mock. Không kết nối AWS hoặc PostgreSQL/Supabase, không gửi email, không ghi DB thật.
- JWT được ký RSA trong test và xác minh bằng NimbusJwtDecoder với issuer/timestamp validation tương ứng cấu hình mặc định hiện tại. Có test chữ ký sai và issuer sai trả 401. Đây là kiểm thử validator bằng khóa test, không phải giả mạo chữ ký AWS hay gọi Cognito thực tế.
- Source hiện có 46 endpoint; 38 endpoint yêu cầu đăng nhập đã được kiểm tra token thiếu, token hết hạn và trạng thái BLOCKED. Các API nghiệp vụ không phải Sync cũng được kiểm tra User local chưa tồn tại. Sync có quyền tạo User lần đầu nên không áp dụng kỳ vọng 401 chỉ vì User chưa tồn tại cho endpoint này.
- Không kết luận về constraint SQL, transaction rollback thật, khóa cạnh tranh, lịch sử dữ liệu cũ, quyền IAM/App Client, refresh-token rotation hoặc hiệu năng DB. Các phần đó cần kiểm thử tích hợp riêng với môi trường test.
- Các trường hợp CORS, khóa đăng nhập và thu hồi token sau Logout thể hiện khoảng trống theo mục tiêu web/nghiệp vụ đã trao đổi. Nếu triển khai cùng origin hoặc Logout chỉ có ý nghĩa trả URL thì cần phân biệt giới hạn đó với lỗi request thông thường.

## F01 — Chưa kiểm tra loại token và App Client ở lớp bảo vệ API chung

**Ưu tiên: P1. Hai test chưa đạt.**

API tái hiện: `GET /api/users/me`; vấn đề nằm ở cấu hình chung nên không giới hạn ở API này.

| Token test, đều có chữ ký hợp lệ và cùng issuer | Kỳ vọng | Thực tế |
| --- | ---: | ---: |
| `token_use = id`, `aud = client-main` | 401 | 200 |
| `token_use = access`, `client_id = another-client` | 401 | 200 |
| Chữ ký bị sửa | 401 | 401 |
| Issuer khác, dù chữ ký hợp lệ theo khóa test | 401 | 401 |

Nguyên nhân: `SecurityConfig` dùng JWT decoder mặc định với issuer; không có validator chung yêu cầu access token của App Client dự án. Kiểm tra `token_use` và `client_id` đang có trong nhánh Refresh không bảo vệ các API khác.

Vị trí: [SecurityConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/config/SecurityConfig.java:59), [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java:262).

Hướng sửa: bổ sung validator chung cho `token_use = access` và `client_id` đúng cấu hình, đồng thời giữ kiểm tra chữ ký, issuer và thời hạn. Token App Client khác ở đây là token **cùng User Pool**, không phải token của User Pool khác.

[AWS hướng dẫn xác minh token và App Client](https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html).

## F02 — Login nhận body thiếu trường gây NullPointerException

**Ưu tiên: P1. Một test chưa đạt.**

```http
POST /api/auth/login
Content-Type: application/json

{}
```

Kỳ vọng 400. Code phát sinh `NullPointerException` chưa được xử lý, tương ứng 500 trong ứng dụng. `Map.of("USERNAME", request.getEmail(), "PASSWORD", request.getPassword())` không nhận giá trị null. Trường hợp này lỗi xảy ra trước lời gọi Cognito.

Vị trí: [AuthController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/controller/AuthController.java:65), [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java:163).

Hướng sửa: kích hoạt Bean Validation bằng `@Valid`, kiểm tra DTO và dữ liệu bắt buộc trước khi tạo request AWS; không dùng lỗi 500 để trả trường hợp người dùng bỏ trống thông tin.

## F03 — Năm API Auth chưa chặn payload không hợp lệ trước AWS

**Ưu tiên: P2. Năm test chưa đạt.**

| Endpoint POST | Payload kiểm thử |
| --- | --- |
| `/api/auth/login` | `{"email":"not-an-email","password":"password"}` |
| `/api/auth/register` | `{"email":"not-an-email","password":"password","displayName":"Learner"}` |
| `/api/auth/forgot-password` | `{}` |
| `/api/auth/reset-password` | `{}` |
| `/api/auth/confirm-register` | `{}` |

Kỳ vọng backend trả 400 trước khi gửi AWS. Thực tế các payload này đi vào service và được chuyển sang Cognito; trong test, client mock trả thành công nên controller trả 200.

**Không hiểu kết quả này thành Cognito thật chắc chắn chấp nhận email sai hoặc request rỗng.** AWS thực tế có thể từ chối; vấn đề đã chứng minh là validation ở backend không chạy, hợp đồng lỗi phụ thuộc phản hồi AWS.

Nguyên nhân: các method controller trên thiếu `@Valid`. `ForgotPasswordRequest` và `ConfirmRegisterRequest` cũng thiếu constraint cho trường bắt buộc. `LoginRequest`, `RegisterRequest`, `ResetPasswordRequest` có constraint nhưng controller chưa kích hoạt. Riêng tên Register vẫn có kiểm tra service sau phần PATCH đã bổ sung trước đó.

Vị trí: [AuthController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/controller/AuthController.java:41).

Hướng sửa: bổ sung constraint đúng DTO và `@Valid` cho các request, giữ validation tên hiện có; không gửi request thiếu trường sang AWS để chờ AWS phát hiện.

## F04 — Nhóm Auth cũ phân loại sai lỗi AWS

**Ưu tiên: P2. Mười hai test chưa đạt.**

| Tình huống giả lập AWS | Endpoint | Kỳ vọng | Thực tế |
| --- | --- | ---: | ---: |
| Sai mật khẩu, `NotAuthorizedException` | Login | 401 | 400 |
| `TooManyRequestsException` | Login/Register/Forgot/Reset/Confirm | 429 | 400 |
| AWS HTTP 500, `InternalErrorException` | Login/Register/Forgot/Reset/Confirm | 503 | 400 |
| Lỗi mạng `SdkClientException` | Login | 503 | Exception chưa xử lý → 500 |

Nguyên nhân: handler chung bắt `CognitoIdentityProviderException` rồi trả `badRequest(...)` cho mọi loại lỗi. Các method Auth cũ không phân loại lỗi như Refresh, Resend và service thuộc tính người dùng mới. `SdkClientException` không phải lớp con của lỗi Cognito service nên lọt ra ngoài handler này.

Vị trí: [GlobalExceptionHandler.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/GlobalExceptionHandler.java:77), [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java:159).

Hướng sửa: phân loại lỗi xác thực, dữ liệu không hợp lệ, throttling và upstream/network riêng. Giữ nguyên handler `ResponseStatusException` đã được bổ sung để không làm mất status do service chủ động chọn.

## F05 — Login chưa có nhánh xử lý Cognito challenge

**Ưu tiên: P2. Một test chưa đạt.**

Giả lập Cognito trả `challengeName = NEW_PASSWORD_REQUIRED`, có `session` nhưng không có `authenticationResult`.

Code đọc `result.accessToken()` khi `result == null` và phát sinh NullPointerException/500.

Vị trí: [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java:167).

Hướng sửa: kiểm tra challenge trước khi đọc token. Nếu V1 không hỗ trợ challenge, trả lỗi có chủ đích và thông báo rõ; nếu hỗ trợ thì cần response/flow tiếp tục challenge. Test tạm chọn 409 làm hợp đồng lỗi; 409 không phải status duy nhất bắt buộc, nhưng NPE/500 là lỗi cần bỏ. Nhánh này phát sinh khi có tài khoản yêu cầu đổi mật khẩu, MFA hoặc challenge khác; chưa xác nhận AWS hiện tại có bật các luồng đó hay không.

## F06 — Khóa local chưa ngăn Login cấp token nếu Cognito vẫn Enabled

**Ưu tiên: P2, cần hoàn thiện nếu giữ yêu cầu khóa cả đăng nhập. Một test chưa đạt.**

Tình huống: User local có `status = BLOCKED`, Cognito vẫn trả token sau khi xác thực email/mật khẩu. `POST /api/auth/login` hiện trả 200 vì không đọc trạng thái local và không có tích hợp khóa Cognito trong nhánh PATCH trạng thái.

Vị trí: [AdminUserServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/admin/service/impl/AdminUserServiceImpl.java:282), [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java:159).

**Không phải tài khoản BLOCKED gọi được toàn bộ API:** các test request nghiệp vụ với User BLOCKED đều bị chặn 403, kể cả Auth Sync. Khoảng trống là Login vẫn có thể cấp token nếu AWS chưa bị khóa.

Hướng sửa: nếu yêu cầu là không đăng nhập Cognito được, triển khai Disable/Enable Cognito cùng trạng thái local, có xử lý lỗi đồng bộ và retry. Chỉ kiểm tra local sau Login không ngăn người dùng tự gọi Cognito trực tiếp. PostgreSQL transaction không thể tự rollback thay đổi AWS.

## F07 — Logout hiện chỉ trả URL, token cũ vẫn dùng được

**Ưu tiên: P2, khoảng trống nếu Logout phải kết thúc phiên backend. Một test chưa đạt.**

1. Giữ một access token còn hạn.
2. Gọi `POST /api/auth/logout`.
3. Dùng lại token đó gọi `GET /api/users/me`.

Thực tế request cuối vẫn trả 200. Method Logout chỉ ghép URL và trả response, không gọi Cognito revoke/sign-out và không ghi nhận phiên/token đã kết thúc.

Vị trí: [AuthServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/auth/service/impl/AuthServiceImpl.java:84).

Hướng sửa: thống nhất Logout một phiên hay toàn bộ phiên, thực hiện thu hồi phù hợp và có cơ chế backend từ chối JWT đã kết thúc phiên. Không giả định gọi Cognito revoke xong thì mọi thư viện xác minh JWT offline tự biết token đã bị thu hồi. [AWS nêu rõ giới hạn này](https://docs.aws.amazon.com/cognito/latest/developerguide/token-revocation.html).

Nếu API chỉ được định nghĩa là cung cấp URL chuyển hướng, 200 hiện tại đúng với chức năng đó, nhưng chưa đáp ứng yêu cầu đăng xuất kèm vô hiệu hóa token backend.

## F08 — Chưa có CORS cho frontend khác origin

**Ưu tiên: P2, có thể chặn tích hợp FE nếu FE và backend khác origin. Ba test chưa đạt.**

Origin dùng trong test: `http://localhost:5173`.

| Request | Thực tế |
| --- | --- |
| OPTIONS `/api/auth/login`, xin POST và content-type | 200 nhưng không có `Access-Control-Allow-Origin` |
| POST `/api/auth/login` có Origin | 200 nhưng không có `Access-Control-Allow-Origin` |
| OPTIONS `/api/users/me`, xin PATCH và authorization/content-type | 401, không có `Access-Control-Allow-Origin` |

Chỉ nhìn status 200 của Login sẽ bỏ sót lỗi này: trình duyệt vẫn không cho FE đọc response nếu thiếu header CORS. Preflight của API được bảo vệ còn bị yêu cầu xác thực trước.

Vị trí: [CorsConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/config/CorsConfig.java:3), [SecurityConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/config/SecurityConfig.java:55).

`CorsConfig` hiện là class rỗng, không phải cấu hình CORS đang hoạt động. Hướng sửa là khai báo danh sách origin FE thực tế, các method/header cần dùng và tích hợp CORS trước bước xác thực của Spring Security. [Hướng dẫn Spring Security](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html).

Nếu dùng reverse proxy để FE/backend cùng origin, CORS không phải nguyên nhân chặn trong cách triển khai đó. Port 5173 chỉ là origin kiểm thử minh họa, không phải cấu hình FE đã được xác nhận.

## Những trường hợp sai đã được chặn đúng trong bộ test

- Toàn bộ 38 endpoint được bảo vệ: thiếu JWT → 401; JWT hết hạn → 401; User BLOCKED → 403.
- 37 endpoint không phải Sync: User chưa có local → 401.
- JWT sai chữ ký, sai issuer → 401.
- Learner gọi các API Admin được kiểm tra → 403.
- Admin tự khóa → 409; khóa Admin khác → 403; target không tồn tại → 404; status ngoài ACTIVE/BLOCKED → 400.
- Page âm, size 0/101, offset vượt Integer, filter role/status sai, khoảng ngày audit đảo ngược → 400.
- Config key lạ, giá trị boolean/số sai, số vượt Integer, thiếu description, tắt bản ghi global → 400.
- Submit session của người khác → 404; phiên COMPLETED/FAILED/ABANDONED → 409.
- Trả lời sai thứ tự, trả lời lại câu đã đúng, vượt lượt thử, snapshot thiếu → 409.
- Payload attempt thiếu/rỗng/chứa null/chữ không hợp lệ/timeMs âm → 400.
- Tạo thêm active session → 409; topic rỗng → 400; REVIEW trước khi học → 409.
- Xem history người khác → 404; dùng history để xem đáp án phiên đang chơi → 409.
- Learning bỏ qua từ đầu, nhảy từ, từ sai topic → 400; từ inactive → 404; chưa thắng checkpoint mà học tiếp → 409.
- Update/delete từ vựng sai topic → 404; request tạo/sửa thiếu trường → 400.
- IPA thiếu word/sai ký tự → 400; chưa có API key → 503.
- Refresh token bị Cognito từ chối → 401; Refresh/Resend body rỗng → 400.

Các kết quả trên chỉ xác nhận những nhánh đã liệt kê và fixture của test. Không có nghĩa mọi tình huống sai của các API đã được bao phủ hết.

## File bằng chứng và chạy lại

- Bộ test tách riêng: `target/negative-api-audit/src/test/java/audit/`.
- POM audit dùng source chính nhưng output/test source riêng: `target/negative-api-audit/pom.xml`.
- Kết quả từng case: `target/negative-api-audit/negative-results.tsv`.
- JUnit XML theo từng nhóm: `target/negative-api-audit/build/surefire-reports/TEST-audit.*.xml`.
- Log chạy: `run-final.log`, `run-cors-confirmation.log`, `run-security-confirmation.log` trong thư mục audit. CORS và Security được chạy lại sau khi hoàn thiện test, kết quả tổng hợp lấy các JUnit XML mới nhất.
- CSV lưu cùng báo cáo: `docs/API_NEGATIVE_RESULTS_2026-11-02.csv`.

Từ thư mục backend:

```powershell
Set-Content -LiteralPath target/negative-api-audit/negative-results.tsv -Value '' -NoNewline
mvn -o -f target/negative-api-audit/pom.xml '-Dtest=*AuditTest' test
```

Thư mục target là artifact tạm và có thể bị xóa bởi Maven clean. Bộ audit không được thêm vào test suite production hiện tại. Không chạy `BackendApplicationTests` trong lần audit này vì test đó khởi động toàn bộ ứng dụng theo cấu hình kết nối thật.

Thứ tự nên sửa trước khi giao FE: F01 → F02/F03 → F04 → F08; sau đó chốt và hoàn thiện F05/F06/F07 theo phạm vi V1. Tất cả hướng sửa ở đây là góp ý, chưa áp dụng vào source nghiệp vụ.
