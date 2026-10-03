# Plan: Report Question Ticket
Status: DONE

## Session Notes
<!-- Updated by cook automatically — do not edit manually -->

**Last active:** 2026-10-03 20:45
**Phase in progress:** —
**Status:** All 3 phases complete

### Decisions made this session
- Added `sourceQuestionPublicId` as the last field in `SnapshotContentResponse.Item` to preserve backward compat of existing overloaded constructors (they all pass `null`)
- `ExamPreviewItem.sourceQuestionPublicId: string | null` added to `scheduling/index.ts`
- `TicketEntityType` now `"EXAM_SESSION" | "QUESTION"`
- `ENTITY_TYPE_LABELS` updated with `QUESTION: "Question"` in tenant-web constants
- `useReportQuestion(questionPublicId)` mutates with `description: string`, follows same `apiClient` singleton pattern as other hooks
- `reportedQuestionIds: Set<string>` tracks per-session submitted reports for idempotency (UI-only)
- `ReportQuestionModal` rendered conditionally via `reportingQuestionId !== null` (not mounted until needed)

### Next immediate action
—

## Overview
Thêm nút "!" vào từng câu hỏi trong `ExamPreviewModal` (tenant-web) để HOST_ADMIN có thể báo cáo câu hỏi có vấn đề. Khi nhấn, mở `ReportQuestionModal` tự điền sẵn `category=CONTENT_COMPLAINT`, `entityType=QUESTION`, `entityId=sourceQuestionPublicId` — admin chỉ cần nhập mô tả, ticket gửi đến PLATFORM_ADMIN kèm ID câu hỏi chính xác.

## Phases
- [x] Phase 1: Backend — Expose `sourceQuestionPublicId` trong `ExamPreviewResponse` và `SnapshotContentResponse`
- [x] Phase 2: API Client — Thêm `sourceQuestionPublicId` vào `ExamPreviewItem` type và `"QUESTION"` vào `TicketEntityType`
- [x] Phase 3: UI — Tạo `ReportQuestionModal`, hook `useReportQuestion`, và gắn nút "!" vào `ExamPreviewModal`

## Research Summary
Cả hai research report đều thống nhất chọn **Option A**: tạo `ReportQuestionModal` mỏng + hook `useReportQuestion` riêng gọi thẳng `submitTicket`. Không mở rộng `CreateTicketModal` vì hook `useSubmitTicket` hiện tại hardcode `entityType: "EXAM_SESSION"`. Giữ tách biệt để tránh rủi ro regression.

## Dependencies
- `SnapshotItem` domain entity đã có `private UUID sourceQuestionPublicId` (line 43) — backend chỉ cần expose ra DTO
- `submitTicket` API function đã có trong `packages/api-client` từ phase support-ticket-ui
- Backend API support ticket đã hoàn thiện (plans/host-support-ticket)

## Risks
- MEDIUM: `sourceQuestionPublicId` có thể null với câu hỏi cũ không có nguồn — nút "!" chỉ render khi field khác null
- LOW: `TicketEntityType` union đã được dùng ở nhiều chỗ — thêm `"QUESTION"` cần kiểm tra không có exhaustive switch bị miss
- LOW: `ExamPreviewResponse.Item` là Java record — thêm field mới có thể break deserialization nếu consumer nào đó dùng positional constructor (kiểm tra mapper)
