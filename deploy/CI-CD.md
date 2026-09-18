# CI/CD deploy production

Hai workflow giá»‘ng nhau Ä‘Æ°á»£c Ä‘áº·t trong `pte-api` vÃ  `pte-web`. Push vÃ o `main`
cá»§a má»™t trong hai repository sáº½ SSH vÃ o VPS, fetch `main` cá»§a cáº£ hai repository,
build láº¡i stack vÃ  khá»Ÿi Ä‘á»™ng cÃ¡c container má»›i.

Workflow khÃ´ng lÆ°u `.env` trong GitHub vÃ  khÃ´ng cháº¡y `docker compose down -v`.
Database and Nginx/Certbot certificate volumes are preserved.

## GitHub Actions secrets

Táº¡o environment `production` trong cáº£ hai repository, sau Ä‘Ã³ thÃªm bá»‘n secrets:

| Secret | GiÃ¡ trá»‹ |
|---|---|
| `DEPLOY_HOST` | `217.142.185.74` hiá»‡n táº¡i; nÃªn thay báº±ng reserved public IP sau nÃ y |
| `DEPLOY_USER` | `ubuntu` |
| `DEPLOY_SSH_PRIVATE_KEY` | ToÃ n bá»™ ná»™i dung file private key `.key`, khÃ´ng pháº£i Ä‘Æ°á»ng dáº«n file |
| `DEPLOY_KNOWN_HOSTS` | Host key SSH cá»§a VPS Ä‘Ã£ xÃ¡c minh |

Táº¡o `DEPLOY_KNOWN_HOSTS` trÃªn mÃ¡y Ä‘Ã£ SSH thÃ nh cÃ´ng vÃ o VPS:

```powershell
ssh-keyscan -H 217.142.185.74
```

Chá»‰ Ä‘Æ°a káº¿t quáº£ vÃ o secret sau khi Ä‘á»‘i chiáº¿u fingerprint vá»›i host key Ä‘Ã¡ng tin
cáº­y cá»§a VPS. KhÃ´ng dÃ¹ng `ssh-keyscan` trá»±c tiáº¿p trong workflow Ä‘á»ƒ trÃ¡nh tin mÃ¹
vÃ o host key táº¡i thá»i Ä‘iá»ƒm deploy.

Náº¿u dÃ¹ng organization secrets thay vÃ¬ repository/environment secrets, giá»›i háº¡n
quyá»n truy cáº­p chá»‰ cho `pte-api` vÃ  `pte-web`.

## Äiá»u kiá»‡n trÃªn VPS

CÃ¡c Ä‘iá»u kiá»‡n nÃ y Ä‘Ã£ cÃ³ trÃªn VPS hiá»‡n táº¡i:

- `/home/ubuntu/pte-org/pte-api` vÃ  `/home/ubuntu/pte-org/pte-web` lÃ  Git clone,
  checkout branch `main`.
- `/home/ubuntu/pte-org/pte-api/.env` tá»“n táº¡i, permission `600`, vÃ  khÃ´ng cÃ³
  placeholder secret.
- User `ubuntu` cÃ³ quyá»n cháº¡y Docker.
- Docker Compose cÃ³ thá»ƒ Ä‘á»c ba file compose khi Ä‘á»©ng táº¡i `pte-api`.
- Ingress TCP `80` vÃ  `443` Ä‘Ã£ má»Ÿ á»Ÿ OCI Security List vÃ  iptables.

Workflow sáº½ fail trÆ°á»›c khi build náº¿u working tree trÃªn VPS cÃ³ thay Ä‘á»•i thá»§ cÃ´ng.
Äiá»u nÃ y báº£o vá»‡ `.env` vÃ  trÃ¡nh deploy Ä‘Ã¨ lÃªn chá»‰nh sá»­a chÆ°a Ä‘Æ°á»£c review.

## Láº§n cháº¡y Ä‘áº§u tiÃªn

Sau khi táº¡o Ä‘á»§ secrets, commit vÃ  push workflow nÃ y lÃªn `main` cá»§a tá»«ng repository.
Theo dÃµi tab **Actions**. CÃ³ thá»ƒ dÃ¹ng **Run workflow** Ä‘á»ƒ cháº¡y thá»§ cÃ´ng mÃ  khÃ´ng
cáº§n táº¡o thÃªm commit.

Build Ä‘áº§u tiÃªn trÃªn Oracle A1 cÃ³ thá»ƒ máº¥t vÃ i phÃºt. Workflow giá»›i háº¡n build cÃ²n
hai tiáº¿n trÃ¬nh Ä‘á»ƒ phÃ¹ há»£p vá»›i 2 vCPU vÃ  bá»™ nhá»› VPS hiá»‡n táº¡i, sau Ä‘Ã³ chá» tá»‘i Ä‘a
10 phÃºt cho cÃ¡c container cÃ³ healthcheck chuyá»ƒn sang `healthy`.
