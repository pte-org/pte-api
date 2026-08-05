# Spec: Migrate Jackson 2 ObjectMapper → Jackson 3 JsonMapper (toàn bộ 9 service)

**Date:** 2026-08-05
**Status:** Draft

---

## Problem Statement

Spring Boot 4.1.0 đã chuyển autoconfigure sang Jackson 3
(`tools.jackson.databind.*`) và không còn tạo bean Jackson 2
`com.fasterxml.jackson.databind.ObjectMapper` nữa. 30 file trên toàn bộ 9
service (outbox writer, RabbitMQ consumer, config loader, cache service,
mapper) vẫn tiêm `ObjectMapper` kiểu Jackson 2 qua constructor — khiến
**application context của cả 9 service fail khi start standalone**
(`NoSuchBeanDefinitionException`), không riêng service `admin` hay đường
outbox. Đây là pre-existing bug, phát hiện khi verify plan
`remove-flyway-hibernate-only`, không liên quan Flyway/JPA.

---

## User Stories

- **[P1]** Là dev, tôi muốn chạy `java -jar` cho bất kỳ service nào trong 9
  service và application context khởi động thành công (không
  `NoSuchBeanDefinitionException` cho `ObjectMapper`/`JsonMapper`).
  Accepted when: cả 9 service start standalone thành công (đến log "Started
  ...Application"), verify bằng cách chạy từng service với Postgres +
  RabbitMQ local đã lên.

- **[P1]** Là dev, tôi muốn toàn bộ đường serialize/deserialize JSON (outbox
  write, RabbitMQ consumer đọc event, config loader đọc JSON tĩnh, Redis
  cache) dùng nhất quán 1 phiên bản Jackson (Jackson 3), không còn 2 phiên
  bản Jackson sống chung trong cùng 1 jar.
  Accepted when: `unzip -l <service>.jar | grep -i jackson-databind` chỉ còn
  đúng 1 dòng (`jackson-databind-3.x`), không còn `jackson-databind-2.x`.

- **[P2]** Là dev, tôi muốn behavior xử lý lỗi serialize/deserialize (throw,
  catch, log) tương đương trước khi migrate — không im lặng nuốt lỗi do bỏ sót
  khi chuyển từ checked (`JsonProcessingException`) sang unchecked
  (`JacksonException`).
  Accepted when: review từng `try/catch (JsonProcessingException...)` cũ và
  từng `throws IOException` liên quan tới Jackson trong 30 file — xác nhận
  logic xử lý lỗi tương đương (giữ catch + wrap, hoặc xác nhận không cần catch
  nữa) chứ không xoá try/catch mà không thay thế.

---

## Functional Requirements

1. FR-01: `AbstractOutboxWriter` (`pte-common`) và 9 `OutboxWriter` subclass
   đổi constructor param từ `com.fasterxml.jackson.databind.ObjectMapper`
   sang `tools.jackson.databind.json.JsonMapper`; `write()` cập nhật xử lý
   `JacksonException` (unchecked) thay cho `catch (JsonProcessingException)`.
2. FR-02: ~15 RabbitMQ consumer (đọc `com.fasterxml.jackson.databind.ObjectMapper`)
   đổi sang `JsonMapper`; rà soát từng method signature `throws IOException`
   liên quan tới `readValue` — xác nhận còn cần thiết hay bỏ.
3. FR-03: 2 config loader JSON tĩnh (`TaskSkillMappingConfig`,
   `PteTaskTypeSkillMapping`) đổi sang `JsonMapper`.
4. FR-04: `PinnedSnapshotCacheService` (Redis cache, exam-delivery) đổi sang
   `JsonMapper`.
5. FR-05: `AttemptMapper` (exam-delivery) đổi sang `JsonMapper`.
6. FR-06: Gỡ khai báo tường minh `jackson-databind` (Jackson 2, compile/provided
   scope) khỏi `pte-common/pom.xml` và toàn bộ 9 `services/*/pom.xml` — Jackson
   3 đã có sẵn transitively qua `spring-boot-starter-webmvc` →
   `spring-boot-starter-jackson`.
7. FR-07: Test hiện có liên quan (vd.
   `AnswerIngestConsumerTest`, `ProctorCommandConsumerTest`) cập nhật theo
   type mới, tiếp tục pass.

---

## Non-Functional Requirements

- Correctness: không service nào serialize/deserialize sai dữ liệu sau khi
  đổi Jackson version — verify bằng test suite hiện có + 1 lượt smoke test
  thủ công cho outbox write + consumer read trên ít nhất 1 service.
- Consistency: chỉ 1 phiên bản Jackson (3.x) tồn tại trong toàn bộ dependency
  tree sau migration, không còn Jackson 2 nào (trừ nếu 1 thư viện bên thứ 3
  khác kéo theo ngoài ý muốn — cần rà soát nếu `mvn dependency:tree` vẫn thấy
  Jackson 2).
- Build: `mvn clean install` xanh toàn bộ 13 module sau migration.

---

## Success Criteria

- [ ] Cả 9 service start standalone thành công (log "Started
      <X>Application"), 0 `NoSuchBeanDefinitionException` liên quan Jackson.
- [ ] `unzip -l` mỗi service jar chỉ còn `jackson-databind-3.x`, không còn
      `2.x`.
- [ ] `mvn clean install` build xanh, toàn bộ test suite hiện có pass không
      sửa assertion (trừ import/type thay đổi).
- [ ] Grep `com.fasterxml.jackson.databind.ObjectMapper` toàn repo trả về 0
      kết quả trong `src/main`.

---

## Out of Scope

- Đổi các annotation Jackson (`@JsonProperty`, `@JsonIgnore`...) trên DTO/event
  class — các annotation này (`com.fasterxml.jackson.annotation.*`) tương
  thích cả Jackson 2 lẫn Jackson 3, không bắt buộc đổi trong migration này trừ
  khi phát sinh lỗi compile cụ thể.
- Migrate custom Jackson module/serializer phức tạp (nếu phát hiện thêm khi
  cook) — nếu có, tách thành finding riêng, không mở rộng scope giữa chừng.
- Performance tuning cấu hình `JsonMapper` (custom `MapperFeature`,
  `SerializationFeature`...) — giữ default tương đương config Jackson 2 hiện
  tại, không tối ưu thêm.

---

## Assumptions

- 30 file đã grep (`com\.fasterxml\.jackson\.databind\.ObjectMapper`) là danh
  sách đầy đủ tại thời điểm brainstorm — cần re-verify grep lúc lập plan chi
  tiết phòng khi có file mới phát sinh.
- Không có code nào phụ thuộc hành vi checked-exception cũ của
  `JsonProcessingException` theo cách sẽ gây lỗi âm thầm nếu đổi sang
  unchecked (vd. dựa vào compiler ép handle exception để không quên xử lý) —
  cần review từng call site theo FR-02/P2 thay vì giả định an toàn.

---

## Decisions

- Kiểu tham số khi inject: **`tools.jackson.databind.json.JsonMapper`** (concrete),
  áp dụng nhất quán cho cả 30 file — theo hướng đã chốt lúc brainstorm, không
  dùng `tools.jackson.databind.ObjectMapper` (abstract base).
