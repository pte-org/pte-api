# Phase 1: Enum & Schema Audit

Covers user stories: P1 (build succeeds after Flyway removal), underpins the
NFR on safe schema recoverability. Must complete before Phase 2 deletes any
`db/migration` files.

## Requirements
Confirm, service by service, that no Postgres native enum type
(`CREATE TYPE ... AS ENUM`) exists in any current migration, and that every
Java entity's `@Enumerated` mapping is compatible with Hibernate's default
handling — so deleting Flyway's migrations and switching to `ddl-auto=update`
cannot surprise anyone with a startup crash. Also inventory explicit indexes,
since `ddl-auto=update` silently does not recreate them (see step 7).

## Steps
1. Search every service's `src/main/resources/db/migration/*.sql` files for
   `CREATE TYPE` and `AS ENUM` (case-insensitive) to find any native Postgres
   enum column definitions.
2. Search every service's entity/domain Java classes for `@Enumerated` usage
   and note, per field, whether it's mapped as `STRING` or ordinal.
3. Cross-reference each enum-backed column's SQL column type (from step 1's
   migrations) against its entity mapping (from step 2) to confirm they
   agree — flag any column that isn't a plain `VARCHAR`/text type feeding a
   `STRING`-mapped enum.
4. For any service where a mismatch or native enum type is found, record the
   specific fix needed (e.g. entity mapping change) so it can be applied as
   part of that service's Phase 2 work, not discovered later as a crash.
5. Write down the audit result per service (columns checked, enum types
   found, mismatches found/fixed) so the outcome is traceable before
   deletion happens. Append this record to this phase file under a new
   `## Audit Results` heading (per service) — this is the fixed location for
   traceability, not a separate doc or PR description.
6. Confirm all 9 services (admin, authoring, exam-delivery, iam, media,
   notification, proctor, reporting, scheduling, scoring) are either clear or
   have a documented fix before Phase 2 starts.
7. Search every service's `db/migration/*.sql` files for `CREATE INDEX`
   statements and list them (index name, table, columns) per service.
   `ddl-auto=update` only creates PKs, unique constraints, and FKs from JPA
   metadata — it does **not** recreate plain non-unique indexes unless
   declared via `@Table(indexes=...)`/`@Index` on the entity, which none of
   the 9 services currently use. Record the full index inventory in the same
   `## Audit Results` section; this list feeds Phase 4's documentation note
   ("indexes intentionally not recreated — must be re-added before any
   production baseline").

## Success Criteria
- A written audit record exists covering all 9 services, listing every
  enum-mapped entity field and its corresponding migration column type.
- Every service is marked either "clear" (no native enum type, mapping
  matches) or "fix required" with the fix documented — zero services left
  unchecked.
- No `CREATE TYPE ... AS ENUM` statement remains unaccounted for in any
  service's migration files at the time Phase 2 begins.
- A full inventory of `CREATE INDEX` statements across all 9 services exists
  in this file's `## Audit Results` section, ready to hand off to Phase 4.

## Risks
- False negative from an unusual SQL syntax variant (e.g. lowercase `enum`,
  multi-line `CREATE TYPE`) not caught by a simple grep: mitigate by also
  manually skimming each service's `V1__*.sql` file's `CREATE TABLE` column
  list, not relying on the search alone.

## Audit Results

**Date:** 2026-08-04

### Enum audit — all 9 services CLEAR
- `grep -i "CREATE TYPE|AS ENUM"` across all 22 migration files (all 9
  services): **zero matches**. No native Postgres enum types anywhere.
- `grep "@Enumerated"` across all service entities: found in admin, authoring
  (x5), exam-delivery (x3), iam (x3), media, notification (x2), proctor (x2),
  scheduling, scoring — all use `EnumType.STRING`, consistent with the
  `VARCHAR`-typed columns in the corresponding migrations. `reporting` has no
  enum fields at all.
- Verdict: **all 9 services clear, no fix required.**

### Index inventory — 32 indexes across 8 services (admin has none)
Counted directly from `CREATE INDEX` statements in each service's
`V1__*.sql` (none found in any `V2`/`V3` file — those only add columns/tables
via `ALTER TABLE`/`CREATE TABLE`, no indexes). Actual count is **32**, not
the 30 estimated during planning — using the actual count for Phase 2.

- **media** (1): `idx_media_objects_owner` on `media_objects(owner_public_id)`
- **exam-delivery** (3): `idx_attempts_tenant` on `exam_attempts(tenant_id)`;
  `idx_pinned_items_snapshot` on `pinned_items(pinned_snapshot_id)`;
  `idx_answers_attempt` on `attempt_answers(attempt_id)`
- **iam** (2): `idx_users_tenant` on `users(tenant_id)`; `idx_refresh_user` on
  `refresh_tokens(user_id)`
- **authoring** (8): `idx_questions_tenant` on `questions(tenant_id)`;
  `idx_questions_visibility` on `questions(visibility)`;
  `idx_questions_task_type` on `questions(pte_task_type)`;
  `idx_options_question` on `question_options(question_id)`;
  `idx_blueprints_tenant` on `exam_blueprints(tenant_id)`;
  `idx_blueprint_items_blueprint` on `blueprint_items(blueprint_id)`;
  `idx_snapshots_tenant` on `exam_snapshots(tenant_id)`;
  `idx_snapshot_items_snapshot` on `snapshot_items(snapshot_id)`
- **scoring** (3): `idx_scoring_answers_session` on
  `scoring_answers(session_public_id)`; `idx_scoring_answers_attempt` on
  `scoring_answers(attempt_public_id)`; `idx_scoring_answers_status` on
  `scoring_answers(status)`
- **reporting** (2): `idx_attempt_reports_session` on
  `attempt_reports(session_public_id)`; `idx_answer_projections_attempt` on
  `answer_projections(attempt_public_id)`
- **notification** (3): `idx_user_directory_tenant` on
  `user_directory_entries(tenant_id)`; `idx_user_directory_roles_entry` on
  `user_directory_roles(user_directory_entry_id)`;
  `idx_notification_logs_tenant` on `notification_logs(tenant_id)`
- **proctor** (5): `idx_proctor_sessions_session_proctor` on
  `proctor_sessions(session_public_id, proctor_public_id)` (composite);
  `idx_proctor_sessions_tenant` on `proctor_sessions(tenant_id)`;
  `idx_violation_events_session` on `violation_events(proctor_session_id)`;
  `idx_violation_events_exam_session` on
  `violation_events(session_public_id)`; `idx_violation_events_attempt` on
  `violation_events(attempt_public_id)`
- **scheduling** (5): `idx_snapshot_ref_items_ref` on
  `snapshot_ref_items(snapshot_ref_id)`; `idx_sessions_tenant` on
  `exam_sessions(tenant_id)`; `idx_compositions_session` on
  `session_compositions(session_id)`; `idx_enrollments_session` on
  `enrollments(session_id)`; `idx_proctor_assignments_session` on
  `proctor_assignments(session_id)`
- **admin** (0): no `CREATE INDEX` statements — nothing to mirror.

### Conclusion
Phase 2 may proceed for all 9 services with no entity-mapping fixes needed.
32 indexes (not 30) must be mirrored as `@Index` annotations per the list
above.
