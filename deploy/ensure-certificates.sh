#!/bin/bash
set -euo pipefail

# Run from the API repository root, using the same Compose project as deploy.
compose() {
    docker compose --env-file .env \
        -f docker-compose.yml \
        -f docker-compose.services.yml \
        -f docker-compose.deploy.yml "$@"
}

edge_ready=false
for attempt in $(seq 1 30); do
    if compose exec -T nginx wget -qO- http://127.0.0.1/nginx-health >/dev/null 2>&1; then
        edge_ready=true
        break
    fi
    sleep 2
done
if [ "$edge_ready" != true ]; then
    echo "Certificate setup stopped: Nginx did not become ready."
    exit 1
fi

tenant_domain="$(compose exec -T nginx printenv TENANT_DOMAIN)"
admin_domain="$(compose exec -T nginx printenv ADMIN_DOMAIN)"
practice_domain="$(compose exec -T nginx printenv PRACTICE_DOMAIN)"
acme_email="$(awk -F= '$1 == "ACME_EMAIL" { print substr($0, index($0, "=") + 1) }' .env)"
: "${tenant_domain:?TENANT_DOMAIN is required}"
: "${admin_domain:?ADMIN_DOMAIN is required}"
: "${practice_domain:?PRACTICE_DOMAIN is required}"
: "${acme_email:?ACME_EMAIL is required}"

# Check SANs and expiry inside the certificate volume, not root-owned host paths.
# No private key or env values are printed by the check.
if compose run --rm --no-deps --entrypoint python certbot -c '
import pathlib, ssl, sys, time
directory = pathlib.Path("/etc/letsencrypt/live/pte-platform")
if not (directory / "fullchain.pem").is_file() or not (directory / "privkey.pem").is_file():
    sys.exit(1)
certificate = ssl._ssl._test_decode_cert(str(directory / "fullchain.pem"))
names = {value for kind, value in certificate.get("subjectAltName", []) if kind == "DNS"}
renewal_window_seconds = 30 * 24 * 60 * 60
valid_until = ssl.cert_time_to_seconds(certificate["notAfter"])
sys.exit(0 if set(sys.argv[1:]) <= names and valid_until > time.time() + renewal_window_seconds else 1)
' "$tenant_domain" "$admin_domain" "$practice_domain"; then
    echo "Existing certificate covers all hosted domains."
    exit 0
fi

compose run --rm --no-deps certbot certonly \
    --webroot -w /var/www/certbot \
    --cert-name pte-platform \
    --expand --keep-until-expiring \
    --email "$acme_email" \
    --agree-tos --no-eff-email --non-interactive \
    -d "$tenant_domain" -d "$admin_domain" -d "$practice_domain"

compose restart nginx
