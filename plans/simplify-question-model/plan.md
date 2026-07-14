# Plan: Simplify Question Model

## Overview
This plan implements the refactoring of the `Question` model by removing `Passage` and `QuestionOption` entities, flattening their data into the `Question` entity itself. This will reduce database JOINs and simplify the codebase.

## Risks
- **Data Migration:** Deleting entities without a migration script will drop data if this was already in production. Assuming this is safe for the current environment.
- **Frontend Contract Breakage:** The API response for questions will change significantly. The frontend must be ready to handle the new `options` and `correctAnswers` flat lists, and parse `content` for `[Blank X]` markers.

## Phases
1. **Phase 1: Domain Model Refactoring** - Update Entities.
2. **Phase 2: Repository and DTO Updates** - Remove dead code and adapt DTOs.
3. **Phase 3: Service and Controller Refactoring** - Remove dead controllers/services and fix `QuestionService`.
