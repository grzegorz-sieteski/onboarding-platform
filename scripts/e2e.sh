#!/usr/bin/env bash
# End-to-end scenario against a running platform (docker compose up).
set -euo pipefail

ONBOARDING=${ONBOARDING_URL:-http://localhost:8081}
SCREENING=${SCREENING_URL:-http://localhost:8082}
RESTRICTION=${RESTRICTION_URL:-http://localhost:8083}

json() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

wait_for_status() {
  local customer_id=$1 expected=$2
  for _ in $(seq 1 60); do
    status=$(curl -s "$ONBOARDING/api/onboardings/$customer_id" | json "['status']")
    [[ "$status" == "$expected" ]] && return 0
    sleep 0.5
  done
  echo "FAIL: $customer_id expected $expected, got $status" >&2
  return 1
}

onboard() {
  curl -s -X POST "$ONBOARDING/api/onboardings" -H 'Content-Type: application/json' \
    -d "{\"firstName\":\"$1\",\"lastName\":\"$2\",\"email\":\"$1@example.com\",\"dateOfBirth\":\"1990-01-01\"}" \
    | json "['customerId']"
}

echo "1) Compliance officer blacklists surname 'Michałowski'"
curl -s -X POST "$SCREENING/api/blacklist" -H 'Content-Type: application/json' \
  -d '{"lastName":"Michałowski","reason":"Sanctions list EU-2026/17"}'; echo

echo "2) Onboard Anna Kowalska (clean) and Adam MICHALOWSKI (blacklisted, different case, no diacritics)"
clean=$(onboard Anna Kowalska)
blacklisted=$(onboard Adam MICHALOWSKI)
echo "   clean=$clean blacklisted=$blacklisted"
echo "   right after submit: clean=$(curl -s "$ONBOARDING/api/onboardings/$clean" | json "['status']")  (expected PENDING_SCREENING until screening clears)"

echo "3) Waiting for the choreography:"
echo "   clean:       onboarding -> screening (ClientCleared) -> onboarding = ONBOARDED"
echo "   blacklisted: onboarding -> screening (ClientRestricted) -> restriction (ClientBanned) -> onboarding = BANNED"
wait_for_status "$blacklisted" BANNED
sleep 1
wait_for_status "$clean" ONBOARDED

echo
echo "Onboarding  (blacklisted): $(curl -s "$ONBOARDING/api/onboardings/$blacklisted")"
echo "Screening   (blacklisted): $(curl -s "$SCREENING/api/screenings/$blacklisted")"
echo "Restriction (blacklisted): $(curl -s "$RESTRICTION/api/restrictions/$blacklisted")"
echo "Onboarding  (clean):       $(curl -s "$ONBOARDING/api/onboardings/$clean")"
echo "Screening   (clean):       $(curl -s "$SCREENING/api/screenings/$clean")"
echo
echo "E2E OK"
