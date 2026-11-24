#!/usr/bin/env bash
# tour.sh: the whole story in one run, narrated in plain words.
# Drives the same stages as ./demo stage, sends traffic, and says after each step
# who served customers and what the checks found. The detail goes to .run/tour.log.
set -euo pipefail

ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_URL="${APP_URL:-http://localhost:8080}"
DEMO="$ENV_DIR/demo"
RUN_DIR="$ENV_DIR/.run"
LOG="$RUN_DIR/tour.log"
PAUSE=true

usage() {
  cat <<EOF
Usage: ./demo tour [--no-pause]

Runs the whole story, stage 0 to stage 4, in about five minutes (longer the first time,
while Docker builds the images). After each stage it says who served the customers and
what the checks found. It pauses between stages; --no-pause runs straight through.
The full output of each step goes to .run/tour.log.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --no-pause) PAUSE=false; shift ;;
    -h|--help)  usage; exit 0 ;;
    *) usage >&2; exit 1 ;;
  esac
done
[[ -t 0 && -t 1 ]] || PAUSE=false

bold()  { printf '\033[1m%s\033[0m\n' "$*"; }
dim()   { printf '\033[2m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }
para()  { printf '%s\n' "$*" | fold -s -w 90; }
note()  { printf "%s\n" "$*" | fold -s -w 86 | sed "s/^/  /"; }

pause() {
  if [[ "$PAUSE" == true ]]; then
    printf '\n'
    read -r -p "Press Enter for the next stage (Ctrl-C to stop) " _
  fi
}

# Runs a demo command with its output in the log. On failure, shows the end of the log and stops.
run() {
  printf '\n$ ./demo %s\n' "$*" >> "$LOG"
  if ! "$DEMO" "$@" >> "$LOG" 2>&1; then
    red "  ./demo $* failed. The end of $LOG:"
    tail -15 "$LOG" | sed 's/^/    /'
    exit 1
  fi
}

# "pkg:amorphic/pricing-calc@2.1?lang=java" -> "Java 2.1". The jq filter, for use inside other filters.
SHORT='def short: capture("@(?<v>[^?]+)\\?lang=(?<l>.*)") as $m
  | (({"python":"Python","java":"Java"} | .[$m.l]) // $m.l) + " " + $m.v;'

traffic() {
  local n="$1"
  printf '  Sending %s customer quotes ' "$n"
  printf '\n$ ./demo traffic --requests %s\n' "$n" >> "$LOG"
  "$ENV_DIR/scripts/traffic.sh" --requests "$n" >> "$LOG" 2>&1 \
    || { echo; red "  Some quotes failed. See $LOG"; exit 1; }
  echo "... done"
}

# Who answered the customers, as shares of all calls in this stage.
served() {
  curl -fsS "$APP_URL/evidence" | jq -r "$SHORT"'
    (.served | map(.total) | add // 0) as $all
    | .served | sort_by(-.total)
    | map("\(.address | short) \(if .binding == "local" then "(inside the app) " else "" end)served \((.total * 100 / $all + 0.5) | floor)% (\(.total))")
    | "  Customers were answered by: " + join(", ")'
}

# What the checks found, in words. Prints one line per compared pair.
checks() {
  curl -fsS "$APP_URL/evidence" | jq -r "$SHORT"'
    .comparisons[]
    | if .mismatches == 0
      then "  Checks: \(.total) answers from \(.served | short) were compared with \(.shadow | short). All of them matched."
      else "  Checks: \(.total) answers from \(.served | short) were compared with \(.shadow | short). \(.mismatches) disagreed."
      end'
}

# Pass or fail, as the gate decides, with the reason in words.
gate() {
  local min="$1"
  if "$ENV_DIR/scripts/gate.sh" "$min" >> "$LOG" 2>&1; then
    green "  Verdict: safe to take the next step (at least $min matches in a row, none wrong)."
    return 0
  fi
  red "  Verdict: not safe. The new version stays out of customers' way."
  return 1
}

# Values the sample account through the app. Prints the response.
report() {
  curl -fsS "$APP_URL/accounts/A-1001/value?currency=USD" -H "X-Request-Id: $1"
}

# What the account would have been worth on the shadow's answers: never sent back, so read from the evidence.
# The comparisons finish in the background, so it waits a moment for them.
shadow_total() {
  local v="" i
  for i in 1 2 3 4 5 6 7 8 9 10; do
    v="$(curl -fsS "$APP_URL/evidence" | jq -r --arg id "$1" '.outcomes[]
      | select(.outcome | startswith("pkg:outcome/account-value")) | .lastMismatch
      | select(. != null and .requestId == $id) | .shadow.amount')"
    [[ -n "$v" ]] && break
    sleep 0.3
  done
  echo "${v:-unknown}"
}

# Asks a pricing service directly, bypassing the app.
direct() {
  curl -fsS "$1/convert?amount=$2&from=GBP&to=USD" | jq -r .amount
}

SUMMARY=()
row() { SUMMARY+=("$(printf '  %-10s %-38s %s' "$1" "$2" "$3")"); }

mkdir -p "$RUN_DIR"
: > "$LOG"

clear 2>/dev/null || true
bold "The amorphic demo: a narrated tour"
echo
para "An app quotes prices, and it can say what a customer's account is worth. For each, the app asks for what it wants, a quote or an account's value, and the platform works out the steps. Those steps ask a pricing capability to convert amounts. The app's code is the same in every stage below. What changes is where those conversions go."
echo
para "The pricing starts out in a trusted Python service. A Java rewrite arrives, and we want to move the work to it, and eventually inside the app itself, without customers ever getting a wrong price. So the new version earns trust one step at a time, and at each step the evidence says whether it's safe to go on."
echo
dim "The first run builds the Docker images and takes a few minutes longer. The full output is in .run/tour.log."
pause

# --- Stage 0 -------------------------------------------------------------------------------
echo; bold "Stage 0: the starting point"
para "Python does all the pricing. This is what everything else is measured against."
run stage 0
traffic 200
served
row "0" "Python 1.0 serves everyone" "the baseline"
pause

# --- Stage 1 -------------------------------------------------------------------------------
echo; bold "Stage 1: shadow. Java 2.0 is checked, but serves no one"
para "Every customer still gets Python's answer. Behind the scenes each request also goes to Java 2.0, and the two answers are compared."
run stage 1
traffic 1200
served
checks
gate 1000 || true
bad="$(curl -fsS "$APP_URL/evidence" | jq '[.comparisons[].mismatches] | add // 0')"
echo
note "Java 2.0 got $bad prices wrong. No customer saw them, because only Python's answers were sent back."
note "Here's one, asked directly: 732.98 GBP is exactly 916.225 USD, half a penny."
printf '    Python 1.0 says %s   Java 2.0 says %s\n' "$(direct http://localhost:8000 732.98)" "$(direct http://localhost:8090 732.98)"
note "Python rounds a half to the even digit; Java 2.0 always rounds it up. Java passed its own tests, because none of them hit this case. Comparing it on live traffic found it in seconds."
echo
note "The same bug reaches an account's value. The sample account holds 732.98 GBP, 1000.00 EUR and 250.00 USD. Valuing it takes three steps: load what it holds, convert each holding to USD (the cash needs no conversion), and add them up."
printf '\n$ ./demo report\n' >> "$LOG"
r="$(report tour-stage-1)"; echo "$r" >> "$LOG"
printf '    The customer is told %s USD, from Python'"'"'s conversions.\n' "$(jq -r .total <<< "$r")"
printf '    On Java 2.0'"'"'s conversions it would have been %s USD. That total was never sent.\n' "$(shadow_total tour-stage-1)"
row "1" "Python serves; Java 2.0 checked" "$bad wrong answers caught; not safe"
pause

# --- Stage 1b ------------------------------------------------------------------------------
echo; bold "Stage 1b: the fix. Java 2.1 is checked"
para "The caught case became a test, and Java 2.1 fixes the rounding. It goes back into the shadow slot and starts earning trust from zero."
run stage 1b
traffic 1200
served
checks
gate 1000
printf '    Java 2.1 now says %s for 732.98 GBP, the same as Python.\n' "$(direct http://localhost:8090 732.98)"
row "1b" "Python serves; Java 2.1 checked" "every answer matched; safe"
pause

# --- Stage 2 -------------------------------------------------------------------------------
echo; bold "Stage 2: canary. Java serves 1 customer in 10"
para "Java now answers real customers, but only a small share. Python is still asked on those calls too, so every answer Java gives is still checked."
run stage 2
traffic 1500
served
checks
gate 100
row "2" "Java 10%, Python 90%" "every Java answer matched; safe"
pause

echo; bold "Stage 2, raised: Java serves half the customers"
run stage 2 --weight 50
traffic 2500
served
checks
gate 1000
row "2 (50%)" "Java 50%, Python 50%" "every Java answer matched; safe"
pause

# --- Stage 3 -------------------------------------------------------------------------------
echo; bold "Stage 3: promote. Java serves everyone, and Python checks it"
para "The roles flip. Java answers every customer, and Python becomes the one compared in the background, to confirm nothing broke in the handover."
run stage 3
traffic 1200
served
checks
gate 1000
row "3" "Java 2.1 serves everyone" "Python still agrees; safe"
pause

# --- Stage 3b ------------------------------------------------------------------------------
echo; bold "Stage 3b: retire Python"
para "With that much matched evidence behind Java, the Python service is switched off. The pricing has changed language, and the app's code hasn't changed at all."
run stage 3b
traffic 200
served
echo "  Running now: the app and the Java service. Python is gone."
row "3b" "Java 2.1 alone" "Python switched off"
pause

# --- Stage 4 -------------------------------------------------------------------------------
echo; bold "Stage 4: fold in. The pricing moves inside the app"
para "The Java pricing was built as a plain library, so the app is rebuilt with it inside. The call that once crossed the network is now an ordinary method call, and the separate Java service disappears. (This is the one step that needs a rebuild.)"
run stage 4
traffic 200
served
q="$(curl -fsS "$APP_URL/quote?amount=732.98&from=GBP&to=USD")"
printf '    732.98 GBP is %s USD, answered by %s %s\n' "$(jq -r .amount <<< "$q")" \
  "$(jq -r "$SHORT"' .servedBy | short' <<< "$q")" \
  "$(jq -r 'if .binding == "local" then "inside the app" else "over the network" end' <<< "$q")"
printf '\n$ ./demo report\n' >> "$LOG"
r="$(report tour-stage-4)"; echo "$r" >> "$LOG"
printf '    The sample account is worth %s USD, every conversion made inside the app\n' "$(jq -r .total <<< "$r")"
echo "  Running now: only the app."
row "4" "Java 2.1 inside the app" "no pricing service left to run"

# --- Summary -------------------------------------------------------------------------------
echo; bold "What just happened"
printf '  %-10s %-38s %s\n' "Stage" "Who answered customers" "What the evidence said"
printf '%s\n' "${SUMMARY[@]}"
echo
para "The pricing moved from a Python service to Java, and then inside the app, in small steps. Java was trusted with customers only after it had matched Python on live traffic, and its bug was caught before anyone was charged the wrong amount or told the wrong value. The app's code never changed: it asks for a quote or an account's value, and the platform works out how."
echo
para "Here a script took each step. In the guide's Part 2 a governor proposes each one from the same evidence, and you approve it: docs/guide.md. When you're done: ./demo down"
