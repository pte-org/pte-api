# Brainstorm: UTC timezone bền vững (Postgres/JVM) + Jackson 3 migration cho ObjectMapper

**Date:** 2026-08-05

## Bối cảnh

Trong lúc verify plan `remove-flyway-hibernate-only` (2026-08-04), chạy thử
service `admin` standalone bằng `java -jar` phát hiện 2 vấn đề môi trường/pre-existing,
ngoài scope của plan đó:

1. JVM default timezone (`Asia/Saigon`, alias IANA cũ) không được `postgres:17`
   image nhận diện khi PgJDBC gửi `SET TimeZone` lúc connect — chỉ nhận
   `Asia/Ho_Chi_Minh` (tên canonical).
2. `admin` (và khả năng cao cả 9 service, vì cùng dùng `AbstractOutboxWriter`
   từ `pte-common`) fail application context với
   `NoSuchBeanDefinitionException` cho `com.fasterxml.jackson.databind.ObjectMapper`
   (Jackson 2) — xảy ra sau khi JPA/Hibernate init xong.

Phiên brainstorm này đào sâu cả 2 để tìm hướng xử lý bền vững, thay vì chỉ
workaround.

## Ideas Explored

### Vấn đề 1 — Timezone
- **Giữ nguyên `Asia/Ho_Chi_Minh` làm workaround per-run** (`-Duser.timezone=...`)
  — dismissed, chỉ vá triệu chứng, mỗi dev/máy mới lại dính lại.
- **Set `TZ=Asia/Ho_Chi_Minh` cho container Postgres** — dismissed, vẫn phụ
  thuộc vào việc JVM gửi đúng tên canonical, không giải quyết gốc lớp bug
  alias timezone.
- **Chuẩn hoá toàn hệ thống theo UTC** (JVM `user.timezone=UTC` + Postgres mặc
  định UTC + cột `timestamptz`) — **hướng được chọn**: loại bỏ hẳn lớp bug
  alias vĩnh viễn, không phụ thuộc múi giờ máy dev.

### Vấn đề 2 — ObjectMapper/Jackson
- Static audit ban đầu (đọc `admin/pom.xml`, `pte-common/pom.xml`) cho thấy
  classpath "đáng lẽ đúng": `jackson-databind` compile-scope, không có
  autoconfigure exclusion nào — không giải thích được lỗi trên giấy.
- **Repro thật** (bật Docker, `mvnw clean package`, chạy jar, đọc log) xác
  nhận đây là `NoSuchBeanDefinitionException` thật, không phải log diễn giải
  sai.
- Đào file jar đã đóng gói: có **2 phiên bản Jackson cùng tồn tại**
  (`jackson-databind-2.21.4` và `jackson-databind-3.1.4`) — Spring Boot
  4.1.0 đã chuyển hẳn autoconfigure sang Jackson 3
  (`tools.jackson.databind.*`, builder pattern), không còn tạo bean Jackson 2
  `ObjectMapper` nữa.
- Grep toàn bộ 9 service: **30 file** tiêm `ObjectMapper` (Jackson 2) trực
  tiếp — không chỉ `OutboxWriter`, mà cả ~15 RabbitMQ consumer, 2 config
  loader JSON tĩnh, 1 Redis cache service, 1 mapper. Nghĩa là **cả 9 service
  đang không start được**, không riêng gì đường outbox.
- Kiểm tra API Jackson 3 thật (`javap`): `JsonMapper extends
  tools.jackson.databind.ObjectMapper`; `writeValueAsString`/`readValue` ném
  `JacksonException extends RuntimeException` (**unchecked**) — khác Jackson 2
  (`JsonProcessingException extends IOException`, checked). Migration không
  chỉ là đổi tên type.
- **Option cân nhắc: bean tương thích Jackson 2 tạm thời** (unblock nhanh 9
  service, giữ 29/30 file không đổi) — **dismissed** theo lựa chọn của user:
  chấp nhận migrate cả 30 file 1 lần, khớp đúng hướng Boot 4 đã chọn, tránh 2
  phiên bản Jackson sống chung dài hạn.

## User's Direction

**Vấn đề 1 (timezone):**
- Convert cả 12 entity đang dùng `LocalDateTime`/`OffsetDateTime` sang
  `Instant` — đồng bộ toàn bộ timestamp thành `timestamptz` thật sự, không mơ
  hồ về zone.
- JVM + Postgres đều chuẩn hoá UTC.

**Vấn đề 2 (Jackson):**
- Migrate toàn bộ 30 file (không riêng `AbstractOutboxWriter`) sang
  `tools.jackson.databind.json.JsonMapper` (Jackson 3) ngay trong 1 lần, thay
  vì vá tạm bằng bean tương thích Jackson 2.

## Open Questions

- **Cơ chế set JVM `user.timezone=UTC`**: code-level (`TimeZone.setDefault()`
  ở đầu mỗi `main()`, 9 file) so với dựa vào launch script/env var — cần quyết
  định khi lập plan chi tiết. Code-level đáng tin cậy hơn (không phụ thuộc
  cách dev chạy jar) nhưng chạm 9 file thay vì 1 chỗ chung.
- **Reset local DB cho đổi kiểu cột `timestamp` → `timestamptz`**: Hibernate
  `ddl-auto=update` không tự `ALTER COLUMN TYPE` cột đã tồn tại — mỗi dev cần
  reset DB local (giống pattern đã làm ở plan `remove-flyway-hibernate-only`).
- **Dọn `pom.xml`**: sau khi migrate Jackson 3, các khai báo tường minh
  `jackson-databind` (Jackson 2, compile-scope) ở `pte-common` +9 service
  không còn cần thiết — nên gỡ khỏi tất cả pom trong cùng đợt để tránh 2
  phiên bản Jackson tồn tại song song trong jar.
- **Thứ tự làm 2 vấn đề**: độc lập về mặt kỹ thuật, không phụ thuộc nhau —
  có thể tách thành 2 plan/cook run riêng, làm song song hoặc tuần tự tuỳ ưu
  tiên.

## Risks

- **Jackson 3 migration chạm 30 file trên cả 9 service** — regression risk
  rải rác nếu thiếu test coverage cho từng consumer/mapper/cache service;
  đổi checked → unchecked exception có thể để lọt case xử lý lỗi bị mất khi
  bỏ `try/catch (JsonProcessingException)` không cẩn thận.
- **Convert 12 entity sang Instant** là breaking change cho DB schema hiện có
  (đổi kiểu cột) — bắt buộc reset local DB, và nếu có API/DTO nào serialize
  `LocalDateTime` ra ngoài (frontend, event payload) theo format khác
  `Instant` (ISO-8601 UTC `Z` suffix), cần rà soát tương thích ngược ở phía
  consumer của các API/event đó.
- **Trùng lặp công việc nếu làm song song với dev khác**: cả 2 vấn đề chạm
  diện rộng (9 service), nên coi là 1 đợt thay đổi lớn, tránh vừa làm vừa có
  người khác sửa entity/OutboxWriter cùng lúc.
