# Phase 1: API Client

## Requirements
Thêm request functions và TS types cho support ticket vào `packages/api-client` để cả tenant-web và vendor-web đều dùng được.

## Steps

1. Tạo `packages/api-client/src/types/support/index.ts`:
   - `TicketCategory`: `'BUG' | 'CONTENT_COMPLAINT' | 'GENERAL_FEEDBACK'`
   - `TicketStatus`: `'OPEN' | 'IN_PROGRESS' | 'RESOLVED'`
   - `TicketEntityType`: `'EXAM_SESSION' | 'EXAM_ATTEMPT' | 'QUESTION'`
   - `SupportTicketNoteResponse`: `{ publicId, adminPublicId, content, createdAt }`
   - `SupportTicketResponse`: full response với `notes: SupportTicketNoteResponse[]`
   - `SupportTicketSummaryResponse`: no notes
   - `SubmitTicketRequest`: `{ category, description, entityType?, entityId? }`
   - `UpdateTicketStatusRequest`: `{ status: TicketStatus }`
   - `AddNoteRequest`: `{ content: string }`
   - `SupportTicketListParams`: `{ status?, category?, page?, size? }`
   - `AdminSupportTicketListParams`: `{ status?, category?, tenantId?, page?, size? }`

2. Tạo `packages/api-client/src/requests/support/tickets.ts`:
   - `submitTicket(client, body: SubmitTicketRequest): Promise<SupportTicketResponse>`
   - `listTickets(client, params: SupportTicketListParams): Promise<PagedResult<SupportTicketSummaryResponse>>`
   - `getTicket(client, publicId: string): Promise<SupportTicketResponse>`
   - `adminListTickets(client, params: AdminSupportTicketListParams): Promise<PagedResult<SupportTicketSummaryResponse>>`
   - `adminGetTicket(client, publicId: string): Promise<SupportTicketResponse>`
   - `adminUpdateTicketStatus(client, publicId: string, body: UpdateTicketStatusRequest): Promise<SupportTicketResponse>`
   - `adminAddNote(client, publicId: string, body: AddNoteRequest): Promise<SupportTicketResponse>`

3. Re-export từ `packages/api-client/src/requests/index.ts` và `packages/api-client/src/types/index.ts`.

## Files to Create or Modify

| File | Action |
|---|---|
| `packages/api-client/src/types/support/index.ts` | Create |
| `packages/api-client/src/requests/support/tickets.ts` | Create |
| `packages/api-client/src/requests/index.ts` | Modify — add support export |
| `packages/api-client/src/types/index.ts` | Modify — add support export |

Tất cả paths relative to `d:\FPT\9thSemester\pte\pte-web\`.

## Success Criteria
- `import { submitTicket, listTickets } from '@pte/api-client'` hoạt động từ cả 2 app
- TypeScript không báo lỗi type cho tất cả request functions
- `PagedResult` type dùng đúng shape từ backend (`{ data, meta: { page, size, total, totalPages, first, last, hasNext, hasPrevious } }`)

## Risks
- `PagedResult` type đã có sẵn trong api-client chưa? Kiểm tra trước khi define lại — tránh duplicate. Tìm trong `src/types/` xem có `Paged*` hay `Page*` type nào chưa.

## Execution Log

### Errors Encountered
- None

### Root Cause
- N/A

### Resolution
- N/A

### Test Results After Fix
- `pnpm --filter @pte/api-client exec tsc --noEmit` → 0 errors — BUILD SUCCESS
