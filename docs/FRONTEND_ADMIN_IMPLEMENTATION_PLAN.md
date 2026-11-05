# Kế hoạch implement và setup Frontend Admin V1

Ngày phân tích: 03/11/2026.

## 1. Phạm vi và căn cứ

- Yêu cầu hiện tại: đọc, phân tích và lập kế hoạch implement/setup. Chưa triển khai màn Admin, sửa production code, cấp quyền tài khoản hoặc thay đổi AWS/DB trong bước này.
- Tài liệu đầu vào: `D:/AWS/FRONTEND_ADMIN_ISSUES.md`, gồm 13 issue, 21 endpoint Admin và 8 phụ thuộc BE.
- Source FE thực tế: `D:/AWS/english-learn-game-FE-main`.
- Source BE: `D:/AWS/PJ_AWS_1/aws-workshop-main/backend`.
- Đã đối chiếu FE router/auth/client/page Admin và BE controller, DTO, xử lý blocked/Cognito/config. Không chạy lại 43 test được tài liệu báo cáo; không coi số đó là bằng chứng mới của lần phân tích này.
- Cấu trúc `.js` trong tài liệu là đề xuất logic. FE thực tế dùng React + TypeScript + Vite; triển khai bằng `.ts/.tsx` theo cấu trúc hiện có.
- Workspace BE có thay đổi đang làm dở từ trước. Không ghi đè hoặc gộp các thay đổi đó vào công việc FE.

## 2. Hiện trạng và khoảng thiếu đã xác định

| Phần | Đã có | Cần implement/sửa |
|---|---|---|
| Stack | React 19, TypeScript, Vite, Axios, TanStack Query, React Hook Form, Zod, Vitest, oxlint | Tận dụng stack này; chưa cần đổi framework hay thêm state manager |
| Admin UI | `src/features/admin/pages/AdminTopicListPage.tsx` là placeholder | Bảng dữ liệu, form, dialog, menu và các page nghiệp vụ |
| Router | `/admin`, `/admin/topics`, `/admin/users`, `/admin/vocabularies`, `/admin/game-configs` cùng trỏ về placeholder | AdminGuard, AdminLayout, routes theo topicId, config và audit |
| Guard | ProtectedRoute chỉ kiểm tra đăng nhập; nhóm Admin chưa dùng guard này | Chỉ render Admin khi profile BE có role=admin và status=ACTIVE |
| Auth | Login, sync, me, refresh và token storage đã có | Chặn điều hướng thành công khi chưa lấy được profile; xử lý blocked xuyên suốt |
| Trạng thái User | Type FE còn `ACTIVE | LOCKED`; LoginForm còn kiểm tra ACCOUNT_LOCKED | Dùng `ACTIVE | BLOCKED`, nhận ACCOUNT_BLOCKED từ BE |
| Lỗi API | Client đọc `errorCode` và `message` | Nhận cả `code`, `detail`, body rỗng/text và giữ HTTP status |
| Phân trang | PageResponse dùng items/totalItems | Admin BE dùng content/totalElements; tạo type riêng hoặc adapter rõ ràng |
| HTTP | Base `/api`, Bearer, refresh dùng DTO trực tiếp và hàng đợi đã có | FormData, request options timeout/signal, 204 và các nhánh refresh lỗi cần hoàn thiện |
| Audio | VocabularyAudioPlayer và hook game đã có | Tái sử dụng player cho Admin; renewal dùng API list Vocabulary, không dùng game resume |
| Cache | TanStack Query mặc định staleTime 2 phút, mutation retry=false | Keys theo topic/filter; invalidate sau mutation; hạn chế cache URL có chữ ký |

### Các lỗi nền cần ưu tiên

1. Client bỏ qua `code`, nên ACCOUNT_BLOCKED và COGNITO_* dễ bị biến thành FORBIDDEN/SERVER_ERROR chung.
2. AuthProvider chưa kết thúc phiên khi gặp ACCOUNT_BLOCKED; LoginForm nuốt lỗi sync/me rồi vẫn có thể báo đăng nhập thành công.
3. Client đặt Content-Type JSON mặc định; phải bỏ header này khi data là FormData để trình duyệt sinh multipart boundary.
4. Client hiện gộp mọi lỗi refresh thành hết phiên. 502/503 cần được phân biệt với refresh token thực sự hết hạn; giữ retry có giới hạn, không phát lại vô hạn.
5. BE có trường hợp User chưa sync trả 401 chỉ kèm message; FE không thể chỉ đợi USER_NOT_SYNCED. Đảm bảo sync thành công trong luồng login trước khi mở Admin; nếu cần recovery sau reload phải có quy ước rõ, không suy đoán mọi 401 là chưa sync.
6. Comment/type dùng `/api/v1` và giả định mọi timestamp có timezone đã cũ; sửa khi cập nhật module liên quan.

## 3. Kiến trúc đề xuất theo repo hiện tại

```text
src/
  app/router.tsx
  lib/
    apiClient.ts                 # mở rộng client chung
    normalizeApiError.ts         # code/errorCode + message/detail + status
    vocabularyAudio.ts           # tái sử dụng player hiện có
  types/api.types.ts             # thêm AdminPageResponse<T>, lỗi chuẩn hóa
  components/common/
    ConfirmDialog.tsx
    Pagination.tsx
    AudioButton.tsx
  features/auth/                 # sửa trạng thái, login và session recovery
  features/admin/
    components/AdminLayout.tsx
    components/AdminGuard.tsx
    api/{topicsApi,vocabulariesApi,gameConfigsApi,auditsApi,usersApi}.ts
    types/admin.types.ts
    hooks/queryKeys.ts
    pages/
      AdminTopicListPage.tsx
      AdminVocabularyListPage.tsx
      AdminGlobalConfigPage.tsx
      AdminTopicConfigPage.tsx
      AdminAuditListPage.tsx
      AdminUserListPage.tsx
    components/
      TopicForm.tsx
      VocabularyForm.tsx
      IpaLookupField.tsx
      AudioBackfillPanel.tsx
      ConfigForm.tsx
      AuditDetailPanel.tsx
      UserStatusDialog.tsx
```

Giữ auth/token store hiện có và một QueryClient. Không tạo thêm session store hay HTTP client độc lập riêng cho Admin.

BE trả DTO/array/page trực tiếp; envelope ApiResponse<T> hiện có chỉ là adapter nội bộ FE. Có thể tiếp tục sử dụng adapter này, nhưng tuyệt đối không đọc thêm một tầng data giả từ response BE. Chuẩn hóa 204 thành kết quả void ở tầng API.

Routes:

| Route | Màn hình |
|---|---|
| `/admin` | Redirect `/admin/topics` |
| `/admin/topics` | Topic list + form thêm/sửa |
| `/admin/topics/:topicId/vocabularies` | Vocabulary list + form + IPA/audio |
| `/admin/game-configs/global` | Global configs |
| `/admin/topics/:topicId/game-configs` | Topic overrides |
| `/admin/game-configs/audits` | Audit list, detail theo auditId ở query hoặc panel |
| `/admin/users` | User list + status dialog |

Menu Vocabulary cấp cao dẫn đến chọn Topic; không giả lập API trả toàn bộ Vocabulary mọi Topic. Deep-link Topic tải Admin topics list và tìm ID vì không có Topic detail endpoint.

## 4. Các giai đoạn triển khai

### Giai đoạn 1 — Nền Admin và phiên đăng nhập (FE-ADM-01, P0)

- AdminGuard kiểm tra loading → session → role/status từ BE; Learner hiển thị thiếu quyền hoặc về khu học.
- Hoàn thiện Login → Sync → profile ACTIVE → điều hướng; không dùng role tự lưu làm căn cứ.
- Chuẩn hóa mã lỗi `code ?? errorCode`, message/detail và status; ApiError giữ mã COGNITO_*.
- ACCOUNT_BLOCKED: dừng request quản trị, xóa token/profile/cache và chuyển Login với thông báo hiển thị được sau điều hướng. 403 vì role không được xem là bị khóa.
- Refresh tối đa một lần/request, dùng chung một refresh; kiểm tra trường hợp nhiều 401, retry vẫn 401, logout/blocked trong lúc refresh để token cũ không phục hồi phiên.
- Mở rộng http methods nhận request options: signal, timeout và headers; FormData không ép Content-Type.
- Dùng component loading/empty/error/retry hiện có ở nơi phù hợp; thêm dialog/pagination chung khi cần.

Nghiệm thu: Admin ACTIVE vào được, Learner/BLOCKED bị chặn; reload xác thực đúng; 204/401/403/text/rỗng không crash; không vòng lặp refresh và không báo sai lỗi dịch vụ thành sai mật khẩu.

### Giai đoạn 2 — Quản lý Topic (FE-ADM-02/03/04, P0)

- GET Admin topics; hiển thị active/hidden, ảnh fallback, orderNo read-only; lọc phía client.
- Create/Update dùng FormData; name <=100, description <=1000; create bắt buộc ảnh.
- Kiểm tra JPEG/PNG/WebP, giới hạn chốt với BE; preview dùng object URL và revoke khi thay file/rời form.
- PUT chỉ append field thay đổi; không gửi form không đổi; mô tả rỗng hiện không thể clear ở BE.
- DELETE có nhãn “Ẩn chủ đề”; PATCH restore có xác nhận và refetch. Vẫn thấy Topic ẩn ở bảng Admin.
- Invalidate Admin topics và dữ liệu Topic phía Learner có liên quan sau mutation.

Nghiệm thu: tạo/sửa từng field và ảnh, 409 với tên của Topic ẩn, 413, lỗi S3, hide/restore/204 và reload deep-link.

### Giai đoạn 3 — Vocabulary, IPA và audio (FE-ADM-05/06 P0; 07/08 P1)

- List theo topicId: word, meaningVi, IPA, audio, active, orderNo; lọc phía client và soft delete có xác nhận.
- Không thêm restore Vocabulary; backend chưa có contract.
- Form word <=100, meaningVi <=255; word sau chuẩn hóa hợp lệ `[a-z]+(?:[-'][a-z]+)*`.
- POST có cả word/meaningVi; PUT chỉ field đổi, không gửi `{}` hoặc field trắng. IPA/audio do BE tạo và trả về.
- Tra IPA bằng nút riêng, encode query; bỏ response cũ khi word đã đổi.
- AudioButton phát audioUrl của đúng ID; null thì disabled; dừng âm thanh cũ khi chọn từ/rời màn. Khi lỗi URL, refetch list đúng Topic và tìm lại cùng ID, retry một lần.
- Backfill không body, khóa nút trong lúc chạy. Hiển thị totalMissing/generated/failed/failedVocabularyIds và tình huống 200 thành công một phần.
- Cho phép timeout riêng được thống nhất theo dữ liệu thử; không giả lập tiến độ phần trăm, không tự retry POST khi timeout. Refetch để kiểm tra kết quả thực tế.

Nghiệm thu: nghe đúng từ, null/expired/missing S3; CRUD và IPA lỗi; tránh response cũ giữa các Topic; backfill 0 từ, một phần lỗi và timeout.

### Giai đoạn 4 — Cấu hình game (FE-ADM-09/10, P0)

- Chỉ hỗ trợ max_attempt_per_word, checkpoint_pass_points, score_enabled, streak_enabled.
- configValue luôn là String. Integer: attempts >=1; points >=0; cả hai <=2147483647. Boolean serialize `"true"`/`"false"`.
- Description bắt buộc; đọc globalDescription/overrideDescription theo scope.
- Global không có công tắc active=false. Lưu từng key, không giả định transaction nhiều key.
- Topic hiển thị global/override/effective/source từ BE. Phân biệt overrideActive null và false.
- Tách “Dùng cấu hình riêng” khỏi giá trị bật/tắt tính năng: active=false là kế thừa Global.
- Reset một key/toàn bộ bằng DELETE có xác nhận; GET lại sau 204.
- Invalidate config scope liên quan, effective config đang dùng ở FE và audit queries. Không làm thay đổi snapshot phiên game đang chơi.

Nghiệm thu: đủ 4 key, kiểu String đúng, missing global báo lỗi, reset đúng phạm vi, không tự tính sai với chuỗi `"false"`.

### Giai đoạn 5 — Quản lý User (FE-ADM-12/13, P0)

- GET phân trang server content/page/size/totalElements/totalPages; keyword/role/status vào query, page bắt đầu 0.
- Đổi filter hoặc size reset page=0; bỏ param rỗng, bỏ response cũ. Không thêm API sort/detail/đổi role/xóa User.
- Chỉ Learner có hành động BLOCKED/ACTIVE; dialog ghi đúng người và status cần gửi.
- Không optimistic toggle trạng thái. PATCH 200 cập nhật từ response rồi refetch; có thể row biến mất khỏi filter.
- PATCH khóa lỗi COGNITO_* vẫn có thể đã commit BLOCKED local: refetch kể cả nhánh lỗi, giữ thông báo sync và nút retry cùng status yêu cầu.
- Mở khóa gọi Cognito thành công trước khi local ACTIVE. Không đảo status để retry và không biến lỗi sync AWS thành refresh JWT Admin.
- Khi không refetch được sau timeout, hiển thị chưa xác định kết quả; giữ hành động yêu cầu để kiểm tra/thử lại có chủ đích.

Nghiệm thu: Learner/admin/self, filtered pagination, 429/502/503, khóa local thành công nhưng AWS lỗi, retry BLOCKED, mở khóa lỗi và Login của tài khoản bị khóa.

### Giai đoạn 6 — Audit và tích hợp cuối (FE-ADM-11, P1)

- List phân trang server; filter scope/topicId/configKey/action/actorUserId/operationId/changedFrom/changedTo.
- Reset page khi filter đổi; GLOBAL bỏ topicId; validate UUID, ID dương và from<=to; đổi thời gian chọn sang Instant có offset/Z.
- Detail render JSON Map trước/sau an toàn, chấp nhận null/key động/snake_case; không inject HTML.
- Actor chỉ có ID; không tạo request User detail chưa có. operationId dùng filter hiện có.
- Hiển thị thay đổi description/active ngay cả khi oldValue==newValue; audit chỉ đọc dữ liệu server.

Nghiệm thu: empty, INSERT/UPDATE/DELETE, JSON null, group theo operationId bằng filter, khoảng ngày theo múi giờ hiển thị.

## 5. Setup local và môi trường tích hợp

### FE

File `.env` tại root FE, dùng giá trị local hiện tại:

```dotenv
VITE_API_BASE_URL=/api
VITE_BACKEND_URL=http://localhost:8080
```

Chạy từ `D:/AWS/english-learn-game-FE-main`:

```powershell
npm ci
npm run dev
```

Vite dùng port 3000 và proxy `/api`; khởi động lại khi đổi env/config. Không đưa AWS credentials, Cognito secret hoặc key từ điển vào biến VITE_. Production phải có reverse proxy `/api`, hoặc build với URL API tuyệt đối và CORS tương ứng.

### BE và dữ liệu test

| Hạng mục | Điều cần xác nhận | Ảnh hưởng |
|---|---|---|
| Backend runtime | DB và các biến cấu hình hiện có được nạp vào process, không chỉ đặt trong file `.env` | Spring Boot không được giả định tự đọc `.env` nếu chưa có cơ chế nạp |
| CORS | Origin chính xác của FE; local hiện 3000/5173 | Không dùng wildcard/credentials include để chữa sai origin |
| Admin test | Cognito identity gắn User local role=admin, status=ACTIVE | Không có API FE đổi role; cần người quản lý dữ liệu chuẩn bị tài khoản test |
| User test | Learner ACTIVE/BLOCKED riêng cho kiểm tra trạng thái | Không dùng tài khoản thật để thử khóa |
| Topic/Vocabulary test | Topic active/hidden, từ active/inactive, trùng tên, URL null | Đủ ca CRUD và soft delete |
| Upload | Chốt `spring.servlet.multipart.max-file-size=2MB`, request size lớn hơn tổng ảnh+field, ví dụ 3MB, nếu chọn giới hạn service 2 MiB | Đây là cấu hình đề xuất, chưa áp dụng; kiểm tra runtime và lỗi 413 |
| Cognito | Client/pool/region thống nhất; BE lấy pool ID từ JWT issuer; quyền AdminDisableUser/AdminEnableUser | Mock test không xác nhận IAM thật |
| Storage | `aws.s3.bucket`, `aws.s3.region`, SDK credentials và quyền thao tác object theo service | Kiểm tra ảnh/MP3 thật; URL ký không chứng minh object tồn tại |
| Polly / Dictionary | Quyền synthesize speech, region và `merriam-webster.api-key` | Tạo/sửa word, IPA và backfill cần dịch vụ thật |
| Database config | Có 4 global keys, constraint phục vụ upsert và trigger audit | Không tự seed hoặc tạo trigger trong bước lập kế hoạch |
| Thời gian | Chốt quy ước LocalDateTime của User cũ; audit Instant hiển thị theo Asia/Ho_Chi_Minh | Không tự parse mọi User date như giờ trình duyệt |

Các phụ thuộc upload/IAM/DB không cản việc xây UI bằng mock contract. Chúng là điều kiện để nghiệm thu tích hợp thật. Restore Vocabulary và clear Topic description chưa được BE hỗ trợ; V1 phải phản ánh hạn chế đó.

## 6. Cache, trạng thái và dữ liệu request

- Keys gợi ý: `['admin','topics']`, `['admin','vocabularies',topicId]`, `['admin','configs','global']`, `['admin','configs','topic',topicId]`, `['admin','users',filters]`, `['admin','audits',filters]`.
- GET nhận AbortSignal từ Query khi có thể; IPA/Topic switch vẫn phải kiểm tra request hiện hành. Hủy request FE không đồng nghĩa mutation BE dừng.
- Mutation retry=false; không auto retry upload/create/backfill/PATCH User. Error 502 khóa User vẫn phải refetch.
- Signed URLs chỉ nằm trong cache ngắn hạn; refresh khi media lỗi, refetch khi vào lại trang. Không persist chúng vào localStorage.
- Audio Admin dùng một player đang hoạt động trong list để ngăn nhiều hàng phát chồng nhau; cleanup khi đổi Topic/unmount.
- Sau blocked/logout xóa dữ liệu nhạy cảm đã cache; ngăn request chậm/refresh đang chạy dựng lại phiên cũ.

## 7. Kiểm tra và tiêu chuẩn bàn giao

Kiểm tra FE tự động:

```powershell
npm run build
npm test
npm run lint
```

Nếu kiểm tra bản staging đặt trong `backend/target`, oxlint có thể bỏ qua vì gitignore của repo cha; dùng `npx oxlint --no-ignore src vite.config.ts` cho đúng phạm vi source.

Test có giá trị cần thêm: AdminGuard; code ACCOUNT_BLOCKED; refresh đồng thời; multipart boundary; 204; query content/totalElements; gửi đúng field thay đổi; serialization config String; override inactive; lỗi PATCH nhưng local đã BLOCKED; URL renew đúng ID; stale response sau đổi Topic/filter.

Kiểm tra tích hợp trên môi trường test: 21 endpoint Admin và Auth phụ thuộc, role/token/blocked, ảnh thực và MP3 S3, từ điển/Polly, config/audit DB. Có thể dùng Postman CLI đã cài cho API test với biến môi trường cục bộ; không nhúng token/password vào collection hoặc báo cáo.

Không coi HTTP 200 luôn là thành công toàn bộ của backfill; không coi timeout/502 luôn là chưa thay đổi dữ liệu. Không coi mock passing là đủ xác nhận IAM/DB/S3 thật.

## 8. Thứ tự bàn giao

1. Nền Admin + guard/auth/client, kèm regression cho login/game hiện có.
2. Topic list/form/hide/restore chạy API thật.
3. Vocabulary CRUD và nút audio; tiếp đến IPA preview/backfill.
4. Global config và Topic override/reset.
5. User list/status + luồng lỗi đồng bộ Cognito.
6. Audit, kiểm tra tích hợp và tài liệu setup.

Mỗi phần bàn giao kèm API/types/UI, loading/empty/error, kiểm tra đúng nghiệp vụ và các phụ thuộc còn chưa xác nhận. Không thêm dashboard thống kê toàn hệ thống, đổi role, restore Vocabulary, upload audio hay reorder khi chưa có contract.

Ưu tiên bắt đầu implement bằng giai đoạn 1 và 2. Chỉ bổ sung test tooling nếu bộ test UI/thao tác cần khả năng mà Vitest hiện có chưa cung cấp; chưa cần cài thêm thư viện cho bước lập kế hoạch.
