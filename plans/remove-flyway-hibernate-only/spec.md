# Spec: Loại bỏ Flyway, dùng Hibernate ddl-auto cho giai đoạn dev

**Date:** 2026-08-04
**Status:** Ready

---

## Problem Statement

Đội dev đang mất thời gian viết SQL migration thủ công và gặp conflict version
number / lỗi migrate khi setup Flyway trên môi trường mới, trong khi hệ thống
còn ở giai đoạn dev, chưa có production/staging thật và chưa có dữ liệu quan
trọng. Loại bỏ Flyway tạm thời và để Hibernate tự quản lý schema giúp team
đổi entity nhanh hơn mà không cần viết migration, miễn là có kế hoạch rõ ràng
để đưa migration control trở lại trước khi go-live.

---

## User Stories

- **[P1]** As a developer, tôi muốn đổi entity Java và thấy schema Postgres
  local tự cập nhật theo, mà không cần viết file SQL migration.
  Accepted when: sửa 1 entity (thêm/đổi field), start lại service, DB local
  tự động phản ánh thay đổi mà không cần thao tác thủ công.

- **[P1]** As a developer, tôi muốn tất cả 9 service không còn phụ thuộc
  `flyway-core` / `flyway-database-postgresql` và không còn thư mục
  `db/migration`.
  Accepted when: build tất cả 9 service (`mvn clean install`) thành công sau
  khi gỡ dependency Flyway khỏi từng `pom.xml`.

- **[P2]** As a tech lead, tôi muốn việc add lại Flyway + JPA Buddy chỉ diễn
  ra khi tôi (project owner) chủ động yêu cầu — không tự động, không phải do
  team dev tự quyết định thời điểm.
  Accepted when: tài liệu/README ghi rõ "chỉ re-introduce Flyway khi có yêu
  cầu tường minh từ project owner", không đặt điều kiện tự động nào khác
  (không dựa vào ngày, không dựa vào %-hoàn-thành).

- **[P3]** _(out of scope) Tự động sinh baseline migration đầu tiên bằng
  script — để lại cho lúc lập plan chi tiết khi thực sự chuẩn bị production._

---

## Functional Requirements

1. FR-01: Gỡ dependency `org.flywaydb:flyway-core` và
   `org.flywaydb:flyway-database-postgresql` khỏi `pom.xml` của cả 9 service
   (admin, authoring, exam-delivery, iam, media, notification, proctor,
   reporting, scheduling, scoring).
2. FR-02: Set `spring.jpa.hibernate.ddl-auto=update` (hoặc giá trị tương
   đương phù hợp) trong config của từng service cho profile dev/local.
3. FR-03: Xoá hẳn thư mục `db/migration` (44 file SQL) khỏi cả 9 service.
   Không giữ lại, không convert baseline — khi add lại Flyway sau này sẽ tạo
   baseline mới từ schema thực tế tại thời điểm đó.
4. FR-04: Xác nhận (đã audit qua grep toàn bộ `pte-api/**/src/test/**`)
   không có test nào hiện dùng Testcontainers hoặc phụ thuộc Flyway — không
   cần thay đổi gì ở cấu hình test/CI cho requirement này.
5. FR-05: Ghi lại vào tài liệu dự án (README hoặc docs) rằng việc add lại
   Flyway + JPA Buddy chỉ được thực hiện khi có yêu cầu tường minh từ project
   owner — không có mốc/điều kiện tự động nào khác kích hoạt việc này.

---

## Non-Functional Requirements

- Rủi ro dữ liệu: chấp nhận được ở giai đoạn hiện tại vì chưa có dữ liệu
  quan trọng; **không được áp dụng `ddl-auto=update` cho môi trường
  production/staging** sau khi launch.
- Khả năng phục hồi kiểm soát schema: khi add lại Flyway lúc production-ready,
  phải có khả năng tạo baseline migration khớp với schema thực tế do
  Hibernate sinh ra tại thời điểm đó.

---

## Success Criteria

- [ ] Cả 9 service build và start thành công không còn dependency Flyway.
- [ ] Dev có thể đổi entity và thấy schema local tự cập nhật mà không cần
      viết SQL, xác nhận qua ít nhất 1 lần thử thực tế (thêm cột mới vào 1
      entity bất kỳ).
- [ ] Có tài liệu ghi rõ: việc add lại Flyway/JPA Buddy chỉ xảy ra khi project
      owner yêu cầu tường minh, kèm quy trình thực hiện khi được yêu cầu.

---

## Out of Scope

- Tự động sinh/­migrate baseline SQL ngay bây giờ — chỉ thực hiện khi thực sự
  chuẩn bị deploy production.
- Đổi sang Liquibase hoặc migration engine khác — không được cân nhắc trong
  brainstorm này.
- Thay đổi chiến lược cho môi trường CI/staging dùng chung (chưa tồn tại ở
  thời điểm hiện tại).

---

## Assumptions

- Mỗi dev tiếp tục dùng 1 Postgres instance local riêng (không share DB) —
  nếu điều này thay đổi (vd. chuyển sang DB dev dùng chung), cần đánh giá lại
  vì nhiều instance `ddl-auto=update` chạy song song trên cùng DB có thể xung
  đột.
- Project owner là người chủ động nhớ và ra yêu cầu add lại Flyway trước khi
  go-live — không có gate tự động nào nhắc việc này; nếu owner quên yêu cầu,
  rủi ro schema không kiểm soát ở production vẫn tồn tại.

