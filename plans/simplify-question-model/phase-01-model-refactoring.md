# Phase 1: Domain Model Refactoring

Maps to Spec Stories: [P1] Store options/answers as flat arrays, [P1] Merge passage text.

## Steps
1. Delete `src/main/java/com/aptis/modules/questionbank/domain/QuestionOption.java`.
2. Delete `src/main/java/com/aptis/modules/questionbank/domain/Passage.java`.
3. Modify `src/main/java/com/aptis/modules/questionbank/domain/Question.java`:
   - Remove `private Long passageId;`
   - Add `private List<String> options;` mapped to DB array/JSON.
   - Add `private List<String> correctAnswers;` mapped to DB array/JSON.
   - Use `@JdbcTypeCode(SqlTypes.ARRAY)` and `columnDefinition = "text[]"` (similar to `topicTags`) for simple string lists in PostgreSQL.
