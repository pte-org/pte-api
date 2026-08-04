# Phase 4: End-to-End Verification

testing: skipped (infra work — verified via docker compose up + health checks, see spec Success Criteria)

Covers spec user stories: **[P1]** "chạy 1 lệnh dựng toàn bộ hạ tầng + gateway + 10 service, gọi API qua gateway trả về response hợp lệ" (primary story), **[P1]** "chỉ gateway expose port" (verification of FR-05), **[P2]** "khởi động thành công không cần restart thủ công" (verification of FR-06).

## Requirements
Starting from a clean state, the single combined compose command brings up the full 17-container stack (6 infra + gateway + 10 services), and a client request through the gateway to a backend service succeeds with no DB/RabbitMQ/Redis connection errors.

## Steps
1. Confirmed with the user (2026-07-31): current `pte_postgres_data`/`pte_minio_data` hold no data worth keeping, so `down -v` is safe for this pass. **This confirmation does not carry forward** — these volumes are shared with the ordinary IDE-based dev workflow (infra-only `docker-compose.yml` run alone), so re-check before running `down -v` on any future run once real dev data accumulates.
2. If starting from a **pre-existing** `pte_postgres_data` volume that predates some of the 10 per-service databases in `01-create-databases.sql`, note that Postgres only runs `/docker-entrypoint-initdb.d` scripts on an *empty* data directory — a stale volume will not retroactively create the missing databases, and the affected service will fail Flyway with "database does not exist" (a stale-volume symptom, not a compose/env bug). A genuinely clean volume is required the first time this full-stack file is used.
3. Run `docker compose -f docker-compose.yml -f docker-compose.services.yml up --build` and watch containers reach steady state: the 6 pre-existing infra services report `healthy` (their existing healthchecks), and the gateway + 10 backend services — now that Phase 2/3 gave them `healthcheck:` blocks — also report `healthy` rather than just `running`.
4. Confirm the total container count matches expectations (6 infra + gateway + 10 services = 17), all reporting `healthy`.
5. Call the gateway's health endpoint for at least one routed backend service and confirm a successful response with no DB/RabbitMQ/Redis connection errors in that service's logs.
6. Inspect `docker compose ps` to confirm only the gateway has a host port mapping.
7. Record and fix any deviation (crash-looping container, missing env override, failed inter-service call) by patching the Dockerfile or compose file from the earlier phases, then re-run from a clean state until stable.

## Success Criteria
- All four spec Success Criteria checkboxes are satisfied: 17 containers up clean and reporting `healthy`; `GET http://localhost:8080/api/iam/actuator/health` returns 200 through the gateway; no backend service other than gateway has a host port mapping; exactly one Dockerfile is used by all 11 service images.
- A clean-state `up --build` reaches steady state (all 17 containers `healthy`) without any manual container restart.
- `docker compose ps` output confirms port-exposure matches spec FR-05 exactly.

## Risks
- A module that built fine in Phase 1 isolation still fails once real runtime dependencies (DB/RabbitMQ) are involved: rerun `docker compose logs <service>` to isolate whether it's a build-time or run-time issue, and fix at the source (Dockerfile for build issues, compose env overrides for runtime issues).
- Cold-start ordering race even with health-gated `depends_on` (e.g. Flyway migration lock, JVM startup lag past DB "healthy"): Spring Boot's default connection retry typically self-heals within the container's own startup; treat a one-time settle delay as expected, but a persistent crash loop as a real defect requiring a compose fix.
- Running `down -v` routinely (e.g. scripting this verification into a loop) will keep silently wiping the shared dev volumes — treat teardown as a deliberate, confirmed action, not an automated step in a repeatable script.
