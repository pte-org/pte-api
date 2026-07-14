# Plan: Manual Correct Answers Order

## Goal
Cho phép người dùng nhập thủ công danh sách các đáp án đúng (correctAnswers) thay vì tự động parse từ danh sách options. Mục đích là để giữ đúng thứ tự nhập của các đáp án đúng, phục vụ đối chiếu cho các câu hỏi dạng ghép câu (MATCHING) hoặc đục lỗ nhiều vị trí.

## Scope Challenge
- **Exists?**: Backend đã hỗ trợ lưu trữ đúng thứ tự (`List<String>` sang `text[]` của Postgres). Vấn đề chỉ nằm ở Frontend đang tự động filter theo thứ tự của `options`.
- **Minimum**: Thêm một ô nhập liệu (input field) riêng trên giao diện Frontend để người dùng điền trực tiếp danh sách đáp án đúng (cách nhau bởi dấu phẩy hoặc newline) và gửi nguyên list đó xuống Backend.
- **Complexity**: Fast (Chỉ sửa file HTML và JS ở Frontend `aptis-demo-fe`).

## Phases
- [Phase 1: Update Frontend UI & Logic](./phase-01-frontend-update.md)
