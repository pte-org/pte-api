# Phase 1: Dockerfile + .dockerignore

testing: skipped (infra work — verified via docker compose up + health checks, see spec Success Criteria)

Covers spec user stories: **[P1]** "một Dockerfile multi-stage dùng chung, tham số hoá qua build-arg module" (FR-01).

## Requirements
A single multi-stage Dockerfile lives at the `pte-api/` repo root, parametrized by a build-arg naming the Maven module to package, and produces a runnable image for any of the 11 modules (gateway + 10 services). A `.dockerignore` keeps the build context lean.

## Steps
1. Create `pte-api/Dockerfile` with a build stage (Maven + JDK 21 capable image) and a runtime stage (slim JRE 21 image).
2. Parametrize the build stage with a build-arg that selects the target Maven module (e.g. `services/iam`, `gateway`).
3. In the build stage, package only the target module plus its dependencies (skipping tests), using a dependency cache so repeat builds across the 11 modules aren't fully cold each time.
4. In the runtime stage, copy only the resulting jar from the build stage and set it as the container's entrypoint. Also install `curl` in the runtime stage (e.g. `apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*` if the base is Debian-based) — the slim JRE base has no HTTP client by default, and Phase 2/3 rely on `curl` being present for each container's own `healthcheck:` command against its actuator endpoint.
5. Add `pte-api/.dockerignore` excluding build output directories, VCS/IDE metadata, and log files, so the build context stays small.
6. Validate the Dockerfile in isolation by building a representative sample of modules (a simple service, a service with more dependencies, and the gateway) before wiring anything into compose.

## Success Criteria
- `docker build --build-arg SERVICE_MODULE=services/iam -t pte-iam-test .` (run from `pte-api/`) completes successfully.
- `docker build --build-arg SERVICE_MODULE=gateway -t pte-gateway-test .` completes successfully.
- `docker run --rm pte-iam-test` boots the JVM and begins Spring context initialization (full startup isn't expected without a DB — success here is "the jar runs", not "the app is healthy").
- Exactly one Dockerfile exists at the repo root, used by every module.

## Risks
- A service module doesn't currently compile cleanly (skeleton/incomplete state): surfaces immediately as a build failure for that module only — fix the module or flag it and exclude it from Phase 2 until fixed.
- Maven dependency cache not actually reused across separate `docker build` invocations without BuildKit enabled: confirm BuildKit is active (default in modern Docker / Compose v2) so the cache mount works as intended.
