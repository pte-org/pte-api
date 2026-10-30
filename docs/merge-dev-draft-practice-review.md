# Đánh giá merge dev-draft vào practice-revamp

Ngày: 2026-10-26. Hai nguồn đối chiếu: partner `0ee005e9` và refactor `d64dff2c`.
Trong lúc xử lý, merge đã được ghi nhận tại `938ed652`; các sửa bổ sung nằm sau trạng thái merge đó trong worktree.
Các migration practice chưa được sử dụng đã được đánh lại số liên tục từ `V2` đến `V5`, sau `V1__baseline.sql`.

## Quyết định tích hợp

| Phần giao nhau | Cách xử lý |
| --- | --- |
| Entity câu trả lời practice | Dùng `PracticeResponse`, `PracticeResponseRepository`, `PracticeResponseStatus`; cập nhật cả JPQL thống kê, review và mock test. Không khôi phục `PracticeSessionItem`. |
| Lifecycle session | Giữ service/helper trong `internal.service.session` và shared `IdempotencyKeyService`; chuyển thêm lệnh `SAVE_DRAFT` vào ledger chung. Không khôi phục bảng operation cũ. |
| Đăng nhập practice | Giữ đăng nhập Student bằng tài khoản roster và kiểm tra entitlement của partner. Không khôi phục email OTP hoặc practice identity riêng. |
| Mock test và timing | Chuyển dependency từ `ScoreTemplateService` sang `ExamDefinitionService`; giữ thứ tự câu, snapshot nội dung/evaluation, lịch sử câu đã gặp, số câu và timing từng câu. |
| Media | Dùng `MediaAsset` và các helper media của refactor. Playback được đặt trong `CloudinaryMediaReader`, kiểm tra owner/tenant/session/item/upload; không yêu cầu session còn live để xem lại recording cũ. |
| Câu đã skip | Giữ quyền trả lời, lưu nháp và recording lại khi session còn cho phép thao tác. Scoring tiếp tục đi qua router của refactor. |
| Schema | V2 xóa thêm `otp_challenges` theo tên baseline mới; V3 thêm timing vào `practice_responses` thay vì bảng đã bị loại bỏ. Giữ bảng mock test/profile của chức năng partner. |
| Quy tắc kiến trúc | Tách phần chọn/pin câu thành `PracticeResponseFactory`, phần tạo mock test thành assembler; nhóm service/DTO theo trách nhiệm. Không tăng allowlist hoặc nới test kiến trúc. Profile tiếp tục dùng injected Clock và múi giờ Việt Nam. |

## Các hành vi được bảo toàn

- Luyện theo task hoặc kỹ năng; chọn số câu và timed/untimed.
- Mock test cố định; resume/restart và ước lượng điểm.
- Không có deadline toàn session đối với flow mới; timing nằm trên từng câu.
- Lưu nháp, save-and-exit và resume session đã bắt đầu.
- Recording playback, review, task/skill statistics và ngày thi trong profile.
- Student ownership, entitlement, optimistic version và idempotency của thao tác.

## Kiểm chứng

Review độc lập đã đối chiếu hai parent và source sau khi chuyển kiến trúc, verdict `APPROVED`, không có lỗi hồi quy cụ thể được xác nhận.

Lệnh cuối: `mvn -pl app -am clean test`, exit **0**, `BUILD SUCCESS`.
Maven thống kê **2.912 tests, 0 failures, 0 errors, 58 skipped** (2.854 test thực thi).
Các test PostgreSQL/AI cần cấu hình môi trường riêng vẫn bị skip theo điều kiện sẵn có của repository.
Lượt build sạch biên dịch 1.503 production source files và 392 test source files; các kiểm tra Clock, package density, file size và module boundary đều qua.
Test H2 `PracticeResponseRepositoryMergeTest` cũng qua riêng và được chạy lại trong lượt clean test này.
Sau khi đánh lại số migration, đã kiểm tra chuỗi source/classpath đều là `V1` đến `V5`, baseline và SQL thực thi không đổi; `mvn -pl app -am process-resources` exit 0. Lượt chuẩn hóa tên này không chạy lại toàn bộ test hoặc thực thi migration trên database.

Regression tests bổ sung kiểm tra playback và quyền truy cập media, trả lời/lưu nháp câu đã skip, picker và pin nội dung/reference/timing, cùng các JPQL thống kê/cách ly Student trên entity đã đổi tên.

## Giới hạn database

`dev-draft` thay lịch sử migration cũ bằng `V1__baseline.sql`. Vì vậy, test code/H2 không chứng minh database PostgreSQL đã chạy các migration cũ có thể nâng cấp trực tiếp. Cần kế hoạch chuyển dữ liệu/baseline phù hợp với database đích trước khi triển khai; lần xử lý này không reset hoặc thay đổi database đang dùng. Docker daemon không chạy nên chưa thực thi migration trên PostgreSQL.
