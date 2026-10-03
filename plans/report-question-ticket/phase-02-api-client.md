# Phase 2: API Client

## Requirements
TS types trong `packages/api-client` phản ánh đúng response mới từ backend: `ExamPreviewItem` có `sourceQuestionPublicId`, và `TicketEntityType` chấp nhận `"QUESTION"`.

## Steps

1. Tìm định nghĩa `ExamPreviewItem` (hoặc type tương đương cho item trong exam preview) trong `packages/api-client/src/types/` và thêm field `sourceQuestionPublicId: string | null`.

2. Mở `packages/api-client/src/types/support/index.ts` và thêm `"QUESTION"` vào union `TicketEntityType`.

3. Chạy `pnpm --filter @pte/api-client exec tsc --noEmit` và fix bất kỳ lỗi type nào phát sinh từ hai thay đổi trên.

## Files to Create or Modify

Tất cả paths relative to `d:\FPT\9thSemester\pte\pte-web\`:

| File | Action |
|---|---|
| `packages/api-client/src/types/exams/index.ts` | Modify — add `sourceQuestionPublicId: string \| null` to `ExamPreviewItem` (verify exact file/type name first) |
| `packages/api-client/src/types/support/index.ts` | Modify — add `"QUESTION"` to `TicketEntityType` union |
| `apps/tenant-web/features/supportTickets/constants/index.ts` | Modify — add `QUESTION: "Question"` to `ENTITY_TYPE_LABELS`; without this, `SupportTicketDetailView` will throw a TypeScript error when rendering a QUESTION-type ticket |

## Success Criteria
- `pnpm --filter @pte/api-client exec tsc --noEmit` → 0 errors
- `pnpm --filter tenant-web exec tsc --noEmit` → 0 errors (catches the `ENTITY_TYPE_LABELS` exhaustive-check)
- `ExamPreviewItem.sourceQuestionPublicId` có type `string | null` (không phải `undefined`)
- `TicketEntityType` bao gồm `"QUESTION"` trong union
- `ENTITY_TYPE_LABELS["QUESTION"]` trả về `"Question"` (không phải `undefined`)

## Risks
- `ExamPreviewItem` có thể tên khác hoặc nằm ở file khác — cần `grep` cho `ExamPreview` trong `packages/api-client/src/types/` để xác nhận trước khi sửa
- Nếu `TicketEntityType` được dùng trong exhaustive switch ở `tenant-web` hoặc `vendor-web`, thêm variant mới có thể gây lỗi TypeScript — kiểm tra sau khi thêm

## Execution Log

### Errors Encountered
- (not executed yet)

### Root Cause
- (not executed yet)

### Resolution
- (not executed yet)

### Test Results After Fix
- (not executed yet)
