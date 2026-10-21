#!/bin/bash
set -Eeuo pipefail

API_DIR=/home/ubuntu/pte-org/pte-api
WEB_DIR=/home/ubuntu/pte-org/pte-web
PRACTICE_DIR=/home/ubuntu/pte-org/pte-practice

test -d "$API_DIR/.git"
test -d "$WEB_DIR/.git"
test -d "$PRACTICE_DIR/.git"
test -f "$API_DIR/.env"
test "$(stat -c '%a' "$API_DIR/.env")" = "600"

# All three repositories deploy the shared stack under the same VPS lock.
if [ "${PTE_DEPLOY_LOCK_HELD:-0}" != 1 ]; then
  exec 9>/tmp/pte-platform-production-deploy.lock
  if ! flock -w 1800 9; then
    echo "Deployment stopped: another deployment still holds the VPS lock."
    exit 1
  fi
fi

sync_repo() {
  local repo_dir="$1"

  test "$(git -C "$repo_dir" branch --show-current)" = "main"
  if [ -n "$(git -C "$repo_dir" status --porcelain)" ]; then
    echo "Deployment stopped: $repo_dir has uncommitted changes."
    git -C "$repo_dir" status --short
    exit 1
  fi

  git -C "$repo_dir" fetch --prune origin main
  git -C "$repo_dir" merge --ff-only FETCH_HEAD
}

sync_repo "$API_DIR"
sync_repo "$WEB_DIR"
sync_repo "$PRACTICE_DIR"

cd "$API_DIR"

compose() {
  docker compose --env-file .env \
    -f docker-compose.yml \
    -f docker-compose.services.yml \
    -f docker-compose.deploy.yml "$@"
}

compose config --quiet
export COMPOSE_PARALLEL_LIMIT=2
# Keep the SSH channel active while BuildKit is quiet on a small VPS.
heartbeat() {
  while sleep 20; do
    echo "Docker build still running..."
  done
}
heartbeat &
heartbeat_pid=$!
if compose build; then
  build_status=0
else
  build_status=$?
fi
kill "$heartbeat_pid" 2>/dev/null || true
wait "$heartbeat_pid" 2>/dev/null || true
if [ "$build_status" -ne 0 ]; then
  exit "$build_status"
fi
compose up -d --no-build --remove-orphans

bash deploy/ensure-certificates.sh

deadline=$((SECONDS + 600))
while true; do
  unhealthy="$(
    compose ps -aq \
      | xargs -r docker inspect --format '{{.Name}}|{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}' \
      | awk -F'|' '$2 != "running" || ($3 != "" && $3 != "healthy")'
  )"

  if [ -z "$unhealthy" ]; then
    break
  fi

  if [ "$SECONDS" -ge "$deadline" ]; then
    echo "Deployment stopped: containers did not become healthy."
    printf '%s\n' "$unhealthy"
    compose ps -a
    exit 1
  fi

  sleep 10
done

compose ps
