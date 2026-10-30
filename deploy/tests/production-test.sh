#!/bin/bash
set -Eeuo pipefail

# Execute the complete deploy and certificate scripts with isolated command stubs.
# Only the three fixed repository paths are redirected to disposable fixtures.
deploy_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
fixture_dir="$(mktemp -d /tmp/pte-production-test.XXXXXX)"
[[ "$fixture_dir" == /tmp/pte-production-test.* ]]
trap 'rm -rf -- "$fixture_dir"' EXIT
mkdir -p "$fixture_dir/api/.git" "$fixture_dir/web/.git" "$fixture_dir/practice/.git" "$fixture_dir/api/deploy" "$fixture_dir/bin"
printf 'ACME_EMAIL=deployment-test@example.invalid\n' > "$fixture_dir/api/.env"
cp "$deploy_dir/ensure-certificates.sh" "$fixture_dir/api/deploy/"
sed -e "s|^API_DIR=.*|API_DIR='$fixture_dir/api'|" \
    -e "s|^WEB_DIR=.*|WEB_DIR='$fixture_dir/web'|" \
    -e "s|^PRACTICE_DIR=.*|PRACTICE_DIR='$fixture_dir/practice'|" \
    "$deploy_dir/production.sh" > "$fixture_dir/production.sh"

cat > "$fixture_dir/stubs.sh" <<'STUBS'
git() {
  case "$*" in
    *'branch --show-current') printf 'main\n' ;;
    *'status --porcelain'|*'fetch --prune origin main'|*'merge --ff-only FETCH_HEAD') ;;
    *) echo "Unexpected git command" >&2; return 90 ;;
  esac
}
stat() { printf '600\n'; }
sleep() {
  # End the build heartbeat immediately; advance the unhealthy timeout without waiting.
  if [ "$1" = 20 ]; then return 1; fi
  if [ "$SCENARIO" = unhealthy ]; then SECONDS=$((SECONDS + 601)); fi
}
docker() {
  if [ "$1" = inspect ]; then
    if [ "$SCENARIO" = unhealthy ] || [ ! -f "$FIXTURE_STATE" ]; then
      echo health-pending >> "$EVENT_LOG"
      printf '/app|running|unhealthy\n'
      touch "$FIXTURE_STATE"
    else
      echo health-ready >> "$EVENT_LOG"
      printf '/app|running|healthy\n'
    fi
    return 0
  fi
  [ "$1" = compose ] || { echo "Unexpected docker command" >&2; return 90; }
  shift
  while [ "$1" = --env-file ] || [ "$1" = -f ]; do shift 2; done
  case "$*" in
    'config --quiet')
      echo compose-config >> "$EVENT_LOG"
      [ "$SCENARIO" != compose-invalid ] ;;
    build) echo build >> "$EVENT_LOG" ;;
    'up -d --no-build --remove-orphans') echo up >> "$EVENT_LOG" ;;
    'exec -T nginx wget -qO- http://127.0.0.1/nginx-health') return 0 ;;
    'exec -T nginx printenv TENANT_DOMAIN') echo tenant.example.invalid ;;
    'exec -T nginx printenv ADMIN_DOMAIN') echo admin.example.invalid ;;
    'exec -T nginx printenv PRACTICE_DOMAIN') echo practice.example.invalid ;;
    'run --rm --no-deps --entrypoint python certbot -c '*)
      echo certificate-valid >> "$EVENT_LOG" ;;
    'ps -aq') echo fixture-container ;;
    'ps -a') echo diagnostics >> "$EVENT_LOG" ;;
    ps) echo finished >> "$EVENT_LOG" ;;
    'exec -T nginx nginx -t')
      echo nginx-test >> "$EVENT_LOG"
      [ "$SCENARIO" != nginx-invalid ] ;;
    'exec -T nginx nginx -s reload')
      echo nginx-reload >> "$EVENT_LOG"
      [ "$SCENARIO" != reload-failure ] ;;
    *) echo "Unexpected compose command" >&2; return 90 ;;
  esac
}
STUBS

# xargs executes a binary rather than a shell function; keep inspect stubbed too.
printf '#!/bin/bash\ndocker "$@"\n' > "$fixture_dir/bin/docker"
chmod +x "$fixture_dir/bin/docker"
export PATH="$fixture_dir/bin:$PATH"

assert_event() { grep -qx "$1" "$EVENT_LOG"; }
assert_absent() { ! grep -qx "$1" "$EVENT_LOG"; }
assert_before() {
  local first second
  first="$(grep -nx "$1" "$EVENT_LOG" | head -1 | cut -d: -f1)"
  second="$(grep -nx "$2" "$EVENT_LOG" | head -1 | cut -d: -f1)"
  [ -n "$first" ] && [ -n "$second" ] && [ "$first" -lt "$second" ]
}

for scenario in normal unhealthy nginx-invalid compose-invalid reload-failure; do
  export SCENARIO="$scenario" EVENT_LOG="$fixture_dir/$scenario.events"
  export FIXTURE_STATE="$fixture_dir/$scenario.state"
  : > "$EVENT_LOG"
  status=0
  BASH_ENV="$fixture_dir/stubs.sh" PTE_DEPLOY_LOCK_HELD=1 \
    bash "$fixture_dir/production.sh" > "$fixture_dir/$scenario.output" 2>&1 || status=$?
  case "$scenario" in
    normal)
      [ "$status" -eq 0 ]
      assert_event certificate-valid
      assert_before up certificate-valid
      assert_before certificate-valid health-pending
      assert_before health-pending health-ready
      assert_before health-ready nginx-test
      assert_before nginx-test nginx-reload
      assert_before nginx-reload finished
      [ "$(grep -cx nginx-reload "$EVENT_LOG")" -eq 1 ] ;;
    unhealthy)
      [ "$status" -ne 0 ]
      assert_event health-pending
      assert_event diagnostics
      assert_absent health-ready
      assert_absent nginx-test
      assert_absent nginx-reload
      assert_absent finished ;;
    nginx-invalid)
      [ "$status" -ne 0 ]
      assert_before health-ready nginx-test
      assert_absent nginx-reload
      assert_absent finished ;;
    compose-invalid)
      [ "$status" -ne 0 ]
      assert_event compose-config
      assert_absent up
      assert_absent nginx-test
      assert_absent nginx-reload ;;
    reload-failure)
      [ "$status" -ne 0 ]
      assert_before nginx-test nginx-reload
      assert_absent finished ;;
  esac
  printf 'PASS %s\n' "$scenario"
done
