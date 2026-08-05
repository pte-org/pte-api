# Phase 3: RabbitMQ Consumers & Message Converters

Covers user stories: P1 (all 9 services start), P1 (single consistent
Jackson version, message-compatible fleet-wide), P2 (equivalent
error-handling behavior — the highest-stakes phase for this story, since a
mis-handled exception here crashes a live listener thread, not just a
constructor). Implements FR-02 and the consumer-side half of FR-07 (existing
consumer tests keep passing).

**Scope correction from plan review:** the original 30-file inventory was
built by grepping for the literal string `com\.fasterxml\.jackson\.databind\.
ObjectMapper`, which does not match `Jackson2JsonMessageConverter` — a
distinct Spring AMQP class that itself constructs a Jackson 2 `ObjectMapper`
internally and requires `jackson-databind` 2.x on the classpath to load. This
bean is declared in **9** `RabbitMqConfig.java` files (admin, authoring,
exam-delivery, iam, notification, proctor, reporting, scheduling, scoring —
same 9 services that declare the explicit Jackson 2 POM dependency), wired as
the `MessageConverter` for every service's `RabbitTemplate` and listener
container. If Phase 4 removes the Jackson 2 `jackson-databind` dependency
without this being migrated first, every one of these beans fails to load at
context startup — recreating the exact class of crash this whole plan exists
to fix, just discovered at the last verification gate instead of here. Verified
via `javap` against the actual `spring-amqp-4.1.0.jar` in the local Maven
repo: `org.springframework.amqp.support.converter.JacksonJsonMessageConverter`
is the Jackson-3-native replacement shipped in the same Spring AMQP version
this project already uses, with a constructor accepting
`tools.jackson.databind.json.JsonMapper` directly — no new dependency needed.

## Requirements
All 12 `@RabbitListener` consumer classes across 6 services (scoring,
exam-delivery, reporting, notification, scheduling, iam) deserialize incoming
event payloads using the Jackson 3 `JsonMapper`, with every
`JsonProcessingException`/`throws IOException` on the deserialization path
explicitly reviewed and given a deliberate decision, and the 2 existing
consumer unit tests (`AnswerIngestConsumerTest`,
`ProctorCommandConsumerTest`) updated to match and still passing. Additionally,
all 9 `RabbitMqConfig.java` `MessageConverter` beans switch from
`Jackson2JsonMessageConverter` to `JacksonJsonMessageConverter`, constructed
with the shared, format-pinned `JsonMapper` bean from Phase 1 (not a fresh
default-constructed one) so the AMQP wire format matches the outbox/REST
format exactly.

## Steps
1. Confirm Phase 1's shared date-format bean is available and `pte-common`
   itself builds clean before starting (already covered by Phase 1 step 7) —
   this phase does not require Phase 2 to have landed first (per plan.md's
   Dependencies section, Phase 2 and Phase 3 may run in either order); it
   only requires the shared `pte-common` bean Phase 1 provides. Regardless of
   ordering, consumers on the same queue as a Phase 2 producer must still
   land in the same coordinated release before deployment (Phase 4's
   fleet-wide gate), not before Phase 3 can start.
2. In each of the 12 consumer classes, change the injected/used
   `ObjectMapper` field to `JsonMapper` and update the import.
3. For every method signature carrying `throws IOException` because of a
   Jackson `readValue`/`readTree` call (found in at least
   `AnswerIngestConsumer` and `ProctorCommandConsumer`, and to be re-checked
   in the other 10), decide explicitly whether the `throws` clause is still
   needed for a different checked exception on that method, or whether it
   should be dropped now that the Jackson-specific part of it is unchecked —
   record the decision per consumer rather than applying one blanket rule.
4. For every `try/catch` around a deserialization call, apply the same
   deliberate review as Phase 1/2: keep explicit handling (e.g. dead-letter,
   log-and-skip, or an existing idempotency/dedup guard) if the current
   behavior depends on catching that exception, or confirm intentional
   propagation to Spring AMQP's container-level error handling if it does
   not.
5. Update `AnswerIngestConsumerTest` and `ProctorCommandConsumerTest` (mocks,
   imports, and any exception-type assertions) to the new `JsonMapper` type
   and confirm both tests pass.
6. Run the full test suite for each of the 6 affected service modules
   (`mvn -pl services/<name> test`) to confirm no other test silently broke
   from the exception-type or import change.
7. Run a repo-wide grep limited to each service's `messaging/consumer/`
   package to confirm zero remaining Jackson 2 `ObjectMapper` references
   across this phase's file set, including the 2 test files.
8. In each of the 9 `RabbitMqConfig.java` files, replace
   `new Jackson2JsonMessageConverter()` with
   `new JacksonJsonMessageConverter(<shared Phase 1 JsonMapper bean>)`,
   injecting the shared bean rather than letting the converter build its own
   default `JsonMapper` — update the import from
   `org.springframework.amqp.support.converter.Jackson2JsonMessageConverter`
   to `...JacksonJsonMessageConverter` accordingly.
9. Run a repo-wide grep for `Jackson2JsonMessageConverter` and
   `Jackson2XmlMessageConverter` across all `RabbitMqConfig.java` files and
   confirm zero remaining matches.
10. For each of the 9 services with a migrated `RabbitMqConfig`, run that
    service's test suite (if step 6 didn't already cover it) and, where
    feasible, a live publish/consume smoke test against local RabbitMQ to
    confirm the `MessageConverter` change doesn't alter wire format for any
    in-flight message shape.

## Success Criteria
- All 12 consumer classes import `tools.jackson.databind.json.JsonMapper`,
  not the Jackson 2 `ObjectMapper`.
- Every `throws IOException`/`catch (JsonProcessingException ...)` touch
  point identified in steps 3–4 has a recorded, deliberate decision.
- `AnswerIngestConsumerTest` and `ProctorCommandConsumerTest` pass with the
  new type, with no assertion logic changed beyond type/import updates.
- Each of the 6 affected service modules' full test suite passes
  (`mvn -pl services/<name> test` green).
- The Phase 3 grep sweep (step 7) returns zero matches for the old Jackson 2
  import across all 12 consumer classes and both test files.
- All 9 `RabbitMqConfig.java` files construct `JacksonJsonMessageConverter`
  with the shared Phase 1 `JsonMapper` bean, not `Jackson2JsonMessageConverter`.
- Step 9's grep for `Jackson2JsonMessageConverter`/`Jackson2XmlMessageConverter`
  returns zero matches repo-wide.

## Risks
- A dropped `catch` block on a consumer's deserialization path lets an
  uncaught `JacksonException` crash the `@RabbitListener` container thread
  instead of being handled/logged as before — the single highest-impact risk
  in this migration, since it affects live message processing, not just
  compile-time correctness. Mitigated by making step 4's per-consumer review
  a named, non-skippable step rather than a mechanical find-replace.
- A consumer and its upstream producer (from Phase 2) disagree on `Instant`
  format if either drifts from the shared Phase 1 bean — mitigated by both
  phases depending on the same `pte-common` shared bean rather than each
  service configuring its own format independently.
- `notification`'s 4 consumers are the largest single-service cluster in this
  phase and easiest to under-review as "just more of the same pattern" —
  mitigated by treating each of the 12 consumers as its own line item in
  steps 3–4, not a single batched notification-service edit.
- Missing the `RabbitMqConfig.java` `MessageConverter` bean migration (steps
  8–9) entirely was the plan-reviewer's BLOCK finding against the original
  version of this plan — it's the load-bearing risk this phase revision
  exists to close. Mitigated by treating it as its own explicit step/grep
  pair here rather than folding it silently into the general consumer
  migration steps 2–7, which never would have caught it (different file,
  different import, different class name).
