# Âm thanh cho màn học và game

## API trả âm thanh

- `GET /api/topics/{topicId}/learning`: `vocabularies[].audioUrl` đã có sẵn.
- `POST /api/topics/{topicId}/game-sessions`: thêm `questions[].audioUrl`.
- `GET /api/topics/{topicId}/game-sessions/active`: thêm `questions[].audioUrl`.

FE hiển thị nút loa cho từng từ/câu hỏi. Khi nhấn, phát file MP3 từ `audioUrl`.
Nếu `audioUrl = null`, vô hiệu hóa nút loa; người dùng vẫn học/chơi bình thường.
Khi chuyển câu, chọn âm thanh theo `vocabularyId`, không dựa vào thứ tự từ vựng trong DB.
Response tạo game đã có danh sách câu hỏi, nên dùng `nextVocabularyId` của submit attempt
để chọn câu tiếp theo từ danh sách đó.

```javascript
async function playPronunciation(audioUrl) {
    if (!audioUrl) return;
    try {
        await new Audio(audioUrl).play();
    } catch {
        // Hiển thị thông báo không phát được và cho phép thử lại.
    }
}
```

Gọi hàm khi người dùng nhấn nút loa. FE cần dừng audio trước đó khi chuyển câu/màn hình.
Phát trực tiếp URL đã ký; không cần đính kèm JWT của backend vào request S3.

## Lưu trữ và phát âm

- Amazon Polly hiện dùng `word` tiếng Anh, giọng `JOANNA`, định dạng MP3.
- S3 lưu file; `vocabularies.audio_url` lưu object key `vocabulary-audio/...`.
- `game_sessions.question_snapshot` lưu thêm thuộc tính `audioKey` cho từng câu hỏi.
- Không lưu presigned URL vào snapshot. Mỗi lần tạo response/resume sẽ ký URL mới.
- URL hiện được cấu hình 15 phút; có thể hết hạn sớm hơn nếu credentials ký hết hạn.
  FE gọi lại API học hoặc resume để lấy URL mới, không tạo game session mới chỉ để đổi URL.
- Upload audio mới dùng key có UUID để không ghi đè file mà các snapshot cũ đang tham chiếu.
  Khi dọn file S3, phải giữ các file còn được vocabulary/snapshot sử dụng.
- Theo mapping hiện tại `question_snapshot` là JSONB: không cần thêm bảng/cột hay chạy migration.

## Tương thích với phiên cũ

Snapshot trước đây không có `audioKey` vẫn đọc được; âm thanh không phải trường bắt buộc
để xác thực một phiên. Khi resume, backend lấy các vocabulary thiếu audio trong một lần đọc.
Chỉ dùng audio hiện tại nếu ID, topic và từ tiếng Anh vẫn khớp snapshot (bỏ khoảng trắng
hai đầu, không phân biệt hoa thường). Nếu từ đã đổi, vocabulary không còn hoặc key không hợp lệ,
trả `audioUrl = null`, không phát âm thanh của một từ khác. GET không ghi lại snapshot.

Nếu DB chưa có audio, Admin có thể dùng API hiện có:

`POST /api/admin/topics/{topicId}/vocabularies/generate-missing-audio`

API này tạo file thật trên AWS và ghi DB khi được gọi. Đợt sửa code này không chạy API đó.
Key đúng định dạng không chứng minh file còn tồn tại trên S3; cần mở URL để kiểm tra thực tế.

## File đã sửa

| File | Mục đích |
|---|---|
| `game/entity/GameQuestionSnapshot.java` | Lưu key âm thanh của câu hỏi trong JSON snapshot. |
| `game/dto/GameQuestionResponse.java` | Trả `audioUrl` khi tạo phiên. |
| `game/dto/GameResumeQuestionResponse.java` | Trả `audioUrl` khi khôi phục phiên. |
| `game/service/impl/GameSessionServiceImpl.java` | Lưu key, ký URL, xử lý snapshot cũ theo đúng từ. |
| `storage/service/impl/S3StorageServiceImpl.java` | Dùng UUID trong key của audio mới để tránh ghi đè. |

Phần học đã có mapping audio nên không cần sửa production code ở `LearningServiceImpl`.
Các đường dẫn trong bảng được tính từ `src/main/java/com/aws/alphabetuniverse/`.

## Kiểm tra

Test riêng cho audio dùng repository/S3 client giả lập, presign cục bộ bằng credentials thử nghiệm;
không kết nối AWS hay Supabase. Có kiểm tra tạo/resume, giữ tiến độ, JSON snapshot cũ/mới,
không phát sai từ sau khi Admin sửa, dữ liệu thiếu audio, URL được ký lại và key không ghi đè.

Kết quả ngày 03/11/2026: build thành công; 15 test âm thanh mới và 39 test Auth/Profile/Sync
hiện có đều qua (54 test, không lỗi). Đây là kiểm tra cục bộ, chưa xác nhận file thật trên S3.

Chạy lại riêng các test âm thanh từ thư mục backend:

```powershell
mvn "-Dtest=GameSessionAudioTest,S3VocabularyAudioTest,LearningAudioTest" test
```

Kiểm tra thực tế sau khi triển khai:

1. Mở màn học, tạo game và resume: URL trả về mở được MP3 đúng từ tiếng Anh.
2. FE nhấn loa, chuyển câu rồi nhấn lại: âm thanh thuộc đúng câu đang hiển thị.
3. Thiếu audio: nút loa bị vô hiệu hóa, game vẫn chạy.
4. Sau khi URL hết hạn: tải lại dữ liệu học/resume để nhận URL mới.
5. Admin sửa từ: phiên cũ dùng file đã chốt; phiên mới dùng audio mới.

Đợt sửa này chỉ hoàn thiện BE; nút loa cần được FE gắn với field `audioUrl`.
