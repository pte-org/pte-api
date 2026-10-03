# Phase 3: vendor-web (PLATFORM_ADMIN)

## Requirements
PLATFORM_ADMIN xem tất cả ticket từ mọi tenant, filter cross-tenant, advance status theo flow OPEN→IN_PROGRESS→RESOLVED, và thêm notes vào ticket.

## Steps

1. Tạo `features/supportTickets/types/index.ts` (tương tự tenant-web nhưng có thêm admin actions):
   - Reuse types từ Phase 2 — chỉ thêm gì khác biệt nếu cần

2. Tạo `features/supportTickets/constants/index.ts`:
   - Giống Phase 2 + thêm:
   - `ADMIN_SUPPORT_TICKETS_QUERY_KEY = ['admin-support-tickets']`
   - `ADMIN_SUPPORT_TICKET_QUERY_KEY = (id: string) => ['admin-support-tickets', id]`
   - `STATUS_TRANSITIONS`: `{ OPEN: 'IN_PROGRESS', IN_PROGRESS: 'RESOLVED', RESOLVED: null }` — drives button rendering
   - `TRANSITION_LABELS`: `{ IN_PROGRESS: 'Bắt đầu xử lý', RESOLVED: 'Đánh dấu đã giải quyết' }`

3. Tạo `features/supportTickets/api/index.ts`:
   - `useAdminSupportTickets(params)` — `useQuery` gọi `adminListTickets`
   - `useAdminSupportTicket(publicId)` — `useQuery` gọi `adminGetTicket`
   - `useUpdateTicketStatus()` — `useMutation` gọi `adminUpdateTicketStatus`, `onSuccess` invalidate detail + list query
   - `useAddTicketNote()` — `useMutation` gọi `adminAddNote`, `onSuccess` invalidate detail query

4. Tạo components (reuse `_TicketStatusBadge`, `_TicketCategoryBadge`, `_NoteThread` từ Phase 2 nếu extract vào `@pte/ui` hoặc duplicate nếu không):
   - `_AdminTicketTable.tsx` — `<DataTable>` với columns: ID, tenantId, category, status, description, createdAt, action link
   - `_AdminTicketFilters.tsx` — status + category + tenantId (UUID text input) dropdowns/inputs
   - `_TicketEmptyState.tsx` — empty state cho admin
   - `_StatusPanel.tsx` — hiển thị status hiện tại + nút transition hợp lệ (dựa trên `STATUS_TRANSITIONS`), disabled + tooltip khi RESOLVED
   - `_AddNoteForm.tsx` — textarea + "Thêm note" button, clear sau submit
   - `AdminSupportTicketsView.tsx` — page list: `<PageHeader>` + `_AdminTicketFilters` + `_AdminTicketTable` + `<PaginationControls>`
   - `AdminSupportTicketDetailView.tsx` — page detail: `<PageHeader>` + ticket info section + `_StatusPanel` + `_NoteThread` + `_AddNoteForm`

5. Tạo routes:
   - `app/(dashboard)/admin/support-tickets/page.tsx` → `<AdminSupportTicketsView />`
   - `app/(dashboard)/admin/support-tickets/[publicId]/page.tsx` → `<AdminSupportTicketDetailView ticketPublicId={publicId} />`

6. Thêm nav entry vào `lib/navigation.tsx`:
   - Thêm `{ label: 'Support Tickets', href: '/admin/support-tickets', icon: TicketIcon }` vào `ADMIN_NAV`

## Files to Create or Modify

Tất cả paths relative to `d:\FPT\9thSemester\pte\pte-web\apps\vendor-web\`:

| File | Action |
|---|---|
| `features/supportTickets/types/index.ts` | Create |
| `features/supportTickets/constants/index.ts` | Create |
| `features/supportTickets/api/index.ts` | Create |
| `features/supportTickets/components/index.ts` | Create (barrel) |
| `features/supportTickets/components/_AdminTicketTable.tsx` | Create |
| `features/supportTickets/components/_AdminTicketFilters.tsx` | Create |
| `features/supportTickets/components/_TicketEmptyState.tsx` | Create |
| `features/supportTickets/components/_StatusPanel.tsx` | Create |
| `features/supportTickets/components/_AddNoteForm.tsx` | Create |
| `features/supportTickets/components/_NoteThread.tsx` | Create |
| `features/supportTickets/components/AdminSupportTicketsView.tsx` | Create |
| `features/supportTickets/components/AdminSupportTicketDetailView.tsx` | Create |
| `app/(dashboard)/admin/support-tickets/page.tsx` | Create |
| `app/(dashboard)/admin/support-tickets/[publicId]/page.tsx` | Create |
| `lib/navigation.tsx` | Modify — add support-tickets to ADMIN_NAV |

## Success Criteria
- PLATFORM_ADMIN thấy "Support Tickets" trong sidebar vendor-web
- Vào `/admin/support-tickets` thấy list cross-tenant, filter tenantId/status/category hoạt động
- Mở detail ticket OPEN → thấy nút "Bắt đầu xử lý", nhấn → status đổi IN_PROGRESS, nút đổi thành "Đánh dấu đã giải quyết"
- Detail ticket RESOLVED → không có nút transition
- Add note → note xuất hiện trong `_NoteThread`, textarea cleared
- HOST_ADMIN mở lại detail ticket → thấy status mới + notes

## Risks
- `_StatusPanel` phải handle optimistic UI hoặc refetch sau mutation — dùng `onSuccess` invalidate `ADMIN_SUPPORT_TICKET_QUERY_KEY`
- `_AddNoteForm` cần ref để clear textarea — dùng controlled input với local state
- tenantId filter là UUID free text — nên debounce 500ms trước khi trigger query để tránh request thừa
