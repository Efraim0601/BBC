#!/usr/bin/env bash
# Simulates the on-site fingerprint agent: posts check-ins to the cloud API.
# Open the "Présence" page in the app and watch rows appear live.
#
# Usage: ./tools/simulate-device.sh [BASE_URL] [DEVICE_ID] [DEVICE_KEY]
set -euo pipefail

BASE="${1:-http://localhost:8080}"
# Device id is printed by: SELECT id FROM device;  — pass it as $2.
DEVICE_ID="${2:?Pass the device UUID (SELECT id FROM device) as arg 2}"
DEVICE_KEY="${3:-dev-key-bbc-portal-a}"

# Some demo matricules (create students first via the UI, then use their matricules).
MATRICULES=("BBC-1001" "BBC-1002" "BBC-1003" "BBC-1004" "BBC-1005")

for m in "${MATRICULES[@]}"; do
  TIME=$(printf "07:%02d" $((RANDOM % 59)))
  echo "→ check-in $m at $TIME"
  curl -s -X POST "$BASE/api/devices/$DEVICE_ID/attendance" \
    -H "Content-Type: application/json" \
    -H "X-Device-Key: $DEVICE_KEY" \
    -d "{\"matricule\":\"$m\",\"time\":\"$TIME\",\"dedupKey\":\"$m-$(date +%s)-$RANDOM\"}" \
    && echo
  sleep 1
done
