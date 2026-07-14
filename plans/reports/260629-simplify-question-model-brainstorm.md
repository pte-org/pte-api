# Brainstorm: Simplify Question Model

**Date:** 2026-06-29

## Ideas Explored
- **Option A**: Use JPA `@ElementCollection` to store `List<String>` for options and correct answers. Dismissed because it still relies on background JOIN operations and separate tables, which goes against the core goal of flattening the model.
- **Option B**: Store lists as JSON/JSONB or Array directly in the database. Chosen because it provides a purely flat structure with zero additional tables or JOINs, perfectly aligning with the simplicity goal.

## User's Direction
The user decided to drop `Passage` and `QuestionOption` entities. All sub-questions and the passage text will be merged into the `content` field of a single `Question` entity. The `options` and `correctAnswers` will be stored as flat `List<String>` using a JSON/JSONB or string array column type in the DB. The frontend will be responsible for parsing special tags (e.g. `[Blank 1]`) embedded within the markdown/JSON `content` to correctly map the flattened list of options to specific sub-questions.

## Open Questions
- None blocking planning. The logic for generating and parsing `[Blank 1]` markers will need to be agreed upon between frontend and backend.

## Risks
- **Data Integrity**: Backend won't easily be able to validate if the number of options matches the number of `[Blank X]` tags in the content.
- **Searchability**: Querying questions based on specific option text or passage text will be more complex since it's embedded inside a JSON/Array column.
