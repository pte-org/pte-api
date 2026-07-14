# Tài Liệu Đặc Tả API: Tạo Mới Câu Hỏi (Kiến trúc Tách Biệt Asset & Option)

Tài liệu này mô tả chi tiết payload cho API tạo mới một câu hỏi (`Question`) trong hệ thống thi Aptis. Phiên bản này áp dụng kiến trúc chuẩn hóa CSDL (Database Normalization) mức độ cao: 
1. Tách biệt quản lý file đa phương tiện (`Asset`).
2. Tách biệt các lựa chọn câu trả lời thành một Class/Bảng riêng (`QuestionOption` - Quan hệ 1-N).

**Endpoint:** `POST /api/v1/questions`  
**Content-Type:** `application/json`

---

## 1. Kiến Trúc Của Class `QuestionOption` (Bảng Lựa Chọn)

Vì bạn lưu Option thành một class riêng, dữ liệu thực tế trong Database sẽ có cấu trúc như sau (Backend sẽ tự động bóc tách từ payload để lưu vào bảng này):
* `id` (UUID/Int): Khóa chính của option.
* `question_id` (UUID/Int): Khóa ngoại trỏ về bảng `Question`.
* `content` (String): Nội dung của lựa chọn (VD: "To the local cinema").
* `is_correct` (Boolean): Đánh dấu đây có phải đáp án đúng không (`true`/`false`). Việc đưa trường này vào Option giúp bạn **không cần** lưu trường `correct_answer` ở bảng `Question` nữa.
* `order_index` (Integer): Thứ tự hiển thị (A, B, C, D).

---

## 2. Cấu Trúc Payload (Request Body)

Khi Frontend gọi API tạo Question, để tối ưu số lần gọi API (Round-trip time), Frontend vẫn sẽ gửi kèm danh sách Options trong cùng một request. Backend sẽ sử dụng **Database Transaction** để lưu Question trước, lấy `question_id`, rồi lưu tiếp vào bảng Option.

### A. Nhóm Dữ Liệu Cốt Lõi (Core Content)
| Trường (Field) | Kiểu Dữ Liệu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `content` | `String` | Có | Nội dung chính của câu hỏi (VD: "He _______ to school every day."). |
| `options` | `Array[Object]`| Tùy loại | Danh sách các phương án. Backend sẽ bóc tách mảng này lưu vào class `QuestionOption`. Mỗi object bao gồm `content`, `is_correct`, `order_index`. |
| `assetIds` | `Array[UUID/String]` | Không | Danh sách các ID của Asset (hình ảnh, âm thanh) đã được tải lên trước đó thông qua Asset API. |

### B. Nhóm Phân Loại (Taxonomy)
| Trường (Field) | Kiểu Dữ Liệu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `skill` | `Enum` | Có | `GRAMMAR`, `VOCABULARY`, `LISTENING`, `READING`, `WRITING`, `SPEAKING`. |
| `part` | `Integer` | Có | Số thứ tự phần thi. VD: 1, 2, 3, 4. |
| `questionType` | `Enum` | Có | `MULTIPLE_CHOICE`, `FILL_IN_BLANK`, `MATCHING`, `DRAG_DROP`, `TEXT_INPUT`, `AUDIO_RECORD`. |
| `difficultyLevel`| `Enum` | Có | Độ khó: `A1`, `A2`, `B1`, `B2`, `C1`. |
| `topicTags` | `Array[String]` | Không | Danh sách các nhãn (tags) chủ đề để phân loại và tìm kiếm câu hỏi (VD: `["grammar", "present_perfect"]`). |

### C. Nhóm Ngữ Cảnh & Liên Kết (Context & Relation)
| Trường (Field) | Kiểu Dữ Liệu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `instruction` | `String` | Không | Lời chỉ dẫn cho thí sinh (VD: "Listen and choose..."). |
| `passageId` | `Int/UUID` | Không | Khóa ngoại trỏ về bảng `Passage`. Dùng để gom nhóm nhiều câu hỏi thuộc chung một đoạn văn Đọc/Nghe. |
| `parentId` | `Int/UUID` | Không | (Chỉ đọc/Hệ thống tự gán) Dùng trong cơ chế Versioning. Là ID của câu hỏi gốc để kết nối các phiên bản `v1`, `v2`, `v3` với nhau. Frontend thường gửi `null` khi tạo mới. |

### D. Nhóm Chấm Điểm & Cấu Hình Thi (Scoring & Config)
| Trường (Field) | Kiểu Dữ Liệu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `scoreWeight` | `Float` | Có | Trọng số điểm (thường mặc định là `1.0`). |
| `explanation` | `String` | Không | Lời giải thích tại sao đáp án lại đúng. |
| `timeLimit` | `Integer` | Không | Thời gian giới hạn làm câu hỏi (tính bằng giây). |
| `prepTime` | `Integer` | Không | Thời gian chuẩn bị trước khi ghi âm (Speaking). |
| `maxPlayCount` | `Integer` | Không | (Chỉ dành cho Listening). Giới hạn số lần thí sinh được phép nghe lại đoạn Audio (thường là 2). |
| `status` | `Enum` | Có | Trạng thái: `DRAFT`, `ACTIVE`, `ARCHIVED`. |

### E. Nhóm Dữ Liệu Hệ Thống (System/Read-only Fields)
*Các trường này Frontend KHÔNG gửi lên khi Create. Backend sẽ tự động sinh ra và trả về trong Response.*

| Trường (Field) | Kiểu Dữ Liệu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `id` | `UUID` | Có | ID công khai của câu hỏi (dùng trong endpoint get/update/delete). |
| `version` | `Integer` | Có | Số phiên bản của câu hỏi, mặc định = 1 khi tạo mới. |
| `isCurrent` | `Boolean` | Có | Đánh dấu đây có phải phiên bản mới nhất hay không. |
| `isImmutable` | `Boolean` | Có | Đánh dấu câu hỏi đã bị khóa (nằm trong đề thi chính thức) hay chưa. |
| `createdBy` | `UUID` | Có | ID của Admin/Giáo viên tạo câu hỏi. |
| `createdAt` | `DateTime` | Có | Thời gian tạo (ISO-8601). |
| `updatedAt` | `DateTime` | Có | Thời gian cập nhật gần nhất. |

---

## 3. Ví Dụ Cấu Trúc JSON (Request Payload)

Lưu ý: Do class Option đã tách riêng và tự chứa cờ `is_correct`, chúng ta đã loại bỏ trường `correct_answer` ở cấp độ Question.

```json
{
  "skill": "LISTENING",
  "part": 1,
  "questionType": "MULTIPLE_CHOICE",
  "difficultyLevel": "B1",
  
  "content": "Where are the speakers planning to go this weekend?",
  "instruction": "Listen to the conversation and choose the correct answer.",
  "passageId": null,
  "parentId": null,
  
  "assetIds": [
    "a1b2c3d4-5678-90ab-cdef-123456789012"
  ],
  
  "options": [
    {
      "content": "To the local cinema",
      "isCorrect": false,
      "orderIndex": 1
    },
    {
      "content": "To the newly opened beach resort",
      "isCorrect": true,
      "orderIndex": 2
    },
    {
      "content": "To the shopping mall",
      "isCorrect": false,
      "orderIndex": 3
    },
    {
      "content": "They decided to stay at home",
      "isCorrect": false,
      "orderIndex": 4
    }
  ],
  
  "scoreWeight": 1.0,
  "explanation": "The woman suggests checking out the new beach resort, and the man agrees.",
  
  "timeLimit": 60,
  "prepTime": 0,
  "maxPlayCount": 2,
  "topicTags": ["travel", "weekend_plans"],
  "status": "ACTIVE"
}
```

---

## 4. Phụ lục: Cơ Chế Quản Lý Phiên Bản (Data Versioning)

Để bảo vệ tính toàn vẹn của kết quả thi, hệ thống áp dụng cơ chế **Copy-on-Write Versioning** cho bảng `Question`. Cơ chế này được điều khiển bởi 4 trường dữ liệu hệ thống:

Ba trường version, isCurrent, và isImmutable tạo thành một cơ chế cực kỳ cao cấp trong các hệ thống EdTech gọi là Data Versioning (Quản lý phiên bản câu hỏi) nhằm bảo vệ tính toàn vẹn của kết quả thi.

Để hiểu tại sao cần 3 trường này, hãy xét một kịch bản rủi ro: Tháng 1, Giáo viên A tạo câu hỏi Q1 (Đáp án đúng là A). Đưa Q1 vào Đề thi Giữa kỳ. Học sinh B làm chọn A -> được 10 điểm. Tháng 2, Giáo viên A phát hiện đề bị sai, bèn vào Ngân hàng câu hỏi sửa lại đáp án đúng của Q1 thành B. Hậu quả: Nếu hệ thống ghi đè thẳng vào DB, khi xem lại lịch sử thi của Tháng 1, điểm của học sinh B sẽ tự nhiên bị tụt xuống 0 điểm một cách vô lý!

Để chống lại điều này, hệ thống áp dụng cơ chế Versioning qua 3 cờ sau:

### A. `is_immutable` (Cờ Bất Biến)
- **Hoạt động**: Khi câu hỏi mới được tạo (chưa ai thi), `is_immutable = false`. Nhưng ngay khi câu hỏi này được gán vào một Đề thi chính thức (Active Exam), hệ thống sẽ tự động khóa nó lại (`is_immutable = true`).
- **Ý nghĩa**: Bất kỳ hành động sửa đổi (UPDATE) nào lên một câu hỏi đã bị khóa sẽ không được phép ghi đè trực tiếp lên record cũ trong Database, nhằm tránh làm sai lệch điểm số lịch sử của thí sinh đã thi.

### B. `version` (Số Phiên Bản)
- **Hoạt động**: Nếu câu hỏi đang bị khóa (`is_immutable = true`) mà Admin vẫn thực hiện API Update, Backend sẽ giữ nguyên bản cũ (ví dụ `version = 1`), và tự động **nhân bản (clone)** ra một dòng record mới với các nội dung vừa sửa, đánh số `version = 2`.
- **Ý nghĩa**: Đảm bảo lịch sử thi cũ vẫn trỏ vào bản v1, trong khi các đề thi tạo mới từ nay về sau sẽ dùng bản v2.

### C. `is_current` (Cờ Phiên Bản Hiện Tại)
- **Hoạt động**: Trong một vòng đời nhân bản (v1 -> v2 -> v3), chỉ có **duy nhất bản mới nhất** được đánh cờ `is_current = true`, các bản cũ sẽ tự động bị set về `false`.
- **Ý nghĩa**: Giúp UI của Backend/Admin khi gọi API List Questions sẽ không bị hiển thị trùng lặp. Mặc định API GET list sẽ luôn tự động chèn thêm điều kiện `WHERE is_current = true`.

### D. `parent_id` (Dây Rốn Phiên Bản / Root ID)
- **Hoạt động**: Khi sinh ra các bản sao (v2, v3), hệ thống cần biết chúng đều bắt nguồn từ một câu hỏi gốc. Do đó, `parent_id` của v2, v3 sẽ luôn trỏ về **ID của bản v1 (Root ID)**.
- **Ý nghĩa**: Nhờ có `parent_id`, Backend có thể dễ dàng truy xuất lịch sử thay đổi của một câu hỏi, hoặc tìm ra tất cả các "anh em" của nó để tắt cờ `is_current = false` khi có phiên bản mới hơn ra đời.
