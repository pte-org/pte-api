package com.aptis.modules.questionbank.domain.enums;

public enum QuestionType {
    MULTIPLE_CHOICE,
    FILL_IN_BLANK,
    MATCHING,
    TEXT_INPUT,
    AUDIO_RECORD,
    ORDERING
}

// MULTIPLE_CHOICE (Trắc nghiệm chọn 1 đáp án): Cực kỳ quan trọng. Dùng cho toàn bộ phần Grammar & Vocabulary, toàn bộ phần Listening, và một số câu hỏi Đọc hiểu.
// FILL_IN_BLANK (Điền vào chỗ trống): Dùng nhiều trong phần Reading (Part 1 và Part 4).
// MATCHING (Nối đáp án): Bắt buộc phải có cho phần Reading. Aptis sử dụng dạng này để nối tiêu đề (headings) với đoạn văn, hoặc nối ý kiến (opinions) với đúng người nói.
// TEXT_INPUT (Nhập văn bản): Dùng riêng và bắt buộc cho phần thi Writing (viết câu ngắn, viết email, viết bài luận). Cần có thêm tính năng đếm số từ (word count) đi kèm type này.
// AUDIO_RECORD (Ghi âm): Bắt buộc cho phần thi Speaking.
// ORDERING (Sắp xếp): Thường dùng cho các dạng bài yêu cầu học sinh kéo thả các câu hoặc các mục để tạo thành một đoạn văn hay một danh sách có thứ tự hợp lý. 
// NOTE: Có thể không có câu trả lời mẫu (correct_answer) nếu dạng bài là tự do (ví dụ: viết luận).
// Lưu ý giao diện cho FILL_IN_BLANK: Trong bài thi Aptis thực tế, dạng điền từ vào chỗ trống thường hiển thị dưới dạng Dropdown Box (nhấp vào chỗ trống, xổ ra 3 từ để chọn) chứ không 
// bắt thí sinh tự gõ chữ (free-text typing). Vì vậy, ở phía frontend của type FILL_IN_BLANK, bạn nên hỗ trợ UI dạng dropdown.