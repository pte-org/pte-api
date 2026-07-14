# Phase 1: Update Frontend UI & Logic

## Objective
Thay đổi cách thu thập `correctAnswers` từ việc tick checkbox sang việc người dùng tự nhập một chuỗi (ví dụ: cách nhau bởi phẩy) để chủ động kiểm soát thứ tự.

## Changes

1. **`index.html`**:
   - Gỡ bỏ cột chứa Checkbox "Correct" trong danh sách Option.
   - Thêm một thẻ `<div class="form-group">` mới bên dưới khối Options:
     - **Label**: `Correct Answers (comma-separated, in precise order)`
     - **Input**: `<input type="text" id="qCorrectAnswers" placeholder="Option 3, Option 1, Option 2">`
   
2. **`app.js`**:
   - Trong hàm `renderOptions()`: Xóa bỏ nút Checkbox và class `checkbox-wrap` vì không còn cần dùng để đánh dấu đúng sai.
   - Xóa hàm `toggleCorrect()`.
   - Trong `questionForm.addEventListener`:
     - Bỏ logic tự tính `correctAnswers` bằng `options.filter(opt => opt.isCorrect)...`.
     - Thay vào đó, đọc giá trị từ `document.getElementById('qCorrectAnswers').value`, split theo dấu phẩy (`,`) và `trim()` để tạo thành một mảng chuỗi (`List<String>`).
     - Đưa mảng này thẳng vào payload: `correctAnswers: parsedCorrectAnswers`.
     - Sửa lại logic Validate: Kiểm tra mảng `parsedCorrectAnswers` phải có độ dài lớn hơn 0.

## Validation
- Nhập options là "A", "B", "C".
- Nhập correctAnswers là "C, A".
- Submit và kiểm tra Request Payload gửi đi có `correctAnswers: ["C", "A"]` đúng thứ tự hay không.
