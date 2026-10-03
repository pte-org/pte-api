# Brainstorm: Host Support Ticket System

**Date:** 2026-10-03

## Ideas Explored

- **Standalone ticket module (`support`)** — New domain, no coupling to existing `reporting`. Cleaner separation; `reporting` stays about score reports. Recommended direction.
- **Extend existing `reporting` module** — Reuse infrastructure but conflates score reports with support tickets. Bad coupling; dismissed.
- **External helpdesk (email, Jira, etc.)** — No in-system traceability; dismissed.
- **Notification on status change** — User considered it; deferred to later iteration.

## User's Direction

Host gửi report lên PLATFORM_ADMIN. Ba loại: (1) lỗi kỹ thuật/hệ thống, (2) khiếu nại nội dung câu hỏi, (3) phản hồi tổng quát. Flow ticket: open → in-progress → resolved. Admin có thể thêm note để host đọc. Report nên gắn với entity cụ thể (ExamSession, ExamAttempt, Question) để admin dễ truy vết.

## Open Questions

- Ai được phép submit? Chỉ HOST_ADMIN, hay cả PROCTOR / EXAMINER?
- Host có thể add thêm comment sau khi submit không, hay chỉ đọc note của admin?
- Khi admin resolve, host có thể reopen không?
- Có cần phân loại priority (low/medium/high) không?

## Risks

1. Entity reference bị stale — nếu ExamSession bị xóa, FK sẽ bị broken. Cần soft-delete hoặc nullable reference.
2. Scope creep: "note của admin" có thể mở rộng thành thread discussion — giữ đơn giản ở MVP.
3. Quyền đọc: HOST_ADMIN chỉ nên thấy ticket của tenant mình, không thấy của tenant khác.
