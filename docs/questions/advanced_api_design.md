# Kiến Trúc Tách Biệt API (Two-Step API Architecture)
*Tài liệu dành cho Backend & Frontend Developers*

## 1. Bài toán
Khi xây dựng một hệ thống LMS/Thi trắc nghiệm, các form tạo câu hỏi thường chứa một khối dữ liệu JSON lồng nhau (Nested JSON) rất phức tạp, điển hình như:
- Meta data câu hỏi (Skill, Part, Difficulty, Tags...)
- Danh sách các lựa chọn (`options` list), mỗi lựa chọn lại chứa nội dung, trạng thái đúng/sai, giải thích...

Đồng thời, người dùng còn cần tải lên các tệp tin đa phương tiện lớn (Media Files) như âm thanh nghe (`.m4a`, `.mp3`) hoặc hình ảnh (`.png`, `.jpg`).

## 2. Hạn chế của thiết kế Cũ (One-Step / Multipart-Form)
Trước đây, hệ thống sử dụng một API duy nhất (`POST /api/v1/questions` với `Content-Type: multipart/form-data`) để vừa hứng khối JSON vừa hứng file media.

**Hậu quả:**
1. **Ác mộng Frontend**: Lập trình viên Frontend không thể sử dụng `JSON.stringify()`. Họ phải tự đóng gói toàn bộ mảng `options` thành `FormData` với cú pháp index (`options[0].content=...`), dẫn đến code rất rối rắm, dễ sinh bug.
2. **UX kém (Bottle-neck)**: Người dùng phải đợi file 10MB tải lên xong thì toàn bộ request mới đến được Backend để validate JSON. Nếu JSON bị thiếu 1 field nhỏ (như thiếu `skill`), Backend từ chối, người dùng lại phải upload lại file từ đầu.
3. **Swagger không thân thiện**: Swagger UI gặp khó khăn trong việc gửi đúng header `application/json` cho từng part riêng lẻ trong một request multipart.

---

## 3. Thiết Kế Mới: Tách biệt hoàn toàn (Two-Step Architecture)

Để giải quyết triệt để, Aptis API áp dụng chuẩn công nghiệp (Industry Standard) bằng cách tách logic upload file ra khỏi logic nghiệp vụ của Question.

### Bước 1: API Upload File Độc Lập
Ngay khi người dùng chọn file âm thanh/hình ảnh trên giao diện, Frontend sẽ lập tức gọi API này ở dạng **Background Upload**.

- **Endpoint**: `POST /api/v1/assets/upload`
- **Content-Type**: `multipart/form-data`
- **Params**:
  - `file`: (Binary) File upload lên.
  - `assetType`: Enum phân loại (e.g., `AUDIO_QUESTION`, `IMAGE_QUESTION`).
- **Response**: Trả về thông tin Asset vừa lưu, trong đó quan trọng nhất là `publicId`.

Lúc này, người dùng vẫn đang tiếp tục gõ nội dung các đáp án (không bị block UI). File được đẩy lên Cloudinary song song.

### Bước 2: API Submit Form (Thuần JSON)
Khi người dùng bấm nút "Lưu Câu Hỏi", Frontend chỉ việc gom toàn bộ data thành một cục JSON sạch sẽ và gắn cái `publicId` (lấy từ bước 1) vào field `audioAssetId` hoặc `imageAssetId`.

- **Endpoint**: `POST /api/v1/questions`
- **Content-Type**: `application/json`
- **Payload**:
  ```json
  {
    "skill": "LISTENING",
    "part": 1,
    "questionType": "MULTIPLE_CHOICE",
    "content": "Nghe đoạn hội thoại sau",
    "audioAssetId": "e5c016ea-5534-4367-b335-0c367ad612f7", 
    "difficultyLevel": "B1",
    "options": [
      {
        "content": "Đáp án A",
        "isCorrect": true,
        "orderIndex": 1
      },
      {
        "content": "Đáp án B",
        "isCorrect": false,
        "orderIndex": 2
      }
    ]
  }
  ```

## 4. Lợi ích mang lại
- **Frontend Developer Happiness**: Trở lại với `JSON.stringify` quen thuộc. Không cần đụng đến `FormData` nữa.
- **Micro-interactions (UX)**: Giao diện có thể hiển thị thanh tiến trình (progress bar) tải file độc lập. Submit câu hỏi sẽ diễn ra gần như tức thì.
- **Fail-Fast Validation**: Request tạo Question bằng JSON siêu nhẹ. Nếu JSON sai logic, Backend lập tức từ chối mà không bắt người dùng phải upload lại file 10MB kia (vì file đã nằm an toàn trên hệ thống qua AssetController rồi).
- **Reusable**: `AssetController` trở thành endpoint dùng chung cho toàn bộ ứng dụng (Upload Avatar, Upload Assignment, Upload Document) thay vì code upload bị khóa chặt bên trong `QuestionController`.
