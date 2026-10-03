# Phase 2: tenant-web (HOST_ADMIN)

## Requirements
HOST_ADMIN có thể submit ticket mới, xem danh sách ticket của tenant mình (với filter), và xem chi tiết ticket kèm notes của admin.

## Steps

1. Tạo `features/supportTickets/types/index.ts`:
   - Re-export hoặc map từ `@pte/api-client` types sang domain types nếu cần transform
   - `SupportTicket` (domain type từ `SupportTicketSummaryResponse`)
   - `SupportTicketDetail` (domain type từ `SupportTicketResponse`, có `notes[]`)
   - `CreateTicketInput`: `{ category, description, entityType?, entityId? }`

2. Tạo `features/supportTickets/constants/index.ts`:
   - `SUPPORT_TICKETS_QUERY_KEY = ['support-tickets']`
   - `SUPPORT_TICKET_QUERY_KEY = (id: string) => ['support-tickets', id]`
   - `STATUS_LABELS`: `{ OPEN: 'Mở', IN_PROGRESS: 'Đang xử lý', RESOLVED: 'Đã giải quyết' }`
   - `STATUS_VARIANTS`: `{ OPEN: 'neutral', IN_PROGRESS: 'warning', RESOLVED: 'success' }`
   - `CATEGORY_LABELS`: `{ BUG: 'Lỗi hệ thống', CONTENT_COMPLAINT: 'Khiếu nại nội dung', GENERAL_FEEDBACK: 'Góp ý' }`
   - `CATEGORY_VARIANTS`: `{ BUG: 'danger', CONTENT_COMPLAINT: 'warning', GENERAL_FEEDBACK: 'neutral' }`
   - `TEXT`, `ERRORS` string constants

3. Tạo `features/supportTickets/api/index.ts`:
   - `useSupportTickets(params)` — `useQuery` gọi `listTickets`
   - `useSupportTicket(publicId)` — `useQuery` gọi `getTicket`
   - `useSubmitTicket()` — `useMutation` gọi `submitTicket`, `onSuccess` invalidate list query

4. Tạo `features/supportTickets/utils/validateCreateTicket.ts`:
   - Validate: category required, description 1–2000 chars, entityId là UUID format nếu entityType đã chọn

5. Tạo components:
   - `_TicketStatusBadge.tsx` — renders `<StatusBadge>` với đúng variant từ `STATUS_VARIANTS`
   - `_TicketCategoryBadge.tsx` — renders `<Badge>` với đúng variant từ `CATEGORY_VARIANTS`
   - `_TicketTable.tsx` — `<DataTable>` với columns: ID, category, status, description, createdAt, action link
   - `_TicketFilters.tsx` — status dropdown + category dropdown, emit onChange
   - `_TicketEmptyState.tsx` — `<EmptyState>` với CTA "Submit ticket đầu tiên"
   - `_NoteThread.tsx` — danh sách notes chronological, mỗi note có header (adminPublicId + createdAt) + content
   - `CreateTicketModal.tsx` — form modal: category select, description textarea, entityType select (optional), entityId input (hidden khi không có entityType)
   - `SupportTicketsView.tsx` — page list: `<PageHeader>` + "Tạo ticket" button + `_TicketFilters` + `_TicketTable>` + `<PaginationControls>`
   - `SupportTicketDetailView.tsx` — page detail: `<PageHeader>` + ticket info + `_NoteThread>`

6. Tạo routes:
   - `app/(dashboard)/host/support-tickets/page.tsx` → `<SupportTicketsView />`
   - `app/(dashboard)/host/support-tickets/[publicId]/page.tsx` → `<SupportTicketDetailView ticketPublicId={publicId} />`

7. Thêm nav entry vào `lib/navigation.tsx`:
   - Thêm `{ label: labels.supportTickets, href: '/host/support-tickets', icon: TicketIcon, requiredRoles: ['HOST_ADMIN'] }` vào `buildHostNav()`
   - Thêm label key vào `OrgLabels` type nếu cần (kiểm tra pattern hiện tại)

## Files to Create or Modify

Tất cả paths relative to `d:\FPT\9thSemester\pte\pte-web\apps\tenant-web\`:

| File | Action |
|---|---|
| `features/supportTickets/types/index.ts` | Create |
| `features/supportTickets/constants/index.ts` | Create |
| `features/supportTickets/api/index.ts` | Create |
| `features/supportTickets/utils/validateCreateTicket.ts` | Create |
| `features/supportTickets/components/index.ts` | Create (barrel) |
| `features/supportTickets/components/_TicketStatusBadge.tsx` | Create |
| `features/supportTickets/components/_TicketCategoryBadge.tsx` | Create |
| `features/supportTickets/components/_TicketTable.tsx` | Create |
| `features/supportTickets/components/_TicketFilters.tsx` | Create |
| `features/supportTickets/components/_TicketEmptyState.tsx` | Create |
| `features/supportTickets/components/_NoteThread.tsx` | Create |
| `features/supportTickets/components/CreateTicketModal.tsx` | Create |
| `features/supportTickets/components/SupportTicketsView.tsx` | Create |
| `features/supportTickets/components/SupportTicketDetailView.tsx` | Create |
| `app/(dashboard)/host/support-tickets/page.tsx` | Create |
| `app/(dashboard)/host/support-tickets/[publicId]/page.tsx` | Create |
| `lib/navigation.tsx` | Modify — add support-tickets nav entry |

## Success Criteria
- HOST_ADMIN thấy "Support Tickets" trong sidebar
- Vào `/host/support-tickets` thấy list + filter + pagination
- Nhấn "Tạo ticket" → modal mở, submit → toast + ticket xuất hiện trong list
- Nhấn vào ticket → `/host/support-tickets/{id}` thấy detail + notes (hoặc empty state)
- Filter status/category hoạt động

## Risks
- `buildHostNav()` nhận `labels` object — kiểm tra OrgLabels type để biết có cần thêm key hay dùng hardcoded string
- Icon cho support tickets: kiểm tra icon set đang dùng trong navigation (Heroicons? Lucide?)

## Execution Log

### Errors Encountered
- `_TicketFilters.tsx`: `as const` array is readonly, incompatible with mutable `SelectOption[]`
- `SupportTicketsView.tsx`: `useToast()` returns `{ showToast }` not `{ toast }`

### Root Cause
- `as const` makes array readonly; Select component expects mutable type
- Toast API uses `showToast(message, { tone })` not `toast({ title, variant })`

### Resolution
- Removed `as const` from `STATUS_OPTIONS`; cast spread with `as { value: string; label: string }[]`
- Changed `toast(...)` → `showToast(CREATE_TICKET_TEXT.SUCCESS_TOAST, { tone: "success" })`

### Test Results After Fix
- `pnpm --filter tenant-web exec tsc --noEmit` → 0 errors — BUILD SUCCESS
