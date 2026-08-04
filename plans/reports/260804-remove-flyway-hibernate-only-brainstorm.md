# Brainstorm: Loại bỏ Flyway, chỉ dùng Hibernate cho schema quản lý (giai đoạn dev)

**Date:** 2026-08-04

## Bối cảnh

Hệ thống có 9 service Spring Boot (admin, authoring, exam-delivery, iam, media,
notification, proctor, reporting, scheduling, scoring), mỗi service dùng
`spring-boot-starter-data-jpa` (Hibernate) + `flyway-core` /
`flyway-database-postgresql` với 1 Postgres instance riêng.

## Ideas Explored

- **Giữ Flyway, sửa pain points cụ thể** (timestamp-based versioning để tránh
  conflict version, CI validation chặt hơn cho lỗi checksum/order) — dismissed,
  không giải quyết gốc "viết SQL thủ công chậm".
- **Giữ Flyway, tự động sinh SQL migration từ diff entity** (JPA Buddy hoặc
  script diff schema) — dismissed cho giai đoạn hiện tại, vì vẫn cần setup
  thêm tool ngay bây giờ trong khi risk mất data = 0.
- **Hybrid theo Spring profile**: `ddl-auto=update` ở local, Flyway bật ở
  CI/staging — dismissed, thêm phức tạp không cần thiết khi chưa có môi
  trường staging/CI dùng chung.
- **Loại bỏ Flyway hoàn toàn ở giai đoạn dev, add lại (kèm JPA Buddy) khi
  feature-complete chuẩn bị deploy production** — **hướng được chọn**.

## User's Direction

- Pain points với Flyway hiện tại: viết SQL migration thủ công chậm, conflict
  version number khi nhiều dev cùng làm, lỗi khi deploy/migrate lên môi
  trường mới.
- Giai đoạn hiện tại: dev, chưa có dữ liệu quan trọng, chưa có
  production/staging thật.
- Mỗi dev dùng 1 Postgres instance local riêng (không share DB) → loại bỏ rủi
  ro tranh chấp lock khi nhiều instance Hibernate `ddl-auto=update` cùng chạy
  song song trên cùng 1 DB.
- Quyết định: bỏ hẳn Flyway dependency + migration files ở tất cả 9 service
  ngay bây giờ, dùng `spring.jpa.hibernate.ddl-auto=update` (hoặc tương
  đương) để Hibernate tự quản lý schema trong lúc dev.
- Khi feature-complete và chuẩn bị deploy production thật: add lại Flyway
  (migration engine để chạy SQL) + JPA Buddy (tool sinh SQL migration từ diff
  entity, không tự chạy migration) cùng lúc.

## Open Questions

- File migration Flyway hiện có (đã từng chạy trên local DB của dev) sẽ xử lý
  ra sao: xoá hẳn, giữ làm tài liệu tham khảo, hay convert thành 1 file
  baseline schema? → cần quyết định khi lập plan chi tiết.
- Khi add lại Flyway lúc chuẩn bị production, baseline đầu tiên sẽ được tạo
  từ schema hiện tại do Hibernate sinh ra (`flyway baseline` hoặc dump schema
  hiện tại thành `V1__baseline.sql`) — cần quy trình rõ ràng để tránh baseline
  sai lệch với entity thực tế.
- Cấu hình test/CI (nếu có dùng Testcontainers + Flyway để dựng schema cho
  integration test) cần rà soát lại vì đổi sang `ddl-auto`.

## Risks

- **Quên quay lại Flyway trước khi go-live**: nếu không có gate/checklist rõ
  ràng, `ddl-auto=update` có thể lọt vào production và gây mất data (drop
  column, đổi kiểu dữ liệu không rollback được).
- **Schema drift giữa các dev**: không còn version lịch sử schema chung, các
  dev DB local có thể lệch nhau nếu ai đó chỉnh entity nhưng chưa sync code —
  khó phát hiện hơn so với khi có Flyway file làm nguồn sự thật.
- **Baseline migration đầu tiên (lúc thêm lại Flyway) dễ sai** nếu không có
  quy trình generate từ schema thực tế tại thời điểm đó.
