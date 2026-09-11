# Triển khai demo — Oracle A1 (aarch64)

Runbook cho môi trường demo công khai. Ba origin, một VM, không có gì ngoài 80/443
mở ra internet.

| Origin | Phục vụ |
|---|---|
| `pte-tenant.duckdns.org` | tenant-web + `/api/*` → gateway (cùng origin, không CORS) |
| `pte-admin.duckdns.org` | vendor-web + `/api/*` → gateway |
| `pte-media.duckdns.org` | MinIO — bắt buộc tách origin, xem `Caddyfile` |

---

## 0. Việc chỉ chủ tài khoản Oracle làm được

Ba thứ này bạn không tự làm được nếu chỉ có quyền SSH:

1. **Nâng boot volume lên ≥100GB** (OCI Console → Block Storage). Mặc định ~46GB;
   11 image JVM + 2 image Node + Maven cache sẽ làm đầy disk giữa lúc build.
2. **VCN Security List**: thêm ingress TCP `80`, `443` từ `0.0.0.0/0`.
3. `sudo usermod -aG docker <user>`.

Cộng thêm một câu hỏi: **RAM được dùng bao nhiêu trong 24GB?** Overlay này đặt
trần ~11GB.

## 1. DNS

DuckDNS cần **ba** bản ghi, đều trỏ về IP của VM. Nếu mới có hai, thêm `pte-media`.

IP public của Oracle mặc định là ephemeral — đổi sau mỗi lần stop/start. Hoặc nhờ
chủ máy reserve IP, hoặc chạy cron trên VM:

```bash
*/5 * * * * curl -s "https://www.duckdns.org/update?domains=pte-tenant,pte-admin,pte-media&token=<TOKEN>&ip=" >/dev/null
```

Để trống `ip=` thì DuckDNS lấy IP nguồn của chính request.

## 2. Chuẩn bị máy

```bash
# swap — image Oracle không có sẵn; build Maven sẽ bị OOM killer giết giữa chừng
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
```

**iptables — tầng firewall thứ hai.** Image Ubuntu của Oracle nạp sẵn
`/etc/iptables/rules.v4` chặn mọi thứ trừ 22. Mở Security List mà bỏ qua bước này
thì request từ ngoài **treo rồi timeout**, không có log ở đâu cả — và người ta sẽ
đi debug Caddy, DNS, compose, sai hết.

```bash
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80  -j ACCEPT
sudo iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save
```

## 3. Lấy mã nguồn

Hai repo phải là thư mục anh em — `web.Dockerfile` build pnpm workspace member nên
cần cả root của `pte-web` làm build context:

```bash
mkdir -p ~/pte-org && cd ~/pte-org
git clone <pte-api-url> pte-api
git clone <pte-web-url> pte-web
```

## 4. Cấu hình

```bash
cd ~/pte-org/pte-api
cp deploy/env.template .env
openssl rand -base64 24   # chạy 3 lần, điền vào 3 dòng CHANGE_ME
$EDITOR .env
```

`.env` là **thứ duy nhất** khác nhau giữa các máy đích. Chuyển stack sang VM khác
nghĩa là copy file này rồi chạy lại compose.

## 5. Build

```bash
COMPOSE_PARALLEL_LIMIT=2 docker compose \
  -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml \
  build
```

`COMPOSE_PARALLEL_LIMIT=2` là bắt buộc. Compose build song song mặc định; 13 tiến
trình build cùng lúc (11 Maven + 2 `next build`), mỗi cái 1–2GB, sẽ vượt 24GB.

Lần build đầu lâu. `Dockerfile` đã sửa để chạy Maven **một lần** cho cả reactor
thay vì 11 lần — nếu thấy Maven khởi động lại ở mỗi service thì cache stage `build`
đang không được tái dùng, kiểm tra lại rằng không có `ARG` nào ở stage đó.

## 6. Chạy

```bash
docker compose \
  -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml \
  up -d

watch docker compose ps
```

Chờ tới khi 11 service + gateway đều `healthy`. `start_period` đã nâng lên 120s
cho lần boot đầu trên ARM.

## 7. Kiểm chứng — theo đúng thứ tự này

```bash
# 1. TLS + route API
curl -I https://pte-tenant.duckdns.org/actuator/health

# 2. Frontend
curl -I https://pte-tenant.duckdns.org/
curl -I https://pte-admin.duckdns.org/
```

**3. WebSocket của proctor.** Thứ dễ vỡ nhất và là thứ duy nhất trong stack không
phải HTTP thuần. Đăng nhập bằng tài khoản giám thị rồi mở DevTools → Network → WS,
xác nhận `wss://pte-tenant.duckdns.org/api/proctor/ws/...` lên `101 Switching
Protocols`. Test trước khi test bất cứ thứ gì khác — hỏng ở đây thì cả luồng giám
thị chết mà các endpoint HTTP vẫn xanh.

**4. Presigned URL của media.** Bắt đầu một attempt có audio prompt, xác nhận URL
trả về trỏ `https://pte-media.duckdns.org/...` chứ không phải `localhost:9000`, và
mở được trong tab mới. Nếu 403 `SignatureDoesNotMatch` thì Host header đang bị sửa
ở đâu đó giữa Caddy và MinIO.

## 8. Backup — không bỏ qua

Dữ liệu nằm trên máy người khác. Cron `pg_dump` là thứ biến "mất VM" từ mất trắng
thành mất một buổi chiều.

```bash
0 2 * * * cd ~/pte-org/pte-api && for c in core exam live async; do \
  docker compose exec -T pg-$c pg_dumpall -U postgres | gzip > ~/backup/$c-$(date +\%F).sql.gz; done
```

Đẩy `~/backup` lên Cloudflare R2 hoặc kéo về máy bạn. Backup nằm cùng ổ đĩa với
thứ nó bảo vệ thì không phải là backup.

---

## Debug

Không có port nào của service mở ra internet. Xem qua SSH tunnel:

```bash
ssh -L 15672:localhost:15672 \
    -L 16686:localhost:16686 \
    -L 9001:localhost:9001 \
    -L 8080:localhost:8080 <host>
```

| Port | UI |
|---|---|
| 15672 | RabbitMQ management |
| 16686 | Jaeger |
| 9001 | MinIO console |
| 8080 | Gateway trực tiếp, bỏ qua Caddy |

Cert Let's Encrypt nằm trong named volume `caddy_data`. **Đừng xóa volume đó khi
dọn dẹp** — mỗi lần mất là một lần xin cert mới, và giới hạn là 5 cert/domain/tuần.

```bash
docker compose ... down          # giữ volume
docker compose ... down -v       # xóa cả cert lẫn toàn bộ database
```
