# Tài Liệu Đặc Tả API: Quản Lý Passage (Đoạn Văn)

Tài liệu này mô tả các endpoint liên quan đến thực thể `Passage`. `Passage` được sử dụng để gom nhóm nhiều `Question` phụ thuộc chung vào một đoạn văn bản hoặc một đoạn âm thanh.

**Base URL:** `/api/v1/passages`

---

## 1. Tạo Mới Passage (Create Passage)
- **Method**: `POST`
- **Endpoint**: `/`
- **Content-Type**: `application/json`

### Payload (Request Body)
| Trường (Field) | Kiểu Dữ Liệu | Bắt buộc | Mô tả |
| :--- | :--- | :---: | :--- |
| `title` | `String` | Có | Tiêu đề của đoạn văn (VD: "Reading Passage 1: The History of Tea"). |
| `content` | `String` | Có | Nội dung đoạn văn. Hỗ trợ HTML/Markdown. |
| `assetIds` | `Array[UUID/String]` | Không | Danh sách Asset ID (hình ảnh, âm thanh). Thường dùng nếu đây là bài Nghe dài. |
| `skill` | `Enum` | Có | `READING`, `LISTENING`... |
| `part` | `Integer` | Có | Số thứ tự phần thi (VD: 3, 4). |
| `difficultyLevel`| `Enum` | Có | Độ khó: `A1`, `A2`, `B1`, `B2`, `C1`. |

### JSON Request Mẫu
```json
{
  "title": "Passage 1: Global Warming",
  "content": "Global warming is the long-term heating of Earth's climate system...",
  "assetIds": [],
  "skill": "READING",
  "part": 3,
  "difficultyLevel": "B2"
}
```

### JSON Response Mẫu (201 Created)
```json
{
  "success": true,
  "message": "Tạo Passage thành công",
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "title": "Passage 1: Global Warming",
    "content": "Global warming is the long-term heating of Earth's climate system...",
    "assets": [],
    "skill": "READING",
    "part": 3,
    "difficultyLevel": "B2",
    "createdAt": "2026-06-27T10:00:00Z",
    "updatedAt": "2026-06-27T10:00:00Z"
  }
}
```

---

## 2. Cập Nhật Passage (Update Passage)
- **Method**: `PUT`
- **Endpoint**: `/{publicId}`
- **Content-Type**: `application/json`
- **Payload**: Tương tự như API Create.

---

## 3. Lấy Chi Tiết Passage (Get Passage)
- **Method**: `GET`
- **Endpoint**: `/{publicId}`

---

## 4. Lấy Danh Sách Passage (List Passages)
- **Method**: `GET`
- **Endpoint**: `/?page=0&size=20`

---

## 5. Xóa Passage (Delete Passage)
- **Method**: `DELETE`
- **Endpoint**: `/{publicId}`

> **Cảnh báo (Cascade Delete)**: Việc gọi API này sẽ không chỉ xóa bản thân Passage, mà nó sẽ kích hoạt việc **tự động xóa toàn bộ các Câu hỏi (Question) đang tham chiếu tới nó**! Hãy cực kỳ cẩn thận khi sử dụng API này trên Frontend.
