# CI/CD deploy production

Hai workflow giống nhau được đặt trong `pte-api` và `pte-web`. Push vào `main`
của một trong hai repository sẽ SSH vào VPS, fetch `main` của cả hai repository,
build lại stack và khởi động các container mới.

Workflow không lưu `.env` trong GitHub và không chạy `docker compose down -v`.
Database, MinIO data và Caddy certificate volumes được giữ nguyên.

## GitHub Actions secrets

Tạo environment `production` trong cả hai repository, sau đó thêm bốn secrets:

| Secret | Giá trị |
|---|---|
| `DEPLOY_HOST` | `217.142.185.74` hiện tại; nên thay bằng reserved public IP sau này |
| `DEPLOY_USER` | `ubuntu` |
| `DEPLOY_SSH_PRIVATE_KEY` | Toàn bộ nội dung file private key `.key`, không phải đường dẫn file |
| `DEPLOY_KNOWN_HOSTS` | Host key SSH của VPS đã xác minh |

Tạo `DEPLOY_KNOWN_HOSTS` trên máy đã SSH thành công vào VPS:

```powershell
ssh-keyscan -H 217.142.185.74
```

Chỉ đưa kết quả vào secret sau khi đối chiếu fingerprint với host key đáng tin
cậy của VPS. Không dùng `ssh-keyscan` trực tiếp trong workflow để tránh tin mù
vào host key tại thời điểm deploy.

Nếu dùng organization secrets thay vì repository/environment secrets, giới hạn
quyền truy cập chỉ cho `pte-api` và `pte-web`.

## Điều kiện trên VPS

Các điều kiện này đã có trên VPS hiện tại:

- `/home/ubuntu/pte-org/pte-api` và `/home/ubuntu/pte-org/pte-web` là Git clone,
  checkout branch `main`.
- `/home/ubuntu/pte-org/pte-api/.env` tồn tại, permission `600`, và không có
  placeholder secret.
- User `ubuntu` có quyền chạy Docker.
- Docker Compose có thể đọc ba file compose khi đứng tại `pte-api`.
- Ingress TCP `80` và `443` đã mở ở OCI Security List và iptables.

Workflow sẽ fail trước khi build nếu working tree trên VPS có thay đổi thủ công.
Điều này bảo vệ `.env` và tránh deploy đè lên chỉnh sửa chưa được review.

## Lần chạy đầu tiên

Sau khi tạo đủ secrets, commit và push workflow này lên `main` của từng repository.
Theo dõi tab **Actions**. Có thể dùng **Run workflow** để chạy thủ công mà không
cần tạo thêm commit.

Build đầu tiên trên Oracle A1 có thể mất vài phút. Workflow giới hạn build còn
hai tiến trình để phù hợp với 2 vCPU và bộ nhớ VPS hiện tại, sau đó chờ tối đa
10 phút cho các container có healthcheck chuyển sang `healthy`.
