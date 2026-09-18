#!/usr/bin/env bash
# Manual end-to-end seed script for the CURRENT merged design (Plan A/B's
# skills + ScoreTemplate exam generation, gated by dev's Subscription/billing
# module — see .claude/plans/merge-dev-conflict-resolution/plan.md).
#
# Rewritten from scratch after the second `dev` merge (Caddy->Nginx, /api/v1
# versioned routes, tenant onboarding rework, billing/subscription module).
# The OLD version of this script (and scripts/seed-e2e.ps1, which still
# reflects a manual-blueprint/composition flow Plan B removed) are stale —
# do not use them as a reference for the CreateSessionRequest/question shapes.
#
# What it does:
#   1. Logs in as the bootstrap PLATFORM_ADMIN (must already exist — see the
#      SQL block in this repo's merge-conflict chat history, or bootstrap it
#      yourself: username/email admin@test.local, PLATFORM_ADMIN role,
#      against the running `pte-postgres` container's `pte` database).
#   2. Onboards a Tenant (which provisions one Organization), creates a HOST_ADMIN and 2
#      STUDENT users, a Program, a StudentClass, and enrolls both students.
#   3. As platform admin: creates + activates a Plan, issues a LicenseCode.
#   4. As host: redeems the LicenseCode (billing gate needs an ACTIVE
#      Subscription before a session can be created), looks up the resulting
#      subscriptionPublicId.
#   5. As platform admin: creates + publishes enough READING-section
#      questions (max_count of all 5 Reading task types = 20 questions) so a
#      "skills": ["READING"] exam can always generate successfully.
#   6. As host: creates one exam Session (skills=["READING"], gated by the
#      Subscription from step 4), assigns the Class to it.
#
# Usage:
#   BASE_URL=http://localhost:8080 ADMIN_EMAIL=admin@test.local \
#     ADMIN_PASSWORD='Password123!' ./scripts/seed-plan-b.sh
#
# Requires: curl, jq. Requires the full docker compose stack running
# (docker compose --env-file .env -f docker-compose.yml -f
# docker-compose.services.yml [-f docker-compose.local-test.yml] up -d).

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:?Set ADMIN_EMAIL to your bootstrapped PLATFORM_ADMIN username/email}"
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
ADMIN_TOKEN=$(api POST /api/v1/auth/login "" "{\"username\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jq -r '.data.accessToken')

echo "== Onboarding Tenant =="
TENANT_CODE="seed-$RUN_ID"
TENANT_ID=$(api POST /api/v1/tenants "$ADMIN_TOKEN" "{\"code\":\"$TENANT_CODE\",\"name\":\"Seed Tenant $RUN_ID\",\"organizationType\":\"SCHOOL\",\"packageName\":\"STANDARD\",\"studentLimit\":100}" | jq -r '.data.publicId')
echo "  tenant: $TENANT_ID (code: $TENANT_CODE)"

echo "== Reading provisioned Organization =="
ORG_ID=$(api GET "/api/v1/tenants/$TENANT_ID/organizations" "$ADMIN_TOKEN" | jq -r '.data[0].publicId')
echo "  organization: $ORG_ID"

echo "== Creating HOST_ADMIN user =="
HOST_EMAIL="host+$RUN_ID@seed.test"
HOST_PASSWORD='Password123!'
api POST /api/v1/users "$ADMIN_TOKEN" "{\"email\":\"$HOST_EMAIL\",\"fullName\":\"Seed Host Admin\",\"password\":\"$HOST_PASSWORD\",\"roles\":[\"HOST_ADMIN\"],\"tenantId\":\"$TENANT_ID\"}" > /dev/null
echo "  host: $HOST_EMAIL / $HOST_PASSWORD"

echo "== Logging in as host =="
HOST_TOKEN=$(api POST /api/v1/auth/login "" "{\"username\":\"$HOST_EMAIL\",\"password\":\"$HOST_PASSWORD\"}" | jq -r '.data.accessToken')

echo "== Creating Program =="
PROGRAM_ID=$(api POST "/api/v1/organizations/$ORG_ID/programs" "$HOST_TOKEN" "{\"name\":\"Seed Program $RUN_ID\"}" | jq -r '.data.publicId')
echo "  program: $PROGRAM_ID"

echo "== Creating StudentClass =="
CLASS_ID=$(api POST "/api/v1/organizations/$ORG_ID/programs/$PROGRAM_ID/classes" "$HOST_TOKEN" '{"name":"Seed Class A"}' | jq -r '.data.publicId')
echo "  class: $CLASS_ID"

echo "== Creating 2 students and enrolling them in the Class =="
for i in 1 2; do
  STUDENT_ID=$(api POST /api/v1/users "$ADMIN_TOKEN" "{\"email\":\"student$i+$RUN_ID@seed.test\",\"fullName\":\"Seed Student $i\",\"password\":\"Password123!\",\"roles\":[\"STUDENT\"],\"tenantId\":\"$TENANT_ID\"}" | jq -r '.data.publicId')
  api POST "/api/v1/organizations/$ORG_ID/programs/$PROGRAM_ID/classes/$CLASS_ID/students" "$HOST_TOKEN" "{\"studentPublicId\":\"$STUDENT_ID\"}" > /dev/null
  echo "  student $i: $STUDENT_ID (enrolled in class)"
done

# --- Billing: Plan -> LicenseCode -> redeem -> active Subscription ---
echo "== Creating + activating a Plan (platform admin) =="
PLAN_ID=$(api POST /api/v1/plans "$ADMIN_TOKEN" '{"name":"Seed Exam Package","description":"Seed plan","type":"EXAM_PACKAGE","price":0,"currency":"VND","durationDays":30,"maxStudentsPerSession":100}' | jq -r '.data.publicId')
api POST "/api/v1/plans/$PLAN_ID/activation" "$ADMIN_TOKEN" "" > /dev/null
echo "  plan: $PLAN_ID (active)"

echo "== Issuing a LicenseCode for that Plan =="
CODE_EXPIRES=$(date -u -d '+30 days' +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v+30d +"%Y-%m-%dT%H:%M:%SZ")
LICENSE_CODE=$(api POST /api/v1/license-codes "$ADMIN_TOKEN" "{\"planId\":\"$PLAN_ID\",\"codeExpiresAt\":\"$CODE_EXPIRES\"}" | jq -r '.data.code')
echo "  license code: $LICENSE_CODE"

echo "== Redeeming the LicenseCode as host (activates the Subscription) =="
api POST /api/v1/license-code-redemptions "$HOST_TOKEN" "{\"code\":\"$LICENSE_CODE\"}" | jq '.data'

echo "== Looking up the resulting Subscription =="
SUBSCRIPTION_ID=$(api GET /api/v1/subscriptions "$HOST_TOKEN" | jq -r '.data[0].publicId')
echo "  subscription: $SUBSCRIPTION_ID"

# --- Question bank: enough READING-section stock for a deterministic exam ---
# max_count of all 5 Reading task types (V14__score_template.sql), so a
# "skills": ["READING"] session always generates regardless of the random roll.
publish_question() {
  local body="$1"
  local id
  id=$(api POST /api/v1/questions "$ADMIN_TOKEN" "$body" | jq -r '.data.publicId')
  api POST "/api/v1/questions/$id/publish" "$ADMIN_TOKEN" "" > /dev/null
  echo "  published: $id"
}

echo "== Seeding + publishing Reading question bank (platform admin) =="

for i in $(seq 1 3); do
  publish_question "{\"pteTaskType\":\"MC_READING_SINGLE\",\"title\":\"MCR-S $RUN_ID-$i\",\"promptText\":\"Urban planners increasingly use green roofs to reduce the heat-island effect.\",\"options\":[{\"text\":\"Green roofs reduce urban heat.\",\"correct\":true,\"orderIndex\":0},{\"text\":\"Green roofs are illegal.\",\"correct\":false,\"orderIndex\":1},{\"text\":\"Planners avoid rooftops.\",\"correct\":false,\"orderIndex\":2},{\"text\":\"Heat islands only occur rurally.\",\"correct\":false,\"orderIndex\":3}]}"
done

for i in $(seq 1 3); do
  publish_question "{\"pteTaskType\":\"MC_READING_MULTIPLE\",\"title\":\"MCR-M $RUN_ID-$i\",\"promptText\":\"Which of the following are benefits of renewable energy?\",\"options\":[{\"text\":\"Lower long-term costs\",\"correct\":true,\"orderIndex\":0},{\"text\":\"Reduced emissions\",\"correct\":true,\"orderIndex\":1},{\"text\":\"Instant installation\",\"correct\":false,\"orderIndex\":2},{\"text\":\"No maintenance\",\"correct\":false,\"orderIndex\":3}]}"
done

for i in $(seq 1 3); do
  publish_question "{\"pteTaskType\":\"RE_ORDER_PARAGRAPHS\",\"title\":\"ROP $RUN_ID-$i\",\"options\":[{\"text\":\"First paragraph.\",\"correct\":false,\"orderIndex\":0},{\"text\":\"Second paragraph.\",\"correct\":false,\"orderIndex\":1},{\"text\":\"Third paragraph.\",\"correct\":false,\"orderIndex\":2}]}"
done

for i in $(seq 1 5); do
  publish_question "{\"pteTaskType\":\"FILL_BLANKS_READING\",\"title\":\"FBR $RUN_ID-$i\",\"promptText\":\"Climate change is one of the most ___ issues of our time.\",\"options\":[{\"text\":\"pressing\",\"correct\":true,\"orderIndex\":0},{\"text\":\"irrelevant\",\"correct\":false,\"orderIndex\":1},{\"text\":\"amusing\",\"correct\":false,\"orderIndex\":2}]}"
done

for i in $(seq 1 6); do
  publish_question "{\"pteTaskType\":\"FILL_BLANKS_READING_WRITING\",\"title\":\"FBRW $RUN_ID-$i\",\"promptText\":\"Solar power has become increasingly ___ over the past decade.\",\"options\":[{\"text\":\"affordable\",\"correct\":true,\"orderIndex\":0},{\"text\":\"forbidden\",\"correct\":false,\"orderIndex\":1},{\"text\":\"irrelevant\",\"correct\":false,\"orderIndex\":2}]}"
done

echo "== Creating exam Session (skills: READING, gated by the Subscription) as host =="
OPENS_AT=$(date -u -d '+10 minutes' +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v+10M +"%Y-%m-%dT%H:%M:%SZ")
CLOSES_AT=$(date -u -d '+2 hours' +"%Y-%m-%dT%H:%M:%SZ" 2>/dev/null || date -u -v+2H +"%Y-%m-%dT%H:%M:%SZ")
SESSION_RESPONSE=$(api POST /api/v1/sessions "$HOST_TOKEN" "{\"name\":\"Reading Mock $RUN_ID\",\"subscriptionPublicId\":\"$SUBSCRIPTION_ID\",\"skills\":[\"READING\"],\"opensAt\":\"$OPENS_AT\",\"closesAt\":\"$CLOSES_AT\",\"examMode\":\"PRACTICE\",\"capacity\":30}")
SESSION_ID=$(echo "$SESSION_RESPONSE" | jq -r '.data.publicId')
echo "  session: $SESSION_ID"
echo "$SESSION_RESPONSE" | jq '.data'

echo "== Assigning Class to the Session =="
api POST "/api/v1/sessions/$SESSION_ID/classes" "$HOST_TOKEN" "{\"classPublicId\":\"$CLASS_ID\"}" | jq '.data'

echo "== Listing assigned classes (sanity check) =="
api GET "/api/v1/sessions/$SESSION_ID/classes" "$HOST_TOKEN" | jq '.data'

cat <<EOF

== Done ==
Tenant:       $TENANT_ID
Org:          $ORG_ID
Host:         $HOST_EMAIL / $HOST_PASSWORD
Program:      $PROGRAM_ID
Class:        $CLASS_ID
Plan:         $PLAN_ID
Subscription: $SUBSCRIPTION_ID
Session:      $SESSION_ID
EOF
