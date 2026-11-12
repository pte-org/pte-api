#!/usr/bin/env bash
# Sets or removes keys in an env file without printing any value.
#   deploy/scripts/env-set.sh .env KEY=value [KEY=value ...] [--unset KEY ...]
# A key that exists is replaced in place; a new key is appended. The file is backed up first (FILE.bak-<time>) and
# keeps its permissions, so a production .env stays mode 600. Running it again with the same arguments changes nothing.
set -euo pipefail

file="${1:?usage: env-set.sh FILE KEY=value ... [--unset KEY ...]}"
shift
[ -f "$file" ] || { echo "no such file: $file" >&2; exit 1; }

backup="$file.bak-$(date +%Y%m%d-%H%M%S)"
cp -p "$file" "$backup"
work="$(mktemp)"
trap 'rm -f "$work"' EXIT
cp -p "$file" "$work"

mode=set
for argument in "$@"; do
  if [ "$argument" = "--unset" ]; then
    mode=unset
    continue
  fi
  if [ "$mode" = unset ]; then
    key="$argument"
    grep -v -E "^${key}=" "$work" > "$work.next" || true
    mv "$work.next" "$work"
    echo "unset $key"
    continue
  fi
  key="${argument%%=*}"
  value="${argument#*=}"
  [[ "$key" =~ ^[A-Z_][A-Z0-9_]*$ ]] || { echo "bad key: $key" >&2; exit 1; }
  if grep -q -E "^${key}=" "$work"; then
    KEY="$key" VALUE="$value" awk 'BEGIN { k = ENVIRON["KEY"]; v = ENVIRON["VALUE"] }
      index($0, k "=") == 1 { print k "=" v; next } { print }' "$work" > "$work.next"
    mv "$work.next" "$work"
    echo "set $key (replaced)"
  else
    printf '%s=%s\n' "$key" "$value" >> "$work"
    echo "set $key (added)"
  fi
done

cat "$work" > "$file"   # writes in place, so the file keeps its owner and mode
echo "saved $file (backup: $backup)"
