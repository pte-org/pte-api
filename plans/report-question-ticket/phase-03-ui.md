# Phase 3: UI

## Requirements
HOST_ADMIN thấy nút "!" bên cạnh mỗi câu hỏi có `sourceQuestionPublicId` trong `ExamPreviewModal`. Nhấn nút mở `ReportQuestionModal` — chỉ cần nhập mô tả — sau đó submit tạo ticket với đúng `category`, `entityType`, `entityId`.

## Steps

1. Thêm string constants cho modal vào `features/supportTickets/constants/index.ts`: title, description label, placeholder, cancel/submit button text, success toast message — gom vào object `REPORT_QUESTION_TEXT`.

2. Tạo hook `useReportQuestion(questionPublicId: string)` trong `features/supportTickets/api/index.ts`: gọi thẳng `submitTicket` với `{ category: "CONTENT_COMPLAINT", entityType: "QUESTION", entityId: questionPublicId }`, `onSuccess` invalidate `SUPPORT_TICKETS_QUERY_KEY` và hiện success toast.

3. Tạo `features/supportTickets/components/ReportQuestionModal.tsx`: modal mỏng với textarea mô tả (required, 1–2000 ký tự) và readonly label hiển thị question ID, dùng `useReportQuestion`.

4. Trong `features/exams/components/ExamPreviewModal.tsx`, thêm:
   - State `reportingQuestionId: string | null` — ID đang mở modal
   - State `reportedQuestionIds: Set<string>` — tập các ID đã report thành công trong session này (idempotency UI-only)
   - Nút "!" (icon button) vào mỗi `PreviewItem` — chỉ render khi `item.sourceQuestionPublicId != null`; disabled khi ID đó đã có trong `reportedQuestionIds`
   - Khi `ReportQuestionModal` báo submit thành công, thêm `questionPublicId` vào `reportedQuestionIds` và đóng modal

5. Chạy `pnpm --filter tenant-web exec tsc --noEmit` và fix lỗi type; smoke test thủ công bằng cách mở modal preview và kiểm tra nút "!" xuất hiện đúng chỗ.

## Files to Create or Modify

Tất cả paths relative to `d:\FPT\9thSemester\pte\pte-web\apps\tenant-web\`:

| File | Action |
|---|---|
| `features/supportTickets/constants/index.ts` | Modify — add `REPORT_QUESTION_TEXT` object |
| `features/supportTickets/api/index.ts` | Modify — add `useReportQuestion(questionPublicId)` hook |
| `features/supportTickets/components/ReportQuestionModal.tsx` | Create — thin modal: description textarea + readonly question ID label |
| `features/exams/components/ExamPreviewModal.tsx` | Modify — add "!" button per item (guard: `sourceQuestionPublicId != null`), wire to `ReportQuestionModal` |

## Success Criteria
- Mở `ExamPreviewModal` → item có `sourceQuestionPublicId` thấy nút "!", item không có thì không thấy
- Nhấn "!" → `ReportQuestionModal` mở, question ID hiển thị readonly
- Submit mô tả → toast success + ticket xuất hiện trong danh sách `/host/support-tickets`
- `pnpm --filter tenant-web exec tsc --noEmit` → 0 errors

## Risks
- `ExamPreviewModal` có thể render items theo nhiều dạng khác nhau (list, grid) — kiểm tra component hiện tại để đặt nút đúng chỗ không phá layout
- `useReportQuestion` không tái dùng `useSubmitTicket` — import `submitTicket` từ `@pte/api-client` và dùng `import { apiClient } from "@/lib/apiClient"` (module singleton, giống các hooks khác trong cùng file, KHÔNG phải `useApiClient()`)

## Execution Log

### Errors Encountered
- (not executed yet)

### Root Cause
- (not executed yet)

### Resolution
- (not executed yet)

### Test Results After Fix
- (not executed yet)
