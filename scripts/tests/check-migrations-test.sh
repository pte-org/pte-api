#!/bin/bash
set -Eeuo pipefail

# Runs scripts/check-migrations.sh against throwaway git repositories, one per case. Nothing in this
# repository is touched.
guard="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/check-migrations.sh"
work_dir="$(mktemp -d "${TMPDIR:-/tmp}/pte-migration-guard-test.XXXXXX")"
trap 'rm -rf -- "$work_dir"' EXIT
migrations="app/src/main/resources/db/migration"
failed=0
case_number=0

# Creates a repository whose main branch has V1, V9 and V10 (no V2..V8: gaps are allowed), then a feature branch.
new_repo() {
  case_number=$((case_number + 1))
  repo="$work_dir/case-$case_number"
  mkdir -p "$repo/$migrations"
  cd "$repo"
  git init --quiet --initial-branch=main
  git config user.email guard-test@example.invalid
  git config user.name guard-test
  git config core.autocrlf false
  for name in V1__baseline V9__ninth V10__tenth; do
    echo "-- $name" > "$migrations/$name.sql"
  done
  git add . && git commit --quiet -m base
  git checkout --quiet -b feature
}

commit_all() {
  git add -A && git commit --quiet -m "$1"
}

# expect pass <label>            the guard exits 0
# expect fail <label> <message>  the guard exits 1 AND names the broken rule. The exit status alone proves
#                                nothing: a guard that crashed halfway also exits 1.
expect() {
  local wanted="$1" label="$2" message="${3:-}" status=0 verdict=bad
  bash "$guard" main > "$repo/guard.log" 2>&1 || status=$?
  if [ "$wanted" = pass ] && [ "$status" -eq 0 ]; then
    verdict=good
  elif [ "$wanted" = fail ] && [ "$status" -eq 1 ] && [ -n "$message" ] \
      && grep -qF -- "$message" "$repo/guard.log" && grep -qF -- "problem(s)" "$repo/guard.log"; then
    verdict=good
  fi
  if [ "$verdict" = good ]; then
    echo "ok   - $label"
  else
    echo "FAIL - $label (wanted $wanted${message:+ with \"$message\"}, exit $status)"
    sed 's/^/       /' "$repo/guard.log"
    failed=$((failed + 1))
  fi
}

changed="merged migration was changed"
duplicated="is used by more than one file"
too_low="must be numbered above"
misnamed="not named V<number>__<description>.sql"

new_repo
expect pass "an untouched branch passes"

new_repo
echo "-- new" > "$migrations/V11__eleventh.sql" && commit_all add
expect pass "a new migration above the highest version passes"

new_repo
echo "-- new" > "$migrations/V11__eleventh.sql"
expect pass "a new migration that is not committed yet passes too"

new_repo
echo "-- edited" >> "$migrations/V9__ninth.sql" && commit_all edit
expect fail "editing a merged migration fails" "$changed"

new_repo
echo "-- edited" >> "$migrations/V9__ninth.sql"
expect fail "editing a merged migration without committing fails" "$changed"

new_repo
git rm --quiet "$migrations/V9__ninth.sql" && commit_all delete
expect fail "deleting a merged migration fails" "$changed"

new_repo
git mv "$migrations/V9__ninth.sql" "$migrations/V12__ninth.sql" && commit_all renumber
expect fail "renumbering a merged migration fails" "$changed"

new_repo
echo "-- clash" > "$migrations/V10__other_tenth.sql" && commit_all duplicate
expect fail "a version already used on the base fails" "$duplicated"

new_repo
echo "-- low" > "$migrations/V5__fifth.sql" && commit_all low
expect fail "a new version below the highest fails even when the number is free" "$too_low"

new_repo
echo "-- two" > "$migrations/V2__second.sql" && commit_all numeric
expect fail "versions compare as numbers: V2 is below V10" "$too_low"

new_repo
echo "-- bad" > "$migrations/V11_eleventh.sql" && commit_all badname
expect fail "a file not named V<number>__<description>.sql fails" "$misnamed"

# Two branches add the same version; the other one is merged first.
new_repo
echo "-- mine" > "$migrations/V11__mine.sql" && commit_all mine
git checkout --quiet main
echo "-- theirs" > "$migrations/V11__theirs.sql" && commit_all theirs
git checkout --quiet feature
expect fail "a version merged to the base meanwhile by another branch fails" "$duplicated"

if [ "$failed" -gt 0 ]; then
  echo "$failed case(s) failed"
  exit 1
fi
echo "all $case_number cases passed"
