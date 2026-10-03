# Phase 1: Backend

## Requirements
`ExamPreviewResponse` và `SnapshotContentResponse` expose thêm `sourceQuestionPublicId` để frontend có thể dùng ID câu hỏi gốc khi tạo report ticket.

## Steps

1. Kiểm tra `SnapshotItem` entity (đã có `sourceQuestionPublicId` ở line 43) và xác nhận cách mapper hiện tại build `SnapshotContentResponse.Item` để biết field cần thêm vào đúng chỗ.

2. Thêm `sourceQuestionPublicId` (UUID, nullable) vào `Item` record trong `SnapshotContentResponse.java` và cập nhật mapper để pull giá trị từ `SnapshotItem.sourceQuestionPublicId`.

3. Thêm `sourceQuestionPublicId` (UUID, nullable) vào `Item` record trong `ExamPreviewResponse.java` và cập nhật `toPreviewItem()` trong `SessionExamPreviewService.java` để set field đó.

4. Chạy build (`./mvnw compile`) và verify không có lỗi compile; kiểm tra test hiện có cho `SessionExamPreviewService` còn pass.

## Files to Create or Modify

Tất cả paths relative to `d:\FPT\9thSemester\pte\pte-api\`:

| File | Action |
|---|---|
| `app/src/main/java/com/pte/assessment/dto/response/SnapshotContentResponse.java` | Modify — add `sourceQuestionPublicId` (UUID, nullable) to `Item` record |
| `app/src/main/java/com/pte/session/internal/dto/response/ExamPreviewResponse.java` | Modify — add `sourceQuestionPublicId` (UUID, nullable) to `Item` record |
| `app/src/main/java/com/pte/session/internal/service/SessionExamPreviewService.java` | Modify — pass `sourceQuestionPublicId` in `toPreviewItem()` |

Mapper cho `SnapshotContentResponse.Item` — tìm class/method build `Item` từ `SnapshotItem` và thêm field tương ứng.

## Success Criteria
- `GET /api/exam-sessions/{id}/preview` trả về mỗi item có field `sourceQuestionPublicId` (UUID string hoặc null)
- `./mvnw compile` không có lỗi
- Các unit test hiện có cho `SessionExamPreviewService` vẫn pass

## Risks
- Java record constructor positional — thêm field mới cần kiểm tra tất cả call site dùng `new Item(...)` thay vì builder/mapper để tránh compile error
- Mapper `SnapshotContentResponse.Item` có thể nằm trong MapStruct mapper riêng — cần tìm đúng file trước khi sửa

## Execution Log

### Errors Encountered
- (not executed yet)

### Root Cause
- (not executed yet)

### Resolution
- (not executed yet)

### Test Results After Fix
- (not executed yet)
