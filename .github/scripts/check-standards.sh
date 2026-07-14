#!/bin/bash
# Coding Standards violation check — warning-only (exit 0)
# Surfaces common violations but does NOT block merge

WARN=0

echo "=== aptis-api Coding Standards Check ==="
echo ""

# Check hardcoded strings in service/controller (not in *Constants.java)
HARDCODED=$(grep -rn '"[A-Z][a-z].*"' \
  --include="*Service*.java" \
  --include="*Controller*.java" \
  --include="*ServiceImpl*.java" \
  src/ 2>/dev/null | grep -v "//.*\"" | grep -v "Constants" | head -20)

if [ -n "$HARDCODED" ]; then
  echo "[WARN] Hardcoded strings found in service/controller (should be in *Constants.java):"
  echo "$HARDCODED"
  echo ""
  WARN=1
fi

# Check @Transactional on non-service files
TX_OUTSIDE=$(grep -rn "@Transactional" \
  --include="*Controller*.java" \
  --include="*Repository*.java" \
  src/ 2>/dev/null | head -10)

if [ -n "$TX_OUTSIDE" ]; then
  echo "[WARN] @Transactional found outside Service layer:"
  echo "$TX_OUTSIDE"
  echo ""
  WARN=1
fi

# Check files over 300 lines (excluding generated code patterns)
LARGE_FILES=$(find src/ -name "*.java" 2>/dev/null | while read f; do
  lines=$(wc -l < "$f")
  if [ "$lines" -gt 300 ]; then
    # Skip Lombok/annotation-generated patterns
    echo "$lines $f"
  fi
done | sort -rn | head -10)

if [ -n "$LARGE_FILES" ]; then
  echo "[WARN] Files exceeding 300 lines (verify not Lombok/codegen exempt):"
  echo "$LARGE_FILES"
  echo ""
  WARN=1
fi

# Check potential secret hardcoding (keywords near quotes)
SECRETS=$(grep -rn -i "password\s*=\s*\".\|secret\s*=\s*\".\|api.key\s*=\s*\".\|token\s*=\s*\"." \
  --include="*.java" src/ 2>/dev/null | grep -v "//.*=" | head -10)

if [ -n "$SECRETS" ]; then
  echo "[WARN] Potential secrets hardcoded in Java source (should be env vars):"
  echo "$SECRETS"
  echo ""
  WARN=1
fi

if [ "$WARN" -eq 0 ]; then
  echo "[OK] No violations detected."
fi

echo ""
echo "=== Check complete (warning-only — merge not blocked) ==="
exit 0
