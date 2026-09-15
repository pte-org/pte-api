# Admin student-roster rebuild

The roster is an Admin-owned projection. IAM remains the source of truth and
is queried only through its internal, service-key-authenticated export API.

## Run a full rebuild

Run this from the `pte-api` repository with the production environment file
already loaded by Compose. The command executes inside the Admin container, so
the request stays on the private Compose network and the key values are read
from the container environment rather than copied into a command or log.

```sh
docker compose --env-file .env -f docker-compose.yml -f docker-compose.services.yml \
  exec -T admin sh -c 'curl -fsS -X POST http://127.0.0.1:8082/api/admin/internal/rebuild/students \
  -H "X-Internal-Service-Key: $INTERNAL_SERVICE_KEY" \
  -H "X-Internal-Bootstrap-Key: $INTERNAL_BOOTSTRAP_KEY"'
```

The response reports `rowsApplied` and `durationMs`. The process is bounded to
200 IAM students per page and follows an opaque cursor until `hasMore` is
false. It is safe to rerun from the beginning and safe to run while the live
RabbitMQ consumer is applying new user events.

## Confirm counts by tenant

Use the database operator connection for the Admin database and run:

```sql
SELECT tenant_id, COUNT(*) AS student_count
FROM student_roster_entries
GROUP BY tenant_id
ORDER BY tenant_id;
```

Compare these counts with the IAM export result or the IAM-side student count
for each tenant. Do not query the IAM database from Admin or put either
service's database password in this runbook.
