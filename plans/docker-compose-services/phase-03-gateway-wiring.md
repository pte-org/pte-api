# Phase 3: Gateway-Specific Wiring

testing: skipped (infra work — verified via docker compose up + health checks, see spec Success Criteria)

Covers spec user stories: **[P1]** "chạy `docker compose up --build` để test end-to-end qua gateway" (FR-04), **[P1]** "chỉ gateway expose port ra host" (gateway half of FR-05).

## Requirements
The gateway is added to `docker-compose.services.yml` as the single service exposed to the host, with every one of its 10 route URIs (plus the websocket variant) and its JWKS endpoint pointed at the containerized backend services instead of `localhost`.

## Steps
1. Add the `gateway` service block to `docker-compose.services.yml`, building from the shared Dockerfile with its own module build-arg, joined to `pte-network`.
2. Map the gateway's container port to the host (host port configurable via env var, defaulting to 8080), and confirm it is the only service in this file with a `ports:` entry.
3. Override every gateway route URI — including the websocket proctor route, which must keep its `ws://` scheme — to point at the matching backend service's container name and port.
4. Override the gateway's JWKS URI and Redis host env vars to point at the containerized `iam` and `redis`.
5. Leave the gateway without a hard `depends_on`/healthy gate on the backend services, consistent with the spec's decision that JWT-consuming services accept a retry window on first boot rather than blocking startup.
6. Add a `healthcheck:` block to the gateway service, `curl`-ing its own actuator health endpoint (`http://localhost:8080/actuator/health`, no context-path prefix per its `application.yml`), matching the pattern added to the 10 backend services in Phase 2.

## Success Criteria
- `docker compose -f docker-compose.yml -f docker-compose.services.yml config` shows `gateway` as the only service with a `ports:` entry, mapped to port 8080 (or the overridden value), and includes a `healthcheck:` block.
- The rendered config (via `docker compose config`) shows every gateway route URI and the JWKS URI resolved to a container hostname, not `localhost`.
- `docker compose up gateway` (with its dependencies already running from Phase 2) starts without route-resolution or bean-creation errors in its logs, and eventually reports `healthy` via `docker compose ps`.

## Risks
- Missing or mistyped route URI override for one of the 10 routes leaves that route pointing at `localhost:<port>`, which doesn't exist inside the container network: cross-check the full route list (`IAM_URI`, `ADMIN_URI`, `AUTHORING_URI`, `SCHEDULING_URI`, `EXAM_DELIVERY_URI`, `PROCTOR_URI`, `PROCTOR_WS_URI`, `SCORING_URI`, `REPORTING_URI`, `NOTIFICATION_URI`, `MEDIA_URI`) one by one against the gateway's `application.yml`.
- Gateway boots before `iam` is ready, so its first JWKS fetch may fail: acceptable per spec (only Postgres/RabbitMQ are hard-gated); document as an expected first-boot retry window rather than treating it as a defect.
