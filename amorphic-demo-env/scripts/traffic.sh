#!/usr/bin/env bash
# traffic.sh: steady /quote load with a configurable share of edge-case inputs.
set -euo pipefail

ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_URL="${APP_URL:-http://localhost:8080}"
REQUESTS=1200
EDGE_SHARE=20
PAIR="GBP:USD"
EDGE_FILE="$ENV_DIR/traffic/edge-inputs.txt"

usage() {
  cat <<EOF
Usage: ./demo traffic [--requests N] [--edge-share PCT] [--pair FROM:TO] [--edge-file PATH]

  --requests N      Number of /quote calls (default $REQUESTS; the gate needs 1000 consecutive matches).
  --edge-share PCT  Percentage of calls that use an edge-case input (default $EDGE_SHARE).
  --pair FROM:TO    Currency pair for ordinary calls (default $PAIR).
  --edge-file PATH  Edge-case inputs, one "amount from to" per line (default traffic/edge-inputs.txt).
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --requests)   REQUESTS="$2"; shift 2 ;;
    --edge-share) EDGE_SHARE="$2"; shift 2 ;;
    --pair)       PAIR="$2"; shift 2 ;;
    --edge-file)  EDGE_FILE="$2"; shift 2 ;;
    -h|--help)    usage; exit 0 ;;
    *) usage >&2; exit 1 ;;
  esac
done

# Load edge cases (skip comments and blank lines). bash 3.2 compatible: no mapfile.
EDGES=()
if [[ -f "$EDGE_FILE" ]]; then
  while IFS= read -r line; do
    case "$line" in ''|'#'*) continue ;; esac
    EDGES+=("$line")
  done < "$EDGE_FILE"
fi
if [[ ${#EDGES[@]} -eq 0 && "$EDGE_SHARE" -gt 0 ]]; then
  echo "traffic: no edge-case inputs in $EDGE_FILE, sending ordinary amounts only" >&2
  EDGE_SHARE=0
fi

FROM="${PAIR%%:*}"
TO="${PAIR##*:}"
ok=0
failed=0
edge=0
start=$(date +%s)

for i in $(seq 1 "$REQUESTS"); do
  if [[ $((RANDOM % 100)) -lt "$EDGE_SHARE" ]]; then
    read -r amount from to <<< "${EDGES[$((RANDOM % ${#EDGES[@]}))]}"
    edge=$((edge + 1))
  else
    # Random amount between 0.01 and 999.99.
    amount=$(printf '%d.%02d' $((RANDOM % 1000)) $((RANDOM % 99 + 1)))
    from="$FROM"
    to="$TO"
  fi
  if curl -fsS -o /dev/null -H "X-Request-Id: traffic-$start-$i" \
       "$APP_URL/quote?amount=$amount&from=$from&to=$to" 2>/dev/null; then
    ok=$((ok + 1))
  else
    failed=$((failed + 1))
  fi
  if [[ $((i % 100)) -eq 0 ]]; then
    printf '\r%d/%d sent (%d edge cases, %d failed)' "$i" "$REQUESTS" "$edge" "$failed"
  fi
done

printf '\r%d/%d sent (%d edge cases, %d failed) in %ss\n' \
  "$REQUESTS" "$REQUESTS" "$edge" "$failed" "$(( $(date +%s) - start ))"
[[ "$failed" -eq 0 ]]
