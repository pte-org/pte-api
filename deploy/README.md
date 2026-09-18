# PTE hosted deployment runbook

The hosted demo is one modular-monolith stack on the Oracle VPS. Nginx is the
only public edge: it terminates TLS and routes the tenant, vendor, API, and
WebSocket origins. Question-bank media uploads go directly from the browser to
Cloudinary after the app issues signed upload parameters.

| Origin | Routing |
|---|---|
| `pte-tenant.duckdns.org` | tenant-web, `/api/v1/*`, `/actuator/*`, `/ws` |
| `pte-admin.duckdns.org` | vendor-web and the same API/transport routes |
Only TCP 22, 80, and 443 should be allowed from the internet. The hosted
Compose overlay removes host bindings for app, web, PostgreSQL, Redis,
RabbitMQ, Mailpit, and Jaeger.

## VPS and environment

Open TCP 80 and 443 in both the OCI security list and the VPS firewall. Keep a
reserved public IP for DNS, or update all three DuckDNS records when the IP
changes. Clone the repositories as siblings:

```bash
mkdir -p /home/ubuntu/pte-org
cd /home/ubuntu/pte-org
git clone <pte-api-url> pte-api
git clone <pte-web-url> pte-web
```

Create `/home/ubuntu/pte-org/pte-api/.env` from `.env.example`, fill all
required credentials, and set these hosted values:

```text
TENANT_DOMAIN=pte-tenant.duckdns.org
ADMIN_DOMAIN=pte-admin.duckdns.org
ACME_EMAIL=admin@example.com
```

```bash
chmod 600 /home/ubuntu/pte-org/pte-api/.env
```

Never commit `.env`, certificates, or private keys.

## First deployment and certificate bootstrap

The Nginx entrypoint selects HTTP bootstrap mode when the named
`pte_letsencrypt` volume has no `pte-platform` certificate. The deployment
workflow then runs Certbot through the shared webroot and restarts Nginx into
TLS mode.

For a manual first deployment:

```bash
cd /home/ubuntu/pte-org/pte-api
docker compose --env-file .env -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml up -d --build
docker compose --env-file .env -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml run --rm certbot certonly --webroot -w /var/www/certbot --cert-name pte-platform --email "$ACME_EMAIL" --agree-tos --no-eff-email --non-interactive -d "$TENANT_DOMAIN" -d "$ADMIN_DOMAIN"
docker compose --env-file .env -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml restart nginx
```

Do not issue certificates repeatedly; preserve `pte_letsencrypt`.

## Renewal and monitoring

Run renewal twice a day from cron. Reload Nginx only after renewal and config
validation succeed:

```bash
cd /home/ubuntu/pte-org/pte-api
docker compose --env-file .env -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml run --rm certbot renew --webroot -w /var/www/certbot
docker compose --env-file .env -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml exec -T nginx nginx -t
docker compose --env-file .env -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml exec -T nginx nginx -s reload
```

Test renewal without changing certificates:

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml run --rm certbot renew --dry-run
```

Alert when the certificate has fewer than 14 days remaining:

```bash
openssl x509 -checkend $((14*24*3600)) -noout -in /var/lib/docker/volumes/pte_letsencrypt/_data/live/pte-platform/cert.pem
```

## Verification

```bash
docker compose --env-file .env -f docker-compose.yml -f docker-compose.services.yml -f docker-compose.deploy.yml ps
curl -fsS https://pte-tenant.duckdns.org/actuator/health
curl -fsS https://pte-tenant.duckdns.org/
curl -fsS https://pte-admin.duckdns.org/
```

Expected: app healthy, both Next.js containers running, Nginx healthy, and
HTTP 200 from all public checks. An authenticated business API may return 401;
that is an auth result, not an edge failure. A proctor WebSocket must reach
HTTP 101, and presigned media URLs must keep the media host and object path.

## Debugging and rollback

Use `docker compose ... ps -a` and `docker compose ... logs nginx app` to
diagnose failures. Always run `docker compose ... exec -T nginx nginx -t`
before reloading.

Rollback means checking out the previously verified commit in both repositories
and rerunning deployment. Do not delete `pte_letsencrypt`,
`pte_postgres_data`; deleting it destroys certificates or application data and
is not a normal rollback.
