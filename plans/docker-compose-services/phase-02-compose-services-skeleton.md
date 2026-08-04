# Phase 2: docker-compose.services.yml Skeleton (10 Backend Services)

testing: skipped (infra work — verified via docker compose up + health checks, see spec Success Criteria)

Covers spec user stories: **[P1]** "mỗi service build image ... tham số hoá qua build-arg module" (FR-02), **[P1]** "chỉ gateway expose port ... service khác chỉ giao tiếp nội bộ" (backend half of FR-05), **[P2]** "service tự chờ Postgres/RabbitMQ sẵn sàng" (FR-06).

## Requirements
`docker-compose.services.yml` declares the 10 backend services (everything except gateway), each building from the shared Dockerfile with its own module build-arg, joined to the existing `pte-network`, with every `localhost`-default env var re-pointed at the correct container hostname, and startup correctly gated on Postgres/RabbitMQ health.

## Steps
1. Create `docker-compose.services.yml` declaring the 10 backend services, each with a `build` block (context `.`, shared `Dockerfile`, `args.SERVICE_MODULE` set per service) and membership in `pte-network`.
2. Override each service's database URL env var to point at the `postgres` container using that service's actual database name (note: `exam-delivery`'s database is `exam_delivery` with an underscore, while its module directory is `exam-delivery` with a hyphen — don't conflate the two).
3. Override the RabbitMQ host env var and add a `depends_on: rabbitmq (service_healthy)` gate on the 9 services that declare RabbitMQ config in their `application.yml`; deliberately exclude `media`, which has no RabbitMQ configuration at all.
4. Add `depends_on: postgres (service_healthy)` on all 10 backend services, plus the remaining infra overrides where each service actually uses them: Redis host (services with a Redis-backed feature), shared OTLP tracing endpoint (all 10), MinIO endpoint/credentials (`media` only), mail host/port (`notification` only).
5. Override the direct inter-service base-URL env vars that bypass the gateway — `exam-delivery`'s calls to authoring and scheduling, `scheduling`'s call to authoring, `proctor`'s call to scheduling, and `reporting`'s calls to exam-delivery and scoring — to point at the corresponding container hostname, preserving each target service's context path in the URL.
6. Confirm none of the 10 backend service definitions include a `ports:` mapping.
7. Add a `healthcheck:` block to each of the 10 backend service definitions, `curl`-ing that service's own actuator health endpoint including its context path (e.g. `http://localhost:8081/api/iam/actuator/health` for `iam`), matching the interval/timeout/retries/start_period pattern already used for the infra services in `docker-compose.yml`. Without this, Compose reports these containers as "running" but never "healthy," which Phase 4's verification step depends on to tell a genuinely-ready service apart from one still starting or crash-looping.

## Success Criteria
- `docker compose -f docker-compose.yml -f docker-compose.services.yml config` parses cleanly and shows all 10 backend services with their build args, network membership, healthcheck blocks, and no host `ports:` entries.
- `docker compose -f docker-compose.yml -f docker-compose.services.yml up --build` for the 10 backend services (gateway added in Phase 3) starts without a connection-refused/Flyway crash loop, and each eventually reports `healthy` via `docker compose ps`.
- `media`'s logs show no attempted RabbitMQ connection or failed AMQP handshake.
- Every database URL override resolves to the correct database name per service, verified by successful Flyway migration in each service's startup logs.

## Risks
- Env var name typo (e.g. wrong `{SERVICE}_DB_URL` prefix) silently falls back to the `localhost` default and fails only when the container actually tries to connect: check every override literally against the service's own `application.yml`, not from memory or another service's pattern.
- Treating RabbitMQ dependency as uniform across all 10 services (it isn't — `media` has none): explicitly exclude `media` from both the env override and the `depends_on` gate, don't copy-paste the block from another service.
- Missed inter-service base-URL override leaves a cross-service call silently pointing at `localhost` inside the container — works when the service starts, fails only when that specific code path is exercised: covered explicitly in Phase 4's end-to-end check, not just a "container is running" check.
- Wrong context path in a healthcheck URL makes Compose report a genuinely-fine service as unhealthy (false negative): copy the context path from that service's own `application.yml` (`server.servlet.context-path`), not from another service.
