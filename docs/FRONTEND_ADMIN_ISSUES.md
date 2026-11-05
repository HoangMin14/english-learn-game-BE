# Alphabet Universe — Issues triển khai Frontend Admin V1

Ngày đối chiếu: **03/11/2026**. Đối tượng sử dụng: thành viên FE triển khai giao diện quản trị.

## 1. Phạm vi và kết quả kiểm tra backend

- Đã đối chiếu **21 endpoint Admin** qua Controller, DTO, Service, Repository và cấu hình Security/CORS trong source hiện tại.
- Đã chạy **43 test**, tất cả thành công: đổi trạng thái User, tích hợp service Cognito giả lập, Login bị khóa, filter User BLOCKED và CORS. Các test dùng mock, không thay đổi AWS/Supabase.
- Đây là kiểm tra contract từ source và các test nêu trên; **chưa phải xác nhận chạy thực tế đủ 21 endpoint với DB, S3, Polly, Merriam-Webster và IAM thật**.
- Tài liệu gồm **13 issue FE**, có thể tách từng issue để đưa vào công cụ quản lý công việc. P0/P1 là thứ tự triển khai đề xuất.
- JSON bên dưới là dữ liệu minh họa đúng tên field/kiểu trả về trong source, không phải dữ liệu lấy từ DB. Không hardcode các ID, URL hoặc giá trị minh họa vào FE.
- Trong đợt soạn tài liệu này không sửa production code, DB hoặc cấu hình AWS.

### Danh mục endpoint Admin đã đối chiếu

| # | Method | Endpoint | Thành công | Issue FE |
|---|---|---|---|---|
| 1 | GET | `/api/admin/topics` | 200, array | FE-ADM-02 |
| 2 | POST | `/api/admin/topics` | 201, object | FE-ADM-03 |
| 3 | PUT | `/api/admin/topics/{topicId}` | 200, object | FE-ADM-03 |
| 4 | DELETE | `/api/admin/topics/{topicId}` | 204, không body | FE-ADM-04 |
| 5 | PATCH | `/api/admin/topics/{topicId}/restore` | 200, object | FE-ADM-04 |
| 6 | GET | `/api/admin/topics/{topicId}/vocabularies` | 200, array | FE-ADM-05 |
| 7 | POST | `/api/admin/topics/{topicId}/vocabularies` | 201, object | FE-ADM-06 |
| 8 | PUT | `/api/admin/topics/{topicId}/vocabularies/{vocabularyId}` | 200, object | FE-ADM-06 |
| 9 | DELETE | `/api/admin/topics/{topicId}/vocabularies/{vocabularyId}` | 204, không body | FE-ADM-05 |
| 10 | GET | `/api/admin/vocabularies/ipa` | 200, object | FE-ADM-07 |
| 11 | POST | `/api/admin/topics/{topicId}/vocabularies/generate-missing-audio` | 200, object | FE-ADM-08 |
| 12 | GET | `/api/admin/game-configs/global` | 200, array | FE-ADM-09 |
| 13 | PUT | `/api/admin/game-configs/global/{configKey}` | 200, object | FE-ADM-09 |
| 14 | GET | `/api/admin/game-configs/topics/{topicId}` | 200, array | FE-ADM-10 |
| 15 | PUT | `/api/admin/game-configs/topics/{topicId}/{configKey}` | 200, object | FE-ADM-10 |
| 16 | DELETE | `/api/admin/game-configs/topics/{topicId}/{configKey}` | 204, không body | FE-ADM-10 |
| 17 | DELETE | `/api/admin/game-configs/topics/{topicId}` | 204, không body | FE-ADM-10 |
| 18 | GET | `/api/admin/game-configs/audits` | 200, page object | FE-ADM-11 |
| 19 | GET | `/api/admin/game-configs/audits/{auditId}` | 200, object | FE-ADM-11 |
| 20 | GET | `/api/admin/users` | 200, page object | FE-ADM-12 |
| 21 | PATCH | `/api/admin/users/{userId}/status` | 200, object | FE-ADM-13 |

### Quy ước dùng chung

1. Base URL ở local: `http://localhost:8080`. Đặt base URL trong cấu hình chung của FE, không rải trong các page.
2. Tất cả endpoint Admin cần `Authorization: Bearer <accessToken>`. Dùng Cognito **access token**, không dùng ID token hay refresh token làm Bearer.
3. Chỉ User local có `role = admin` và `status = ACTIVE` được dùng màn quản trị. Quyền trên BE là lớp kiểm tra quyết định; guard FE phục vụ giao diện.
4. Backend chỉ có hai role: `admin`, `learner`. Trạng thái tài khoản: `ACTIVE`, `BLOCKED`.
5. JSON thành công hiện **không có envelope chung** như `{success, data, message}`. Có endpoint trả array, có endpoint trả object/phân trang; giữ đúng schema của từng API.
6. Với HTTP 204, không gọi `response.json()`. Với lỗi, body có thể rỗng hoặc khác format ở lớp Security/validation; có thông báo dự phòng theo HTTP status.
7. Chỉ User và Audit có phân trang phía server. Topic/Vocabulary hiện trả toàn bộ danh sách, chưa nhận `page`, `size`, `keyword`, `sort`.
8. Không dùng cache lâu dài cho URL S3 có chữ ký. Code cấu hình hạn URL 15 phút; lấy lại danh sách để nhận URL mới khi cần. URL có thể hết hạn sớm hơn nếu credentials ký hết hạn.
9. Audit dùng `Instant`, thường có `Z`/offset trong JSON. User dùng `LocalDateTime`, không có offset; cần thống nhất cách hiển thị múi giờ với BE, nhất là dữ liệu cũ. Không mặc định mọi chuỗi không có `Z` đều là giờ trình duyệt.
10. `TopicResponse` dùng **`isActive`**, còn `AdminVocabularyResponse` dùng **`active`**. Không dùng chung tên field một cách máy móc.

### Cấu trúc FE đề xuất

Đây là **cấu trúc logic đề xuất, chưa được tạo trong repo**. Giao diện mẫu hiện có là JavaScript; không giả định team đã chọn React/Vue. Nếu FE dùng framework khác, giữ ranh giới module và đổi đuôi file theo stack của team.

```text
src/
  shared/
    api/httpClient.js
    api/normalizeApiError.js
    auth/sessionStore.js
    ui/ConfirmDialog.js
    ui/AsyncState.js
    ui/Pagination.js
    ui/AudioButton.js
  admin/
    AdminLayout.js
    routes.js
    auth/AdminGuard.js
    auth/authApi.js
    topics/topicsApi.js
    topics/TopicListPage.js
    topics/TopicForm.js
    topics/TopicStatusActions.js
    vocabularies/vocabulariesApi.js
    vocabularies/VocabularyListPage.js
    vocabularies/VocabularyForm.js
    vocabularies/IpaLookupField.js
    vocabularies/AudioBackfillPanel.js
    game-configs/gameConfigsApi.js
    game-configs/GlobalConfigPage.js
    game-configs/TopicConfigPage.js
    game-configs/ConfigForm.js
    audits/auditsApi.js
    audits/AuditListPage.js
    audits/AuditFilters.js
    audits/AuditDetailPanel.js
    users/usersApi.js
    users/UserListPage.js
    users/UserFilters.js
    users/UserStatusDialog.js
```

---

## FE-ADM-01 — [P0] Khởi tạo phiên Admin, guard và HTTP client dùng chung

### Mô tả vấn đề

Cần có nền chung để mọi màn Admin xác định được người đang đăng nhập, gửi đúng token và xử lý lỗi thống nhất. Khi User bị khóa hoặc token hết hạn, giao diện phải dừng thao tác quản trị và hiển thị trạng thái phù hợp.

Luồng: Login thành công → Sync User → kiểm tra `role/status` từ BE → mở AdminLayout. Khi tải lại trang, dùng phiên hiện có để lấy thông tin User; không chỉ tin role lưu trong trình duyệt. Có loading trong lúc kiểm tra phiên, tránh nháy màn quản trị trước khi xác nhận quyền.

### Các endpoint

Các API này là **phụ thuộc Auth/User**, không tính vào 21 endpoint Admin:

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/auth/login` | Nhận token từ email/password. |
| POST | `/api/auth/sync` | Đồng bộ User local bằng access token; không có body. |
| GET | `/api/users/me` | Lấy thông tin phiên hiện tại. |
| POST | `/api/auth/refresh` | Body `{"refreshToken":"<refresh-token>"}`. |

Login gửi JSON `{"email":"admin@example.com","password":"<password>"}`. Login/Refresh không cần gửi Bearer cũ. Sync/Me/Admin cần Bearer access token.

### Lưu ý nếu có

- 401: có thể thử refresh tối đa một lần cho request được bảo vệ; dùng một refresh đang chạy chung cho các request đồng thời. Không tự refresh khi Login sai mật khẩu và không lặp vô hạn.
- 403 + `ACCOUNT_BLOCKED`: hiển thị message, xóa trạng thái đăng nhập cũ và về Login. Không tiếp tục Sync/điều hướng vào Admin.
- 403 do role learner: hiển thị không đủ quyền hoặc chuyển về khu Learner; không mặc định mọi 403 đều là tài khoản bị khóa.
- HTTP client chuẩn hóa thông báo từ `message`, `detail`, hoặc thông báo dự phòng. Không giả định Security luôn trả JSON.
- API JSON gửi `Content-Type: application/json`; FormData để trình duyệt tự đặt multipart boundary.
- CORS hiện có cấu hình origin local mặc định `http://localhost:3000,http://localhost:5173`; origin triển khai thực tế cần được BE cho phép. Không dùng `credentials: include` như một yêu cầu bắt buộc của contract hiện tại.
- Không chuyển mọi lỗi 502/503 thành lỗi sai mật khẩu hoặc hết phiên.

### File structure

```text
src/shared/api/httpClient.js
src/shared/api/normalizeApiError.js
src/shared/auth/sessionStore.js
src/admin/auth/authApi.js
src/admin/auth/AdminGuard.js
src/admin/AdminLayout.js
src/admin/routes.js
```

### Response format mong đợi

Login/Refresh 200, cùng các field:

```json
{
  "accessToken": "<access-token>",
  "idToken": "<id-token>",
  "refreshToken": "<refresh-token>",
  "expiresIn": 3600
}
```

Sync/Me 200:

```json
{
  "id": 1,
  "email": "admin@example.com",
  "displayName": "Admin",
  "role": "admin",
  "status": "ACTIVE"
}
```

Tài khoản bị khóa, HTTP 403:

```json
{
  "code": "ACCOUNT_BLOCKED",
  "message": "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."
}
```

Lỗi nghiệp vụ thông thường có thể chỉ có `{"message":"..."}`, không có `code`.

### Tiêu chí nghiệm thu

- [ ] Admin ACTIVE vào được; Learner hoặc User BLOCKED không vào được màn quản trị.
- [ ] Reload trang vẫn xác thực lại phiên đúng cách; không tin role do client tự sửa.
- [ ] 401, 403, body lỗi rỗng, HTTP 204 và lỗi mạng không làm page crash.
- [ ] Nhiều request 401 không tạo vòng lặp refresh hoặc nhiều lần refresh song song.

---

## FE-ADM-02 — [P0] Danh sách Topic cho Admin

### Mô tả vấn đề

Tạo màn danh sách Topic gồm cả Topic đang hiển thị và đã ẩn. Hiển thị ảnh, tên, mô tả, thứ tự và trạng thái; có thao tác mở danh sách từ vựng, sửa, ẩn/khôi phục và cấu hình game của Topic.

### Các endpoint

`GET /api/admin/topics` — không có query phân trang/lọc trong contract hiện tại.

### Lưu ý nếu có

- Dữ liệu trả theo `orderNo ASC, id ASC`; nếu thêm tìm kiếm/lọc thì thực hiện phía client và ghi rõ chỉ lọc trên danh sách đã tải.
- Không dùng `/api/topics` thay API Admin vì API dành người học chỉ lấy Topic active.
- Không có `GET /api/admin/topics/{topicId}`. Màn sửa lấy dữ liệu từ danh sách hiện có; khi deep-link/reload có thể tải lại danh sách và tìm theo ID.
- `imageUrl`, `iconUrl`, `description`, `orderNo` có thể cần fallback khi dữ liệu cũ thiếu. Không tự tạo URL S3 từ ID.
- `orderNo` do BE sinh, chưa có API đổi thứ tự; hiển thị read-only.

### File structure

```text
src/admin/topics/topicsApi.js
src/admin/topics/TopicListPage.js
src/shared/ui/AsyncState.js
```

### Response format mong đợi

HTTP 200, array trực tiếp; danh sách rỗng là `[]`:

```json
[
  {
    "id": 1,
    "name": "Food",
    "description": "Từ vựng về thực phẩm",
    "iconUrl": null,
    "orderNo": 1,
    "imageUrl": "https://example.test/food.jpg?signed=demo",
    "isActive": true
  },
  {
    "id": 2,
    "name": "Travel",
    "description": null,
    "iconUrl": null,
    "orderNo": 2,
    "imageUrl": null,
    "isActive": false
  }
]
```

### Tiêu chí nghiệm thu

- [ ] Phân biệt rõ trạng thái hiển thị/đã ẩn; ảnh lỗi có placeholder.
- [ ] Có loading, empty, error và nút tải lại.
- [ ] Không gọi endpoint chi tiết/phân trang/sắp xếp mà BE chưa hỗ trợ.

---

## FE-ADM-03 — [P0] Form tạo và cập nhật Topic kèm ảnh

### Mô tả vấn đề

Tạo form dùng lại cho thêm/sửa Topic. Khi tạo phải có tên và ảnh; khi sửa chỉ gửi nội dung thay đổi. Sau thành công, đồng bộ lại danh sách Topic và hiển thị dữ liệu do BE trả về.

### Các endpoint

| Method | Endpoint | Request | Thành công |
|---|---|---|---|
| POST | `/api/admin/topics` | `multipart/form-data` | 201 |
| PUT | `/api/admin/topics/{topicId}` | `multipart/form-data` | 200 |

| Field form | Tạo mới | Cập nhật |
|---|---|---|
| `name` | Bắt buộc, không trắng, tối đa 100 ký tự. | Gửi khi đổi tên; bỏ trống/null không đổi tên. |
| `description` | Tùy chọn, tối đa 1000 ký tự. | Chỉ đổi khi gửi chuỗi không trắng. |
| `image` | Bắt buộc chọn file. | Chỉ gửi khi chọn ảnh mới. |

### Lưu ý nếu có

- Dùng FormData, không gửi JSON, base64, đường dẫn file hoặc URL thay cho `image`.
- Service chấp nhận JPEG/PNG/WebP, tối đa 2 MiB. Có chênh lệch cấu hình multipart cần BE xác nhận ở phần phụ thuộc cuối tài liệu; xử lý cả HTTP 413.
- Không tự đặt header `Content-Type: multipart/form-data` khi dùng FormData trong trình duyệt.
- Tên trùng không phân biệt hoa thường trả 409; kiểm tra trùng bao gồm Topic đã ẩn.
- Source hiện chưa cho xóa mô tả Topic bằng chuỗi rỗng: khi sửa, rỗng/null nghĩa là giữ nguyên. UI không được báo đã xóa mô tả nếu thực tế BE bỏ qua field.
- PUT chỉ có trường rỗng/không có trường hợp lệ trả 400. Nếu Admin không đổi gì, giữ nút Lưu disabled hoặc đóng form mà không gửi.
- Không gửi `isActive`, `orderNo`, `iconUrl`, `imageUrl` vào form để thay đổi chúng: không nằm trong TopicRequest.

### File structure

```text
src/admin/topics/TopicForm.js
src/admin/topics/topicsApi.js
src/shared/ui/AsyncState.js
```

Ví dụ tạo FormData: `name`, `description` nếu có, và `image` là File thực tế. Với PUT chỉ append field cần sửa.

### Response format mong đợi

POST 201 / PUT 200 trả cùng schema TopicResponse:

```json
{
  "id": 3,
  "name": "Animals",
  "description": "Từ vựng về động vật",
  "iconUrl": null,
  "orderNo": 3,
  "imageUrl": "https://example.test/animals.jpg?signed=demo",
  "isActive": true
}
```

Các lỗi cần hiển thị: 400 dữ liệu/file không hợp lệ, 404 Topic không tồn tại, 409 trùng tên, 413 upload quá giới hạn, và lỗi dịch vụ ảnh/S3.

### Tiêu chí nghiệm thu

- [ ] Tạo thiếu ảnh/tên bị chặn trên form; có preview ảnh và lỗi kích thước/định dạng.
- [ ] PUT chỉ thay ảnh hoặc một field vẫn hoạt động, không ghi đè field còn lại bằng rỗng.
- [ ] Có loading và chống nhấn Lưu nhiều lần; lỗi giữ nguyên dữ liệu đang nhập.
- [ ] Kiểm tra tạo trùng với cả Topic active và đã ẩn.

---

## FE-ADM-04 — [P0] Ẩn và khôi phục Topic

### Mô tả vấn đề

Admin có thể ẩn Topic khỏi danh sách dành cho người học và khôi phục sau đó. Giao diện dùng nhãn “Ẩn chủ đề” / “Khôi phục”, kèm xác nhận và tên Topic, không mô tả đây là xóa vĩnh viễn.

### Các endpoint

| Method | Endpoint | Body | Thành công |
|---|---|---|---|
| DELETE | `/api/admin/topics/{topicId}` | Không | 204, không body |
| PATCH | `/api/admin/topics/{topicId}/restore` | Không | 200, TopicResponse |

### Lưu ý nếu có

- DELETE chỉ đặt `Topic.isActive = false`; không xóa bản ghi và không tự đổi trạng thái từng Vocabulary.
- Khôi phục Topic không khôi phục những Vocabulary đã bị xóa mềm riêng.
- Gọi ẩn/khôi phục lại cùng trạng thái hiện có không cần FE tạo trạng thái mới; tải lại dữ liệu sau thao tác.
- 404: thông báo Topic không còn tồn tại và refresh danh sách.
- Không parse JSON ở response 204.

### File structure

```text
src/admin/topics/TopicStatusActions.js
src/admin/topics/topicsApi.js
src/shared/ui/ConfirmDialog.js
```

### Response format mong đợi

DELETE thành công: HTTP 204, **body rỗng**.

PATCH khôi phục, HTTP 200:

```json
{
  "id": 2,
  "name": "Travel",
  "description": "Từ vựng về du lịch",
  "iconUrl": null,
  "orderNo": 2,
  "imageUrl": null,
  "isActive": true
}
```

### Tiêu chí nghiệm thu

- [ ] Hủy xác nhận không gửi request; đồng ý mới gọi API.
- [ ] Sau ẩn vẫn thấy Topic ở danh sách Admin với trạng thái đã ẩn.
- [ ] Khôi phục cập nhật trạng thái đúng, không báo xóa dữ liệu từ vựng.

---

## FE-ADM-05 — [P0] Danh sách Vocabulary theo Topic và xóa mềm

### Mô tả vấn đề

Từ màn Topic, Admin mở danh sách từ vựng của đúng Topic. Bảng hiển thị `word`, `meaningVi`, IPA, nút nghe phát âm, `orderNo`, trạng thái và các nút sửa/xóa. Xóa cần xác nhận và phải giữ nhận biết đây là xóa mềm.

### Các endpoint

| Method | Endpoint | Thành công |
|---|---|---|
| GET | `/api/admin/topics/{topicId}/vocabularies` | 200, array |
| DELETE | `/api/admin/topics/{topicId}/vocabularies/{vocabularyId}` | 204, không body |

### Lưu ý nếu có

- GET trả cả từ active và inactive, sắp `orderNo ASC, id ASC`, chưa có query phân trang/tìm kiếm.
- Dùng `active`, không phải `isActive`. Bộ lọc/lọc từ phía client phải phân biệt cả hai trạng thái.
- DELETE đặt `active=false`, không xóa lịch sử chơi. ID không thuộc Topic trong URL trả 404.
- **Chưa có API khôi phục Vocabulary** và PUT không nhận `active`. Không thiết kế nút “Khôi phục” hoặc gửi `active=true` qua PUT hiện tại.
- Từ đã ẩn vẫn được kiểm tra trùng khi tạo. Không hứa với Admin rằng có thể xóa rồi tạo lại cùng từ để khôi phục.
- `audioUrl`/`ipa` có thể null. Có URL thì nút loa phát file đó; thiếu URL thì vô hiệu hóa nút.
- Dừng audio khi đổi từ/rời page. Khi URL hết hạn, tải lại danh sách để lấy URL mới.

### File structure

```text
src/admin/vocabularies/VocabularyListPage.js
src/admin/vocabularies/vocabulariesApi.js
src/shared/ui/AudioButton.js
src/shared/ui/ConfirmDialog.js
```

### Response format mong đợi

GET 200, array trực tiếp; rỗng là `[]`:

```json
[
  {
    "id": 17,
    "topicId": 1,
    "topicName": "Food",
    "word": "apple",
    "meaningVi": "Táo",
    "ipa": "/ˈæpəl/",
    "audioUrl": "https://example.test/apple.mp3?signed=demo",
    "active": true,
    "orderNo": 1
  }
]
```

DELETE 204 không có JSON. Sau khi tải lại, từ vẫn có thể nằm trong GET với `active=false`.

### Tiêu chí nghiệm thu

- [ ] Đổi Topic không hiển thị nhầm kết quả request cũ trả về chậm.
- [ ] Nghe đúng từ đang chọn; audio null/lỗi không làm hỏng bảng.
- [ ] Xóa mềm và lọc inactive đúng; không gọi endpoint khôi phục chưa có.

---

## FE-ADM-06 — [P0] Form tạo và sửa Vocabulary

### Mô tả vấn đề

Admin nhập từ tiếng Anh và nghĩa tiếng Việt. BE tự tra IPA, tạo MP3 bằng Polly và upload S3 khi tạo mới hoặc khi thực sự đổi từ tiếng Anh. FE hiển thị kết quả BE trả về thay vì tự coi bản preview là dữ liệu đã lưu.

### Các endpoint

| Method | Endpoint | Request | Thành công |
|---|---|---|---|
| POST | `/api/admin/topics/{topicId}/vocabularies` | JSON | 201 |
| PUT | `/api/admin/topics/{topicId}/vocabularies/{vocabularyId}` | JSON | 200 |

Tạo mới:

```json
{"word":"apple","meaningVi":"Táo"}
```

Sửa riêng nghĩa, giữ nguyên từ tiếng Anh:

```json
{"meaningVi":"Quả táo"}
```

### Lưu ý nếu có

- Tạo mới bắt buộc cả hai field. `word` tối đa 100 ký tự; `meaningVi` tối đa 255 ký tự.
- BE trim và chuẩn hóa word thành chữ thường. Quy tắc word: `[a-z]+(?:[-'][a-z]+)*`; chấp nhận dấu nối/nháy ở giữa, không nhận khoảng trắng, số hoặc dấu tiếng Việt.
- PUT có thể gửi một hoặc hai field. Field bỏ qua/null giữ nguyên; field được gửi mà là chuỗi trắng bị 400. `{}` cũng bị 400.
- Chỉ sửa nghĩa không sinh lại IPA/audio. Gửi lại cùng word sau chuẩn hóa cũng không bắt buộc sinh lại.
- Không có field nhận `ipa`, `audioUrl`, `orderNo`, `active` trong request này. Các thông tin đó hiển thị read-only.
- 409: từ trùng trong cùng Topic, kể cả từ đã ẩn. 404 có thể là Topic/Vocabulary không tồn tại hoặc không tra được IPA; đọc message thay vì suy luận chỉ từ status.
- Tra IPA/Polly/S3 có thể chậm hoặc lỗi 502/503. Giữ dữ liệu form khi lỗi; không tự lặp POST tạo mới khi chưa biết lần trước đã lưu hay chưa.
- Sau timeout, tải lại danh sách để kiểm tra trước khi Admin nhấn tạo lại.

### File structure

```text
src/admin/vocabularies/VocabularyForm.js
src/admin/vocabularies/vocabulariesApi.js
src/admin/vocabularies/IpaLookupField.js
```

### Response format mong đợi

POST 201 / PUT 200, một object đầy đủ:

```json
{
  "id": 17,
  "topicId": 1,
  "topicName": "Food",
  "word": "apple",
  "meaningVi": "Quả táo",
  "ipa": "/ˈæpəl/",
  "audioUrl": "https://example.test/apple-new.mp3?signed=demo",
  "active": true,
  "orderNo": 1
}
```

### Tiêu chí nghiệm thu

- [ ] Tạo thiếu field, word sai định dạng, nghĩa quá dài được báo trên form.
- [ ] Sửa một field không xóa field còn lại; form không đổi gì không gửi `{}`.
- [ ] Kiểm tra 409, lỗi IPA, lỗi audio và request chậm; nút Lưu chống gửi lặp.
- [ ] IPA/audio sau lưu lấy từ response hoặc lần tải lại, không dùng dữ liệu tự dựng.

---

## FE-ADM-07 — [P1] Tra cứu IPA trên form Vocabulary

### Mô tả vấn đề

Bổ sung thao tác “Tra IPA” để Admin kiểm tra phiên âm của từ đang nhập trước khi lưu. Đây là dữ liệu xem trước, không phải một thao tác tạo/sửa Vocabulary.

### Các endpoint

`GET /api/admin/vocabularies/ipa?word=apple`

Query `word` bắt buộc. Dùng URLSearchParams/encodeURIComponent để gửi đúng dấu nháy hoặc dấu nối.

### Lưu ý nếu có

- Áp dụng cùng quy tắc word như FE-ADM-06; BE trả word đã chuẩn hóa và IPA có dấu `/.../`.
- Ưu tiên nút tra cứu rõ ràng hoặc debounce; không gửi request cho từng phím nhấn. Bỏ kết quả cũ nếu input word đã đổi trong lúc request đang chạy.
- 400: thiếu/sai word. 404: không tìm thấy IPA. 502: lỗi gọi/phân tích response bên từ điển. 503: thiếu key cấu hình hoặc từ điển giới hạn số yêu cầu.
- Không đưa Merriam-Webster API key xuống FE. FE chỉ gọi BE.
- POST/PUT Vocabulary vẫn tự tra IPA khi cần; không có tham số lưu IPA do FE tự nhập. Không hứa rằng sửa text IPA preview sẽ được lưu.

### File structure

```text
src/admin/vocabularies/IpaLookupField.js
src/admin/vocabularies/vocabulariesApi.js
```

### Response format mong đợi

HTTP 200:

```json
{
  "word": "apple",
  "ipa": "/ˈæpəl/"
}
```

Ví dụ 404:

```json
{"message":"IPA was not found for word: exampleword"}
```

### Tiêu chí nghiệm thu

- [ ] Input thay đổi không nhận nhầm kết quả IPA của từ trước.
- [ ] Không tìm thấy IPA là trạng thái hiển thị được, không làm form crash.
- [ ] Không lưu key từ điển ở client hoặc gửi trực tiếp request đến nhà cung cấp.

---

## FE-ADM-08 — [P1] Sinh audio còn thiếu cho một Topic

### Mô tả vấn đề

Trong màn Vocabulary của Topic, thêm nút “Tạo âm thanh còn thiếu”. Sau khi chạy, hiển thị tổng số cần tạo, số thành công, số lỗi và ID các từ bị lỗi. Tải lại danh sách để các nút loa nhận URL mới.

### Các endpoint

`POST /api/admin/topics/{topicId}/vocabularies/generate-missing-audio`

Không có request body; cần access token Admin.

### Lưu ý nếu có

- Chỉ xử lý Vocabulary active và có key audio thiếu/không đúng prefix `vocabulary-audio/`.
- Có key đúng định dạng không có nghĩa là file thực sự còn trên S3. API hiện không tự kiểm tra/sinh lại file bị mất nếu key đã đúng prefix.
- Backend xử lý tuần tự và có thể chậm; UI dùng trạng thái đang xử lý, không tự dựng phần trăm tiến độ theo từng từ vì API không cung cấp progress/job ID.
- **HTTP 200 vẫn có thể `failed > 0`**. Phải hiển thị cảnh báo tạo được một phần, không báo tất cả thành công.
- Khi request timeout/cancel ở FE, không kết luận BE đã dừng. Tải lại danh sách và cho Admin chủ động thử lại; không gửi lại hàng loạt tự động.
- Gọi lại bỏ qua từ đã có key hợp lệ. Đây không phải API “tạo lại toàn bộ audio”.
- Trường hợp `totalMissing = 0`: hiển thị không có từ cần tạo, không coi là lỗi.

### File structure

```text
src/admin/vocabularies/AudioBackfillPanel.js
src/admin/vocabularies/vocabulariesApi.js
src/admin/vocabularies/VocabularyListPage.js
```

### Response format mong đợi

HTTP 200, minh họa thành công một phần:

```json
{
  "topicId": 2,
  "totalMissing": 10,
  "generated": 8,
  "failed": 2,
  "failedVocabularyIds": [25, 29]
}
```

Không có từ thiếu: `totalMissing`, `generated`, `failed` đều bằng 0; `failedVocabularyIds` là `[]`.

### Tiêu chí nghiệm thu

- [ ] Trong khi chạy không cho nhấn lặp cùng Topic.
- [ ] Hiển thị đúng cả thành công toàn bộ, thành công một phần và không có từ cần xử lý.
- [ ] Có thể đối chiếu ID lỗi với bảng từ vựng; không tự đặt tên từ nếu chưa có dữ liệu.
- [ ] Tải lại bảng sau khi chạy và nghe thử đúng từ bằng URL mới.

---

## FE-ADM-09 — [P0] Xem và sửa cấu hình game mặc định

### Mô tả vấn đề

Xây dựng màn cấu hình chung của game. Mỗi dòng hiển thị key, tên dễ hiểu, giá trị, mô tả và thao tác sửa. Chỉ hiển thị các key backend đang hỗ trợ, lưu từng key và cập nhật theo response mới.

### Các endpoint

| Method | Endpoint | Thành công |
|---|---|---|
| GET | `/api/admin/game-configs/global` | 200, array |
| PUT | `/api/admin/game-configs/global/{configKey}` | 200, object |

**Chỉ có 4 key hiện được hỗ trợ:**

| configKey chính xác | Kiểu `configValue` gửi JSON | Ràng buộc BE |
|---|---|---|
| `max_attempt_per_word` | String số nguyên, ví dụ `"3"` | Từ 1 đến 2147483647. |
| `checkpoint_pass_points` | String số nguyên, ví dụ `"5"` | Từ 0 đến 2147483647. |
| `score_enabled` | String `"true"` / `"false"` | Không gửi boolean thay cho hợp đồng String. |
| `streak_enabled` | String `"true"` / `"false"` | Không gửi boolean thay cho hợp đồng String. |

Tên key là **`max_attempt_per_word`**, không phải `max_attempts_per_word`.

Ví dụ PUT `/api/admin/game-configs/global/checkpoint_pass_points`:

```json
{
  "configValue": "10",
  "description": "Điểm thưởng khi hoàn thành và thắng checkpoint"
}
```

### Lưu ý nếu có

- `description` là bắt buộc, không null/trắng. Response không có field `description` chung; mô tả mặc định là **`globalDescription`**.
- Global luôn được lưu active. Không làm công tắc tắt bản ghi global bằng `active=false`; BE từ chối 400. Muốn tắt tính điểm, đổi `score_enabled` thành chuỗi `"false"`.
- GET thành công trả 4 phần tử. Nếu thiếu global config trong DB, BE có thể trả 500; không tự điền giá trị giả rồi báo đã tải thành công.
- Các key như số câu mỗi checkpoint, audio_enabled, timeout chưa được service hỗ trợ. Không tự thêm chúng vào form để lưu qua API này.
- Giá trị cấu hình được snapshot khi tạo game session; đổi cấu hình áp dụng cho phiên mới. UI không hứa sẽ đổi điểm/lượt của phiên đang chơi.
- FE hiển thị thành công khi nhận response; không tự tạo bản ghi audit trên client.

### File structure

```text
src/admin/game-configs/gameConfigsApi.js
src/admin/game-configs/GlobalConfigPage.js
src/admin/game-configs/ConfigForm.js
```

### Response format mong đợi

GET 200 là array 4 item cùng schema. Ví dụ **một item** của GET, cũng chính là dạng object của PUT 200:

```json
{
  "topicId": null,
  "configKey": "checkpoint_pass_points",
  "globalValue": "10",
  "globalActive": true,
  "overrideValue": null,
  "overrideActive": null,
  "effectiveValue": "10",
  "source": "GLOBAL",
  "globalDescription": "Điểm thưởng khi hoàn thành và thắng checkpoint",
  "overrideDescription": null
}
```

GET bọc các object trên trong array; PUT không bọc array. 400 khi sai key/giá trị/thiếu mô tả hoặc cố tắt global.

### Tiêu chí nghiệm thu

- [ ] Số nguyên không nhận số lẻ, số âm trái quy tắc hoặc số vượt Integer.
- [ ] Switch tính điểm/streak được serialize thành chuỗi đúng; description không để trắng.
- [ ] Hiển thị đúng 4 key, phân biệt String boolean với Boolean active.
- [ ] Thông báo rõ cấu hình áp dụng cho game session mới.

---

## FE-ADM-10 — [P0] Cấu hình riêng theo Topic và reset về mặc định

### Mô tả vấn đề

Cho Admin chọn Topic, so sánh cấu hình mặc định, override đã lưu và giá trị thực tế đang áp dụng. Admin có thể tạo/sửa override, ngừng áp dụng override, reset một key hoặc reset tất cả override được hỗ trợ của Topic.

### Các endpoint

| Method | Endpoint | Thành công |
|---|---|---|
| GET | `/api/admin/game-configs/topics/{topicId}` | 200, array 4 item |
| PUT | `/api/admin/game-configs/topics/{topicId}/{configKey}` | 200, object |
| DELETE | `/api/admin/game-configs/topics/{topicId}/{configKey}` | 204, không body |
| DELETE | `/api/admin/game-configs/topics/{topicId}` | 204, không body |

PUT bắt buộc `configValue`, `active` và `description`. Bốn key/ràng buộc giá trị giống FE-ADM-09.

Ví dụ tắt tính điểm riêng cho Topic nhưng vẫn áp dụng override:

```json
{
  "configValue": "false",
  "active": true,
  "description": "Topic này không cộng điểm checkpoint"
}
```

### Lưu ý nếu có

- **`active=false` nghĩa là ngừng áp dụng override và fallback về global**, không có nghĩa là tắt tính năng.
- Muốn tắt tính năng tại Topic: gửi `configValue="false"` và `active=true`. Tách rõ công tắc “Dùng cấu hình riêng” với giá trị “Bật tính điểm/streak”.
- Kể cả `active=false`, PUT vẫn cần giá trị hợp lệ và mô tả. Không gửi rỗng để reset; reset dùng DELETE.
- `overrideActive=null`: chưa có bản ghi override. `overrideActive=false`: có bản ghi nhưng không áp dụng. Không gộp hai trạng thái nếu UI cần hiển thị dữ liệu đã lưu.
- Dùng `effectiveValue` và `source` do BE trả; không tự tính giá trị dựa vào truthy/falsy của chuỗi `"false"`.
- `source`: `TOPIC`, `GLOBAL`, hoặc `GLOBAL_INACTIVE`. Trường hợp cuối có thể có effectiveValue null; hiển thị cảnh báo cấu hình chung thay vì giả lập mặc định.
- Reset một/tất cả có hộp xác nhận. Sau 204, gọi lại GET Topic configs để lấy giá trị đang áp dụng.
- Reset tất cả hiện chỉ xóa override của 4 key được hỗ trợ; không phải xóa Topic hoặc cấu hình global.

### File structure

```text
src/admin/game-configs/TopicConfigPage.js
src/admin/game-configs/ConfigForm.js
src/admin/game-configs/gameConfigsApi.js
src/shared/ui/ConfirmDialog.js
```

### Response format mong đợi

Một object PUT 200 (GET là array 4 object cùng schema):

```json
{
  "topicId": 1,
  "configKey": "score_enabled",
  "globalValue": "true",
  "globalActive": true,
  "overrideValue": "false",
  "overrideActive": true,
  "effectiveValue": "false",
  "source": "TOPIC",
  "globalDescription": "Cho phép tính điểm",
  "overrideDescription": "Topic này không cộng điểm checkpoint"
}
```

Sau reset, GET cùng key có `overrideValue=null`, `overrideActive=null`, `overrideDescription=null`; nếu global active thì source là GLOBAL và effectiveValue bằng globalValue.

### Tiêu chí nghiệm thu

- [ ] Không nhầm “tắt override” với “tắt tính điểm/streak”.
- [ ] Đổi Topic không nhận nhầm response cũ; hiển thị đủ giá trị global/override/effective.
- [ ] Reset một key không làm mất override key khác; reset tất cả có xác nhận riêng.
- [ ] Xử lý 400 giá trị sai, 404 Topic không tồn tại, 500 global config thiếu.

---

## FE-ADM-11 — [P1] Danh sách và chi tiết lịch sử thay đổi cấu hình game

### Mô tả vấn đề

Xây dựng bảng audit giúp Admin biết ai đã đổi cấu hình nào, phạm vi nào và lúc nào. Mở chi tiết để so sánh JSON trước/sau, bao gồm thay đổi mô tả hoặc trạng thái mà oldValue/newValue chưa thể hiện hết.

### Các endpoint

| Method | Endpoint | Thành công |
|---|---|---|
| GET | `/api/admin/game-configs/audits` | 200, page object |
| GET | `/api/admin/game-configs/audits/{auditId}` | 200, detail object |

Query của danh sách:

| Param | Kiểu / ràng buộc |
|---|---|
| `page` | Số nguyên từ 0, mặc định 0. |
| `size` | 1–100, mặc định 20. |
| `scope` | GLOBAL hoặc TOPIC; bỏ param khi không lọc. |
| `topicId` | ID dương nếu có. |
| `configKey` | Chuỗi key; lọc khớp chính xác, không phải tìm gần đúng. |
| `action` | INSERT / UPDATE / DELETE. |
| `actorUserId` | ID dương nếu có. |
| `operationId` | UUID hợp lệ nếu có. |
| `changedFrom` | Instant ISO 8601, ví dụ `2026-11-02T17:00:00Z`. |
| `changedTo` | Instant ISO 8601; không nhỏ hơn changedFrom. |

Ví dụ: `/api/admin/game-configs/audits?page=0&size=20&scope=TOPIC&topicId=1&action=UPDATE`.

### Lưu ý nếu có

- Sắp xếp server: `changedAt DESC, id DESC`. Không có query sort tùy ý.
- Đổi filter/size thì đưa page về 0. Input optional rỗng thì bỏ param, không gửi `topicId=` hoặc `operationId=`.
- Khoảng thời gian hiện so sánh **>= changedFrom và <= changedTo**. Nếu UI chọn theo ngày, cần chuyển rõ đầu/cuối khoảng theo múi giờ hiển thị, không chỉ gửi chuỗi ngày không timezone.
- Khi chọn scope GLOBAL, nên bỏ topicId để tránh kết hợp lọc không có dữ liệu.
- `beforeData`, `afterData` là JSON Map, có thể null và key có thể thay đổi theo trigger DB. Render an toàn, dùng tên key thực tế; các key bên trong có thể là snake_case.
- INSERT thường không có beforeData; DELETE thường không có afterData. JSON mẫu bên dưới chỉ minh họa một lần UPDATE.
- oldValue/newValue chỉ lấy `config_value`. Nếu chỉ đổi description hoặc is_active, hai giá trị có thể bằng nhau; vẫn cần xem chi tiết.
- Reset nhiều override có thể tạo nhiều audit cùng operationId. API hiện có filter operationId, không có endpoint riêng `/operations/{operationId}`.
- Response chỉ có actorUserId, không có actorName/email; hiển thị ID hoặc tên đã có trong dữ liệu FE, không tự gọi một API User-detail chưa tồn tại. actorUserId có thể null.
- PUT/DELETE cấu hình không trả auditId/operationId; sau đổi cấu hình, tải lại audit khi mở màn này.

### File structure

```text
src/admin/audits/auditsApi.js
src/admin/audits/AuditListPage.js
src/admin/audits/AuditFilters.js
src/admin/audits/AuditDetailPanel.js
src/shared/ui/Pagination.js
```

### Response format mong đợi

Danh sách HTTP 200:

```json
{
  "content": [
    {
      "id": 101,
      "operationId": "e0520290-7d25-4915-8dab-bfe78436cf08",
      "actorUserId": 1,
      "scope": "TOPIC",
      "topicId": 1,
      "configKey": "max_attempt_per_word",
      "action": "UPDATE",
      "oldValue": "3",
      "newValue": "5",
      "changedAt": "2026-11-03T07:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Chi tiết HTTP 200:

```json
{
  "id": 101,
  "operationId": "e0520290-7d25-4915-8dab-bfe78436cf08",
  "actorUserId": 1,
  "scope": "TOPIC",
  "topicId": 1,
  "configKey": "max_attempt_per_word",
  "action": "UPDATE",
  "beforeData": {
    "topic_id": 1,
    "config_key": "max_attempt_per_word",
    "config_value": "3",
    "description": "Giới hạn cũ",
    "is_active": true
  },
  "afterData": {
    "topic_id": 1,
    "config_key": "max_attempt_per_word",
    "config_value": "5",
    "description": "Giới hạn mới",
    "is_active": true
  },
  "changedAt": "2026-11-03T07:00:00Z"
}
```

Các field bên trong beforeData/afterData trong ví dụ không phải danh sách key bắt buộc của mọi bản ghi.

### Tiêu chí nghiệm thu

- [ ] Phân trang/filter đúng, ngày được gửi kèm timezone và xử lý from > to trên form.
- [ ] Chi tiết hiển thị được INSERT/DELETE có JSON null, không crash khi thiếu key.
- [ ] Thấy được thay đổi mô tả/trạng thái dù oldValue bằng newValue.
- [ ] Không có dữ liệu là empty state; không tự sinh audit giả từ thao tác FE.

---

## FE-ADM-12 — [P0] Danh sách User, tìm kiếm và lọc

### Mô tả vấn đề

Xây dựng bảng User gồm email, tên hiển thị, role, trạng thái, ngày tạo và thao tác khóa/mở khóa cho Learner. Danh sách phải dùng phân trang/lọc server để không chỉ tìm kiếm trên một trang đang hiển thị.

### Các endpoint

`GET /api/admin/users`

| Query | Ràng buộc |
|---|---|
| `page` | Từ 0, mặc định 0. |
| `size` | 1–100, mặc định 20. |
| `keyword` | Tìm chứa chuỗi trong email hoặc displayName, không phân biệt hoa thường. |
| `role` | admin / learner; bỏ param khi chọn tất cả. |
| `status` | ACTIVE / BLOCKED; bỏ param khi chọn tất cả. |

Ví dụ: `/api/admin/users?page=0&size=20&keyword=minh&role=learner&status=ACTIVE`.

### Lưu ý nếu có

- Server sắp `createdAt DESC, id DESC`. Chưa có query sort hoặc filter theo ngày trong API này.
- Đổi keyword/role/status/size phải reset page về 0. Có thể debounce keyword; bỏ response cũ khi điều kiện tìm kiếm đã đổi.
- UI chỉ có hai role; không thêm Teacher/Moderator hoặc nút sửa role.
- User role admin không được quản lý trạng thái bằng API hiện có; ẩn/disable nút và hiển thị lý do.
- Không có `GET /api/admin/users/{userId}`, API chỉnh email/role, xóa User hoặc xuất file danh sách trong scope hiện tại.
- createdAt là chuỗi LocalDateTime không có offset. Không tự ghi nhãn “UTC” hay “giờ Việt Nam” cho mọi dữ liệu cũ trước khi chốt quy ước với BE.
- `displayName` có thể thiếu; fallback email. ID/count là JSON number từ Long; nếu dự án có giá trị vượt Number.MAX_SAFE_INTEGER, cần chốt contract string với BE trước khi xử lý bằng Number.

### File structure

```text
src/admin/users/usersApi.js
src/admin/users/UserListPage.js
src/admin/users/UserFilters.js
src/shared/ui/Pagination.js
```

### Response format mong đợi

HTTP 200:

```json
{
  "content": [
    {
      "id": 7,
      "email": "learner@example.com",
      "displayName": "Minh",
      "role": "learner",
      "status": "ACTIVE",
      "createdAt": "2026-11-01T02:00:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Không có kết quả: content `[]`, totalElements 0 và totalPages 0; vẫn xử lý page hiện tại theo response.

### Tiêu chí nghiệm thu

- [ ] Tìm kiếm/lọc gửi đến server; page UI 1 tương ứng page API 0.
- [ ] Người có role admin không hiện thao tác khóa/mở Learner.
- [ ] Sau thao tác đổi status, refetch đúng trang/bộ lọc; chấp nhận row biến mất nếu không còn khớp filter.
- [ ] 400 pagination/filter sai, 401/403 và lỗi tải danh sách được hiển thị rõ.

---

## FE-ADM-13 — [P0] Khóa/mở khóa Learner và hiển thị trạng thái đồng bộ Cognito

### Mô tả vấn đề

Admin chọn Learner → mở hộp xác nhận ghi rõ email/tên và hành động → PATCH trạng thái → cập nhật bảng bằng dữ liệu BE. User bị khóa phải nhận thông báo khi Login và không được vào màn hình chính.

Trường hợp quan trọng: AWS lỗi khi khóa, DB vẫn có thể đã commit BLOCKED. FE phải hiển thị đúng trạng thái local và cảnh báo đồng bộ, không tự đảo row về ACTIVE chỉ vì request trả lỗi.

### Các endpoint

| Method | Endpoint | Mục đích |
|---|---|---|
| PATCH | `/api/admin/users/{userId}/status` | Khóa/mở khóa. |
| GET | `/api/admin/users` | Tải lại trạng thái sau thao tác hoặc lỗi không chắc chắn. |
| POST | `/api/auth/login` | Phụ thuộc: kiểm tra UX của Learner bị khóa. |

Request khóa:

```json
{"status":"BLOCKED"}
```

Request mở khóa:

```json
{"status":"ACTIVE"}
```

### Lưu ý nếu có

- Chỉ quản lý Learner. BE từ chối tự khóa Admin (409), quản lý Admin khác (403), User không tồn tại (404), trạng thái sai/thiếu (400).
- Chỉ hiển thị “đồng bộ thành công” khi PATCH nhận 200. Response thành công không có field `cognitoSynced` hoặc `syncStatus`; không tự coi những field này tồn tại.
- Khi khóa: BE ghi BLOCKED local rồi gọi Cognito Disable; lỗi đồng bộ vẫn giữ khóa local. Khi mở khóa: Cognito Enable thành công mới đổi local ACTIVE.
- Lỗi 429/502/503 do đồng bộ không phải tín hiệu refresh JWT Admin. Hiển thị message, refetch User và giữ trạng thái thực tế trả từ DB.
- Cho phép Admin gửi lại **cùng status đã yêu cầu** để đồng bộ lại, kể cả row đang BLOCKED. Không dùng một công tắc đảo ngược status để “thử lại”.
- UI có thể giữ cảnh báo cho thao tác lỗi vừa xảy ra và nút “Thử đồng bộ lại”. Danh sách User hiện chỉ lưu trạng thái local, không cung cấp trạng thái pending AWS lâu dài sau reload trang.
- Không tự gửi lại vô hạn. Với lỗi thiếu quyền/cấu hình, hướng Admin liên hệ BE/AWS trước khi thử lại.
- Luồng Login FE: nhận ACCOUNT_BLOCKED thì hiển thị message, xóa token/state cũ, không tiếp tục Sync hoặc chuyển vào trang chính. API đang dùng cũng có thể trả lỗi này khi User đã đăng nhập bị khóa.

### File structure

```text
src/admin/users/UserStatusDialog.js
src/admin/users/usersApi.js
src/admin/users/UserListPage.js
src/shared/api/normalizeApiError.js
src/shared/auth/sessionStore.js
```

### Response format mong đợi

PATCH thành công, HTTP 200:

```json
{
  "id": 7,
  "email": "learner@example.com",
  "displayName": "Minh",
  "role": "learner",
  "status": "BLOCKED",
  "updatedAt": "2026-11-03T07:15:00"
}
```

Ví dụ lỗi đồng bộ HTTP 502; User local đã bị khóa:

```json
{
  "code": "COGNITO_ACCESS_DENIED",
  "message": "Đã khóa tài khoản trên hệ thống, nhưng chưa xác nhận khóa trên Cognito. AWS từ chối thao tác quản trị Cognito. Kiểm tra quyền IAM và credentials của backend. Sau khi xử lý nguyên nhân, gửi lại yêu cầu BLOCKED để đồng bộ."
}
```

Các code lỗi đồng bộ hiện có:

| code | Ý nghĩa cho FE |
|---|---|
| `COGNITO_ACCESS_DENIED` | AWS từ chối thao tác; BE cần kiểm tra IAM/credentials. |
| `COGNITO_USER_NOT_FOUND` | Cần kiểm tra User/sub và User Pool. |
| `COGNITO_POOL_NOT_FOUND` | Cần kiểm tra User Pool/region. |
| `COGNITO_CLIENT_ERROR` | Cần kiểm tra credentials, mạng hoặc timeout. |
| `COGNITO_RATE_LIMITED` | Yêu cầu bị giới hạn; chờ trước khi thử lại. |
| `COGNITO_IDENTITY_MISSING` | User local thiếu sub hợp lệ. |
| `COGNITO_SERVICE_ERROR` | Dịch vụ AWS báo lỗi khác. |
| `COGNITO_STATUS_SYNC_FAILED` | Lỗi đồng bộ tổng quát còn lại. |

User bị khóa gọi Login hoặc API được filter bảo vệ, HTTP 403:

```json
{
  "code": "ACCOUNT_BLOCKED",
  "message": "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên."
}
```

### Tiêu chí nghiệm thu

- [ ] Xác nhận đúng User/hành động; hủy dialog không gửi request; đang gửi không nhấn lặp.
- [ ] BLOCKED/ACTIVE thành công cập nhật đúng theo response và refetch danh sách.
- [ ] AWS lỗi lúc khóa không tự hiển thị lại ACTIVE; có thông báo và thao tác retry đúng BLOCKED.
- [ ] Learner bị khóa Login nhận thông báo và không vào web; role admin không hiện nút quản lý trái quyền.

---

## 2. Các phụ thuộc BE cần chốt trước khi nghiệm thu FE

Đây là kết quả kiểm tra cần phối hợp với BE, không phải yêu cầu FE tự sửa database hoặc gọi AWS trực tiếp.

| Mã | Điểm cần chốt | Bằng chứng từ source / ảnh hưởng FE |
|---|---|---|
| BE-DEP-01 | Giới hạn upload ảnh Topic | Service cho 2 MiB; application.properties hiện không đặt max-file-size. Metadata của Spring Boot Servlet 4.1.1 trong dependency khai báo mặc định 1MB/file, 10MB/request. Nếu runtime không override, ảnh 1–2 MiB có thể bị từ chối trước service. BE cần chốt cấu hình; FE xử lý 413 và dùng giới hạn đã thống nhất. |
| BE-DEP-02 | Khôi phục Vocabulary đã ẩn | Có soft delete nhưng chưa có restore endpoint. Kiểm tra trùng vẫn tính từ inactive. FE không tự thêm nút khôi phục; cần BE bổ sung nếu nghiệp vụ V1 yêu cầu. |
| BE-DEP-03 | Xóa mô tả Topic | PUT bỏ qua description rỗng, chưa có cơ chế clear. FE không thông báo đã xóa mô tả nếu BE không hỗ trợ. |
| BE-DEP-04 | Khóa/mở Cognito thật | Cần IAM principal/credentials BE có quyền AdminDisableUser/AdminEnableUser trên đúng pool. Test mock không xác nhận quyền thực tế; 502/503 có thể là trạng thái local đã khóa nhưng AWS chưa đồng bộ. |
| BE-DEP-05 | Global config và trigger audit | Cần đủ 4 global config, constraint/index phù hợp native upsert và trigger audit đã triển khai trên DB. Java đặt audit context; tài liệu này chưa kiểm tra trigger/schema đang chạy trên Supabase. Không có audit chưa đủ để kết luận lỗi UI. |
| BE-DEP-06 | Thống nhất body lỗi | Lỗi nghiệp vụ thường có message, blocked/sync có code; lỗi validation/Security/multipart chưa được chuẩn hóa toàn bộ. FE phải có fallback thay vì giả định fieldErrors hoặc envelope chung. |
| BE-DEP-07 | Múi giờ dữ liệu User cũ | User trả LocalDateTime không offset; các luồng mới có ghi UTC nhưng chưa xác nhận toàn bộ dữ liệu cũ. Cần quy ước hiển thị/chuyển đổi với BE. |
| BE-DEP-08 | Dịch vụ phát âm và ảnh | File S3/key từ điển/Polly cần hoạt động trên môi trường thử nghiệm. GET trả URL có chữ ký không tự chứng minh file tồn tại; cần kiểm tra mở ảnh/nghe MP3 thực tế. |

Không có API Admin dashboard thống kê tổng quan, đổi role User, upload audio thủ công, restore Vocabulary hoặc đổi orderNo trong contract đã rà. Không thiết kế thao tác ghi dữ liệu cho các chức năng này trước khi BE bổ sung. Có thể hiển thị thống kê trên tập dữ liệu đã tải nếu ghi đúng phạm vi, không gắn nhãn là số liệu toàn hệ thống.

## 3. Thứ tự triển khai và tiêu chuẩn bàn giao

1. FE-ADM-01: phiên, guard, HTTP client và xử lý lỗi chung.
2. FE-ADM-02 → 03 → 04: quản lý Topic.
3. FE-ADM-05 → 06, sau đó 07 → 08: Vocabulary, IPA và audio.
4. FE-ADM-09 → 10: cấu hình global/Topic.
5. FE-ADM-12 → 13: quản lý User và kiểm tra luồng bị khóa.
6. FE-ADM-11: audit lịch sử cấu hình.

Điều kiện chung cho mỗi issue:

- [ ] Có loading/empty/error/success phù hợp với loại dữ liệu.
- [ ] Không có ID, token, dữ liệu demo hoặc giá trị cấu hình được hardcode thay dữ liệu BE trong bản bàn giao.
- [ ] Chỉ cập nhật UI sau kết quả đã xác định; timeout hoặc lỗi đồng bộ phải phân biệt với thất bại hoàn toàn.
- [ ] Có xử lý request trả về chậm sau khi đổi filter/Topic/rời màn hình.
- [ ] Test các ca sai: không token, token hết hạn, Learner vào Admin, User BLOCKED, dữ liệu không hợp lệ, ID không tồn tại và dịch vụ ngoài lỗi.
- [ ] Các thao tác xóa/reset/khóa có xác nhận và chống gửi lặp.
- [ ] Kiểm tra bằng môi trường test và tài khoản/dữ liệu test đã thống nhất; không tự dùng dữ liệu production để thử xóa/khóa.

## 4. Nguồn backend và bằng chứng kiểm tra

Đường dẫn source đang đối chiếu trên máy hiện tại: `D:/AWS/PJ_AWS_1/aws-workshop-main/backend`.

| Nhóm | Source chính |
|---|---|
| Topic | [AdminTopicController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/topic/controller/AdminTopicController.java), [TopicServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/topic/service/impl/TopicServiceImpl.java) |
| Vocabulary | [AdminVocabularyController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/vocabulary/controller/AdminVocabularyController.java), [AdminVocabularyServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/vocabulary/service/impl/AdminVocabularyServiceImpl.java) |
| IPA | [AdminIpaLookupController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/vocabulary/controller/AdminIpaLookupController.java), [IpaLookupServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/vocabulary/service/impl/IpaLookupServiceImpl.java) |
| Game config | [AdminGameConfigController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/admin/controller/AdminGameConfigController.java), [GameConfigServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/game/service/impl/GameConfigServiceImpl.java) |
| Audit | [AdminGameConfigAuditController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/admin/controller/AdminGameConfigAuditController.java), [GameConfigAuditServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/admin/service/impl/GameConfigAuditServiceImpl.java) |
| User | [AdminUserController.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/admin/controller/AdminUserController.java), [AdminUserServiceImpl.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/admin/service/impl/AdminUserServiceImpl.java) |
| Lỗi / Security | [GlobalExceptionHandler.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/exception/GlobalExceptionHandler.java), [ActiveUserFilter.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/security/filter/ActiveUserFilter.java), [CorsConfig.java](D:/AWS/PJ_AWS_1/aws-workshop-main/backend/src/main/java/com/aws/alphabetuniverse/common/config/CorsConfig.java) |

Các JSON được đối chiếu với TopicResponse, AdminVocabularyResponse, IpaLookupResponse, AudioBackfillResponse, GameConfigAdminResponse, các DTO Audit và DTO AdminUser tương ứng. Các cấu trúc file FE ở mỗi issue là đề xuất tổ chức code, không phải đường dẫn FE đã tồn tại trong repo.

Lệnh kiểm tra đã chạy từ thư mục backend, kết quả **43 tests / 0 failures / 0 errors — BUILD SUCCESS**:

```powershell
mvn -o "-Dtest=AdminUserStatusServiceTest,CognitoUserAdminServiceImplTest,AuthLoginBlockedTest,ActiveUserBlockedFilterTest,CorsConfigTest" test
```

Không chạy full application test hoặc gửi các request ghi dữ liệu thật trong đợt kiểm tra này. Các endpoint Topic, Vocabulary, Config và Audit được kiểm tra contract bằng source; cần test tích hợp thật sau khi BE/FE thống nhất môi trường và các phụ thuộc ở mục 2.
