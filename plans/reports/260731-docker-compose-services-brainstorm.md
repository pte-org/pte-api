# Brainstorm: docker-compose file để chạy toàn bộ service (gateway + 10 microservice)

**Date:** 2026-07-31

## Ideas Explored

- **Build image từ source (multi-stage Dockerfile) vs build jar local rồi COPY** — chọn build từ source để không cần bước thủ công `mvn package` trước mỗi lần chạy; đánh đổi là build image lần đầu chậm hơn.
- **1 Dockerfile dùng chung ở root, parametrize qua build-arg `SERVICE_MODULE`** vs **10 Dockerfile riêng mỗi service** — chọn dùng chung vì 10 service đều là Spring Boot module giống hệt cấu trúc build (cùng parent pom, cùng cách package), tránh trùng lặp 10 file gần như y hệt.
- **Chỉ expose gateway ra host** vs **expose tất cả service ra host** vs **không gồm gateway** — chọn chỉ expose gateway, các service backend chỉ giao tiếp nội bộ qua `pte-network`, giống mô hình thật (client luôn đi qua gateway).
- **Tách compose riêng chạy song song `-f docker-compose.yml -f docker-compose.services.yml`** — đã xác nhận là hướng user chọn ngay từ đầu, không merge vào 1 file duy nhất để giữ tách biệt "infra" và "app services".

## User's Direction

Viết thêm **`docker-compose.services.yml`** dùng kèm file infra hiện có (`docker compose -f docker-compose.yml -f docker-compose.services.yml up`). Mục đích: dựng full stack (gateway + 10 service) bằng container để test tích hợp/demo, không phải để dev từng service qua IDE. Build image bằng Dockerfile multi-stage build từ source, dùng 1 Dockerfile chung parametrize theo module, chỉ gateway mở port ra host.

Điểm thuận lợi phát hiện khi scan code: toàn bộ service đã được thiết kế sẵn theo kiểu 12-factor — mọi endpoint hạ tầng (DB URL, RabbitMQ host, Redis host, Minio endpoint, mail host, OTLP endpoint, JWKS URI, các route URI trong gateway) đều đọc qua biến môi trường với default là `localhost`. Compose file mới chỉ cần override các biến `*_HOST` / `*_URI` sang tên container (`postgres`, `rabbitmq`, `redis`, `minio`, `mailpit`, `jaeger`, `iam`, `admin`, ...) — không cần đổi code Java nào.

Danh sách cổng mặc định (khớp giữa `application.yml` từng service và route trong gateway):
gateway=8080, iam=8081, admin=8082, authoring=8083, scheduling=8084, exam-delivery=8085, proctor=8086, scoring=8087, reporting=8088, notification=8089, media=8090.

Username/password DB mặc định trong `application.yml` của từng service đã khớp sẵn với role/db tạo trong `docker/postgres/init/01-create-databases.sql` (ví dụ `iam_svc`/`iam_dev_pw`) — chỉ cần đổi host `localhost` → `postgres`.

## Open Questions

- Có cần tách `.env` riêng cho file services (khác `.env` của infra) hay dùng chung 1 file `.env` ở root? → để /ck:plan quyết định khi thiết kế cụ thể.
- 10 service có build thành công hết không (một số phase có thể còn là skeleton) — cần verify bằng cách build thử trong lúc lập plan/cook, không chặn spec.
- Chạy đồng thời 10 JVM service + 6 container hạ tầng khá nặng RAM/CPU cho máy dev — có cần giới hạn resource (`mem_limit`) hay set JVM heap nhỏ qua `JAVA_TOOL_OPTIONS` không?

## Risks

- **Build context lớn:** Dockerfile dùng chung đặt ở root nghĩa là mỗi lần build 1 service, Docker phải gửi toàn bộ monorepo (10 service + pte-common) làm build context — cần `.dockerignore` tốt (loại `target/`, `.git/`, node_modules nếu có ở web khác) để không chậm.
- **Trùng lặp danh sách route/host giữa gateway và service:** khi thêm service mới trong tương lai phải nhớ sửa cả `docker-compose.services.yml` lẫn `gateway/application.yml` — không tự động hoá, dễ quên.
- **JWKS/JWT phụ thuộc iam sống trước:** service khác (notification, media, gateway) validate JWT qua `IAM_JWKS_URI` — nếu iam chưa healthy khi request tới, sẽ lỗi 401/500 tạm thời dù không phải bug thật; cần cân nhắc `depends_on: condition: service_healthy` cho iam nếu có healthcheck endpoint, hoặc chấp nhận retry ở tầng gateway.
