# Phase 4: Stragglers, POM Cleanup & Full-Repo Verification

Covers user stories: P1 (all 9 services start standalone, zero
`NoSuchBeanDefinitionException`), P1 (single Jackson version — exactly one
`jackson-databind` line per service jar), P2 (equivalent error-handling
behavior for the remaining files). Implements FR-03, FR-04, FR-05, FR-06,
and the final fleet-wide verification for FR-01/FR-02/FR-07 and all Success
Criteria in spec.md.

## Requirements
The 6 remaining files that inject `ObjectMapper` outside the outbox/consumer
paths (2 config loaders plus the newly-found `TaskTimingConfig`, the Redis
cache service, the mapper, and the newly-found `ObjectiveScoringService`)
are migrated to `JsonMapper`; the explicit Jackson 2 `jackson-databind`
dependency is removed from all 10 `pom.xml` files that declare it
(`pte-common` + the 9 services that declare it — not `media`); and the whole
fleet is verified clean: zero remaining Jackson 2 `ObjectMapper` references,
a green `mvn clean install` across all 13 modules, and all 9 services
starting standalone without a Jackson-related
`NoSuchBeanDefinitionException`.

## Steps
1. Migrate the 3 static JSON config loaders (`TaskSkillMappingConfig` in
   reporting, `PteTaskTypeSkillMapping` in authoring, `TaskTimingConfig` in
   exam-delivery) to `JsonMapper`, reviewing each one's
   `catch (IOException | IllegalArgumentException ...)` block (the pattern
   already wraps into `IllegalStateException` at startup) for continued
   correctness under the new exception hierarchy.
2. Migrate `PinnedSnapshotCacheService` (exam-delivery) to `JsonMapper`,
   explicitly reviewing its two distinct existing behaviors — silently
   returning empty on a corrupt cache read vs. throwing on a serialization
   failure during write — to confirm both are preserved deliberately, not
   accidentally merged or dropped.
3. Migrate `AttemptMapper` (exam-delivery) and `ObjectiveScoringService`
   (scoring) to `JsonMapper`, reviewing each one's
   `catch (JsonProcessingException ...)` wrap-and-rethrow for the same
   checked-to-unchecked decision applied in every earlier phase.
4. Remove the explicit `jackson-databind` (Jackson 2) dependency declaration
   from `pte-common/pom.xml` and from each of the 9 service `pom.xml` files
   that declare it (admin, authoring, exam-delivery, iam, notification,
   proctor, reporting, scheduling, scoring), relying on the transitive
   Jackson 3 dependency already pulled in via
   `spring-boot-starter-webmvc`/`spring-boot-starter-jackson`.
5. Run a full repo-wide grep for
   `com\.fasterxml\.jackson\.databind\.ObjectMapper` across every `src/main`
   and `src/test` `.java` file and confirm zero remaining matches.
6. Run `mvn clean install` for the entire 13-module tree and confirm a green
   build with the full existing test suite passing, not just the modules
   touched by this migration.
7. For each of the 10 services (the 9 migrated services plus `media`, which
   needed no code change but still gets a regression startup check — it must
   keep starting cleanly even though nothing in it changed), start the jar
   standalone (matching the verification method that originally surfaced
   this bug) and confirm the log reaches `Started <X>Application` with zero
   `NoSuchBeanDefinitionException` related to `ObjectMapper`/`JsonMapper`.
8. For each of the 9 service jars, run `unzip -l <service>.jar | grep -i
   jackson-databind` and confirm exactly one line (`jackson-databind-3.x`),
   with no `jackson-databind-2.x` line remaining. **This step must find zero
   surviving Jackson 2 jars now that Phase 3 steps 8–9 have explicitly
   migrated every `RabbitMqConfig.java` off `Jackson2JsonMessageConverter`
   (the one class that previously forced Jackson 2 onto the classpath
   regardless of the POM cleanup in step 4)** — a `jackson-databind-2.x` line
   surviving this check is a real regression to investigate, not an expected
   "unrelated third-party library" case to wave through. If one is found,
   first re-verify Phase 3 steps 8–9 actually landed on that service before
   assuming it's genuinely unrelated; only capture it as a follow-up finding
   after ruling that out.

## Success Criteria
- `com\.fasterxml\.jackson\.databind\.ObjectMapper` returns zero matches
  anywhere under `src/main` repo-wide (spec.md's exact acceptance check).
- `mvn clean install` succeeds across all 13 modules with the full existing
  test suite green, no assertion changes beyond import/type updates.
- All 10 services (admin, authoring, exam-delivery, iam, media, notification,
  proctor, reporting, scheduling, scoring — noting `media` needed no code
  change but still gets the standalone-startup check) reach
  `Started <X>Application` when run standalone, with zero Jackson-related
  `NoSuchBeanDefinitionException`.
- `unzip -l` on each of the 9 non-`media` service jars shows exactly one
  `jackson-databind` line, version 3.x, no 2.x line present.

## Risks
- A transitive Jackson 2 dependency survives from a genuinely unrelated
  third-party library even after all explicit declarations are removed and
  Phase 3's `RabbitMqConfig` migration has landed (spec.md's own NFR flags
  this as a known open question) — mitigated by step 8 explicitly checking
  each jar's contents rather than trusting the POM edit alone. Note: the
  previously-known cause of this exact symptom (`Jackson2JsonMessageConverter`
  in `RabbitMqConfig.java`) is now fixed in Phase 3, not deferred here — if
  step 8 still finds a `jackson-databind-2.x` jar, treat it as a new,
  unexplained finding requiring investigation, not an expected/accepted
  outcome.
- `springdoc-openapi`'s internal Jackson 2 dependency (flagged by the
  alternative researcher) becomes a real conflict if springdoc/OpenAPI is
  added to any service after this migration completes — not an active risk
  today (confirmed zero direct springdoc/swagger POM declarations across all
  9 services), captured here only as a documented future re-check trigger.
- Standalone startup verification (step 7) requires Postgres + RabbitMQ
  running locally for each of the 9 services — mitigated by reusing the same
  docker-compose-based verification approach already used successfully in
  the `remove-flyway-hibernate-only` plan's Phase 3, rather than inventing a
  new verification method.
- Removing the Jackson 2 POM dependency (step 4) before all code in Phases
  1–3 is actually migrated would break the build immediately — mitigated by
  this phase's step ordering (code migration in steps 1–3, POM cleanup only
  in step 4, after which nothing in the codebase should still reference the
  old type).
