# Spec: Host Support Ticket

**Date:** 2026-10-03
**Status:** Draft

---

## Problem Statement

HOST_ADMIN không có cách chính thức để báo cáo lỗi hệ thống, khiếu nại nội dung câu hỏi, hay gửi phản hồi tổng quát lên PLATFORM_ADMIN. Admin không có visibility để theo dõi và trả lời. Cần một cơ chế ticket đơn giản với trạng thái rõ ràng và ghi chú phản hồi từ admin.

---

## User Stories

- **[P1]** As a HOST_ADMIN, I want to submit a support ticket linked to a specific entity (ExamSession, ExamAttempt, or Question) so that admin can quickly trace the problem.
  Accepted when: ticket được lưu với `entityType`, `entityId`, `category`, `description`, `status = OPEN`, và hiển thị trong danh sách ticket của host.

- **[P1]** As a HOST_ADMIN, I want to view my submitted tickets and their current status so that I know whether my issue is being handled.
  Accepted when: host gọi `GET /support-tickets` và thấy danh sách ticket của tenant mình với `status`, `category`, `createdAt`, `adminNote`.

- **[P1]** As a PLATFORM_ADMIN, I want to list all tickets across tenants, filter by status/category, and update ticket status so that I can triage and close issues.
  Accepted when: admin gọi `PATCH /support-tickets/{id}` để đổi status (OPEN → IN_PROGRESS → RESOLVED) và thêm/cập nhật `adminNote`.

- **[P2]** As a HOST_ADMIN, I want to see the admin's note on my resolved ticket so that I understand what was done.
  Accepted when: `adminNote` xuất hiện trong response của `GET /support-tickets/{id}` khi status = RESOLVED.

- **[P3]** _(Notification khi status thay đổi — out of scope MVP)_
- **[P3]** _(Host reopen ticket — out of scope MVP)_
- **[P3]** _(Thread discussion / host comment sau khi submit — out of scope MVP)_

---

## Functional Requirements

1. **FR-01 — Submit ticket:** HOST_ADMIN gửi `POST /support-tickets` với body: `category` (BUG | CONTENT_COMPLAINT | GENERAL_FEEDBACK), `description` (max 2000 chars), `entityType` (nullable: EXAM_SESSION | EXAM_ATTEMPT | QUESTION), `entityId` (nullable UUID). Server lưu `tenantId` từ JWT, `submittedBy` = user hiện tại, `status = OPEN`, `createdAt` = now.

2. **FR-02 — List tickets (host view):** `GET /support-tickets` — chỉ trả về ticket thuộc `tenantId` của caller. Hỗ trợ filter `?status=` và `?category=`. Phân trang (page/size).

3. **FR-03 — Get ticket detail:** `GET /support-tickets/{id}` — host chỉ xem ticket của tenant mình. Admin xem tất cả.

4. **FR-04 — Admin list all tickets:** `GET /admin/support-tickets` — PLATFORM_ADMIN thấy tất cả tenant. Filter: `?status=`, `?category=`, `?tenantId=`. Phân trang.

5. **FR-05 — Admin update ticket status:** `PATCH /admin/support-tickets/{id}` với body: `status`. Status transition hợp lệ: OPEN → IN_PROGRESS → RESOLVED. Không cho phép RESOLVED → OPEN ở MVP.

6. **FR-06 — Admin add note:** `POST /admin/support-tickets/{id}/notes` với body: `content` (max 2000 chars). Mỗi note là một record riêng với `adminId`, `content`, `createdAt`. Append-only — không edit/delete note đã ghi. Host đọc được toàn bộ lịch sử note theo thứ tự thời gian.

7. **FR-07 — Entity reference:** `entityType` + `entityId` là nullable pair (cả hai null hoặc cả hai có giá trị). Server validate entity tồn tại khi submit nhưng lưu dưới dạng loose reference (không FK constraint) để tránh cascade delete issue.

---

## Non-Functional Requirements

- **Security:** HOST_ADMIN chỉ đọc/ghi ticket của `tenantId` mình. Cross-tenant access → 403. PLATFORM_ADMIN có full access.
- **Data integrity:** `entityId` không là FK cứng — lưu dạng `VARCHAR` để tránh broken reference khi entity bị xóa. Validate existence tại submit time only.
- **Pagination:** Default page size = 20, max = 100.

---

## Success Criteria

- [ ] HOST_ADMIN submit ticket thành công, status = OPEN, visible trong list của mình.
- [ ] PLATFORM_ADMIN đổi status OPEN → IN_PROGRESS → RESOLVED.
- [ ] PLATFORM_ADMIN thêm nhiều note, host đọc được tất cả theo thứ tự thời gian.
- [ ] HOST_ADMIN không thấy ticket của tenant khác (403 hoặc empty list).
- [ ] Submit với `entityType = QUESTION` và `entityId` không tồn tại → 404 validation error.
- [ ] Submit với `entityType = QUESTION` nhưng `entityId = null` → 400 validation error.

---

## Out of Scope

- Notification (email/in-app) khi status thay đổi.
- Host comment hoặc reply sau khi submit.
- Host reopen ticket đã resolved.
- Priority field (low/medium/high).
- Attachment/file upload.

---

## Assumptions

- Chỉ HOST_ADMIN được submit ticket (confirmed).
- `tenantId` có thể lấy từ JWT principal của HOST_ADMIN.
- `reporting` module hiện tại (AttemptReport) không liên quan — module mới là `support` package tách biệt.
- Module đặt tên `com.pte.support`, table `support_ticket`.

