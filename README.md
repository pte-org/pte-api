# aptis-api

Spring Boot backend for the Aptis LMS platform. Organized as a set of
DDD-flavored bounded-context modules in a single deployable — not
microservices, but structured so that splitting one out later wouldn't
require a rewrite.

> Backend only. The web frontend (`aptis-web`, Next.js) and the exam-taking
> client (`aptis-app`, Flutter) are separate repos — this service shares no
> code with either; the only contract between them is the REST API.

## Setup

- Java 21, Maven (wrapper included, no local Maven install needed).
- Run the app:
  ```
  ./mvnw spring-boot:run
  ```
- Build / test (must pass before code review):
  ```
  ./mvnw clean compile
  ./mvnw clean test
  ```
- `compose.yaml` exists (for `spring-boot-docker-compose` auto-support) but
  currently declares zero services — see "Known Gaps" below before relying
  on `docker compose up` for a database.

## Directory Structure

```
aptis-api/
├── src/main/java/com/aptis/
│   ├── aptis_api/
│   │   └── AptisApiApplication.java   # @SpringBootApplication(scanBasePackages = "com.aptis")
│   │                                   # — the override is mandatory, see "The scanBasePackages gotcha"
│   └── modules/                        # One folder per bounded context. Same 7-subpackage
│       │                               # shape in every module — see "Module shape" below.
│       ├── iam/                          # Admins, hosts, students, credentials, password hashing
│       ├── tenancy/                      # Tenant/host-organization records (shell only — no MVP entities yet)
│       ├── questionbank/                 # Questions + answer options
│       ├── examoperations/                # Exam authoring, roster import, credential export
│       ├── examdelivery/                   # Exam attempts in progress, answers
│       └── scoring/                         # Score computation (shell only — no MVP entities yet)
├── src/test/java/com/aptis/
│   ├── aptis_api/                      # ComponentScanTest, AptisApiApplicationTests
│   └── modules/                         # Mirrors main/ — one test package per module that has logic to test
├── src/main/resources/application.properties
├── compose.yaml                         # Currently empty (services: {}) — see Known Gaps
├── lombok.config                        # config.stopBubbling = true; doNotUseGetters tuning (see entity convention)
└── pom.xml
```

## Module shape — every module under `modules/*` has the same 7 subpackages

```
modules/<context>/
├── constant/      # Business-rule constants (BR codes, limits)
├── controller/     # @RestController — thin, delegates to service/
├── domain/           # @Entity classes + this module's own unchecked exception class
├── dto/                # Request/response records exposed across module boundaries
├── interfaces/          # The module's PUBLIC API — see "Module boundaries" below
├── repository/            # Spring Data JPA repositories
└── service/                # @Service — business logic, the only thing controller/ calls
```

`tenancy` and `scoring` are shells for now: their `domain/`/`repository/`
folders hold only a `package-info.java` placeholder because no MVP entity
has been assigned to them yet (see
`aptis-doc/projects/aptis-mvp/team/techlead/mvp-slice-mapping.md` for the
authoritative entity-to-module mapping). Don't delete the placeholders —
they're there so the package exists and compiles before real work starts.

## Module boundaries — convention only, not yet tool-enforced

There is no ArchUnit / Spring Modulith / multi-module Maven setup. The rule
is enforced by code review:

**A module may only be reached from outside via its own `interfaces.*` +
`dto.*` (plus its one domain exception class, whitelisted for
`@ExceptionHandler` wiring). Never import another module's `service/`,
`repository/`, or `domain/` directly.**

Worked example — `examdelivery`'s public contract:

```java
// modules/examdelivery/interfaces/ExamDeliveryOperations.java
public interface ExamDeliveryOperations {
    ExamAttemptResponse submitAttempt(SubmitExamRequest request);
}
```

Only `ExamAttemptResponse`/`SubmitExamRequest` (both in `dto/`) ever cross
the boundary — never the `ExamAttempt` entity itself.

There is exactly **one** sanctioned cross-module *service* call in the
whole codebase: `examoperations.service.RosterImportService` calls
`iam.interfaces.CredentialProvisioning` (never `iam.service.CredentialService`
directly) to provision student logins during roster import. If you need a
second one, that's the signal to add a real `interfaces/` contract for it
first, not to reach into the other module's `service/` package.

Cross-module entity *references* go by primitive ID, never by importing the
other module's entity class — e.g. `ExamAttempt.examId` is a `Long`, not an
`Exam` field. This keeps `examdelivery` compilable without depending on
`examoperations`.

Full per-module public/private breakdown, the verification log, and the
escalation path (when to actually introduce ArchUnit) live in
`plans/aptis-be-structure/module-boundary-checklist.md` — read that before
adding a new cross-module dependency, sanctioned or not.

## Entities: rich vs. anemic, and the Lombok convention

JPA entities are the domain model directly — there's no separate anemic
domain model + JPA entity split. This was a deliberate call for MVP scale
(see `plans/aptis-be-structure/plan.md`), not an oversight.

Two entities are **rich** — they enforce business invariants themselves and
throw their module's unchecked exception on violation, rather than letting
a service silently produce bad state:

- `Exam` (`examoperations`) — `addQuestion()`/`removeQuestion()` keep
  `isAssignable` in sync (BR-006) and block removing a question already
  used in an assigned batch (BR-009).
- `ExamAttempt` (`examdelivery`) — `submit()` enforces submit-once (BR-003).

Everything else (`Admin`, `Host`, `Student`, `Question`, `Option`,
`ImportBatch`, `CredentialExport`) is **anemic** — plain getters/setters,
no business methods, by design (see the `// NO business methods` comment in
each).

Lombok convention for every entity (`lombok.config` sets
`doNotUseGetters = true` on `equals`/`hashCode`/`toString` project-wide, so
these annotations are safe to combine):

```java
@Entity
@Getter
@Setter                                          // omit if every mutation goes through a business method
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)  // only @Id gets @EqualsAndHashCode.Include
@ToString(exclude = "questions")                   // exclude collections — avoid lazy-load-in-toString traps
public class Exam {
    @Setter(AccessLevel.NONE)   // mutate only via addQuestion()/removeQuestion()
    @OneToMany(...)
    private List<ExamQuestion> questions = new ArrayList<>();

    public List<ExamQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);   // caller can't bypass the invariant methods
    }
}
```

If a field's only legal mutation path is a business method, mark it
`@Setter(AccessLevel.NONE)` and, if it's a collection, return
`Collections.unmodifiableList(...)` from the getter — both `Exam.questions`
and the pattern itself should be copied verbatim for any new invariant
field.

## How to add the next module

1. Create the 7 standard subpackages under `modules/<name>/` (use a
   `package-info.java` in any that start empty — see `tenancy`/`scoring`).
2. Add the `@Entity` classes in `domain/` first — decide rich vs. anemic
   per entity up front, not after a bug shows up.
3. Define `interfaces/<Name>Operations` — this is the *only* thing other
   modules will ever be allowed to call. Add `dto/` request/response types
   for every method on it.
4. Implement `service/` against the repository + domain, throwing this
   module's own unchecked exception (one class, named like
   `<Name>ConstraintViolationException`) on invariant violations.
5. Add a thin `controller/` that delegates to `service/` — no business
   logic in the controller.
6. If another module needs something from yours, it depends on your
   `interfaces.*` + `dto.*` only — never wire it any other way; if that's
   not enough, expand the interface, don't open a side door.
7. Mirror the structure under `src/test/java/com/aptis/modules/<name>/` —
   `domain/` tests for entity invariants (skip for anemic entities, see
   `AdminTest` for the minimal shape when there's nothing to assert), and a
   `service/` test if the service has logic beyond pass-through CRUD.

## Known gaps (explicitly deferred, not silently missing)

- **No DataSource is configured.** `application.properties` only sets
  `spring.application.name`. Any test that boots a full
  `@SpringBootTest` context (`AptisApiApplicationTests.contextLoads()`)
  fails with "Failed to determine a suitable driver class" because
  `spring-boot-starter-data-jpa` on the classpath makes Spring try to
  auto-create a `HikariDataSource`/`entityManagerFactory` with nothing to
  configure. `ComponentScanTest` exists specifically to verify module
  discovery without booting a context, so this gap doesn't block that
  check. `compose.yaml` is present but declares zero services — wiring a
  real Postgres/MySQL service into it is the fix, not yet done.
- **Unchecked domain exceptions currently surface as HTTP 500.** There is
  no `@ExceptionHandler`/`@ControllerAdvice` mapping invariant violations
  (`ExamConstraintViolationException`, etc.) to a proper 4xx response yet.
  This is deliberate-for-now, not a bug — see
  `module-boundary-checklist.md`'s Follow-up Actions.
- **`PasswordEncoder` is a manually declared bean** (`SecurityConfig`) —
  Spring Security 6+/Boot 4.1 does not auto-provide one just because
  `spring-boot-starter-security` is on the classpath. If you ever remove
  `SecurityConfig`, password hashing breaks at startup, not silently.

## Tech Stack

- **Framework:** Spring Boot 4.1.0, Java 21
- **Persistence:** Spring Data JPA (Hibernate) — entity = domain model, no
  separate anemic model layer
- **Security:** Spring Security (manual `PasswordEncoder` bean — see above)
- **Web:** Spring MVC (`spring-boot-starter-webmvc`)
- **Boilerplate reduction:** Lombok (excluded from the runtime fat jar by
  `spring-boot-maven-plugin`, wired into both compile and test-compile via
  `maven-compiler-plugin`'s annotation processor path)
- **Build:** Maven (wrapper checked in — `./mvnw`, no local install needed)

See `plans/aptis-be-structure/plan.md` and
`plans/aptis-be-structure/module-boundary-checklist.md` for the full
rationale and decision history behind this structure — including the
JPA-entity-as-domain-entity decision, the `scanBasePackages` fix
(`com.aptis.aptis_api` is a *sibling* of `com.aptis.modules.*`, not an
ancestor, so the default component scan would silently miss every module
without the override), and the TDD history per module.
