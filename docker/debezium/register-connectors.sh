#!/bin/sh
# Registers every outbox connector against a running Debezium Connect REST API.
# Idempotent: PUT .../config creates-or-updates, safe to re-run.
set -eu

CONNECT_URL="${DEBEZIUM_CONNECT_URL:-http://debezium-connect:8083}"
CONNECTORS_DIR="$(dirname "$0")/connectors"

echo "Waiting for Debezium Connect at ${CONNECT_URL} ..."
until curl -sf "${CONNECT_URL}/connectors" > /dev/null; do
  sleep 2
done

for file in "${CONNECTORS_DIR}"/*.json; do
  name=$(basename "$file" .json)
  config=$(jq -c '.config' "$file")
  echo "Registering ${name} ..."
  curl -sf -X PUT \
    -H "Content-Type: application/json" \
    -d "${config}" \
    "${CONNECT_URL}/connectors/${name}/config" > /dev/null
done

echo "All outbox connectors registered."
