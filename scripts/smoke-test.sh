#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080/usamis}"
COOKIE_FILE="$(mktemp)"
trap 'rm -f "$COOKIE_FILE"' EXIT

request() {
  local expected="$1"; shift
  local actual
  actual="$(curl -sS -o /tmp/usamis-test-response.json -w '%{http_code}' "$@")"
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: expected HTTP $expected, got $actual for $*" >&2
    cat /tmp/usamis-test-response.json >&2
    exit 1
  fi
}

request 200 "$BASE_URL/api/health"
request 401 "$BASE_URL/api/students"
request 200 -c "$COOKIE_FILE" -H 'Content-Type: application/json' \
  -X POST "$BASE_URL/api/auth/login" \
  --data '{"username":"admin001","password":"admin123"}'

for endpoint in students courses enrollments grades fees dashboard audit; do
  request 200 -b "$COOKIE_FILE" "$BASE_URL/api/$endpoint"
done

echo "PASS: health, authentication, authorization and core API smoke tests"
