# Spec: Support Ticket UI

## Context
Backend API đã hoàn thiện (plans/host-support-ticket). Feature này thêm giao diện cho 2 app:
- **tenant-web** — HOST_ADMIN submit và theo dõi ticket của mình
- **vendor-web** — PLATFORM_ADMIN quản lý ticket cross-tenant, cập nhật status, thêm note

## User Stories

### P1 — HOST_ADMIN (tenant-web)
- **ST-01** HOST_ADMIN xem danh sách ticket của tenant mình, lọc theo status/category
- **ST-02** HOST_ADMIN submit ticket mới (chọn category, nhập description, optional gắn entity)
- **ST-03** HOST_ADMIN xem chi tiết ticket, đọc notes của admin theo thứ tự thời gian

### P1 — PLATFORM_ADMIN (vendor-web)
- **ST-04** PLATFORM_ADMIN xem tất cả ticket cross-tenant, lọc theo status/category/tenantId
- **ST-05** PLATFORM_ADMIN xem chi tiết ticket, advance status (OPEN→IN_PROGRESS→RESOLVED)
- **ST-06** PLATFORM_ADMIN thêm note vào ticket, note hiển thị cho host ngay sau đó

## Functional Requirements

### FR-01 — Ticket list (host)
- Hiển thị: publicId (truncated), category badge, status badge, description (truncated 80 chars), createdAt
- Filter: status dropdown (All / OPEN / IN_PROGRESS / RESOLVED), category dropdown
- Pagination: page + size controls
- Empty state khi không có ticket

### FR-02 — Create ticket modal (host)
- Fields: category (required, select), description (required, textarea 1–2000 chars), entityType (optional, select), entityId (optional, text — UUID)
- entityId chỉ hiển thị khi entityType đã chọn
- Validation inline trước khi submit
- Sau submit: close modal + refetch list + toast "Ticket đã được gửi"

### FR-03 — Ticket detail (host)
- Hiển thị đầy đủ: category, status badge, description, entityType/entityId (nếu có), createdAt
- Notes section: danh sách note của admin theo thứ tự tăng dần, mỗi note có adminPublicId + content + createdAt
- Empty notes state: "Chưa có phản hồi từ admin"

### FR-04 — Ticket list (admin)
- Giống FR-01 + thêm cột tenantId
- Filter thêm: tenantId input
- Link sang detail

### FR-05 — Ticket detail (admin)
- Hiển thị như FR-03
- Status panel: nút transition hợp lệ theo trạng thái hiện tại
  - OPEN → nút "Bắt đầu xử lý" (→ IN_PROGRESS)
  - IN_PROGRESS → nút "Đánh dấu đã giải quyết" (→ RESOLVED)
  - RESOLVED → chỉ hiển thị label, không có nút
- Add note form: textarea + nút "Thêm note"
- Sau add note: refetch detail + clear textarea + toast

## Non-Functional Requirements
- Sử dụng `@pte/ui` components — không thêm thư viện mới
- TanStack Query v5 cho tất cả API calls
- Status badge màu: OPEN=neutral, IN_PROGRESS=warning, RESOLVED=success
- Category badge: BUG=danger, CONTENT_COMPLAINT=warning, GENERAL_FEEDBACK=neutral

## API Endpoints (đã có)
| Endpoint | Dùng ở |
|---|---|
| `POST /api/v1/support-tickets` | tenant-web create |
| `GET /api/v1/support-tickets` | tenant-web list |
| `GET /api/v1/support-tickets/{id}` | tenant-web detail |
| `GET /api/v1/admin/support-tickets` | vendor-web list |
| `GET /api/v1/admin/support-tickets/{id}` | vendor-web detail |
| `PATCH /api/v1/admin/support-tickets/{id}` | vendor-web update status |
| `POST /api/v1/admin/support-tickets/{id}/notes` | vendor-web add note |

## Success Criteria
- HOST_ADMIN submit được ticket và thấy nó trong list
- HOST_ADMIN mở detail thấy notes của admin sau khi admin add
- PLATFORM_ADMIN list thấy ticket từ mọi tenant, filter hoạt động
- PLATFORM_ADMIN advance status, host thấy status mới khi reload detail
- PLATFORM_ADMIN add note, host thấy note trong detail
- HOST_ADMIN gọi admin endpoint → 403 (role guard)
