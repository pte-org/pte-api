#!/bin/bash
set -Eeuo pipefail

# Flyway migrations are immutable once merged: a database that already ran V16 refuses to start when V16 later
# means something else. This guard compares the working tree with a base branch and fails when
#   - a migration that exists on the base was edited, deleted or renamed,
#   - two files share one version (also across the base and this branch),
#   - a new file's version is not above the highest version on the base,
#   - a file name is not V<number>__<description>.sql.
# Gaps in the numbering are fine. Usage: scripts/check-migrations.sh [base-ref]   (default: origin/main)

base_ref="${1:-origin/main}"
migration_dir="${MIGRATION_DIR:-app/src/main/resources/db/migration}"
name_pattern='^V[0-9]+__[A-Za-z0-9_]+\.sql$'
failures=0

fail() {
  echo "migration-guard: $*" >&2
  failures=$((failures + 1))
}

version_of() {
  local number="${1#V}"
  number="${number%%__*}"
  echo $((10#$number))
}

if ! git rev-parse --verify --quiet "${base_ref}^{commit}" > /dev/null; then
  echo "migration-guard: base ref '${base_ref}' not found (fetch it first)" >&2
  exit 2
fi
if [ ! -d "$migration_dir" ]; then
  echo "migration-guard: directory '${migration_dir}' not found (run from the repository root)" >&2
  exit 2
fi

merge_base="$(git merge-base "$base_ref" HEAD)"

# 1) Nothing that was already on the base when this branch started may change.
while IFS=$'\t' read -r status path; do
  [ -n "$status" ] || continue
  [ "$status" = "A" ] || fail "merged migration was changed (${status}): ${path}"
done < <(git diff --name-status --no-renames "$merge_base" -- "$migration_dir")

base_names="$(git ls-tree -r --name-only "$base_ref" -- "$migration_dir" | sed 's|.*/||' | sort)"
head_names="$(for file in "$migration_dir"/*.sql; do
  if [ -e "$file" ]; then basename "$file"; fi
done | sort)"

# 2) Every file in the working tree is named V<number>__<description>.sql.
while IFS= read -r name; do
  [ -n "$name" ] || continue
  [[ "$name" =~ $name_pattern ]] || fail "not named V<number>__<description>.sql: ${name}"
done <<< "$head_names"

valid_names() {
  grep -E "$name_pattern" || true
}

# 3) One version, one file: checked over the base tip and the working tree together, so a version that another
#    branch merged meanwhile is caught here and not at deploy time.
all_valid="$(printf '%s\n%s\n' "$base_names" "$head_names" | sort -u | valid_names)"
# The loops use `if`, never `test && command`: under `set -e` a false test as the last command of a loop inside
# $(...) would end the script without a message.
versions="$(while IFS= read -r name; do
  if [ -n "$name" ]; then version_of "$name"; fi
done <<< "$all_valid")"
while IFS= read -r duplicate; do
  [ -n "$duplicate" ] || continue
  files="$(while IFS= read -r name; do
    if [ -n "$name" ] && [ "$(version_of "$name")" = "$duplicate" ]; then printf '%s ' "$name"; fi
  done <<< "$all_valid")"
  fail "version ${duplicate} is used by more than one file: ${files}"
done < <(sort -n <<< "$versions" | uniq -d)

# 4) A new migration goes on top of everything the base already has.
max_base=0
while IFS= read -r name; do
  [ -n "$name" ] || continue
  version="$(version_of "$name")"
  if [ "$version" -gt "$max_base" ]; then max_base="$version"; fi
done < <(valid_names <<< "$base_names")

while IFS= read -r name; do
  [ -n "$name" ] || continue
  version="$(version_of "$name")"
  [ "$version" -gt "$max_base" ] \
    || fail "new migration ${name} must be numbered above V${max_base}, the highest version on ${base_ref}"
done < <(comm -13 <(printf '%s\n' "$base_names") <(printf '%s\n' "$head_names") | valid_names)

if [ "$failures" -gt 0 ]; then
  echo "migration-guard: ${failures} problem(s). Merged migrations are never edited or renumbered;" \
       "add a new file with the next free version instead." >&2
  exit 1
fi
echo "migration-guard: ok (highest version on ${base_ref}: V${max_base})"
