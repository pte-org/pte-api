# Phase 3: Service and Controller Refactoring

Maps to Spec Stories: [P1] Frontend contract updates, [P1] Backend simplifications.

## Steps
1. Delete Services:
   - `PassageService.java`
   - `QuestionOptionService.java`
2. Delete Controllers:
   - `PassageController.java`
   - `QuestionOptionController.java`
3. Update `QuestionService.java`:
   - Remove dependency injections for `PassageRepository` and `QuestionOptionRepository`.
   - Update `createQuestion`, `updateQuestion`, and `getQuestion` logic to handle the flat `options` and `correctAnswers` lists directly on the `Question` entity.
   - Remove logic that populates `QuestionOptionResponse` and `PassageResponse`.
