#!/usr/bin/env bash
set -euo pipefail

# Local preflight checks. By default this checks toolchain availability only.
# --running checks the HTTP health endpoint.
# --database additionally requires Docker Compose PostgreSQL to be healthy.
# --mcp checks the MCP endpoint with a lightweight HTTP reachability probe.

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
MCP_BASE_URL="${MCP_BASE_URL:-http://127.0.0.1:8080/mcp}"
CHECK_RUNNING=false
CHECK_DATABASE=false
CHECK_MCP=false

usage() {
  cat <<'EOF'
Usage: bash scripts/check-local.sh [options]

Options:
  --running    Require the application health endpoint to be reachable.
  --database   Require Docker, Compose, and a healthy local PostgreSQL service.
  --mcp        Require the configured MCP endpoint to respond to HTTP.
  --help       Show this help.

Examples:
  bash scripts/check-local.sh
  bash scripts/check-local.sh --database
  bash scripts/check-local.sh --running --database
  bash scripts/check-local.sh --running --mcp
EOF
}

fail() {
  printf 'FAIL  %s\n' "$*" >&2
  exit 1
}

pass() {
  printf 'PASS  %s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' is missing."
  pass "command available: $1"
}

for arg in "$@"; do
  case "$arg" in
    --running) CHECK_RUNNING=true ;;
    --database) CHECK_DATABASE=true ;;
    --mcp) CHECK_MCP=true ;;
    --help|-h) usage; exit 0 ;;
    *) usage >&2; fail "Unknown option: $arg" ;;
  esac
done

printf '# Knowledge Agent local preflight\n\n' | { command -v glow >/dev/null 2>&1 && glow - || cat; }

require_command java
require_command mvn
require_command curl

JAVA_VERSION_OUTPUT="$(java -version 2>&1 | head -n 1)"
JAVA_MAJOR="$(printf '%s' "$JAVA_VERSION_OUTPUT" | sed -E 's/.*version "([0-9]+).*/\1/')"
[[ "$JAVA_MAJOR" =~ ^[0-9]+$ ]] || fail "Could not parse Java version from: $JAVA_VERSION_OUTPUT"
(( JAVA_MAJOR >= 25 )) || fail "Java 25 or newer is required; found $JAVA_VERSION_OUTPUT"
pass "Java version: $JAVA_VERSION_OUTPUT"

MAVEN_VERSION_OUTPUT="$(mvn -version 2>&1 | head -n 1)"
pass "Maven: $MAVEN_VERSION_OUTPUT"

if [[ "$CHECK_DATABASE" == true ]]; then
  require_command docker
  docker compose version >/dev/null 2>&1 || fail 'Docker Compose v2 is required.'
  docker info >/dev/null 2>&1 || fail 'Docker daemon is not available. Start Docker Desktop/Engine.'
  docker compose ps --status running postgres >/dev/null 2>&1 \
    || fail 'PostgreSQL is not running. Start it with: docker compose up -d postgres'
  docker compose exec -T postgres pg_isready -U knowledge -d knowledge_agent >/dev/null \
    || fail 'PostgreSQL has not become ready.'
  pass 'PostgreSQL container is running and accepts readiness checks'
fi

if [[ "$CHECK_RUNNING" == true ]]; then
  require_command jq
  HEALTH_FILE="$(mktemp)"
  trap 'rm -f "$HEALTH_FILE"' EXIT
  HTTP_STATUS="$(curl -sS --connect-timeout 3 --max-time 5 -o "$HEALTH_FILE" -w '%{http_code}' "${BASE_URL%/}/actuator/health")" \
    || fail "Cannot reach ${BASE_URL%/}/actuator/health"
  [[ "$HTTP_STATUS" == 200 ]] || fail "Health endpoint returned HTTP $HTTP_STATUS."
  jq -e '.status == "UP"' "$HEALTH_FILE" >/dev/null || fail 'Health endpoint did not report status UP.'
  pass 'application /actuator/health reports UP'
fi

if [[ "$CHECK_MCP" == true ]]; then
  # This is an endpoint reachability probe, not a protocol conformance test.
  # For full initialization/tool/resource/prompt checks, run smoke-test-mcp.sh.
  HTTP_STATUS="$(curl -sS --connect-timeout 3 --max-time 5 -o /dev/null -w '%{http_code}' \
    -H 'Accept: application/json, text/event-stream' "${MCP_BASE_URL%/}")" \
    || fail "Cannot reach MCP endpoint $MCP_BASE_URL"
  [[ "$HTTP_STATUS" =~ ^[234][0-9][0-9]$ ]] \
    || fail "MCP endpoint reachability probe got HTTP $HTTP_STATUS."
  pass "MCP endpoint is reachable (HTTP $HTTP_STATUS; use smoke-test-mcp.sh for protocol checks)"
fi

printf '\nPreflight passed.\n'
