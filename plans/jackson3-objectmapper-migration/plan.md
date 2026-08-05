# Plan: Migrate Jackson 2 ObjectMapper → Jackson 3 JsonMapper
Status: ✅ Complete — implementation, test, code-review PASSED
Date: 2026-08-05
Mode: Hard

## Overview
Migrates all 30 `ObjectMapper` call sites plus 9 `Jackson2JsonMessageConverter`
RabbitMQ bean declarations (39 files total — the latter found only during
plan review, see Research Summary) across `pte-common` and 9 `pte-api`
services from Jackson 2 (no longer autoconfigured by Spring Boot 4.1.0) to
Jackson 3 (`tools.jackson.databind.json.JsonMapper` /
`JacksonJsonMessageConverter`), fixing a startup-crashing
`NoSuchBeanDefinitionException` in all 9 services and leaving exactly one
Jackson major version on the classpath fleet-wide.

## Phases
- [x] Phase 1: pte-common Core — migrate `AbstractOutboxWriter` and add a
      shared bean that pins `Instant` (de)serialization format to ISO-8601
      (matching the already-live Jackson 2 default) before any
      producer/consumer service changes.
- [x] Phase 2: Outbox Writer Family — migrate all 8 per-service
      `OutboxWriter` subclasses plus the one outbox-adjacent service
      (`SnapshotPublishService`) that also injects `ObjectMapper` directly.
- [x] Phase 3: RabbitMQ Consumers & Message Converters — migrate all 12
      `@RabbitListener` consumer classes (6 services) plus their 2 existing
      unit tests, **and** all 9 `RabbitMqConfig.java`
      `Jackson2JsonMessageConverter` beans to `JacksonJsonMessageConverter`
      (a plan-review finding, see Research Summary — missed by the original
      file inventory since it's a different class name, not a literal
      `ObjectMapper` reference).
- [x] Phase 4: Stragglers, POM Cleanup & Full-Repo Verification — migrate the
      remaining 6 non-outbox/non-consumer files, remove the Jackson 2
      dependency from all 10 `pom.xml` files, and verify the whole fleet
      boots clean with a single Jackson version (including zero surviving
      `Jackson2JsonMessageConverter` usage, now that Phase 3 fixes it).

## Research Summary
Two researcher passes (primary + alternative) informed this plan, plus a
planning-time re-verification grep (per spec's own stated assumption to
re-check the file count at plan time) that found a few discrepancies against
the original 30-file description worth flagging explicitly:

- **Big-bang constraint (primary researcher, load-bearing):** all 9 services
  must ship in ONE coordinated release, never partially. RabbitMQ message
  compatibility between producer and consumer depends on both sides using the
  same Jackson major version and the same `Instant` JSON format — a mixed
  fleet (some Jackson 2, some Jackson 3) breaks consumers at runtime with
  malformed dates, not at compile time. No phase in this plan leaves a
  subset of services independently deployable while message-incompatible;
  Phases 2 and 3 are each defined as "all affected services in one pass," and
  Phase 4 is the only point where the fleet is declared consistent.
- **JSR-310 built in (primary researcher):** Jackson 3's `JsonMapper` has
  `java.time`/`Instant` support by default — no separate `JavaTimeModule`
  registration is needed. This helps because a parallel, separate effort is
  also converting entity fields from `LocalDateTime` to `Instant`.
- **CRITICAL GOTCHA — date format default flip (primary researcher):**
  Jackson 3 defaults `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS = false`
  (ISO-8601 strings), the opposite of some Jackson 2 configs. This plan does
  NOT rely on the new default silently — Phase 1 adds an explicit shared
  bean pinning the `Instant` format before any other service touches
  `JsonMapper`, so every producer/consumer pair in Phases 2–3 inherits the
  same pinned format from day one instead of an undocumented assumption.
- **CRITICAL GOTCHA — checked-to-unchecked exception change (primary
  researcher):** `JsonProcessingException` (checked, extends `IOException`)
  becomes `JacksonException` (unchecked, extends `RuntimeException`) in
  Jackson 3. Every `try/catch (JsonProcessingException ...)` and every
  `throws IOException` tied to Jackson calls across all 30 files gets an
  explicit, deliberate decision in Phases 1–4 (keep an explicit catch +
  log/wrap, or intentionally let it propagate to an existing handler) — never
  a silent deletion, since silently dropping a catch could let an uncaught
  exception crash a `@RabbitListener` container thread instead of being
  handled as before.
- **POM cleanup is correct and expected (primary researcher):** removing the
  explicit Jackson 2 `jackson-databind` dependency from `pte-common/pom.xml`
  and the 9 service `pom.xml` files that declare it is the right final step,
  not optional cleanup — leaving both Jackson stacks on the classpath would
  mask migration gaps instead of surfacing them at build time.
- **Planning-time file-count discrepancies (this session's re-verification
  grep, superseding the original brainstorm estimate per the spec's own
  stated assumption):**
  - Only **8** services have a per-service `OutboxWriter.java` that injects
    `ObjectMapper` (admin, authoring, exam-delivery, iam, proctor, reporting,
    scheduling, scoring) — **not 9**. `notification` and `media` have no
    `OutboxWriter` at all; `media` has zero `ObjectMapper` usage anywhere and
    zero explicit `jackson-databind` POM declaration, so it needs no code or
    POM change in this migration.
  - **3** static JSON config loaders exist, not 2:
    `TaskSkillMappingConfig` (reporting), `PteTaskTypeSkillMapping`
    (authoring), and previously-unlisted `TaskTimingConfig` (exam-delivery,
    same static-resource-loading pattern as the other two).
  - **2 extra business-service files** inject `ObjectMapper` for
    non-messaging JSON handling and were not named in the original
    description: `SnapshotPublishService` (authoring — deep-copies question
    options into the immutable snapshot before writing to outbox, grouped
    into Phase 2 since it shares the outbox transaction) and
    `ObjectiveScoringService` (scoring — parses stored `optionsJson` for
    rule-based grading, grouped into Phase 4 as a non-messaging straggler).
  - Consumer count is **12** `@RabbitListener` classes across 6 services
    (scoring x2, exam-delivery x1, reporting x3, notification x4,
    scheduling x1, iam x1), plus **2** existing unit tests
    (`AnswerIngestConsumerTest`, `ProctorCommandConsumerTest`) — close to but
    not exactly the "~15" estimate.
  - Grand total re-confirmed at exactly **30** `src/main` + `src/test` files
    matching `com\.fasterxml\.jackson\.databind\.ObjectMapper`, matching the
    spec's figure even though the per-category breakdown shifted slightly.
  - **9**, not 10, `pom.xml` files declare `jackson-databind` explicitly
    (all services except `media`, which never declared it) — `pte-common`'s
    `pom.xml` makes it 10 POMs total for Phase 4 cleanup, consistent with the
    spec's FR-06 wording.
- **Alternative researcher — compatibility-shim path rejected (context
  only):** a `spring-boot-jackson2` compatibility module exists but is
  documented as deprecated-on-arrival by the Spring team. This plan does not
  use it — full migration is the only phase in this plan, confirming that
  call was already correct.
- **Alternative researcher — hidden springdoc/OpenAPI risk (context only,
  not an active blocker):** `springdoc-openapi` 3.0.0 (the Spring Boot
  4-compatible release) still depends on Jackson 2 internally for schema
  generation. None of the 9 service POMs currently declare springdoc/swagger
  directly (verified), so this migration is not blocked — captured as a Risk
  below to re-check only if springdoc/OpenAPI is ever added later.
- **Plan-review finding — `RabbitMqConfig.java` message converters missed by
  the original file inventory (BLOCK, now resolved):** the 30-file grep
  boundary (`com\.fasterxml\.jackson\.databind\.ObjectMapper`) never matches
  `Jackson2JsonMessageConverter`, a distinct Spring AMQP class declared as
  the `MessageConverter` bean in 9 `RabbitMqConfig.java` files (same 9
  services as the explicit Jackson 2 POM dependency). This class internally
  constructs its own Jackson 2 `ObjectMapper` and requires `jackson-databind`
  2.x on the classpath — Phase 4's original POM cleanup would have broken it
  fleet-wide. Verified via `javap` against the real `spring-amqp-4.1.0.jar`
  in the local `.m2` repo: `JacksonJsonMessageConverter` (Jackson 3-native)
  ships in the same Spring AMQP 4.1.0 version already in use, with a
  constructor accepting `tools.jackson.databind.json.JsonMapper` directly.
  Folded into Phase 3 (steps 8–9) as an explicit migration + grep-verification
  step, and Phase 4 step 8's success check now treats a surviving
  `jackson-databind-2.x` jar as a real regression instead of an
  expected/waved-through follow-up.
- **Plan-review finding — date-format pin target unspecified (HIGH, now
  resolved):** Phase 1 originally said to "pin the format" without stating
  what value, risking an implementer pinning to epoch-millis under the
  mistaken belief that Jackson 3's ISO-8601 default is a regression to
  prevent — when ISO-8601 is actually already the live Jackson 2 behavior
  today (no service overrides `write-dates-as-timestamps` or defines a
  competing `ObjectMapper` bean). Phase 1 steps 4–5 now state the target
  value explicitly (ISO-8601, matching today's default) and expose the bean
  for Phase 3 to reuse in `JacksonJsonMessageConverter`, so AMQP and
  outbox/REST share one format instance instead of two independently
  configured mappers.

## Dependencies
None external. Self-contained within the `pte-api` monorepo. Internally
sequential: Phase 1 must land before Phase 2 (all `OutboxWriter` subclasses
extend `AbstractOutboxWriter`); Phase 2 and Phase 3 can be done in either
order relative to each other but both must land before Phase 4's fleet-wide
POM cleanup and verification, since Phase 4 assumes zero remaining Jackson 2
`ObjectMapper` references anywhere in `src/main`.

## Risks
- HIGH: A mixed fleet is deployed mid-migration (some services on Jackson 2,
  some on Jackson 3) — RabbitMQ producer/consumer pairs would serialize
  `Instant` fields differently and break message consumption at runtime.
  Mitigation: this plan has no independently-deployable intermediate state;
  Phases 2 and 3 each convert their full file set in one pass, and the
  fleet is only considered consistent after Phase 4's full-repo grep and
  standalone-startup verification pass for all 9 services.
- HIGH: Jackson 3's default flip of `DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS`
  to `false` is relied on silently instead of pinned explicitly, causing an
  undocumented format assumption between producer and consumer services.
  Mitigation: Phase 1 adds an explicit shared date-format-pinning bean before
  any other service is touched, so every later phase inherits a known,
  consistent format rather than each service's own copy of the new default.
- MEDIUM: The `JsonProcessingException` (checked) → `JacksonException`
  (unchecked) change causes a silently uncaught exception to crash a
  `@RabbitListener` container thread where a `try/catch` previously handled
  it gracefully. Mitigation: every one of the 30 files gets an explicit,
  reviewed exception-handling decision as its own step in Phases 1–4 (not a
  blanket mechanical find-replace), and Phase 3 in particular treats this as
  a per-consumer checklist item, not an afterthought.
- MEDIUM: A file is missed during the batched, per-phase migration passes
  (especially the newly-discovered `TaskTimingConfig` and
  `ObjectiveScoringService`, absent from the original 30-file description
  used to scope this feature). Mitigation: Phase 4's final step is a
  repo-wide grep for `com.fasterxml.jackson.databind.ObjectMapper` across
  `src/main`, required to return zero results before the plan is considered
  done — not just a spot-check of the files listed in this plan.
- LOW: `springdoc-openapi` 3.0.0 depends on Jackson 2 internally for schema
  generation. Not an active blocker today (no service POM currently declares
  springdoc/swagger), but removing the last Jackson 2 `ObjectMapper`
  bean/dependency fleet-wide would silently break it if springdoc is added
  later without re-checking this constraint. Mitigation: documented here as
  an explicit future re-check trigger, no action needed now.
- LOW: The `spring-boot-jackson2` compatibility module was considered and
  rejected (deprecated-on-arrival per Spring team guidance) — noted so no
  future session re-proposes it as a shortcut instead of finishing the full
  migration.
- LOW: `media` service has neither an `OutboxWriter` nor any `ObjectMapper`
  usage nor an explicit `jackson-databind` POM declaration — confirmed by
  this session's grep, so no risk of it being silently skipped; it simply
  needs zero changes and is excluded from every phase's file list on
  purpose, not by oversight.
- **FOUND DURING PHASE 4 VERIFICATION — confirmed pre-existing, unrelated
  third-party dependency, not a regression:** `unzip -l` on `media`'s jar
  (step 8) found BOTH `jackson-databind-3.1.4.jar` AND
  `jackson-databind-2.21.4.jar` — the only one of the 10 service jars with a
  surviving Jackson 2 jar. Investigated via `mvn dependency:tree` per this
  phase's own instruction (re-verify before accepting as unrelated) rather
  than assuming: `io.minio:minio:8.5.17` (the MinIO object-storage SDK
  `media` uses, never touched by any phase of this plan) transitively pulls
  `jackson-databind:2.21.4` internally — the SDK itself hasn't migrated to
  Jackson 3. Since `media`'s `pom.xml` and source were never edited in any
  phase (confirmed zero changes), this Jackson 2 presence is entirely
  pre-existing and external, not caused by this migration. Documented here
  as a known follow-up (same pattern as the springdoc-openapi risk above) —
  no action taken, since fixing a third-party SDK's own dependency tree is
  out of scope.
- NOTED (plan review): no rollback procedure is documented for the
  coordinated big-bang release. If a bad deploy requires rolling back a
  single service, that recreates the exact mixed-Jackson-fleet failure mode
  this plan exists to prevent, since a rolled-back service would be Jackson 2
  again while its RabbitMQ peers stay on Jackson 3. Not addressed with a
  formal rollback plan here, consistent with this being a dev-only system
  with no production traffic yet (same framing already established for the
  sibling `postgres-utc-timestamptz-migration` plan) — if that assumption
  changes before this ships, a rollback/deployment-ordering plan needs to be
  written first.
- NOTED (plan review): Phase 3 step 4's instruction to "confirm intentional
  propagation to Spring AMQP's container-level error handling" doesn't state
  what that handling actually does per service. Spot-checked
  `services/scoring/.../config/RabbitMqConfig.java`: its DLQ/retry
  `RetryOperationsInterceptor` recoverer catches generic `Throwable`, so the
  `JsonProcessingException` (checked) → `JacksonException` (unchecked) change
  shouldn't bypass that specific path — but this should be explicitly
  verified per service during Phase 3 execution, not assumed safe fleet-wide
  from one spot-check.
- **FOUND DURING PHASE 3 EXECUTION — pre-existing bug, not caused by this
  migration, fixed while touching the exact file it lives in:** grepping for
  `convertAndSend` (looking for other real converter usages beyond the 12
  raw-`Message` consumers) surfaced 2 `@RabbitListener` methods with a typed
  POJO parameter instead of `Message` — `EmailWorker.onEmailJob(EmailJob)`
  /`onDeadLettered(EmailJob)` (notification) and
  `AiScoringWorker.onAiScoringJob(AiScoringJob)`/`onDeadLettered(AiScoringJob)`
  (scoring) — fed by `NotificationDispatchService`/`AiScoringDispatcher`
  calling `rabbitTemplate.convertAndSend(exchange, key, pojo)`. These are the
  ONLY two places in the fleet where the `jsonMessageConverter` bean is
  actually exercised for (de)serialization — every other consumer reads a
  raw `Message` and deserializes with its own injected `JsonMapper`,
  bypassing the converter entirely. A real round-trip unit test
  (`toMessage` → `fromMessage`, not just a compile check) against the new
  `JacksonJsonMessageConverter` failed: `IllegalArgumentException: class
  '...EmailJob' is not in the trusted packages: [java.util, java.lang]`.
  Disassembled `AbstractJacksonMessageConverter.convertContent` to find the
  real cause: the "inferred type" mechanism that would let a typed listener
  parameter bypass the header-trust check reads from
  `MessageProperties.getInferredArgumentType()` (set by Spring's listener
  adapter internally) — but only takes effect when
  `alwaysConvertToInferredType` is explicitly enabled on the converter,
  which was never set. Confirmed via bytecode inspection that
  `Jackson2JsonMessageConverter`'s equivalent type mapper has the identical
  default (same trusted-packages allowlist, same flag), so this bug already
  existed before this migration — it just never surfaced because nothing had
  a real publish/consume round-trip test until now. Fixed by adding
  `converter.setAlwaysConvertToInferredType(true)` to all 9
  `jsonMessageConverter()` beans (uniform even though only 2 currently need
  it, for consistency and to pre-empt the same gap for any future POJO-typed
  listener). Verified with 2 new real round-trip unit tests
  (`RabbitMqConfigTest` in `notification` and `scoring`, `toMessage` →
  `fromMessage` with `setInferredArgumentType` set exactly as Spring's
  listener adapter does it) — both pass.

## Session Notes
<!-- Updated by cook automatically — do not edit manually -->

**Last active:** 2026-08-05 13:30
**Phase in progress:** none — all 4 phases complete, moving to code review (cook Step 4)
**Status:** Implementation complete. All 4 phases done, full-repo build green, all 10
services verified starting live (Postgres + RabbitMQ + MinIO), zero
`NoSuchBeanDefinitionException`, exactly one Jackson version per jar (except
`media`'s pre-existing/unrelated `io.minio` transitive, documented as a
follow-up, not fixed). Awaiting mandatory code review (--hard mode, no
auto-approve).

### Decisions made this session
- Phase 1: implemented the shared date-format-pinning bean as a
  `JsonMapperBuilderCustomizer` (not a replacement `JsonMapper` @Bean) in a
  new `PteJacksonConfig` (`pte-common/.../messaging/PteJacksonConfig.java`),
  registered via `META-INF/spring/org.springframework.boot.autoconfigure.
  AutoConfiguration.imports` so every service picks it up automatically
  without touching any `@SpringBootApplication`/`@ComponentScan` —
  `pte-common` had zero Spring-managed beans of its own before this,
  confirmed by grep before choosing this mechanism.
- Phase 1: empirically verified (compiled + ran a throwaway snippet against
  the real `jackson-databind-3.1.4.jar`) that Jackson 3's default `JsonMapper`
  already serializes `Instant` as ISO-8601 (`"2026-08-05T10:00:00Z"`),
  confirming the pin is a no-op today and exists purely to make the format
  explicit/audit-proof, not a behavior change.
- Phase 1: TDD — wrote `AbstractOutboxWriterTest` (3 cases) and
  `PteJacksonConfigTest` (1 case) before/alongside implementation; confirmed
  RED (compile error against old Jackson 2 signature) before implementing,
  GREEN after.
- Phase 2: all 8 `OutboxWriter.java` files are structurally identical
  boilerplate (constructor + `instantiate()`/`persist()`), so migrated
  mechanically (verified by diffing all 8 before editing) rather than writing
  a bespoke test per file — the actual serialize/wrap behavior they delegate
  to is already covered by Phase 1's `AbstractOutboxWriterTest`. No new tests
  added for these 8 files; TDD focus stayed on Phase 1's shared logic.
  Confirmed via grep that no existing test references any `OutboxWriter`
  constructor directly.
- Phase 2: `SnapshotPublishService` (authoring) has its own independent
  `serializeOptions()` JSON path — migrated `ObjectMapper` → `JsonMapper` and
  `JsonProcessingException` → `JacksonException` matching Phase 1's pattern.
  No new unit test written for it: `authoring` has zero existing test
  infrastructure (`src/test` doesn't exist for this module at all — a
  pre-existing gap, out of scope for this migration per spec's Out of Scope
  section), and building bespoke JPA entity fixtures to test one call site in
  isolation was judged disproportionate to this migration's scope; correctness
  relied on full-module build + side-by-side code review instead (same
  wrap-and-rethrow shape as the now-tested `AbstractOutboxWriter`).
- Phase 2: all 8 affected service modules (`mvn -pl ...install -DskipTests`)
  build clean, and their full existing test suites pass unchanged (including
  the 2 not-yet-migrated consumer tests, `AnswerIngestConsumerTest` and
  `ProctorCommandConsumerTest`, which are Phase 3's concern and correctly
  still compile against Jackson 2 today).

- Phase 3: migrated all 12 consumers (`ObjectMapper`→`JsonMapper`, dropped
  now-vestigial `throws IOException` — nothing else in any of these methods
  throws a checked `IOException`) + both existing consumer tests
  (mock type + field renamed). Migrated all 9 `RabbitMqConfig.java`
  (`Jackson2JsonMessageConverter`→`JacksonJsonMessageConverter(sharedJsonMapper)`).
  Found and fixed a pre-existing (not migration-caused) bug during execution:
  see the new plan.md Risks entry — `alwaysConvertToInferredType` was never
  set, so the 2 real POJO-listener flows (`EmailWorker`, `AiScoringWorker`)
  would have rejected every message with the new converter (and, per
  bytecode inspection, would already have been broken with the old Jackson 2
  converter too). Fixed by setting the flag on all 9 converter beans;
  verified with 2 new real round-trip unit tests, not just a compile check.
- Full build + test suite green across all 9 services after each step
  (`mvn install`, not just `-DskipTests`).
- Phase 4: migrated the 6 stragglers. Found a second real Jackson 3 API
  break while doing so: `JsonNode.fieldNames()` (Jackson 2, returns
  `Iterator<String>`) doesn't exist in Jackson 3 — renamed to
  `propertyNames()` (returns `Collection<String>`), so
  `.forEachRemaining(...)` became `.forEach(...)` in all 3 config loaders.
  Caught by the IDE diagnostic, not assumed. Removed the Jackson 2
  `jackson-databind` dependency from all 10 `pom.xml` files (`pte-common` +
  9 services — `media` never declared it). Full `mvn clean install` from
  repo root: **BUILD SUCCESS**, all 13 modules, full existing suite green.
  Live verification: started all 10 services standalone against
  Postgres+RabbitMQ+MinIO (docker-compose) — first attempt running all 10
  JVMs simultaneously hit a local machine OOM (paging file exhausted, a
  known local-resource issue per the `remove-flyway-hibernate-only` plan's
  own prior note, not a code bug); restarted the 3 that failed in a smaller
  batch and all 10 reached `Started <X>Application` with **zero**
  `NoSuchBeanDefinitionException`. Jar-content check: 9/10 service jars have
  exactly one `jackson-databind` line (3.1.4); `media`'s jar has both 3.1.4
  and 2.21.4 — investigated via `mvn dependency:tree` rather than assumed
  unrelated, confirmed `io.minio:minio:8.5.17` (a pre-existing SDK dependency
  of `media`, never touched by this plan) pulls Jackson 2 internally —
  documented as a known, unrelated follow-up per the plan's own risk
  language, not fixed (third-party SDK's own dependency tree, out of scope).

### Next immediate action
All 4 implementation phases complete. Next: mandatory code review
(cook Step 4, `--hard` mode — no auto-approve), then finalize (project-manager
/ docs-manager / git-manager, cook Step 5).
