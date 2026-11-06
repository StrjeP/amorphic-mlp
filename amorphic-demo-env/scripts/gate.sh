#!/usr/bin/env bash
# gate.sh: the governor's evidence gate.
# Passes only if there is at least one comparison, and every compared address pair
# has >= N consecutive matches and 0 mismatches since the last reset (app restart).
set -euo pipefail

APP_URL="${APP_URL:-http://localhost:8080}"
MIN="${1:-1000}"
[[ "$MIN" =~ ^[0-9]+$ ]] || { echo "usage: ./demo gate [N]" >&2; exit 2; }

evidence="$(curl -fsS "$APP_URL/evidence")" || { echo "GATE ERROR: cannot read $APP_URL/evidence" >&2; exit 2; }

count="$(jq '.comparisons | length' <<< "$evidence")"
if [[ "$count" -eq 0 ]]; then
  echo "GATE FAIL: no comparisons recorded. Nothing is shadowed or canaried in this stage, or no traffic has been sent yet."
  exit 1
fi

echo "Comparisons since last reset (need >= $MIN consecutive matches, 0 mismatches):"
jq -r '.comparisons[] |
  "  \(.served)  vs  \(.shadow)\n    total \(.total)  matches \(.matches)  mismatches \(.mismatches)  consecutive \(.consecutiveMatches)"' \
  <<< "$evidence"

if jq -e --argjson min "$MIN" 'all(.comparisons[]; .mismatches == 0 and .consecutiveMatches >= $min)' \
     <<< "$evidence" >/dev/null; then
  echo "GATE PASS"
  exit 0
fi

echo "GATE FAIL"
jq -r '.comparisons[] | select(.lastMismatch != null) | .lastMismatch |
  "  last mismatch: \(.input.amount) \(.input.from)->\(.input.to)  served \(.servedValue)  shadow \(.shadowValue)  (request \(.requestId))"' \
  <<< "$evidence"
exit 1
