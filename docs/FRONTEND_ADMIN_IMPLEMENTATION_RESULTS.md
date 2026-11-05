# Kết quả triển khai Frontend Admin V1

Ngày: 03/11/2026.

## Đã áp dụng

- FE đang dùng: `D:/AWS/english-learn-game-FE-main`.
- 33 file mới/thay đổi, gồm routes/layout/guard, API/types, các màn quản trị,
  sửa auth/client, tests và `ADMIN_SETUP.md`.
- Backup file cũ và manifest tại
  `D:/AWS/PJ_AWS_1/aws-workshop-main/backend/target/active-fe-admin-backup-20261103-145424`.
- Không thay đổi tài khoản, role, dữ liệu Supabase, trigger, IAM hoặc AWS resources.
- Backend local thêm giới hạn multipart 2MB/file, 3MB/request trong
  `src/main/resources/application.properties` để khớp giới hạn ảnh 2 MiB của service.
  File này gitignored; cần restart BE và chuyển cấu hình sang môi trường triển khai.

## Chức năng

1. AdminGuard chỉ cho role admin và status ACTIVE; profile được lấy từ BE.
2. Login yêu cầu Sync thành công trước khi báo thành công/điều hướng.
3. Client đọc code/errorCode/message/detail; xử lý ACCOUNT_BLOCKED, multipart,
   204, timeout, signal, refresh đồng thời và ngăn refresh cũ phục hồi phiên đã kết thúc.
4. Topic list/create/update/hide/restore; ảnh preview, placeholder và validate file.
5. Vocabulary list/create/update/hide; tra IPA, MP3 đúng ID, URL renewal một lần,
   backfill và báo cáo thành công một phần.
6. Global/Topic configs: bốn key, giá trị String, override, reset có xác nhận.
7. User list/filter/pagination server; khóa/mở Learner, refetch sau lỗi AWS và retry
   cùng status. Không optimistic rollback BLOCKED về ACTIVE khi request lỗi.
8. Audit list/filter/pagination/detail JSON, operationId, thời gian UTC+7.

Các API được kết nối qua `src/features/admin/api/adminApi.ts`, bao phủ 21 endpoint
Admin trong tài liệu yêu cầu. Đây là xác nhận kết nối từ code/test, chưa phải 21
endpoint đã được nghiệm thu với DB/AWS thật.

## Kiểm tra đã thực hiện

- Build TypeScript + Vite: thành công trên staging và thư mục FE đang dùng.
- Vitest toàn bộ: **94 tests passed / 9 files** trên FE đang dùng.
- Sau chỉnh sửa cuối về khởi tạo phiên, chạy lại 5 test auth liên quan: thành công.
- Lint: không lỗi. Còn 2 cảnh báo tồn tại ở `useVocabularyAudio.ts` (dependency)
  và `GameSessionPage.tsx` (setState trong effect). Phần Admin/auth mới không còn warning.
- Build vẫn có cảnh báo chunk >500 kB; chưa thực hiện tối ưu bundle ngoài phạm vi Admin.
- Kiểm tra bố cục desktop bằng HTML render từ component thật với dữ liệu mẫu
  riêng, không nối API. Không đưa mẫu QA hoặc ID mẫu vào source production.
- FE thật `/admin/topics` khi chưa đăng nhập chuyển tới
  `/login?redirect=%2Fadmin%2Ftopics`; không hiển thị dữ liệu Admin.
- Dev server đã khởi động lại tại `http://localhost:3000`.

## Cần nghiệm thu tích hợp

- Đăng nhập bằng Admin ACTIVE thật; User local phải được chuẩn bị bởi BE.
- Topic upload/hide/restore và Vocabulary CRUD trên dữ liệu test.
- Quyền Cognito Disable/Enable và trường hợp local BLOCKED nhưng AWS lỗi.
- IPA/Polly/S3: tạo từ, tạo audio thiếu, mở ảnh/nghe MP3 thật, URL ký hết hạn.
- Bốn global configs, upsert constraints và audit trigger trên DB đang chạy.
- Thống nhất timezone của User LocalDateTime cũ; UI hiện hiển thị nguyên giá trị.

Chưa có thông tin đăng nhập Admin test trong phiên này nên không gửi mutation
thật tới DB/AWS. Không tự cấp quyền hoặc tạo dữ liệu test trên production.
Hướng dẫn vận hành: `D:/AWS/english-learn-game-FE-main/ADMIN_SETUP.md`.
