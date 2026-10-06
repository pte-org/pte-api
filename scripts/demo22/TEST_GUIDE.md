# Hướng dẫn test demo 22 dạng bài PTE (PRACTICE + OFFICIAL)

Dành cho đồng đội test trên máy riêng. Dữ liệu demo chỉ dùng trong nội bộ lớp, không chia sẻ ra ngoài.

## 0. Cần có

- Docker Desktop
- Git for Windows (có sẵn `openssl`)
- Flutter có bật Windows desktop (cần Visual Studio với workload "Desktop development with C++")
- Internet (audio nằm trên Cloudinary và CloudFront)
- Không cần Node hay Python

## 1. Chuẩn bị (làm một lần)

1. Clone 2 repo `pte-api` và `pte-app` (đúng nhánh có thư mục `pte-api/scripts/demo22`).
2. Trong `pte-api`, tạo file `.env.local`:

   ```powershell
   Copy-Item .env.example .env.local
   .\scripts\generate-keys.ps1 -EnvFile .env.local
   ```

3. Mở `.env.local` và sửa:
   - `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `APP_DB_USER`, `APP_DB_PASSWORD`: tự đặt.
   - `PAYOS_*` và `CLOUDINARY_*`: điền giá trị giả, **không để trống**.
   - **Xóa hoặc comment các dòng `MAIL_*`** để dùng Mailpit local (nếu để nguyên, app sẽ cố gửi mail qua `smtp.example.com`).
   - Bỏ dấu `#` và đặt giá trị cho `PTE_ADMIN_PASSWORD`, `PTE_SEED_PASSWORD` (mật khẩu local, nên từ 12 ký tự trở lên, có chữ hoa, chữ thường, số, ký hiệu). **Ghi nhớ `PTE_SEED_PASSWORD`**, đó là mật khẩu đăng nhập app.
   - Thêm 2 dòng (giống cấu hình đã thử): `TASK_TYPES_CUSTOM_CREATION_ENABLED=true` và `TASK_TYPES_CUSTOM_TEMPLATE_ACTIVATION_ENABLED=true`.

## 2. Chạy backend và seed

```powershell
cd pte-api
docker compose --env-file .env.local -f docker-compose.yml -f docker-compose.services.yml up --build -d
```

Chờ vài phút, kiểm tra `docker ps` thấy `pte-platform-app-1` ở trạng thái `healthy`. Sau đó:

```powershell
.\scripts\demo22\seed-demo22.ps1
```

Kết thúc phải thấy `DEMO22 seed complete.` và bảng 6 exam đều `OPEN`, `CanStart True`.

## 3. Chạy app

```powershell
cd pte-app
flutter run -d windows
```

Nếu build báo lỗi CMake "No target aptis_app": chạy `flutter clean` rồi thử lại.

Đăng nhập app:

- Username: `student.demo22@test.local`
- Password: giá trị `PTE_SEED_PASSWORD`
- Exam code: lấy bằng lệnh sau (**mã khác nhau trên mỗi máy**):

  ```powershell
  "select name, session_code from exam_sessions where name like 'DEMO22%';" | docker exec -i pte-postgres sh -c 'psql -U $POSTGRES_USER -d pte'
  ```

## 4. Cần test gì

| Exam | Kiểm tra |
|---|---|
| PRACTICE - Full | Đi hết các task theo thứ tự Speaking → Writing → Reading → Listening. Phải thấy đủ 22 dạng (cộng 1 câu Personal Introduction ở đầu). |
| PRACTICE - Speaking / Writing / Reading / Listening | Chỉ có task của đúng kỹ năng đó. |
| OFFICIAL | Khóa màn hình, replay audio chỉ 1 lần, bắt đầu xong không thi lại. **Chỉ thử khi đã chắc** (xem mục 5). |

Với **mỗi dạng bài**, ghi nhận có hoặc không:

- Hiển thị đúng đề, không lỗi bố cục.
- Audio phát được (nghe rõ, replay đúng luật), ảnh hiện (Describe Image).
- Thao tác nhập đáp án chạy đúng: chọn, kéo thả, dropdown, gõ, highlight, sắp xếp.
- Ghi âm được (Speaking; cần bật micro: Settings → Privacy → Microphone cho desktop apps).
- Đồng hồ chạy, hết giờ thì tự chuyển câu.
- Nộp bài xong không lỗi.

Dạng hay lỗi, nên chú ý: Fill in the Blanks (Type In), Highlight Incorrect Words, Re-order Paragraphs, Summarize Group Discussion, Describe Image.

## 5. Lưu ý

- Mỗi dạng chỉ có 3 câu (ít hơn đề thật), và điểm chấm AI có thể là giả lập nên không dùng để đánh giá điểm số.
- Exam OFFICIAL chỉ thi được một lần. Để làm lại:

  ```powershell
  docker compose --env-file .env.local -f docker-compose.yml -f docker-compose.services.yml down -v
  ```

  Lệnh này xóa **toàn bộ** dữ liệu local. Sau đó chạy lại bước 2 và lấy lại mã exam.
- Audio nằm trên Cloudinary của chủ repo và CloudFront. Nếu không nghe được, thử mở thẳng link audio trong trình duyệt.
- Tài khoản khác (cùng mật khẩu `PTE_SEED_PASSWORD`): host `host.demo22@test.local`, proctor `proctor.demo22@test.local`.

## 6. Khi gặp lỗi, báo kèm

Tên exam, tên dạng bài, câu thứ mấy, ảnh chụp màn hình, và log trong terminal chạy `flutter run`. Nếu lỗi ở bước seed thì gửi nguyên output của `seed-demo22.ps1`.

## Lưu ý về độ tin cậy của hướng dẫn này

Hướng dẫn mới được chạy từ đầu trên một máy (máy của chủ repo). Chưa được chạy trên máy khác nên có thể thiếu biến trong `.env.local` ở bước 1; nếu gặp, gửi thông báo lỗi cho chủ repo. Hai dòng `TASK_TYPES_*` ở bước 1 chưa được thử khi để giá trị `false`.
