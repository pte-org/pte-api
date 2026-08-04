# Plan: docker-compose.services.yml — Full-Stack Local Compose
Status: 🟡 In Progress
Date: 2026-07-31
Mode: Hard
Testing: --no-test (infrastructure/build config — no unit tests apply; verification is functional via `docker compose up` + health checks, see spec Success Criteria)

## Overview
Add a shared multi-stage `Dockerfile` and a second compose file (`docker-compose.services.yml`) so `docker compose -f docker-compose.yml -f docker-compose.services.yml up --build` boots the gateway and all 10 backend microservices as containers alongside the existing infra stack, for local integration testing/demo without an IDE.

## Phases
- [x] Phase 1: Dockerfile + .dockerignore — shared parametrized multi-stage build, verified in isolation per module
- [ ] Phase 2: docker-compose.services.yml skeleton — 10 backend services wired to network, healthy-gated depends_on, infra + inter-service env overrides
- [ ] Phase 3: Gateway wiring — sole host-exposed service, all 10 route URIs + JWKS pointed at container names
- [ ] Phase 4: End-to-end verification — clean-state `up --build`, container count, gateway health check, port-exposure audit

## Research Summary
Both plan-researcher reports converged on the same approach:
- **One shared root Dockerfile** (`pte-api/Dockerfile`), multi-stage, parametrized via `ARG SERVICE_MODULE` (e.g. `services/iam`, `gateway`). Build stage packages only the target module + its dependencies (`-pl ${SERVICE_MODULE} -am package -DskipTests`) with a Maven dependency cache mount; runtime stage is a slim JRE 21 image copying only the resulting fat jar and running it via `ENTRYPOINT ["java","-jar","app.jar"]`.
  - Correction found during codebase verification: a bare `eclipse-temurin:21-jdk` image does not ship a `mvn` binary — the build stage should use a combined Maven+JDK21 image (e.g. `maven:3.9-eclipse-temurin-21`) rather than plain JDK, or fall back to the repo's `mvnw` wrapper. This is an implementation detail for Phase 1, not a scope change.
- **Plain fat-jar, no layertools.** Spring Boot layered jars were explicitly rejected as unnecessary complexity for local dev/demo.
- **Rejected: Jib/Buildpacks (`spring-boot:build-image`).** Breaks the single `docker compose build` workflow — it needs a separate Maven invocation per module outside Compose. An explicit build-arg Dockerfile stays compose-native and keeps the whole stack buildable with one command.
- **`.dockerignore` at `pte-api/` root** (`**/target/`, `.git/`, `.idea/`, `*.iml`, `.vscode/`, `**/*.log`). Build context is already scoped to `pte-api/` since both compose files live there, so sibling repos (`pte-web`, `pte-app`, `pte-doc`) are outside context by construction.
- **Codebase verification found one gap not in the original facts:** `media` is the only backend service with **no `spring.rabbitmq` configuration** — it must be excluded from the RabbitMQ env override and from the `depends_on: rabbitmq (service_healthy)` gate that applies to the other 9 services. Applying it universally to "all 10" (as the initial research assumption stated) would add a spurious, never-satisfied dependency for media.
- **Codebase verification also found undocumented inter-service calls** that bypass the gateway entirely and must also be re-pointed at container hostnames for the stack to function end-to-end: `exam-delivery` → `AUTHORING_URL`/`SCHEDULING_URL`, `scheduling` → `AUTHORING_URL`, `proctor` → `SCHEDULING_URL`, `reporting` → `EXAM_DELIVERY_URL`/`SCORING_URL` (these are full base-URLs including the service's context path, distinct from the gateway's `*_URI` route variables which omit it). Folded into Phase 2.

## Dependencies
- Docker Engine + Compose v2 (`docker compose`, not standalone `docker-compose`) with BuildKit enabled (default in modern Docker) for the Maven dependency cache mount in Phase 1.
- All 11 Maven modules (`gateway` + 10 `services/*`) must currently compile via `mvn package` — unverified per spec Assumptions; any module still at skeleton/non-compiling state will surface as a build failure in Phase 1 and blocks that module only.
- Existing `docker-compose.yml` (network `pte-network`, container names, `01-create-databases.sql`) is unchanged by this work — the new file is additive.

## Risks
- HIGH: A backend module doesn't currently compile cleanly under `mvn -pl <module> -am package` — surfaces only when its image is built. Mitigation: Phase 1 builds every module individually before Phase 2 wires them into compose, isolating the failure to one module instead of discovering it mid-`up --build`.
- MEDIUM: Env var name mismatch between compose override and `application.yml` default silently falls back to `localhost` inside the container (looks fine until first real network call fails). Mitigation: every override in Phase 2/3 is checked literally against the service's `application.yml`, not written from memory; Phase 4 exercises actual network calls, not just container "started" status.
- MEDIUM: Cold-start ordering race (Flyway/connection) even with `depends_on: condition: service_healthy`, since app-level readiness can still lag past DB "healthy". Mitigation: Spring Boot's default datasource retry/backoff typically self-heals within the container's own startup; Phase 4 explicitly verifies a clean-state `up` reaches steady state without manual restarts, and documents it as a known transient if a one-time restart is ever needed.
- LOW: **[NEEDS CLARIFICATION — resolved with default]** Shared vs. separate `.env` for the two compose files. Decision: reuse a single root `.env` shared by both `docker-compose.yml` and `docker-compose.services.yml`, since they are always invoked together via `-f -f` in one command and a single source of truth for credentials (`POSTGRES_USER`, `RABBITMQ_USER`, `MINIO_ROOT_USER`, etc.) is simpler to maintain. Revisit only if the two files are ever run independently.
- LOW: **[NEEDS CLARIFICATION — resolved with default]** No RAM/CPU limits (`deploy.resources.limits` or `JAVA_TOOL_OPTIONS=-Xmx...`) are set for the 10 concurrent JVMs in this pass — out of scope for a local dev/demo stack. Flagged as a future tuning item if dev machines show memory pressure running the full stack; not blocking for this plan.

## Red-Team Review Findings (plan-reviewer, verdict: WARN)
- HIGH (ACCEPTED): `docker-compose.yml`'s `pte_postgres_data`/`pte_minio_data` volumes are shared with the plain IDE-based dev workflow (infra-only compose run alone). Phase 4's teardown step must not treat `down -v` as routine — it silently wipes that other workflow's data too. Fixed in phase-04 Step 1 (explicit check-before-teardown).
- MEDIUM (ACCEPTED): A pre-existing `pte_postgres_data` volume predating some of the 10 per-service databases won't retroactively get them — Postgres only runs `01-create-databases.sql` on an empty data directory. Documented as a first-use prerequisite in phase-04 Step 2.
- MEDIUM (ACCEPTED): None of the 11 app containers (gateway + 10 services) had a `healthcheck:` block in the original phase drafts, so Compose could only ever report them `running`, never `healthy` — making Phase 4's "running/healthy" wording untestable. Fixed by adding an actuator-based `healthcheck:` to every app service in Phase 2 (step 7) and Phase 3 (step 6), and rewording Phase 4 to verify actual `healthy` status.
- LOW (NOTED): `pte-api/.env.example` still reflects a pre-microservices layout (`POSTGRES_PORT=5433`, single `DB_URL`, `JWT_SECRET`, `CLOUDINARY_*`) and doesn't back the "shared `.env` as single source of truth" framing above — not blocking since every compose var already has a working `${VAR:-default}` fallback, but the `.env.example` file itself is stale and could confuse whoever reads it next. Consider a documentation-only pass to refresh it, outside this plan's scope.
- LOW (NOTED): 10 concurrent JVMs + 6 infra containers is a realistic OOM/flakiness risk on 8–16GB dev laptops — already consciously deferred above (RAM/CPU limits out of scope); restated here since the reviewer flagged it independently. If Phase 4 sees instability, the first thing to try is increasing Docker Desktop's memory allocation, not necessarily a compose change.
- **BLOCKING (found during Phase 2 runtime verification, 2026-08-04): pre-existing Flyway-vs-Hibernate ordering bug affects all 10 backend services identically** — Hibernate's `ddl-auto: validate` runs and fails (`missing table [...]`) before Flyway creates the schema; no Flyway log output at all. Confirmed independent of Docker/compose by reproducing identically with a plain `java -jar` run on the host, outside any container. See Session Notes above for full investigation. This blocks Phase 2/4's runtime success criteria (services reaching `healthy`, successful Flyway migration in logs) until fixed at the application level — out of scope for this plan's phases to fix. Pipeline paused here per user decision (2026-08-04) pending an out-of-band fix.

## Session Notes
<!-- Updated by cook automatically — do not edit manually -->

**Last active:** 2026-08-04 12:24
**Phase in progress:** phase-02-compose-services-skeleton
**Status:** ⛔ PAUSED — blocked on a pre-existing application bug (not a Dockerfile/compose defect). User chose to pause `/ck:cook` here and fix the bug outside this pipeline before resuming.

### Decisions made this session
- Build stage base image: `maven:3.9-eclipse-temurin-21` (has both `mvn` and JDK 21 — plain `eclipse-temurin:21-jdk` lacks `mvn`, per plan.md Research Summary correction).
- Runtime base image: `eclipse-temurin:21-jre` (Debian-based, not Alpine) — `apt-get install curl` used for the healthcheck client per the earlier user decision.
- `.dockerignore` also excludes `plans/` (large tree of unrelated markdown/JSON, no reason to ship it into any build context).
- Phase 1 verified directly: built `services/iam`, `services/media` (MinIO-dependent), and `gateway` — all three succeeded; `docker run --rm` booted Spring context and failed only on the expected `localhost:5432` connection refusal (no DB in that isolated run) — matches the phase's own success bar ("the jar runs", not "the app is healthy"). Test images removed after verification.
- Phase 2: wrote `docker-compose.services.yml` for the 10 backend services (build args, `pte-network`, DB/RabbitMQ/Redis/MinIO/Mail/OTLP/JWKS/inter-service env overrides, actuator healthchecks, `depends_on`). Verified via `docker compose config` (JSON, parsed with Node since neither `python3` nor `jq` are available in this shell) that all 10 services have no `ports:`, correct `healthcheck`, correct `pte-network` membership, and correct per-service `depends_on` (media excludes rabbitmq, includes minio; exam-delivery includes redis; notification includes mailpit) — config-level success criteria all PASS.

### Blocker found during runtime verification (NOT a compose/Dockerfile defect)
Ran `docker compose -f docker-compose.yml -f docker-compose.services.yml up --build` for real (infra + all 10 backend services, gateway not yet added). All 10 built and started, connected to Postgres successfully, then **every single one crashed** with the same shape of error:
```
org.hibernate.tool.schema.spi.SchemaManagementException: Schema validation: missing table [login_hashes]   (iam)
org.hibernate.tool.schema.spi.SchemaManagementException: Schema validation: missing table [media_objects]  (media)
```
No Flyway log output at all (no banner, no "Successfully validated N migrations") on any service — Hibernate's schema validation runs and fails before Flyway ever gets a chance to create the tables.

**Confirmed this is application-level, not Docker/compose-caused**, by running `iam` completely outside Docker: built locally with `./mvnw.cmd -pl services/iam -am package -DskipTests`, ran `java -Duser.timezone=UTC -jar services/iam/target/*.jar` directly on the host against the same Postgres container (exposed on `localhost:5432` by the base `docker-compose.yml`) — **identical failure**, `missing table [login_hashes]`, no Flyway output. (A `-Duser.timezone=UTC` override was needed for this local run only, because the host machine's default timezone `Asia/Saigon` isn't accepted by this Postgres/pgjdbc combination as a session `TimeZone` parameter — a separate, host-only quirk, unrelated to the Flyway finding; Docker containers default to UTC so this never surfaces there.)

Verified via `jar tf` on the built image's jar that `flyway-core-12.4.0.jar` and `flyway-database-postgresql-12.4.0.jar` **are** bundled in `BOOT-INF/lib/`, and the migration SQL files **are** present under `BOOT-INF/classes/db/migration/` — so this isn't a missing-dependency or missing-resource problem either.

**Working hypothesis (unconfirmed):** Spring Boot 4.1.0 modularized its autoconfiguration classes by technology (evidence: the JPA autoconfig class is now `org.springframework.boot.hibernate.autoconfigure.HibernateJpaConfiguration`, a renamed/relocated package vs. older Boot 3.x `org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration`). `FlywayAutoConfiguration`'s `@AutoConfigureBefore` ordering against the Hibernate JPA autoconfiguration may not resolve correctly across this new package boundary, so Flyway silently loses its "run before Hibernate" guarantee and never gets to create the schema before Hibernate's `ddl-auto: validate` inspects it. This is unverified — decompiling the relevant autoconfigure jar's ordering metadata would confirm it, but that dive was explicitly not pursued (user paused the pipeline here instead).

**Affects all 10 backend services identically** (same parent POM, same Boot version, same Flyway + JPA + `ddl-auto: validate` pattern) — this is a platform-wide issue, not specific to any one service's code.

### Next immediate action (on resume)
1. Fix/confirm the Flyway-vs-Hibernate ordering issue at the application level (outside this plan's scope — tracked here only as a blocker, not something this plan's phases should touch).
2. Once at least one service (recommend `iam`, already the one investigated) demonstrably runs Flyway successfully against a fresh DB, re-run Phase 2's verification: `docker compose -f docker-compose.yml -f docker-compose.services.yml up --build` for the 10 backend services and confirm all reach `healthy` with successful Flyway migration in logs.
3. Then continue to Phase 3 (gateway wiring) and Phase 4 (end-to-end verification) as originally planned — no changes anticipated to `Dockerfile`, `.dockerignore`, or `docker-compose.services.yml` are expected as part of that fix, since the config-level success criteria for Phase 1/2 already passed independently of this bug.
