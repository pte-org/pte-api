# Phase 1: pte-common Core

Covers user stories: P1 (application context startup, since every other
phase's `OutboxWriter` extends this class), P1 (single consistent Jackson
version), P2 (equivalent error-handling behavior). Implements FR-01 (partial
— the shared base class) and lays the foundation `JsonMapper` bean/format
pinning that FR-01–FR-05 all depend on.

## Requirements
`pte-common`'s `AbstractOutboxWriter` compiles and serializes outbox payloads
using the Jackson 3 `JsonMapper` instead of the Jackson 2 `ObjectMapper`, and
a shared bean exists that pins `Instant` (de)serialization to an explicit,
documented format — so every service that consumes this shared class later
in Phases 2–4 inherits one known format instead of each service silently
picking up Jackson 3's new ISO-8601 default independently.

## Steps
1. Re-confirm the current `AbstractOutboxWriter` constructor, `write()`
   method, and its single `catch (JsonProcessingException ...)` block as the
   starting point for the swap.
2. Change the injected type from the Jackson 2 `ObjectMapper` to the Jackson
   3 `JsonMapper`, updating the import and constructor signature.
3. Review the existing `catch (JsonProcessingException ex)` block against the
   new unchecked `JacksonException` hierarchy and make a deliberate decision
   — keep an explicit catch that wraps and rethrows as
   `IllegalStateException` (matching current behavior) rather than letting it
   silently disappear because the exception is no longer checked.
4. Add a new shared bean in `pte-common` (a `JsonMapper`-builder-level
   customization, not a per-service override) that explicitly pins the
   `Instant` (de)serialization format to **ISO-8601 strings**
   (`DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS = false`) — this is not a new
   choice, it matches the format Spring Boot's autoconfigured Jackson 2
   `Jackson2ObjectMapperBuilder` already produces today (verified: no service
   currently overrides `spring.jackson.serialization.write-dates-as-timestamps`
   or defines a competing `ObjectMapper` bean, so today's live default is
   already ISO-8601). Pin to this value explicitly rather than epoch-millis —
   pinning to epoch-millis to "avoid a flip" would itself be the real
   regression, since it would change the format that's actually live today.
5. Document the chosen `Instant` format (ISO-8601, matching the pre-existing
   Jackson 2 default — not a behavior change) directly in the new bean's
   Javadoc so the decision is discoverable by anyone reading `pte-common`,
   not just implied by test assertions. Expose the bean (or the underlying
   `JsonMapper` instance) so Phase 3 can reuse it when constructing
   `JacksonJsonMessageConverter` for RabbitMQ, ensuring the AMQP wire format
   and the outbox/REST format are the same instance, not two independently
   configured mappers that could drift.
6. Confirm every service already depends on `pte-common` (no service defines
   its own competing `ObjectMapper`/`JsonMapper` bean that would shadow the
   new shared one) before relying on it fleet-wide in later phases.
7. Build `pte-common` in isolation (`mvn -pl pte-common install`) to confirm
   it compiles clean against Jackson 3 before any dependent service is
   touched.

## Success Criteria
- `AbstractOutboxWriter` no longer imports
  `com.fasterxml.jackson.databind.ObjectMapper` or
  `com.fasterxml.jackson.core.JsonProcessingException`; it imports and uses
  `tools.jackson.databind.json.JsonMapper`.
- A new shared bean exists in `pte-common` that pins `Instant`
  (de)serialization format explicitly, with a documented rationale.
- `mvn -pl pte-common install` (or equivalent module-scoped build) succeeds
  with zero Jackson 2 compile errors.
- The exception-handling behavior in `write()` is unchanged from a caller's
  perspective (still throws a runtime exception with the same message shape
  on serialization failure) — verified by reading the updated method
  side-by-side with the original, not assumed.

## Risks
- Getting the shared date-format bean wrong (or skipping it) here means every
  downstream phase inherits the mistake fleet-wide: mitigated by treating
  this as the first and most scrutinized step of the whole plan, with its
  own documented rationale (step 5) rather than a silent default.
- A service already defines its own local `ObjectMapper`/`JsonMapper` bean
  that would shadow the new shared `pte-common` bean, silently defeating the
  format-pinning: mitigated by step 6's explicit check before moving to
  Phase 2.
