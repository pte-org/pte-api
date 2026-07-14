# Phase 2: Repository and DTO Updates

Maps to Spec Stories: [P1] Store options/answers as flat arrays, [P1] Merge passage text.

## Steps
1. Delete Repositories:
   - `PassageRepository.java`
   - `PassageOperations.java`
   - `QuestionOptionRepository.java`
2. Update `QuestionRepository.java`:
   - Remove any custom queries relying on `passageId` or JOINS to passage.
3. Delete DTOs:
   - `PassageRequest.java`
   - `PassageResponse.java`
   - `QuestionOptionResponse.java`
4. Update `QuestionResponse.java` and `QuestionRequest.java` (if exists):
   - Remove nested passage/options DTOs.
   - Add `List<String> options` and `List<String> correctAnswers`.
