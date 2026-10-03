# Plan: Support Ticket UI
Status: 🔄 Not Started

## Session Notes
<!-- Updated by cook automatically — do not edit manually -->

**Last active:** —
**Phase in progress:** None
**Status:** Awaiting execution

## Overview
Thêm giao diện Support Ticket cho 2 app:
- **tenant-web** — HOST_ADMIN submit + list + detail (read-only notes)
- **vendor-web** — PLATFORM_ADMIN list cross-tenant + detail + update status + add note

Tất cả UI dùng `@pte/ui` + TanStack Query v5. API client functions thêm vào `packages/api-client`.

## Phases
- [x] Phase 1: API Client — Request functions + TS types trong `packages/api-client`
- [x] Phase 2: tenant-web — Feature folder, routes, nav entry cho HOST_ADMIN
- [x] Phase 3: vendor-web — Feature folder, routes, nav entry cho PLATFORM_ADMIN

## Research Summary
Codebase: pnpm monorepo + Turborepo, Next.js 16 App Router, TanStack Query v5, `@pte/ui` custom component library (Tailwind 4).

**Pattern chuẩn:**
- `packages/api-client/src/requests/support/tickets.ts` — plain async functions nhận `ApiClient`
- `features/supportTickets/api/index.ts` — TanStack Query wrappers
- `features/supportTickets/components/` — view components + modals + `_` prefixed internals
- `features/supportTickets/constants/index.ts` — query keys + TEXT/ERRORS/STATUS_LABELS
- `features/supportTickets/types/index.ts` — domain types
- Pages là thin wrappers: `<DashboardChrome><FeatureView /></DashboardChrome>`

**Reference features:**
- tenant-web: `features/exams/` (list + detail + modal pattern)
- vendor-web: `features/tenancy/` (list + detail + internal `_` components)

## Dependencies
- Backend API hoàn thiện ✅ (plans/host-support-ticket)
- `packages/api-client` cần thêm support ticket requests trước khi 2 app có thể dùng

## Risks
- `entityId` là UUID string — validate format trên FE bằng regex trước khi submit
- Status transition buttons phải disabled đúng theo state (RESOLVED không có nút)
- tenantId filter ở vendor-web là free-text UUID input — cần validate format UUID hoặc để server trả lỗi
