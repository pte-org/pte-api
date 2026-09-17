#!/usr/bin/env bash
# Manual end-to-end seed script for Plan B (score-template-exam-generation).
#
# Unlike seed-e2e.ps1 (stale — assumes a gateway at :8080 and a separate
# "iam" database, both removed by the modular-monolith migration), this
# talks to the real, current single app at :8091 with no path prefixes.
#
# What it does:
#   1. Logs in as the bootstrap PLATFORM_ADMIN (must already exist — see
#      the SQL block in the accompanying chat message / README's stale
#      "Populating test data" section, which is still correct for the SQL
#      itself, just wrong about the database name: use `pte`, not `iam`).
#   2. Creates a Tenant, Organization, HOST_ADMIN user, Program, StudentClass,
#      and 2 Students, and enrolls both students in the Class.
#   3. As platform admin, creates + publishes enough READING-section
#      questions (max_count of all 5 Reading task types = 20 questions) so
#      a "skills": ["READING"] exam can always generate successfully,
#      regardless of how the random roll lands.
#   4. As the host, creates one exam Session with skills=["READING"],
#      assigns the Class to it, and prints the result.
#
# Usage:
#   ADMIN_EMAIL=admin@test.local ADMIN_PASSWORD='Password123!' ./scripts/seed-plan-b.sh
#
# Requires: curl, jq. Requires pte-api's Postgres/Redis/RabbitMQ/MinIO
# running (docker compose --env-file .env.local -f docker-compose.yml up -d)
# and the app itself running on :8091 (via IDE or ./mvnw -pl app spring-boot:run).

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8091}"
ADMIN_EMAIL="${ADMIN_EMAIL:?Set ADMIN_EMAIL to your bootstrapped PLATFORM_ADMIN email}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:?Set ADMIN_PASSWORD to your bootstrapped PLATFORM_ADMIN password}"
RUN_ID="$(date +%s)"

api() {
  local method="$1" path="$2" token="$3" body="${4:-}"
  if [[ -n "$body" ]]; then
    curl -sf -X "$method" "$BASE_URL$path" \
      -H "Content-Type: application/json" \
      ${token:+-H "Authorization: Bearer $token"} \
      -d "$body"
  else
    curl -sf -X "$method" "$BASE_URL$path" \
      ${token:+-H "Authorization: Bearer $token"}
  fi
}

echo "== Logging in as platform admin ($ADMIN_EMAIL) =="
ADMIN_TOKEN=$(api POST /auth/login "" "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r '.data.accessToken')

echo "== Creating Tenant =="
TENANT_ID=$(api POST /tenants "$ADMIN_TOKEN" "{\"name\":\"Seed Tenant $RUN_ID\",\"organizationType\":\"SCHOOL\",\"packageName\":\"STANDARD\",\"studentLimit\":100}" | jq -r '.data.publicId')
echo "  tenant: $TENANT_ID"

echo "== Creating Organization =="
ORG_ID=$(api POST "/tenants/$TENANT_ID/organizations" "$ADMIN_TOKEN" "{\"name\":\"Main Campus\",\"facilityType\":\"MAIN\"}" | jq -r '.data.publicId')
echo "  organization: $ORG_ID"

echo "== Creating HOST_ADMIN user =="
HOST_EMAIL="host+$RUN_ID@seed.test"
HOST_PASSWORD='Password123!'
api POST /users "$ADMIN_TOKEN" "{\"email\":\"$HOST_EMAIL\",\"fullName\":\"Seed Host Admin\",\"password\":\"$HOST_PASSWORD\",\"roles\":[\"HOST_ADMIN\"],\"tenantId\":\"$TENANT_ID\"}" > /dev/null
echo "  host: $HOST_EMAIL / $HOST_PASSWORD"

echo "== Logging in as host =="
HOST_TOKEN=$(api POST /auth/login "" "{\"email\":\"$HOST_EMAIL\",\"password\":\"$HOST_PASSWORD\"}" | jq -r '.data.accessToken')

echo "== Creating Program =="
PROGRAM_ID=$(api POST "/organizations/$ORG_ID/programs" "$HOST_TOKEN" "{\"name\":\"Seed Program $RUN_ID\"}" | jq -r '.data.publicId')
echo "  program: $PROGRAM_ID"

echo "== Creating StudentClass =="
CLASS_ID=$(api POST "/organizations/$ORG_ID/programs/$PROGRAM_ID/classes" "$HOST_TOKEN" '{"name":"Seed Class A"}' | jq -r '.data.publicId')
echo "  class: $CLASS_ID"

echo "== Creating 2 students and enrolling them in the Class =="
for i in 1 2; do
  STUDENT_ID=$(api POST /users "$ADMIN_TOKEN" "{\"email\":\"student$i+$RUN_ID@seed.test\",\"fullName\":\"Seed Student $i\",\"password\":\"Password123!\",\"roles\":[\"STUDENT\"],\"tenantId\":\"$TENANT_ID\"}" | jq -r '.data.publicId')
  api POST "/organizations/$ORG_ID/programs/$PROGRAM_ID/classes/$CLASS_ID/students" "$HOST_TOKEN" "{\"studentPublicId\":\"$STUDENT_ID\"}" > /dev/null
  echo "  student $i: $STUDENT_ID (enrolled in class)"
done

# --- Question bank: enough READING-section stock for a deterministic exam ---
# max_count of all 5 Reading task types (V14__score_template.sql), so a
# "skills": ["READING"] session always generates regardless of the random roll.
publish_question() {
  local body="$1"
  local id
  id=$(api POST /questions "$ADMIN_TOKEN" "$body" | jq -r '.data.publicId')
  api POST "/questions/$id/publish" "$ADMIN_TOKEN" "" > /dev/null
  echo "  published: $id"
}

echo "== Seeding + publishing Reading question bank =="

for i in $(seq 1 3); do
  publish_question "{\"pteTaskType\":\"MC_READING_SINGLE\",\"visibility\":\"SHARED\",\"title\":\"MCR-S $RUN_ID-$i\",\"promptText\":\"Urban planners increasingly use green roofs to reduce the heat-island effect.\",\"options\":[{\"text\":\"Green roofs reduce urban heat.\",\"correct\":true,\"orderIndex\":0},{\"text\":\"Green roofs are illegal.\",\"correct\":false,\"orderIndex\":1},{\"text\":\"Planners avoid rooftops.\",\"correct\":false,\"orderIndex\":2},{\"text\":\"Heat islands only occur rurally.\",\"correct\":false,\"orderIndex\":3}]}"
done

for i in $(seq 1 3); do
  publish_question "{\"pteTaskType\":\"MC_READING_MULTIPLE\",\"visibility\":\"SHARED\",\"title\":\"MCR-M $RUN_ID-$i\",\"promptText\":\"Which of the following are benefits of renewable energy?\",\"options\":[{\"text\":\"Lower long-term costs\",\"correct\":true,\"orderIndex\":0},{\"text\":\"Reduced emissions\",\"correct\":true,\"orderIndex\":1},{\"text\":\"Instant installation\",\"correct\":false,\"orderIndex\":2},{\"text\":\"No maintenance\",\"correct\":false,\"orderIndex\":3}]}"
done

for i in $(seq 1 3); do
  publish_question "{\"pteTaskType\":\"RE_ORDER_PARAGRAPHS\",\"visibility\":\"SHARED\",\"title\":\"ROP $RUN_ID-$i\",\"options\":[{\"text\":\"First paragraph.\",\"correct\":false,\"orderIndex\":0},{\"text\":\"Second paragraph.\",\"correct\":false,\"orderIndex\":1},{\"text\":\"Third paragraph.\",\"correct\":false,\"orderIndex\":2}]}"
done

for i in $(seq 1 5); do
  publish_question "{\"pteTaskType\":\"FILL_BLANKS_READING\",\"visibility\":\"SHARED\",\"title\":\"FBR $RUN_ID-$i\",\"promptText\":\"Climate change is one of the most ___ issues of our time.\",\"options\":[{\"text\":\"pressing\",\"correct\":true,\"orderIndex\":0},{\"text\":\"irrelevant\",\"correct\":false,\"orderIndex\":1},{\"text\":\"amusing\",\"correct\":false,\"orderIndex\":2}]}"
done

for i in $(seq 1 6); do
  publish_question "{\"pteTaskType\":\"FILL_BLANKS_READING_WRITING\",\"visibility\":\"SHARED\",\"title\":\"FBRW $RUN_ID-$i\",\"promptText\":\"Solar power has become increasingly ___ over the past decade.\",\"options\":[{\"text\":\"affordable\",\"correct\":true,\"orderIndex\":0},{\"text\":\"forbidden\",\"correct\":false,\"orderIndex\":1},{\"text\":\"irrelevant\",\"correct\":false,\"orderIndex\":2}]}"
done

echo "== Creating exam Session (skills: READING) as host =="
OPENS_AT=$(date -u -d '+10 minutes' +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v+10M +"%Y-%m-%dT%H:%M:%SZ")
CLOSES_AT=$(date -u -d '+2 hours' +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v+2H +"%Y-%m-%dT%H:%M:%SZ")
SESSION_RESPONSE=$(api POST /sessions "$HOST_TOKEN" "{\"name\":\"Reading Mock $RUN_ID\",\"skills\":[\"READING\"],\"opensAt\":\"$OPENS_AT\",\"closesAt\":\"$CLOSES_AT\",\"examMode\":\"PRACTICE\",\"capacity\":30}")
SESSION_ID=$(echo "$SESSION_RESPONSE" | jq -r '.data.publicId')
echo "  session: $SESSION_ID"
echo "$SESSION_RESPONSE" | jq '.data'

echo "== Assigning Class to the Session =="
api POST "/sessions/$SESSION_ID/classes" "$HOST_TOKEN" "{\"classPublicId\":\"$CLASS_ID\"}" | jq '.data'

echo "== Listing assigned classes (sanity check) =="
api GET "/sessions/$SESSION_ID/classes" "$HOST_TOKEN" | jq '.data'

cat <<EOF

== Done ==
Tenant:  $TENANT_ID
Org:     $ORG_ID
Host:    $HOST_EMAIL / $HOST_PASSWORD
Program: $PROGRAM_ID
Class:   $CLASS_ID
Session: $SESSION_ID
EOF
