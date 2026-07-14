@docs/CODING_STANDARDS_API.md

# aptis-api — AI Code Generation Rules

## Module Structure

New modules go in: `src/main/java/com/aptis/modules/{name}/{constant,controller,domain,dto,interfaces,repository,service}/`

## Critical Rules (enforce on every file you write)

1. **No hardcoded strings** — all messages, error codes, labels in `*Constants.java`
2. **No hardcoded secrets** — API keys, passwords, tokens in env vars only (never in `*Constants.java`)
3. **@Transactional only on Service** — not on Controller or Repository
4. **Return ApiResponse<T>** — every endpoint wraps response in the standard wrapper
5. **No @Data on @Entity** — use `@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor`
6. **Service ≤ 5 public methods** — split into focused services if over
7. **File ≤ 300 lines** — split class if over (Lombok/annotation-generated code is exempt)
8. **Multi-tenant methods** — new service methods receive `tenantId` as parameter, not from Security context inside Repository
9. **Exception handling** — via `@ControllerAdvice` only; no try-catch in controller methods
10. **N+1 prevention** — use `@EntityGraph` or JOIN FETCH when loading associations

## File Size Rule — Auto-Generated Code

**Exempt from 300-line limit**: Lombok-generated, MapStruct mappers, JPA schema codegen, migration files, build artifacts.

**NOT exempt**: AI-generated code (Claude Code, Copilot, Cursor) — if it exceeds 300 lines, split it.

## When Updating Coding Standards

Any PR that modifies `docs/CODING_STANDARDS_API.md` **must** also update this `CLAUDE.md` in the same PR.
