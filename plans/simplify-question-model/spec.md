# Spec: Simplify Question Model

**Date:** 2026-06-29
**Status:** Ready

---

## Problem Statement
The current `Question` model involves complex relationships with `Passage` and `QuestionOption`, leading to verbose code, multiple database tables, and performance overhead from JOINs. This structure needs to be flattened into a single `Question` entity to improve API response time and simplify the domain model.

---

## User Stories

<!-- P1 = MVP (must ship), P2 = nice-to-have, P3 = future/out-of-scope -->

- **[P1]** As a backend developer, I want to store all question options and correct answers as flat arrays within the `Question` entity so that I don't need to perform JOINs to retrieve a complete question.
  Accepted when: `QuestionOption` entity is removed, and `Question` contains `options` and `correctAnswers` stored as JSON/Array in the database.

- **[P1]** As a backend developer, I want to merge passage text and sub-questions into a single `content` field so that I can eliminate the `Passage` entity.
  Accepted when: `Passage` entity is removed, and the `content` field supports markdown/JSON formats with placeholders (e.g., `[Blank 1]`).

- **[P1]** As a frontend developer, I want the `content` field to contain clear markers (like `[Blank 1]`) so that I can accurately map items from the flattened `options` array to the correct blank spaces in the UI.
  Accepted when: Frontend can successfully parse the API response and render a full reading passage with its respective questions.

---

## Functional Requirements

<!-- Number each. Be specific. -->

1. FR-01: Delete `QuestionOption.java` and related dependencies.
2. FR-02: Delete `Passage.java` and related dependencies.
3. FR-03: Add `List<String> options` and `List<String> correctAnswers` to `Question.java`.
4. FR-04: Configure JPA/Hibernate to map these new lists to a single database column (e.g., using a custom converter for JSON).
5. FR-05: Update all affected services, mappers, and API endpoints to work with the new flattened `Question` structure.

---

## Non-Functional Requirements

<!-- Use numbers, not adjectives. -->

- Performance: API response time for fetching a list of questions should decrease due to the removal of JOIN operations.
- Maintainability: The domain model should be easier to understand with fewer entities.

---

## Success Criteria

<!-- Measurable outcomes. Each must be independently verifiable. -->

- [ ] Entity deletion: `Passage` and `QuestionOption` are completely removed from the codebase.
- [ ] DB Schema: The `Question` table contains `options` and `correct_answers` columns, without any foreign key dependencies to `passage` or `question_option`.
- [ ] API integration: The frontend can correctly display a reading section by parsing the tags in the `content` string.

---

## Out of Scope

- Implementing the frontend parsing logic (this is a backend-focused task, though the API contract supports it).
- Migrating old database records (unless specifically requested during the plan phase).

---

## Assumptions

- We are using a database that supports JSON/JSONB or Array data types (e.g., PostgreSQL or MySQL).
- The frontend team is aware of this architectural change and is prepared to handle the parsing of `[Blank X]` markers.
