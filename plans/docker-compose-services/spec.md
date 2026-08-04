# Spec: docker-compose.services.yml — chạy toàn bộ service kèm hạ tầng hiện có

**Date:** 2026-07-31
**Status:** Draft

---

## Problem Statement

Hiện tại `docker-compose.yml` chỉ dựng hạ tầng (Postgres, Redis, MinIO, Mailpit, RabbitMQ, Jaeger). Muốn chạy thử toàn bộ hệ thống (gateway + 10 microservice) để test tích hợp hoặc demo thì phải tự build/chạy từng service bằng tay. Cần thêm 1 file compose thứ hai, dùng kèm file infra (`docker compose -f docker-compose.yml -f docker-compose.services.yml up`), để dựng full stack bằng container chỉ với 1 lệnh.

---

## User Stories

- **[P1]** Là dev, tôi muốn chạy `docker compose -f docker-compose.yml -f docker-compose.services.yml up --build` để dựng toàn bộ hạ tầng + gateway + 10 service trong container, nhằm test end-to-end hoặc demo mà không cần mở IDE chạy từng service.
  Accepted when: sau khi chạy lệnh, gọi API qua `http://localhost:8080/api/iam/...` (qua gateway) trả về response hợp lệ, không lỗi kết nối DB/RabbitMQ/Redis.

- **[P1]** Là dev, tôi muốn mỗi service build image từ source qua Dockerfile multi-stage dùng chung 1 file, tham số hoá qua build-arg module, để không phải duy trì 10 Dockerfile gần giống hệt nhau.
  Accepted when: chỉ có 1 Dockerfile ở root repo; mỗi service trong `docker-compose.services.yml` reference Dockerfile đó với `build.args.SERVICE_MODULE` khác nhau; build image thành công cho cả 10 service + gateway.

- **[P1]** Là dev, tôi muốn chỉ gateway expose port ra host, các service backend chỉ giao tiếp nội bộ qua network `pte-network`, để môi trường test giống kiến trúc thật (client luôn qua gateway).
  Accepted when: `docker compose ps` chỉ thấy port host mapping cho gateway (8080); các service khác không có `ports:` map ra host nhưng vẫn gọi được lẫn nhau qua tên container trong network.

- **[P2]** Là dev, tôi muốn các service tự chờ Postgres/RabbitMQ sẵn sàng (`depends_on` + healthcheck) trước khi start, để tránh lỗi Flyway/connection refused khi mới `up`.
  Accepted when: `docker compose up` từ trạng thái sạch (chưa có container) khởi động thành công không cần restart thủ công.

- **[P3]** _(out of scope)_ Hot-reload / mount source code để dev từng service qua container thay vì IDE.

---

## Functional Requirements

1. FR-01: Thêm 1 Dockerfile multi-stage ở root repo (`pte-api/Dockerfile`), build-arg `SERVICE_MODULE` xác định module Maven cần build (`services/iam`, `gateway`, ...), chạy `mvn -pl <module> -am package -DskipTests` ở build stage, copy jar kết quả sang runtime stage (JRE 21 slim), `ENTRYPOINT java -jar app.jar`.
2. FR-02: Thêm `docker-compose.services.yml` tại `pte-api/` khai báo 11 service (gateway + 10 backend), mỗi service dùng `build: {context: ., dockerfile: Dockerfile, args: {SERVICE_MODULE: ...}}`, join network `pte-network` (network đã khai báo ở `docker-compose.yml`, không cần `external: true` vì luôn chạy chung 2 file trong 1 lệnh).
3. FR-03: Override toàn bộ biến môi trường `*_HOST`/`*_URI` của từng service từ default `localhost` sang tên container tương ứng: `RABBITMQ_HOST=rabbitmq`, `REDIS_HOST=redis`, `MAIL_HOST=mailpit`, `MINIO_ENDPOINT=http://minio:9000`, `OTLP_ENDPOINT=http://jaeger:4318/v1/traces`, `IAM_JWKS_URI=http://iam:8081/api/iam/auth/jwks`, và từng `{SERVICE}_DB_URL=jdbc:postgresql://postgres:5432/{db}` (username/password giữ nguyên default đã khớp sẵn với `01-create-databases.sql`).
4. FR-04: Override route URI trong gateway (`IAM_URI`, `ADMIN_URI`, `AUTHORING_URI`, `SCHEDULING_URI`, `EXAM_DELIVERY_URI`, `PROCTOR_URI`, `PROCTOR_WS_URI`, `SCORING_URI`, `REPORTING_URI`, `NOTIFICATION_URI`, `MEDIA_URI`) trỏ sang `http://<service-name>:<port>` tương ứng.
5. FR-05: Chỉ `gateway` có `ports: ["${GATEWAY_PORT:-8080}:8080"]` map ra host; 10 service backend không có `ports:` (chỉ expose nội bộ qua network).
6. FR-06: Mỗi service có `depends_on` với `condition: service_healthy` cho `postgres` và `rabbitmq` (những service dùng outbox/queue); service dùng JWT (gateway, notification, media, ...) không bắt buộc chờ `iam` healthy (chấp nhận retry ở runtime, ghi chú trong README nếu cần).

---

## Non-Functional Requirements

- Performance: build image lần đầu cho toàn bộ 11 service hoàn tất trong thời gian hợp lý trên máy dev thông thường (không đặt số cứng — phụ thuộc máy, nhưng cần `.dockerignore` loại bỏ `target/`, `.git/` để tránh build context phình to).
- Security: không commit password thật; giữ nguyên convention hiện tại (default dev password trong `application.yml`/`01-create-databases.sql`, chỉ dùng local).
- Availability: `docker compose up` từ clean state khởi động thành công không cần can thiệp thủ công (nhờ healthcheck + depends_on).

---

## Success Criteria

- [ ] `docker compose -f docker-compose.yml -f docker-compose.services.yml up --build` khởi động đủ 17 container (6 infra + gateway + 10 service) không lỗi.
- [ ] Gọi `GET http://localhost:8080/api/iam/actuator/health` (qua gateway) trả 200.
- [ ] Không service backend nào có port map ra host ngoại trừ gateway.
- [ ] Chỉ tồn tại 1 Dockerfile dùng chung cho toàn bộ 11 service.

---

## Out of Scope

- Hot-reload / dev qua container cho từng service đang sửa code (đã chọn hướng "full stack container", không phải hybrid IDE+container).
- CI/CD, production deployment manifest (k8s, v.v.) — chỉ scope local dev/demo.
- Native image / GraalVM build.

---

## Assumptions

- Toàn bộ 10 service + gateway build thành công qua `mvn package` hiện tại (chưa verify từng service — nếu service nào đang là skeleton chưa compile được, sẽ lộ ra khi build image ở bước /ck:cook).
- Danh sách port/env var đọc từ `application.yml` hiện tại là nguồn sự thật duy nhất (không có override khác ở profile `docker`/`prod` chưa được thấy).
- `docker-compose.yml` hiện tại không đổi (mạng `pte-network`, tên container `pte-postgres`... không đổi tên service key `postgres`, `redis`, ... trong compose).

---

## [NEEDS CLARIFICATION]

- [ ] Dùng chung `.env` ở root cho cả 2 file compose, hay tạo `.env` riêng cho `docker-compose.services.yml`? (không chặn plan — có thể quyết lúc viết file, ảnh hưởng nhỏ)
- [ ] Có cần giới hạn RAM/CPU (`deploy.resources.limits` hoặc `JAVA_TOOL_OPTIONS=-Xmx...`) cho 10 JVM service chạy đồng thời trên máy dev không?
