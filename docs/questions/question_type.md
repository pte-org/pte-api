# Phân Loại Các Định Dạng Câu Hỏi (Question Types)

Tài liệu này mô tả chi tiết các loại câu hỏi (`QuestionType`) được hỗ trợ trong hệ thống thi Aptis, cách sử dụng trong từng kỹ năng và các lưu ý quan trọng về mặt giao diện (UI) cho Frontend.

## 1. MULTIPLE_CHOICE (Trắc nghiệm)
- **Mô tả:** Câu hỏi trắc nghiệm (thường là chọn 1 đáp án đúng từ nhiều lựa chọn).
- **Phạm vi sử dụng:** Cực kỳ quan trọng. Được sử dụng cho toàn bộ phần thi **Grammar & Vocabulary**, toàn bộ phần thi **Listening**, và một số câu hỏi của phần **Reading**.

## 2. FILL_IN_BLANK (Điền vào chỗ trống)
- **Mô tả:** Câu hỏi yêu cầu thí sinh hoàn thành một câu hoặc đoạn văn bằng cách chọn từ đúng vào các vị trí trống.
- **Phạm vi sử dụng:** Dùng nhiều trong phần thi **Reading** (Part 1 và Part 4).
- **💡 Lưu ý UI (Frontend):** Trong bài thi Aptis thực tế, dạng điền từ vào chỗ trống thường hiển thị dưới dạng **Dropdown Box** (khi thí sinh nhấp vào khoảng trống, màn hình sẽ xổ ra danh sách các từ để chọn), chứ **không bắt thí sinh tự gõ chữ (free-text typing)**. Do đó, Frontend cần render UI dạng Dropdown cho các vị trí `[Blank X]`.

## 3. MATCHING (Nối đáp án)
- **Mô tả:** Câu hỏi yêu cầu ghép cặp các vế với nhau sao cho phù hợp.
- **Phạm vi sử dụng:** Bắt buộc phải có cho phần thi **Reading** (và có thể Listening). Aptis sử dụng dạng này chủ yếu để:
  - Nối tiêu đề (headings) với các đoạn văn tương ứng.
  - Nối ý kiến (opinions) với đúng người nói (ví dụ: Man / Woman / Both).

## 4. ORDERING (Sắp xếp)
- **Mô tả:** Câu hỏi yêu cầu thí sinh sắp xếp lại trật tự các thành phần.
- **Phạm vi sử dụng:** Thường dùng cho các dạng bài yêu cầu thí sinh **kéo thả (drag & drop)** các câu hoặc các mục lẻ tẻ để tạo thành một đoạn văn hoàn chỉnh hoặc một danh sách có thứ tự hợp lý.

## 5. TEXT_INPUT (Nhập văn bản)
- **Mô tả:** Câu hỏi tự luận dạng văn bản, thí sinh gõ trực tiếp câu trả lời.
- **Phạm vi sử dụng:** Dùng riêng và bắt buộc cho toàn bộ phần thi **Writing** (bao gồm viết câu ngắn, viết email phản hồi, viết bài luận).
- **💡 Lưu ý UI (Frontend):** Cần có thêm tính năng **đếm số từ (word count)** hiển thị theo thời gian thực đi kèm với textbox của loại câu hỏi này.

## 6. AUDIO_RECORD (Ghi âm)
- **Mô tả:** Câu hỏi yêu cầu thí sinh thu âm trực tiếp câu trả lời bằng micro.
- **Phạm vi sử dụng:** Bắt buộc và dùng riêng cho toàn bộ phần thi **Speaking**.

---

> **📌 Note chung cho Backend & Validation:**
> Đối với các dạng bài thi tự luận mang tính chất mở như `TEXT_INPUT` (viết luận) và `AUDIO_RECORD` (nói tự do), hệ thống **có thể không có câu trả lời mẫu**. Do đó, trường `correctAnswers` có quyền bỏ trống hoặc null mà không bị lỗi Validation. Việc chấm điểm các bài này sẽ do giáo viên hoặc AI thực hiện sau.
