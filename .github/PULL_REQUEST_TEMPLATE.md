## Summary

Mô tả ngắn PR này làm gì (1–2 câu).

## Type

- [ ] `feat:` — Tính năng mới
- [ ] `fix:` — Sửa bug
- [ ] `refactor:` — Cải thiện code (không thêm feature / không sửa bug)
- [ ] `chore:` — Config, dependency, CI
- [ ] `docs:` — Tài liệu

## Changes

- Liệt kê thay đổi chính
- Không cần liệt kê từng dòng (diff sẽ hiển thị)
- Highlight quyết định kiến trúc hoặc workaround quan trọng

## Closes / Related

Closes #<!-- issue number -->

## How to Test

Các bước để verify PR này hoạt động đúng.

---

## AI-Generated Code

- [ ] Một phần/toàn bộ code trong PR này được tạo bởi AI (Claude Code, Copilot, v.v.)
- [ ] Nếu có: đã review thủ công từng file, kiểm tra file size, hardcoded string, layer violation

---

## Code Review Checklist — Universal

- [ ] Commit messages theo Conventional Commits (`feat:`, `fix:`, `refactor:`, `chore:`, `docs:`)
- [ ] Không debug code còn sót (`System.out.println`, `logger.debug` không cần thiết)
- [ ] PR size < 400 dòng diff (không tính lock file, generated code)
- [ ] Không có string hardcode trong business logic (kiểm tra `*Constants.java`)
- [ ] Không có file nào > 300 dòng code logic (auto-generated: Lombok, MapStruct — được miễn)

## Code Review Checklist — aptis-api (Spring Boot / Java)

- [ ] Mọi message string / error code trong `*Constants.java` — không hardcode trong service/controller
- [ ] `@Transactional` chỉ ở Service layer (`readOnly=true` cho query)
- [ ] Service có < 5 public method (nếu hơn → split service)
- [ ] Mọi endpoint trả về `ApiResponse<T>` wrapper
- [ ] Exception handling qua `@ControllerAdvice` (không try-catch trong controller)
- [ ] Không dùng `@Data` trên `@Entity` (dùng `@Getter @Setter @Builder`)
- [ ] N+1 prevention: dùng `@EntityGraph` hoặc JOIN FETCH khi load associations
- [ ] DTO không expose domain object trực tiếp
- [ ] Secrets (API key, password, token) trong env var — không trong `*Constants.java`
- [ ] Method mới trong multi-tenant service có `tenantId` parameter (không access Security context trong Repository)

## Sign-off

- [ ] Đã đọc [Coding Standards API](../docs/CODING_STANDARDS_API.md) trước khi submit
- [ ] Đã test PR locally và verify behavior đúng
- [ ] Không có breaking change (hoặc đã giải thích tại sao cần thiết)
