#!/usr/bin/env bash
# Screening outage scenario: screening is down -> onboarding times out -> manual review -> officer approves.
# When screening comes back, its late ClientCleared must not change anything.
set -euo pipefail
cd "$(dirname "$0")/.."

ONBOARDING=${ONBOARDING_URL:-http://localhost:8081}

json() { python3 -c "import json,sys; print(json.load(sys.stdin)$1)"; }

wait_for_status() {
  local customer_id=$1 expected=$2 status=""
  for _ in $(seq 1 90); do
    status=$(curl -s "$ONBOARDING/api/onboardings/$customer_id" | json "['status']")
    [[ "$status" == "$expected" ]] && return 0
    sleep 1
  done
  echo "FAIL: $customer_id expected $expected, got $status" >&2
  return 1
}

echo "1) Screening outage: stopping screening-service"
docker compose stop screening-service >/dev/null

echo "2) Onboarding Tomasz Nowicki"
customer=$(curl -s -X POST "$ONBOARDING/api/onboardings" -H 'Content-Type: application/json' \
  -d '{"firstName":"Tomasz","lastName":"Nowicki","email":"tomasz@example.com","dateOfBirth":"1985-03-12"}')
echo "   $customer"
customer_id=$(echo "$customer" | json "['customerId']")

echo "3) Waiting for the screening deadline to pass -> MANUAL_REVIEW"
wait_for_status "$customer_id" MANUAL_REVIEW
echo "   queue: $(curl -s "$ONBOARDING/api/manual-reviews")"

echo "4) Compliance officer approves"
curl -s -X POST "$ONBOARDING/api/manual-reviews/$customer_id/approve" -H 'Content-Type: application/json' \
  -d '{"reviewer":"officer.anna","comment":"Identity confirmed by phone"}'; echo
wait_for_status "$customer_id" ONBOARDED

echo "5) Screening comes back and processes the backlog (late ClientCleared)"
docker compose start screening-service >/dev/null
for _ in $(seq 1 60); do curl -sf localhost:8082/actuator/health >/dev/null && break; sleep 2; done
for _ in $(seq 1 30); do
  [[ "$(curl -s "localhost:8082/api/screenings/$customer_id" | json ".get('status')" 2>/dev/null)" == "CLEARED" ]] && break
  sleep 1
done
echo "   screening: $(curl -s "localhost:8082/api/screenings/$customer_id")"
sleep 3

final=$(curl -s "$ONBOARDING/api/onboardings/$customer_id")
echo "   onboarding: $final"
[[ "$(echo "$final" | json "['status']")" == "ONBOARDED" ]] || { echo "FAIL: status changed" >&2; exit 1; }
[[ "$(echo "$final" | json "['version']")" == "5" ]] || { echo "FAIL: late verdict produced extra events" >&2; exit 1; }
echo
echo "E2E SCREENING TIMEOUT OK"
