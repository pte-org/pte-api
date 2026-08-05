# Spec: Chuẩn hoá UTC cho JVM + Postgres, entity dùng timestamptz

**Date:** 2026-08-05
**Status:** Draft

---

## Problem Statement

JVM default timezone (`Asia/Saigon`, alias IANA cũ) không được `postgres:17`
image nhận diện khi connect (`FATAL: invalid value for parameter "TimeZone"`),
vì image chỉ có tzdata rút gọn (`Asia/Ho_Chi_Minh`). Đồng thời 12 entity dùng
`LocalDateTime`/`OffsetDateTime` (map thành `timestamp without time zone`)
thay vì `Instant` (`timestamptz`) — timestamp trong DB không tự mang thông tin
zone, dễ sai lệch nếu sau này chạy trên máy/server khác zone. Chuẩn hoá cả
JVM và schema về UTC loại bỏ toàn bộ lớp bug alias timezone vĩnh viễn, không
chỉ vá triệu chứng cho 1 máy dev.

---

## User Stories

- **[P1]** Là dev, tôi muốn chạy bất kỳ service nào bằng `java -jar` trên máy
  mình (bất kể OS timezone) mà không gặp lỗi `invalid value for parameter
  "TimeZone"` khi connect Postgres.
  Accepted when: chạy `java -jar <service>.jar` trên máy có OS timezone khác
  UTC (vd. `Asia/Ho_Chi_Minh`) vẫn connect Postgres thành công, không cần
  truyền `-Duser.timezone` thủ công.

- **[P1]** Là dev, tôi muốn tất cả cột thời gian trong DB là `timestamptz`
  (không phải `timestamp` mơ hồ zone), để giá trị đọc/ghi luôn nhất quán bất
  kể zone của client hay server.
  Accepted when: `psql \d <table>` trên toàn bộ bảng có cột thời gian (ở cả 9
  service) hiển thị kiểu `timestamp with time zone`, sinh ra thuần từ
  `Instant` field trên entity qua Hibernate `ddl-auto=update` — không viết SQL
  thủ công.

- **[P2]** Là dev, tôi muốn container Postgres tường minh khai báo `TZ=UTC`
  trong `docker-compose.yml`, để hành vi không phụ thuộc ngầm vào default của
  base image (có thể đổi giữa các version image).
  Accepted when: `docker-compose.yml` có `environment: TZ: UTC` cho service
  `postgres`.

---

## Functional Requirements

1. FR-01: Mỗi service (9 service) set JVM default timezone = UTC ngay khi
   khởi động (trước khi bất kỳ kết nối DB nào được mở), độc lập với OS
   timezone của máy chạy.
2. FR-02: Toàn bộ 12 entity đang dùng `LocalDateTime`/`OffsetDateTime` cho
   timestamp field chuyển sang `Instant`.
3. FR-03: `docker-compose.yml` khai báo tường minh `TZ=UTC` cho service
   `postgres`.
4. FR-04: Không migration file SQL thủ công nào được viết — cột đổi kiểu
   thông qua Hibernate `ddl-auto=update` sau khi entity field đổi type (nhất
   quán với quyết định loại bỏ Flyway ở plan `remove-flyway-hibernate-only`).
5. FR-05: Bất kỳ API response/event payload nào hiện đang serialize
   `LocalDateTime` (không có `Z`/offset suffix) cần được rà soát — sau khi đổi
   sang `Instant`, format JSON output đổi sang ISO-8601 UTC (`...Z`); nếu có
   consumer bên ngoài (frontend, service khác) parse chuỗi cũ theo format
   không-offset, cần xác nhận không bị breaking.

---

## Non-Functional Requirements

- Correctness: không có timestamp nào bị lệch giờ (off-by-timezone) sau khi
  migrate, verify bằng cách so sánh giá trị ghi/đọc trước và sau ở ít nhất 1
  entity mỗi service có timestamp field.
- Portability: service phải chạy đúng bất kể OS timezone của máy dev (không
  còn phụ thuộc `Asia/Saigon` hay bất kỳ zone cụ thể nào).
- Dev workflow: mỗi dev cần đúng 1 lần reset local Postgres DB cho các service
  bị đổi kiểu cột — không có bước thủ công lặp lại sau đó.

---

## Success Criteria

- [ ] Chạy `java -jar` cho cả 9 service trên máy với OS timezone
      `Asia/Ho_Chi_Minh` (không truyền `-Duser.timezone`) — tất cả connect
      Postgres thành công, 0 lỗi `invalid value for parameter "TimeZone"`.
- [ ] `psql \d` trên toàn bộ bảng có timestamp column (12 entity đã đổi) xác
      nhận kiểu `timestamp with time zone`.
- [ ] `mvn clean install` build xanh toàn bộ 13 module sau khi đổi entity
      field type.
- [ ] Không còn tham chiếu `LocalDateTime`/`OffsetDateTime` nào cho timestamp
      field trong 12 file đã xác định (verify bằng grep).

---

## Out of Scope

- Format lại API response/DTO đang expose timestamp ra ngoài (frontend) —
  chỉ rà soát tương thích (FR-05), không refactor DTO trong spec này trừ khi
  phát hiện breaking change thật.
- CI/staging/production timezone config — hiện chưa có môi trường đó (theo
  bối cảnh plan `remove-flyway-hibernate-only`), chỉ áp dụng cho dev.
- Đổi kiểu cột `date`-only (không có time component), nếu có — chỉ trong
  scope của cột timestamp có time component.

---

## Assumptions

- Mỗi dev dùng 1 Postgres instance local riêng (đã xác nhận ở plan
  `remove-flyway-hibernate-only`), nên reset DB không ảnh hưởng người khác.
- 12 file đã grep (`LocalDateTime|OffsetDateTime`) là danh sách đầy đủ —
  cần re-verify grep lúc lập plan chi tiết phòng khi có field mới phát sinh
  từ lúc brainstorm tới lúc cook.

---

## Decisions

- Cơ chế set JVM `user.timezone=UTC`: **code-level**
  (`TimeZone.setDefault(TimeZone.getTimeZone("UTC"))` là dòng đầu tiên trong
  mỗi `main()`, cả 9 file) — chọn vì độc lập với cách dev chạy jar (không phụ
  thuộc launch script/env var có đúng discipline hay không), tự chứa và dễ
  audit qua code review.

---

## Addendum (2026-08-05, sau khi lập plan + cook) — đính chính số liệu cũ

Các số liệu sau trong spec này (viết lúc brainstorm, trước khi re-verify grep
lúc lập plan chi tiết) đã sai, giữ nguyên phần trên để làm lịch sử nhưng đính
chính ở đây:

- **"9 service" → thực tế 10 service** (`admin`, `authoring`, `exam-delivery`,
  `iam`, `media`, `notification`, `proctor`, `reporting`, `scheduling`,
  `scoring`). Con số "9" là carry-over lỗi từ plan `remove-flyway-hibernate
  -only` trước đó — `pte-api/pom.xml` liệt kê rõ 10 module `services/*`.
- **"12 entity đã đổi" → thực tế 0 entity cần convert.** Grep lại lúc lập
  plan (`grep -r "LocalDateTime\|OffsetDateTime"` toàn bộ `pte-api/**/*.java`)
  trả về **0 kết quả** — cả 51 `@Entity` class đã dùng `Instant` từ trước
  (qua `BaseEntity.createdAt/updatedAt` hoặc field riêng). Con số "12" trong
  brainstorm ban đầu là do pattern grep khi đó vô tình khớp nhầm file đã
  đúng kiểu `Instant` (`private Instant` cũng khớp cùng pattern), không phải
  file dùng `LocalDateTime`/`OffsetDateTime` thật. Đã re-verify lại lần nữa ở
  Phase 3 cook — vẫn 0 kết quả.
- **"13 module" → thực tế 12 module** (`pte-common`, `gateway`, 10 service —
  không tính `pte-api-parent` aggregator).

## Success Criteria (verified — cook 2026-08-05)

- [x] Chạy `java -jar` cho cả **10** service trên máy dev thật (OS timezone
      `Asia/Ho_Chi_Minh`) — **không truyền `-Duser.timezone`** — tất cả
      connect Postgres thành công, 0 lỗi `invalid value for parameter
      "TimeZone"`. Verify 2 lần độc lập (Phase 1 và Phase 3), cả 2 lần sạch.
- [x] `psql information_schema.columns` trên **toàn bộ** 96 cột timestamp
      của cả 10 database (không phải mẫu) — 100% `timestamp with time zone`,
      0 cột `timestamp without time zone`. Round-trip spot-check (ghi
      instant `10:30:00 UTC`, đọc lại ở session `Asia/Ho_Chi_Minh`) xác nhận
      đúng `17:30+07`, không lệch giờ.
- [x] `mvn clean install` build xanh toàn bộ 12 module (+ parent aggregator),
      full test suite pass.
- [x] 0 tham chiếu `LocalDateTime`/`OffsetDateTime` nào trong toàn bộ
      `pte-api/**/*.java` (grep re-verify 2 lần, Phase 2 và Phase 3).
