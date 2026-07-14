# Cấu Trúc Dữ Liệu Question V4 (Flat Model)

## 1. Tổng Quan (Overview)
Trong phiên bản V4, cấu trúc dữ liệu của module Question Bank đã được thiết kế lại theo hướng **"phẳng hóa" (Flattening)**.
Các Entity liên quan trước đây là `QuestionOption` và `Passage` đã bị **loại bỏ hoàn toàn**. Mục tiêu là:
- Đơn giản hóa kiến trúc Backend (giảm số lượng Model, Repository, Service).
- Tối ưu hóa hiệu năng Database (loại bỏ hoàn toàn các thao tác JOIN phức tạp).
- Giảm tải dung lượng JSON trả về cho Frontend.

Tất cả thông tin về lựa chọn đáp án, đáp án đúng, và đoạn văn chung đều được gộp trực tiếp vào entity `Question`.

---

## 2. Thay Đổi Ở Database (Schema)
Entity `Question` giờ đây có thêm 2 trường cấu trúc mảng để lưu trực tiếp thông tin thay cho các bảng phụ:

| Trường (Field) | Kiểu DB (Postgres) | Kiểu Java | Mô tả |
| --- | --- | --- | --- |
| `options` | `text[]` | `List<String>` | Danh sách toàn bộ các lựa chọn (đáp án) sẽ hiển thị cho thí sinh. |
| `correct_answers` | `text[]` | `List<String>` | Danh sách các đáp án đúng để hệ thống chấm điểm. |
| `content` | `text` | `String` | Nội dung câu hỏi. Trong trường hợp là bài Reading, trường này chứa toàn bộ đoạn văn (Passage) và các placeholder. |

> **Lưu ý:** Việc sử dụng Postgres Array (`text[]`) giúp dữ liệu được truy xuất ngay lập tức cùng với record của `Question` mà không tốn thêm bất kỳ query hay phép JOIN nào.

---

## 3. Quy Ước Frontend - Backend (API Contract)

### 3.1 Cấu Trúc DTO API (Request / Response)
Các API tạo (`CreateQuestionRequest`), cập nhật (`UpdateQuestionRequest`) và lấy chi tiết (`QuestionResponse`) không còn sử dụng các object lồng nhau (nested objects) như trước.

**Ví dụ Payload Request/Response:**
```json
{
  "skill": "READING",
  "part": 1,
  "questionType": "MULTIPLE_CHOICE",
  "content": "Avis represents a return to [Blank 1] that has been lost in the modern era. The artist explores [Blank 2] through a subtle mix of colors.",
  "options": [
    "tradition", "chaos", "order", "confusion",
    "nature", "technology", "space", "time"
  ],
  "correctAnswers": [
    "tradition",
    "nature"
  ]
}
```

### 3.2 Cơ Chế Xử Lý Câu Hỏi Nhóm (Bài Reading có chung đoạn văn)
Thẻ `[Blank X]` sinh ra để giải quyết 2 bài toán lớn trong đề thi (đặc biệt là dạng thi như Aptis):

1. **Câu hỏi đục lỗ (Fill-in-the-blank / Dropdown in text):** Một đoạn văn dài (Grammar hoặc Reading) có nhiều chỗ trống. Thay vì phải tách thành 5 câu hỏi lắt nhắt và lặp lại đoạn văn, bạn chỉ cần tạo 1 câu hỏi duy nhất với nội dung: 
   *"Hôm nay trời [Blank 1], tôi đi [Blank 2]."*
   Frontend khi đọc được text này sẽ tự động parse (cắt chuỗi) và render ra giao diện ô trống hoặc thẻ Dropdown Select ngay tại vị trí chữ `[Blank 1]` và `[Blank 2]`.

2. **Bài tập Reading có câu hỏi chùm (Multiple Choice with Passage):** Dù không phải đục lỗ, nhưng nếu có 1 đoạn văn dùng chung cho 3 câu trắc nghiệm. Bạn có thể quy ước gộp cả đoạn văn và 3 câu hỏi vào 1 field `content`:
   *"(Đoạn văn...) \n\n Câu 1: Ý chính là gì? [Blank 1] \n Câu 2: Tác giả muốn nói gì? [Blank 2]"*

**Quy ước Map Đáp Án:**
- Vì bảng `Passage` (đoạn văn dùng chung) đã bị xóa, thẻ `[Blank X]` là cách để Backend chỉ cần trả về một mảng `options` phẳng.
- Số lượng tùy chọn cho mỗi câu hỏi sẽ được chia đều dựa trên tổng số `options`. Ví dụ: 8 `options` cho 2 khoảng trống `[Blank]` -> Mỗi `[Blank]` có 4 lựa chọn.
- Thứ tự của `options` và `correctAnswers` ánh xạ tuyến tính với thứ tự các `[Blank 1]`, `[Blank 2]` xuất hiện trong đoạn văn. Frontend sẽ làm nhiệm vụ lắp các `options` đó vào đúng vị trí cần điền/chọn trên màn hình.
   
---

## 4. Xóa Bỏ & Clean Up
Các component sau đã chính thức bị gỡ bỏ khỏi dự án trong V4:
- **Domains:** `QuestionOption.java`, `Passage.java`
- **Repositories:** `QuestionOptionRepository`, `PassageRepository`, `PassageOperations`
- **DTOs:** `OptionRequest`, `PassageRequest`, `QuestionOptionResponse`, `PassageResponse`
- **Services:** `QuestionOptionService`, `PassageService`
- **Controllers:** `QuestionOptionController`, `PassageController`
