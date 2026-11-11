# Báo cáo tổng hợp các chỉnh sửa Frontend từ khi nhận source

**Ngày tổng hợp:** 10/11/2026.  
**Dự án FE đang sử dụng:** `D:/AWS/english-learn-game-FE-main`.  
**Source được bạn gửi ban đầu:** `D:/AWS/english-learn-game-FE-main.zip`.  
**BE được đối chiếu khi tích hợp:** `D:/AWS/PJ_AWS_1/aws-workshop-main/backend`.

## 1. Phạm vi và cách đọc báo cáo

Tài liệu này tổng hợp những việc tôi đã thực hiện theo các yêu cầu của bạn trong cuộc trò chuyện, từ xử lý lỗi đăng ký/đăng nhập đến sửa game, streak và điều khiển camera. Nội dung được đối chiếu với source FE hiện tại và các ghi chú bàn giao đã lưu.

- Các mục **đã sửa/đã triển khai** là thay đổi trong source, không đồng nghĩa mọi chức năng đã được nghiệm thu với tài khoản và dịch vụ thật.
- Kết quả test là kết quả tại từng lần bàn giao trước đây. Ngày 10/11 chỉ đọc lại source và tạo báo cáo, không chạy lại test hoặc khởi động server.
- Các đợt có thể sửa trùng một file. Không cộng số file hoặc số test của từng đợt thành một tổng duy nhất.
- File ZIP hiện tại đã có những phần Admin được triển khai trong quá trình làm việc. Vì vậy không coi ZIP hiện tại là bản gốc bất biến để khẳng định chính xác mọi dòng trước/sau hoặc mọi file mới từ lần gửi đầu.

## 2. Tổng quan các đợt công việc

| Đợt | Yêu cầu/vấn đề bạn đưa ra | Công việc đã thực hiện |
|---|---|---|
| 1 | Đăng ký/login từ FE bị 403, Swagger gọi được; sau chạy lại có 401 | Sửa base URL, proxy/CORS, gửi token, refresh token, endpoint OTP và luồng xác thực. |
| 2 | Muốn nhấn icon loa để đọc đúng từ từ `audioUrl` BE trả | Nối MP3 thật cho câu hỏi game, các mode và resume; xử lý thiếu audio, URL hết hạn, chuyển câu. |
| 3 — ghi nhận 03/11/2026 | Đọc `FRONTEND_ADMIN_ISSUES.md`, phân tích implement/setup, sau đó yêu cầu thực hiện | Đối chiếu contract, viết kế hoạch, triển khai khu Admin và kết nối 21 endpoint. |
| 4 — ghi nhận 03/11/2026 | Đọc `D:/AWS/SKILL.md` và cập nhật UI toàn website | Cải thiện responsive, bố cục, trạng thái tương tác, biểu mẫu, bàn phím và khả năng tiếp cận. |
| 5 | `Port 3000 is already in use`, yêu cầu ngưng chạy để bạn tự `npm run dev` | Dừng dev server do tôi khởi động; các đợt sau không tự bật lại server cổng 3000. |
| 6 — bàn giao 04/11/2026 | Win/streak còn hardcode hoặc chưa nối API; chuyển chuột sang cử chỉ tay không được | Dùng dữ liệu game/streak thật, sửa resume/chơi lại/mất kết nối, chọn mode, giữ video/canvas và dọn tài nguyên camera. |

## 3. Đăng ký, đăng nhập, 403/401 và HTTP client

### 3.1. URL và cấu hình chạy local

| Trước/vấn đề đã xác định | Đã chỉnh | Tác dụng |
|---|---|---|
| Prefix FE không khớp controller Spring Boot, có giả định `/api/v1` | Dùng base URL `/api` và các endpoint đúng BE. | Tránh gọi nhầm đường dẫn. |
| Frontend và backend khác origin; Swagger không phản ánh đầy đủ vấn đề CORS của trình duyệt | Nối FE qua Vite proxy `/api`; điều chỉnh CORS local của BE cho `http://localhost:3000` và `http://localhost:5173`. | Request từ FE local được xử lý theo cấu hình origin tương ứng. |
| `VITE_BACKEND_URL` trong file env chưa được Vite config đọc đúng | Dùng `loadEnv(mode, process.cwd(), 'VITE_')` trong Vite config. | Proxy dùng backend theo mode/env đang chọn. |
| Chạy nhầm source hoặc chưa nhận bản sửa | Áp dụng thay đổi vào thư mục FE thật bạn chạy bằng npm. | Bản sửa được đưa vào source đang sử dụng, không chỉ nằm ở bản staging. |

Vite dùng cổng `3000`, `strictPort: true`: nếu cổng đã bị chiếm thì báo lỗi rõ. Lỗi chiếm cổng không phải lỗi đăng nhập hay lỗi API.

Cấu hình public dùng khi chạy local:

```dotenv
VITE_API_BASE_URL=/api
VITE_BACKEND_URL=http://localhost:8080
```

Vite proxy chỉ dùng khi phát triển. Production cần reverse proxy `/api` hoặc cấu hình API URL tương ứng trước khi build. Biến môi trường BE đang chạy có thể ghi đè giá trị CORS mặc định.

### 3.2. Token và refresh token

Đã chỉnh HTTP client dùng chung để:

- Không đính kèm JWT cũ vào các request auth public: đăng ký, xác nhận OTP, gửi lại OTP, đăng nhập, quên/đặt lại mật khẩu và refresh.
- Request cần đăng nhập tiếp tục gửi Bearer access token.
- Đọc response refresh dạng DTO trực tiếp của BE, không giả định sai thêm một tầng `data`.
- Kiểm tra có `accessToken` hợp lệ trong response refresh; đặt timeout.
- Dùng chung một request refresh khi nhiều API đồng thời trả 401.
- Mỗi request chỉ được refresh/phát lại có giới hạn, tránh lặp vô hạn.
- Theo dõi phiên để refresh cũ không lưu token trở lại sau khi logout hoặc tài khoản bị khóa.
- Phân biệt lỗi dịch vụ/mất kết nối với refresh token thực sự không hợp lệ; không coi mọi lỗi refresh là sai mật khẩu.
- Xử lý `ACCOUNT_BLOCKED`, kết thúc phiên và hiển thị thông báo phù hợp khi chuyển về login.

### 3.3. Luồng đăng nhập và phân quyền

- Luồng thành công được sửa thành: **Login → lưu token → gọi Sync → nhận profile ACTIVE → cập nhật AuthContext → điều hướng**.
- Không nuốt lỗi Sync rồi vẫn báo đăng nhập thành công.
- Profile/role/status lấy từ BE; chuẩn hóa trạng thái tài khoản thành `ACTIVE | BLOCKED`.
- Khi tải lại trang, kiểm tra profile hiện tại trước khi hiển thị khu cần đăng nhập.
- Lỗi tải profile có trạng thái thử lại; không giả định chỉ có token là đủ quyền.
- Khi kết thúc phiên, hủy/xóa cache query liên quan để tránh giữ dữ liệu người dùng cũ.
- Logout cục bộ vẫn hoàn tất nếu API logout không truy cập được.
- Redirect sau đăng nhập chỉ nhận đường dẫn nội bộ phù hợp.
- Endpoint gửi lại OTP dùng `/auth/resend-confirmation`.

### 3.4. Chuẩn hóa request/response và lỗi

- Hỗ trợ DTO, array, page trực tiếp từ Spring Boot và response không có body như HTTP 204.
- `ApiResponse` là dạng adapter nội bộ FE; không yêu cầu BE đổi toàn bộ response sang envelope.
- Chuẩn hóa `code`, `errorCode`, `message`, `detail`, HTTP status và lỗi theo field.
- Bổ sung request options như `timeout` và `AbortSignal`.
- Khi gửi `FormData`, bỏ `Content-Type: application/json` để trình duyệt tự tạo multipart boundary.

**File liên quan:** [vite.config.ts](D:/AWS/english-learn-game-FE-main/vite.config.ts), [apiClient.ts](D:/AWS/english-learn-game-FE-main/src/lib/apiClient.ts), [normalizeApiError.ts](D:/AWS/english-learn-game-FE-main/src/lib/normalizeApiError.ts), [tokenStorage.ts](D:/AWS/english-learn-game-FE-main/src/lib/tokenStorage.ts), [authApi.ts](D:/AWS/english-learn-game-FE-main/src/features/auth/api/authApi.ts), [AuthProvider.tsx](D:/AWS/english-learn-game-FE-main/src/features/auth/context/AuthProvider.tsx), [LoginForm.tsx](D:/AWS/english-learn-game-FE-main/src/features/auth/components/LoginForm.tsx), [auth.types.ts](D:/AWS/english-learn-game-FE-main/src/features/auth/types/auth.types.ts).

## 4. Phát âm thanh đúng từ từ MP3

### 4.1. Phần FE đã nối

| Khu vực | Dữ liệu dùng | Đã xử lý |
|---|---|---|
| Game CHECKPOINT/FREE_PLAY/REVIEW | `questions[].audioUrl` | Nút loa phát MP3 của câu hỏi đang hiển thị. |
| Resume game | `questions[].audioUrl` từ API active | Lấy lại âm thanh của đúng `vocabularyId`. |
| Admin Vocabulary | `audioUrl` của từng từ | Nghe từ trong bảng, xử lý thiếu audio và lấy lại URL khi cần. |

Các mode game dùng chung luồng câu hỏi; không hardcode MP3 cho từng mode.

### 4.2. Hành vi đã bổ sung

- Dùng `Audio` để phát URL BE trả về; không đoán đường dẫn hoặc dùng một file cố định cho nhiều từ.
- Chọn audio theo vocabulary ID, không dựa vào thứ tự từ trong DB.
- `audioUrl` null/rỗng thì vô hiệu hóa nút loa, vẫn cho phép tiếp tục chơi.
- Dừng và giải phóng audio cũ khi đổi từ, phát lại hoặc rời màn hình.
- Bỏ kết quả bất đồng bộ cũ để âm thanh của câu trước không phát sau khi đã chuyển câu.
- Có trạng thái đang phát và thông báo không phát được.
- Khi phát lỗi, gọi lại API để lấy URL mới đúng từ và thử lại một lần, tránh vòng lặp.
- Game dùng API resume để đổi URL; Admin tải lại danh sách từ của đúng topic. Không tạo game mới chỉ để đổi URL.
- Phân biệt trình duyệt chặn phát âm thanh với lỗi tải file; yêu cầu người dùng nhấn loa lại khi cần.

**Phạm vi cần ghi rõ:** source FE hiện tại chưa có route/màn học từ vựng riêng nối `GET /api/topics/{topicId}/learning`. Vì vậy phần đã hoàn thành ở FE là **audio game/resume và Admin Vocabulary**, chưa phải toàn bộ UI học từ vựng riêng. BE có field `vocabularies[].audioUrl` không tự có nghĩa FE đã triển khai màn đó.

**File liên quan:** [vocabularyAudio.ts](D:/AWS/english-learn-game-FE-main/src/lib/vocabularyAudio.ts), [useVocabularyAudio.ts](D:/AWS/english-learn-game-FE-main/src/features/game/hooks/useVocabularyAudio.ts), [WordClueCard.tsx](D:/AWS/english-learn-game-FE-main/src/features/game/components/WordClueCard.tsx), [gameSessionApi.ts](D:/AWS/english-learn-game-FE-main/src/features/game/api/gameSessionApi.ts), [gameSession.types.ts](D:/AWS/english-learn-game-FE-main/src/features/game/types/gameSession.types.ts), [GameSessionPage.tsx](D:/AWS/english-learn-game-FE-main/src/features/game/pages/GameSessionPage.tsx), [VocabularyList.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/pages/VocabularyList.tsx).

## 5. Triển khai khu Admin từ tài liệu yêu cầu

### 5.1. Phân tích và setup

Đã đọc `D:/AWS/FRONTEND_ADMIN_ISSUES.md`, đối chiếu controller/DTO của BE và cấu trúc React + TypeScript hiện có, sau đó viết kế hoạch trước khi bạn yêu cầu thực hiện. Không đổi sang framework khác hoặc làm một ứng dụng Admin tách biệt.

Đợt triển khai Admin ghi nhận **33 file mới/thay đổi**, bao gồm API/types, guard/layout, page/form, auth/client, test và tài liệu setup.

### 5.2. Route và quyền truy cập

| Route FE | Chức năng |
|---|---|
| `/admin` | Chuyển tới danh sách chủ đề. |
| `/admin/topics` | Quản lý chủ đề. |
| `/admin/topics/:topicId/vocabularies` | Quản lý từ của một chủ đề. |
| `/admin/game-configs/global` | Cấu hình game chung. |
| `/admin/topics/:topicId/game-configs` | Override cấu hình theo chủ đề. |
| `/admin/users` | Danh sách và trạng thái tài khoản Learner. |
| `/admin/game-configs/audits` | Lịch sử thay đổi cấu hình và chi tiết. |

`AdminGuard` kiểm tra đăng nhập, `role=admin` và `status=ACTIVE` từ profile BE. Learner không được hiển thị khu Admin; chưa đăng nhập được đưa tới login kèm redirect. Menu Quản trị hiển thị theo quyền. BE vẫn là nơi thực thi phân quyền API.

### 5.3. Chức năng từng màn

| Nhóm | Đã triển khai |
|---|---|
| Topic | Danh sách đầy đủ, tìm/lọc, thêm/sửa, chọn và xem trước ảnh, ảnh thay thế khi lỗi, ẩn/khôi phục và tải lại dữ liệu. |
| Vocabulary | Danh sách theo topic, thêm/sửa/ẩn, xem trước IPA, nghe MP3, tạo âm thanh còn thiếu và báo kết quả từng ID lỗi. |
| Global config | Xem/sửa bốn key BE hỗ trợ; gửi `configValue` dạng String. |
| Topic config | So sánh Global/Override/Effective, bật/tắt áp dụng override, reset một key hoặc tất cả, có xác nhận. |
| User | Lọc/phân trang phía server, khóa/mở Learner, refetch và retry đúng trạng thái yêu cầu khi lỗi Cognito. |
| Audit | Lọc/phân trang, xem JSON trước/sau, tìm `operationId`, hiển thị thời gian audit theo UTC+7. |

Validation chính:

- Topic: tên tối đa 100 ký tự, mô tả tối đa 1000; ảnh JPEG/PNG/WebP tối đa 2 MiB; form tạo yêu cầu ảnh.
- Vocabulary: chuẩn hóa từ tiếng Anh, tối đa 100 ký tự, cho phép dấu nối/nháy giữa chữ theo quy tắc; nghĩa tiếng Việt bắt buộc, tối đa 255 ký tự.
- Update chỉ gửi phần thay đổi phù hợp; tránh submit rỗng hoặc dữ liệu trắng.
- Bốn config: `max_attempt_per_word`, `checkpoint_pass_points`, `score_enabled`, `streak_enabled`.
- Tắt áp dụng override là kế thừa Global. Muốn tắt một tính năng riêng ở topic phải dùng giá trị `"false"` với override đang áp dụng.

Xử lý tình huống API:

- User status lỗi 502/503 có thể đã cập nhật DB. FE tải lại và cho retry cùng status, không tự giả lập quay về ACTIVE.
- Backfill audio có thể trả 200 nhưng thành công một phần; hiển thị `totalMissing`, `generated`, `failed`, `failedVocabularyIds`.
- Timeout không chứng minh BE đã dừng thao tác. Không tự retry mutation khi kết quả chưa xác định.
- Timeout riêng: tạo/sửa Vocabulary 60 giây, backfill audio 180 giây, đổi trạng thái User 30 giây.

### 5.4. Danh sách 21 endpoint Admin đã kết nối từ FE

Các path bên dưới đã bao gồm prefix `/api` do HTTP client thêm.

| # | Method | Endpoint |
|---|---|---|
| 1 | GET | `/api/admin/topics` |
| 2 | POST | `/api/admin/topics` |
| 3 | PUT | `/api/admin/topics/{topicId}` |
| 4 | DELETE | `/api/admin/topics/{topicId}` |
| 5 | PATCH | `/api/admin/topics/{topicId}/restore` |
| 6 | GET | `/api/admin/topics/{topicId}/vocabularies` |
| 7 | POST | `/api/admin/topics/{topicId}/vocabularies` |
| 8 | PUT | `/api/admin/topics/{topicId}/vocabularies/{vocabularyId}` |
| 9 | DELETE | `/api/admin/topics/{topicId}/vocabularies/{vocabularyId}` |
| 10 | GET | `/api/admin/vocabularies/ipa` |
| 11 | POST | `/api/admin/topics/{topicId}/vocabularies/generate-missing-audio` |
| 12 | GET | `/api/admin/game-configs/global` |
| 13 | PUT | `/api/admin/game-configs/global/{configKey}` |
| 14 | GET | `/api/admin/game-configs/topics/{topicId}` |
| 15 | PUT | `/api/admin/game-configs/topics/{topicId}/{configKey}` |
| 16 | DELETE | `/api/admin/game-configs/topics/{topicId}/{configKey}` |
| 17 | DELETE | `/api/admin/game-configs/topics/{topicId}` |
| 18 | GET | `/api/admin/users` |
| 19 | PATCH | `/api/admin/users/{userId}/status` |
| 20 | GET | `/api/admin/game-configs/audits` |
| 21 | GET | `/api/admin/game-configs/audits/{auditId}` |

Đây là xác nhận API đã được nối trong source/test FE, không phải xác nhận đã nghiệm thu đủ 21 endpoint với DB/AWS thật.

### 5.5. File Admin chính

| Phần | File |
|---|---|
| API và DTO | [adminApi.ts](D:/AWS/english-learn-game-FE-main/src/features/admin/api/adminApi.ts), [admin.types.ts](D:/AWS/english-learn-game-FE-main/src/features/admin/types/admin.types.ts) |
| Guard và layout | [AdminGuard.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/components/AdminGuard.tsx), [AdminLayout.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/components/AdminLayout.tsx), [router.tsx](D:/AWS/english-learn-game-FE-main/src/app/router.tsx) |
| UI và biểu mẫu | [AdminUI.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/components/AdminUI.tsx), [TopicForm.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/components/TopicForm.tsx), [VocabularyForm.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/components/VocabularyForm.tsx), [FormField.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/components/FormField.tsx), [fieldDescription.ts](D:/AWS/english-learn-game-FE-main/src/features/admin/components/fieldDescription.ts) |
| Màn nghiệp vụ | [TopicList.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/pages/TopicList.tsx), [VocabularyList.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/pages/VocabularyList.tsx), [ConfigPage.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/pages/ConfigPage.tsx), [UserList.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/pages/UserList.tsx), [AuditList.tsx](D:/AWS/english-learn-game-FE-main/src/features/admin/pages/AuditList.tsx) |
| Validation, filter, CSS | [adminForms.ts](D:/AWS/english-learn-game-FE-main/src/features/admin/lib/adminForms.ts), [auditFilters.ts](D:/AWS/english-learn-game-FE-main/src/features/admin/lib/auditFilters.ts), [admin.css](D:/AWS/english-learn-game-FE-main/src/features/admin/admin.css) |

## 6. Cập nhật UI toàn website theo SKILL.md

Đã đọc tài liệu thiết kế bạn gửi và dùng hệ thống thiết kế đang có: Indigo/Coral/Pastel, nền kem, font Plus Jakarta Sans + DM Sans, token bo góc/bóng/khoảng cách. Đợt UI ghi nhận **29 file mới/thay đổi**; không thay contract API hoặc phân quyền trong riêng đợt này, không thêm dependency trong riêng đợt UI.

| Khu vực | Chỉnh sửa |
|---|---|
| Toàn site | Menu mobile responsive; Escape/outside click/đóng sau điều hướng; trả focus; skip link; focus-visible; nút disabled/loading; vùng bấm; hỗ trợ giảm chuyển động. |
| Trang chủ | Typography, spacing, grid, xử lý chữ dài; chỉnh vị trí nhãn hero không chồng nội dung. |
| Chủ đề | Card/grid co giãn, tiến trình dùng `scaleX`, nhãn và ARIA. |
| Auth | Nhãn/lỗi liên kết với input, `aria-invalid`/`aria-describedby`, autocomplete, nút hiện mật khẩu dễ bấm. |
| Game | Bố cục câu hỏi, nút loa, kho chữ và ô chữ phù hợp màn nhỏ; thanh tiến trình dùng transform. |
| Tiến trình/xếp hạng | Số có độ rộng ổn định, tên dài xuống dòng, icon SVG đồng bộ, nhóm số liệu co giãn. |
| Admin | Sidebar/menu, bảng/bộ lọc có nhãn, skeleton/empty/error/success, dialog, validation inline; mobile hiển thị hàng có nhãn dữ liệu. |

File giao diện chính gồm [index.css](D:/AWS/english-learn-game-FE-main/src/index.css), [ui-craft.css](D:/AWS/english-learn-game-FE-main/src/ui-craft.css), [Header.tsx](D:/AWS/english-learn-game-FE-main/src/components/layout/Header.tsx), [AppLayout.tsx](D:/AWS/english-learn-game-FE-main/src/components/layout/AppLayout.tsx), [TopicCard.tsx](D:/AWS/english-learn-game-FE-main/src/components/common/TopicCard.tsx), [HomePage.tsx](D:/AWS/english-learn-game-FE-main/src/pages/HomePage.tsx), [LeaderboardPage.tsx](D:/AWS/english-learn-game-FE-main/src/features/leaderboard/pages/LeaderboardPage.tsx), các form auth, các component game và CSS/page Admin nêu trên.

Kiểm tra UI đã ghi nhận: preview 12 màn tại 375/768/1280px không tràn ngang document; kiểm tra menu mobile/Escape/focus trên FE thật. Dữ liệu mẫu dùng cho QA riêng không được đưa vào thay thế API của FE đang chạy.

## 7. Sửa dữ liệu game, win, streak, lịch sử và chủ đề

### 7.1. Các nhánh hardcode/giả đã bỏ khỏi luồng chơi thực

| Trước | Sau |
|---|---|
| Khi khởi tạo API lỗi, FE dựng phiên giả ID `5001` và các từ mẫu | Báo lỗi, có thử lại; chỉ hiển thị phiên lấy được từ BE. |
| `finishSession` trả cố định thắng, 100 điểm, streak 1 | Kết quả lấy từ submit attempt hoặc game history thật. Không có POST finish giả. |
| Một số nhánh lỗi tự gán thắng/streak 3 hoặc thua/streak 0 | Dùng `sessionWon`, điểm và streak BE trả; không tự tạo kết quả. |
| POST attempt lỗi nhưng ghép đủ ký tự vẫn tự chấm đúng/cộng 10 điểm | Không chấm ở FE khi request lỗi. Yêu cầu đồng bộ phiên trước khi được gửi lượt mới. |
| Cộng điểm cục bộ từ các lượt | Cập nhật điểm tuyệt đối theo `totalScore` của server. |
| Tự kết thúc theo vị trí câu cuối trong mảng | Dùng `sessionCompleted`/`sessionWon`, kể cả BE kết thúc thua sớm. |
| Chuyển câu theo index liên tiếp | Dùng `nextVocabularyId`; nếu không xác định được thì đồng bộ lại. |
| Mặc định 3 lượt bất kể cấu hình/tiến độ | Lấy cấu hình snapshot, lượt đã dùng và lượt còn lại từ API active. |
| Bấm chơi lại chỉ reset index/điểm trên UI | Tạo phiên BE mới và sử dụng session ID mới. |
| Mọi lỗi CHECKPOINT đều thử đổi sang FREE_PLAY | Giữ mode đã chọn và báo lỗi điều kiện từ BE. |
| Lỗi đọc active bị hiểu thành không có phiên | Để lỗi truyền lên UI; chỉ response rỗng/204 mới được hiểu là không có phiên. |

### 7.2. Resume, mất kết nối và chống thao tác trùng

- Sau khi POST tạo phiên, gọi GET active để nhận cấu hình và tiến độ đã chốt của phiên đó.
- Resume chọn đúng `currentVocabularyId`, không tự đoán câu đầu hoặc topic mặc định số 1.
- Khôi phục đúng lượt còn lại, điểm và trạng thái từng câu.
- Timeout POST có thể xảy ra sau khi BE đã chấm. FE đọc lại active/history trước khi cho gửi lượt tiếp theo.
- Quá trình recovery không tự tạo phiên mới để che mất phiên vừa hoàn tất.
- Khóa gửi lặp trong lúc request và thời gian phản hồi trước khi chuyển câu.
- Khóa thoát trong lúc POST attempt và chuyển câu, tránh DELETE đụng POST đang chạy.
- Dọn timer khi unmount; kiểm tra component còn tồn tại trước khi xử lý kết quả bất đồng bộ hoặc điều hướng muộn.
- Kiểm tra tile/slot hợp lệ, tránh dùng trùng cùng một khối chữ khi kéo/thả.

### 7.3. Streak và trang tiến trình

| Vấn đề cũ | Đã sửa |
|---|---|
| Header đọc `user.currentStreak` trong khi `/users/me` không có field này | Gọi `GET /api/users/me/streak` qua hook query dùng chung. |
| Lỗi thống kê bị thay bằng số 0 hoặc độ chính xác 100% | Mỗi khu có loading/error/retry, không dựng thống kê giả khi lỗi. |
| FE đọc lịch sử theo `items`, `totalItems`, `won`, `finishedAt` | Dùng đúng `content`, `totalElements`, `isWon`, `endedAt`. |
| Thắng/điểm/streak không được làm mới sau chơi | Invalidate query tiến trình/streak và chủ đề để tải lại dữ liệu liên quan. |

API tiến trình đã sử dụng:

- `GET /api/users/me/streak`.
- `GET /api/users/me/game-statistics`.
- `GET /api/users/me/progress`.
- `GET /api/users/me/game-history`.
- `GET /api/users/me/game-history/{sessionId}` để đọc kết quả phiên đã kết thúc.

Code cũng có lệnh invalidate key `leaderboard`, nhưng trang xếp hạng hiện vẫn tải bằng `useEffect`/state thay vì query đó. Chưa có cơ sở khẳng định màn xếp hạng đang mở tự cập nhật ngay sau khi chơi.

### 7.4. Danh sách chủ đề và chọn mode

- Bỏ danh sách `FALLBACK_TOPICS` cùng ID/tiến trình mẫu dùng khi API lỗi.
- API trả danh sách rỗng thì hiển thị rỗng; không tự thay bằng 10 topic mặc định.
- Lỗi API có thông báo và nút tải lại.
- Ghép tiến trình theo `topicId` thật từ `/users/me/progress`; không coi DTO metadata `/topics` là có sẵn số từ đã học.
- Thiếu dữ liệu tiến trình thì ẩn số chưa có, không dựng tiến trình giả.
- Bổ sung lựa chọn FREE_PLAY/CHECKPOINT/REVIEW, giữ mode trong URL và request.
- Nếu topic có phiên đang chơi, tiếp tục đúng session ID/mode tương ứng.

**Nghiệp vụ BE đã đối chiếu, không phải FE tự đặt:** FREE_PLAY và REVIEW không cộng điểm checkpoint hoặc tăng streak. Streak chỉ tăng khi thắng CHECKPOINT đủ 5 câu và cấu hình phiên cho phép. CHECKPOINT yêu cầu học đủ nhóm từ; REVIEW cần mở khóa. FE hiển thị/giữ các điều kiện đó, không tự cộng streak theo số lần thắng.

**File chính:** [gameSessionApi.ts](D:/AWS/english-learn-game-FE-main/src/features/game/api/gameSessionApi.ts), [loadGameRoute.ts](D:/AWS/english-learn-game-FE-main/src/features/game/api/loadGameRoute.ts), [gameSession.types.ts](D:/AWS/english-learn-game-FE-main/src/features/game/types/gameSession.types.ts), [GameSessionPage.tsx](D:/AWS/english-learn-game-FE-main/src/features/game/pages/GameSessionPage.tsx), [GameControlBar.tsx](D:/AWS/english-learn-game-FE-main/src/features/game/components/GameControlBar.tsx), [GameResultModal.tsx](D:/AWS/english-learn-game-FE-main/src/features/game/components/GameResultModal.tsx), [progressApi.ts](D:/AWS/english-learn-game-FE-main/src/features/progress/api/progressApi.ts), [useMyStreak.ts](D:/AWS/english-learn-game-FE-main/src/features/progress/hooks/useMyStreak.ts), [ProgressPage.tsx](D:/AWS/english-learn-game-FE-main/src/features/progress/pages/ProgressPage.tsx), [Header.tsx](D:/AWS/english-learn-game-FE-main/src/components/layout/Header.tsx), [useTopics.ts](D:/AWS/english-learn-game-FE-main/src/features/topics/hooks/useTopics.ts), [topicApi.ts](D:/AWS/english-learn-game-FE-main/src/features/topics/api/topicApi.ts), [topic.types.ts](D:/AWS/english-learn-game-FE-main/src/features/topics/types/topic.types.ts), [TopicListPage.tsx](D:/AWS/english-learn-game-FE-main/src/features/topics/pages/TopicListPage.tsx).

## 8. Sửa chuyển chuột ↔ cử chỉ tay và vòng đời camera

### Nguyên nhân đã tìm được

Khi ở chế độ chuột, các component preview/canvas từng trả về `null`. Khi bấm chuyển sang cử chỉ tay, hook cần video/canvas refs nhưng các node đã bị unmount nên không khởi động được. Thu nhỏ preview cũng từng gỡ video.

### Các chỉnh sửa

- Giữ video/canvas luôn mounted khi chuyển mode hoặc thu nhỏ; chỉ thay trạng thái hiển thị.
- Bổ sung trạng thái đang bật camera để tránh bấm khởi động trùng.
- Hiển thị lỗi cấp quyền, không tìm thấy camera, camera bị chiếm hoặc lỗi tải mô hình; có nút thử lại.
- Nếu bật camera thất bại, quay về chuột/cảm ứng và giữ thông báo lỗi.
- Dùng generation/cancellation để bỏ kết quả startup cũ khi đã chuyển mode/rời màn hình.
- Dừng cả stream được cấp muộn sau khi đã hủy; dọn worker và vòng lặp khi dừng.
- Kết thúc phiên chuyển về chuột để dừng camera, không chỉ ẩn preview sau modal kết quả.
- Bỏ phụ thuộc của hook input vào API public config `/game-configs/public` không tồn tại trong BE đang đối chiếu. Các file config cũ có thể còn trong source nhưng không còn được hook này gọi để đổi mode.

**File chính:** [useInputController.ts](D:/AWS/english-learn-game-FE-main/src/features/game/input/hooks/useInputController.ts), [GestureInputController.ts](D:/AWS/english-learn-game-FE-main/src/features/game/input/controllers/GestureInputController.ts), [CameraPreview.tsx](D:/AWS/english-learn-game-FE-main/src/features/game/input/components/CameraPreview.tsx), [HandCursorOverlay.tsx](D:/AWS/english-learn-game-FE-main/src/features/game/input/components/HandCursorOverlay.tsx), [GameSessionHeader.tsx](D:/AWS/english-learn-game-FE-main/src/features/game/components/GameSessionHeader.tsx), [GameSessionPage.tsx](D:/AWS/english-learn-game-FE-main/src/features/game/pages/GameSessionPage.tsx).

## 9. Cấu hình/phần BE liên quan — tách riêng với sửa FE

Các chỉnh sửa cấu hình BE phục vụ tích hợp đã ghi nhận:

1. Mặc định CORS cho hai origin local 3000/5173.
2. Giới hạn multipart `spring.servlet.multipart.max-file-size=2MB`, `spring.servlet.multipart.max-request-size=3MB` để khớp upload ảnh Admin.

Các cấu hình trên liên quan [application.properties](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/resources/application.properties), từng được ghi nhận là gitignored; môi trường khác cần nhận cấu hình phù hợp và restart BE.

BE audio là đầu vào được FE sử dụng: trả `audioUrl` khi tạo/resume game, lưu audio key trong snapshot, ký lại URL, xử lý phiên cũ và dùng key UUID để tránh ghi đè file. Chi tiết đã có trong [GAME_LEARNING_AUDIO.md](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/docs/GAME_LEARNING_AUDIO.md). Đây là cơ chế BE, không tính thành phần UI do FE tạo.

Không gộp toàn bộ code BE đã có từ trước như Cognito, audit trigger, IAM hoặc quy tắc tính điểm/streak vào danh sách “FE vừa triển khai”. Riêng đợt sửa game/streak/input cuối không thay đổi BE.

## 10. Kết quả kiểm tra đã ghi nhận

| Mốc | Kết quả tại lần bàn giao |
|---|---|
| Admin — 03/11/2026 | Build TypeScript/Vite thành công; **94 test / 9 file** đạt. |
| UI — 03/11/2026 | Build thành công; **102 test / 11 file** đạt; kiểm tra responsive/menu như mục 6. |
| Game/streak/input — 04/11/2026 | Trên đúng FE đang dùng: **162 test / 17 file** đạt; build thành công; lint không lỗi. |

Đợt cuối còn một cảnh báo lint cũ ở `useVocabularyAudio.ts` về dependency `identity`; build có cảnh báo JS chunk lớn hơn 500 kB. Không có xác nhận đã tối ưu bundle trong các đợt trên.

Các nhóm kiểm tra đã bổ sung/mở rộng gồm:

- Auth/client: JWT trên request public, refresh đồng thời, hết phiên, blocked, Sync, multipart, 204, lỗi DTO/text/field.
- Admin: guard/quyền, biểu mẫu, config, lỗi đồng bộ User, audit và audio.
- Audio: đúng URL/ID, thiếu audio, dừng audio cũ, renewal một lần và response cũ.
- Game: lỗi POST không tự chấm đúng; đúng lượt/điểm/câu tiếp theo từ server; kết thúc thua sớm; recovery sau timeout; chống gửi trùng; chơi lại dùng session mới; khóa thoát khi POST/transition.
- Input: giữ refs khi đổi mode/thu nhỏ, startup bị hủy, stream đến muộn, lỗi worker và cleanup.
- Progress/Topics/Header: streak thật, đúng DTO lịch sử, không fallback số/topic giả, chọn mode và resume đúng ID.
- UI: menu bàn phím, nhãn/lỗi/ARIA và biểu mẫu.

Đây chủ yếu là test dùng API/Audio/controller/DOM giả lập, không thay thế nghiệm thu thật với Cognito, DB, S3, Polly, dịch vụ từ điển hoặc camera vật lý. Không chạy mutation production để kiểm tra các phần trên.

## 11. Phần chưa thực hiện hoặc chưa xác nhận

| Nội dung | Trạng thái chính xác |
|---|---|
| Màn học từ vựng riêng cho Learner | Chưa có route/UI riêng nối API learning trong FE hiện tại. |
| Camera vật lý, cấp quyền trình duyệt trên máy bạn | Chưa kiểm thử end-to-end; đã có test controller và lifecycle giả lập. |
| Toàn bộ 21 endpoint Admin với DB/AWS thật | Chưa nghiệm thu đầy đủ. |
| Toàn bộ file MP3 thật/S3 URL thật | Chưa xác nhận mọi file tồn tại và mở được. |
| Đổi role hoặc tạo Admin từ FE | Không triển khai. |
| Restore Vocabulary, upload MP3 thủ công, sửa thứ tự thủ công, clear mô tả Topic | Chưa có luồng hỗ trợ trong đợt Admin này. |
| Backfill audio có job ID/phần trăm tiến độ | Không có contract đó; FE không dựng phần trăm giả. |
| Xóa hết mọi dữ liệu hardcode toàn website | Không có xác nhận này. Đã bỏ fallback giả trong game/progress/topics; trang chủ vẫn có nội dung/card/số minh họa tĩnh. |
| Deploy production, đổi IAM/role, tạo dữ liệu/tài khoản production | Không thực hiện trong các đợt FE nêu trên. |
| Chạy server trong lần tạo báo cáo 10/11 | Không chạy. |

Các lỗi 401/403 vẫn có thể xuất hiện hợp lệ khi token hết hạn, tài khoản bị khóa, không đủ quyền hoặc cấu hình môi trường chưa đúng. Các sửa đổi trên xử lý những nguyên nhân đã tìm được trong FE và cấu hình tích hợp, không bỏ kiểm tra xác thực của BE.

## 12. Tài liệu bàn giao và sao lưu

### Tài liệu đã tạo trong quá trình làm

| Tài liệu | Nội dung |
|---|---|
| [AUTH_FIX.md](D:/AWS/english-learn-game-FE-main/AUTH_FIX.md) | Auth, proxy, CORS và chạy local. |
| [FRONTEND_ADMIN_IMPLEMENTATION_PLAN.md](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/docs/FRONTEND_ADMIN_IMPLEMENTATION_PLAN.md) | Phân tích và kế hoạch Admin trước khi triển khai. |
| [FRONTEND_ADMIN_IMPLEMENTATION_RESULTS.md](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/docs/FRONTEND_ADMIN_IMPLEMENTATION_RESULTS.md) | Kết quả triển khai Admin và kiểm tra. |
| [ADMIN_SETUP.md](D:/AWS/english-learn-game-FE-main/ADMIN_SETUP.md) | Cách chạy, quyền Admin, cấu hình và các giới hạn nghiệp vụ. |
| [UI_UPDATE_NOTES.md](D:/AWS/english-learn-game-FE-main/UI_UPDATE_NOTES.md) | Ghi chú cập nhật UI trong FE. |
| [UI_REFRESH_RESULTS.md](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/docs/UI_REFRESH_RESULTS.md) | Kết quả UI, responsive và test. |
| [GAME_API_INPUT_FIXES.md](D:/AWS/english-learn-game-FE-main/GAME_API_INPUT_FIXES.md) | Sửa game/streak/chủ đề/input và cách kiểm tra. |

### Bản sao lưu theo từng đợt

Lịch sử bàn giao ghi nhận đã tạo backup file cũ kèm manifest/hash trước khi áp dụng:

- Admin: `D:/AWS/PJ_AWS_1/aws-workshop-main/backend/target/active-fe-admin-backup-20261103-145424`.
- UI: `D:/AWS/PJ_AWS_1/aws-workshop-main/backend/target/active-fe-ui-backup-20261103-152008`.
- Game/API/input: `D:/AWS/PJ_AWS_1/aws-workshop-main/backend/target/fe-game-api-backup-20261104-104854`; áp dụng 28 file, có kiểm tra hash và không xung đột với baseline lúc áp dụng.

**Kiểm tra ngày 10/11/2026:** ba thư mục backup trên hiện không còn ở các đường dẫn đó. Vì vậy báo cáo chỉ ghi nhận chúng từng được tạo, không khẳng định còn dùng được để khôi phục. Cần dùng lịch sử Git hoặc bản sao khác đang còn nếu muốn rollback; không coi thư mục build `target` là nơi lưu backup lâu dài.

## 13. Tự kiểm tra sau khi chạy lại dự án

Tại `D:/AWS/english-learn-game-FE-main`:

```powershell
npm run dev
```

Các lệnh kiểm tra source khi cần:

```powershell
npm test
npm run build
npm run lint
```

Các luồng cần kiểm tra thực tế với tài khoản/dữ liệu test:

1. Đăng ký → OTP → đăng nhập → reload → logout.
2. Learner không truy cập được Admin; Admin ACTIVE vào được các màn quản trị.
3. Nút loa đọc đúng từ, chuyển câu đổi audio, thiếu URL không gây crash.
4. Chuyển chuột → webcam → chuột, thu nhỏ preview rồi bật lại; rời game phải dừng camera.
5. Chọn đúng mode; so win/score/streak trong UI với response BE.
6. Mất kết nối khi submit → đồng bộ → tiếp tục đúng lượt/câu, không tự cộng điểm.
7. Hoàn tất → chơi lại phải dùng phiên BE mới; Header/Trang tiến trình được làm mới.

Lần tổng hợp này chỉ tạo tài liệu, không sửa thêm source ứng dụng, không đổi dữ liệu và không khởi động dev server.
