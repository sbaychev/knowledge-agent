#!/usr/bin/env bash
set -euo pipefail

# End-to-end MCP Streamable HTTP contract check.
# The application must be running with the mcp-server profile and a migrated DB.

MCP_URL="${MCP_BASE_URL:-http://127.0.0.1:8080/mcp}"
CONNECT_TIMEOUT_SECONDS="${CONNECT_TIMEOUT_SECONDS:-5}"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-15}"
PROTOCOL_VERSION="${MCP_PROTOCOL_VERSION:-2025-06-18}"
TEMP_DIR="$(mktemp -d)"
SESSION_ID=""
NEGOTIATED_VERSION=""
RPC_STATUS=""
RPC_BODY=""

cleanup() {
  rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

fail() {
  printf 'FAIL  %s\n' "$*" >&2
  if [[ -n "$RPC_BODY" ]]; then
    if printf '%s' "$RPC_BODY" | jq '.' >/dev/null 2>&1; then
      printf '%s\n' "$RPC_BODY" | jq '.' | { command -v glow >/dev/null 2>&1 && glow - || cat; } >&2
    else
      printf '%s\n' "$RPC_BODY" >&2
    fi
  fi
  exit 1
}

pass() {
  printf 'PASS  %s\n' "$*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' is not installed."
}

require_command curl
require_command jq
require_command awk

# Store response and headers in temporary files so the HTTP status does not
# corrupt the response body and MCP-Session-Id can be read from the headers.
send_payload() {
  local payload="$1"
  local response_file="$TEMP_DIR/response"
  local headers_file="$TEMP_DIR/headers"
  local -a headers

  headers=(
    -H 'Content-Type: application/json'
    -H 'Accept: application/json, text/event-stream'
  )

  if [[ -n "$SESSION_ID" ]]; then
    headers+=( -H "Mcp-Session-Id: $SESSION_ID" )
  fi

  if [[ -n "$NEGOTIATED_VERSION" ]]; then
    headers+=( -H "MCP-Protocol-Version: $NEGOTIATED_VERSION" )
  fi

  RPC_STATUS="$(curl -sS \
    --connect-timeout "$CONNECT_TIMEOUT_SECONDS" \
    --max-time "$REQUEST_TIMEOUT_SECONDS" \
    -D "$headers_file" \
    -o "$response_file" \
    -w '%{http_code}' \
    "${MCP_URL%/}" \
    "${headers[@]}" \
    -X POST \
    --data "$payload")" || fail "Cannot reach MCP endpoint $MCP_URL"

  RPC_BODY="$(cat "$response_file")"

  if [[ -z "$SESSION_ID" ]]; then
    SESSION_ID="$(awk 'tolower($1) == "mcp-session-id:" { gsub("\\r", "", $2); print $2; exit }' "$headers_file")"
  fi
}

rpc() {
  local method="$1"
  local request_id="$2"
  local params_json="${3:-{}}"
  local payload

  if [[ -n "$request_id" ]]; then
    payload="$(jq -cn \
      --arg method "$method" \
      --argjson id "$request_id" \
      --argjson params "$params_json" \
      '{jsonrpc:"2.0", id:$id, method:$method, params:$params}')"
  else
    # MCP notifications intentionally have no JSON-RPC id and no response body.
    payload="$(jq -cn --arg method "$method" '{jsonrpc:"2.0", method:$method}')"
  fi

  send_payload "$payload"
}

response_json() {
  local body="$1"
  local event_data

  if printf '%s' "$body" | jq -e 'type == "object"' >/dev/null 2>&1; then
    printf '%s\n' "$body"
    return 0
  fi

  # Streamable HTTP may represent a JSON-RPC message as an SSE data event.
  event_data="$(printf '%s\n' "$body" | sed -n 's/^data:[[:space:]]*//p' | head -n 1)"
  if [[ -n "$event_data" ]] && printf '%s' "$event_data" | jq -e 'type == "object"' >/dev/null 2>&1; then
    printf '%s\n' "$event_data"
    return 0
  fi

  return 1
}

assert_status() {
  local expected="$1"
  local label="$2"
  [[ "$RPC_STATUS" == "$expected" ]] || fail "$label expected HTTP $expected, got HTTP $RPC_STATUS."
}

assert_rpc_result() {
  local label="$1"
  local json
  json="$(response_json "$RPC_BODY")" || fail "$label returned a non-JSON-RPC response."
  printf '%s' "$json" | jq -e 'has("error") | not' >/dev/null || fail "$label returned a JSON-RPC error."
  printf '%s' "$json" | jq -e 'has("result")' >/dev/null || fail "$label did not include result."
  printf '%s\n' "$json"
}

printf 'MCP Streamable HTTP smoke test: %s\n\n' "$MCP_URL"

# 1. MCP initialize handshake.
INITIALIZE_PARAMS="$(jq -cn \
  --arg protocolVersion "$PROTOCOL_VERSION" \
  '{protocolVersion:$protocolVersion, capabilities:{}, clientInfo:{name:"knowledge-agent-smoke-test", version:"1.0.0"}}')"
rpc initialize 1 "$INITIALIZE_PARAMS"
assert_status 200 'initialize'
INITIALIZE_JSON="$(assert_rpc_result 'initialize')"
NEGOTIATED_VERSION="$(printf '%s' "$INITIALIZE_JSON" | jq -er '.result.protocolVersion | strings | select(length > 0)')" \
  || fail 'initialize did not negotiate a protocol version.'
pass "initialize negotiated protocol $NEGOTIATED_VERSION"

# 2. Finish the handshake. Notifications have no JSON-RPC id.
rpc notifications/initialized '' '{}'
[[ "$RPC_STATUS" == 200 || "$RPC_STATUS" == 202 || "$RPC_STATUS" == 204 ]] \
  || fail "notifications/initialized expected HTTP 200, 202 or 204, got $RPC_STATUS."
pass 'notifications/initialized accepted'

# 3. Discover the published tools and check the names the agent contract relies on.
rpc tools/list 2 '{}'
assert_status 200 'tools/list'
TOOLS_JSON="$(assert_rpc_result 'tools/list')"
for expected_tool in \
  lookup_order \
  lookup_shipment_by_order \
  lookup_payment_by_order \
  lookup_refunds_by_order; do
  printf '%s' "$TOOLS_JSON" | jq -e --arg name "$expected_tool" \
    '.result.tools | any(.name == $name)' >/dev/null \
    || fail "tools/list did not publish '$expected_tool'."
done
pass 'tools/list publishes all four read-only transactional tools'

# 4. Execute a real tool call. Order 1 is the repository's deterministic seed order.
rpc tools/call 3 "$(jq -cn '{name:"lookup_order", arguments:{orderId:1}}')"
assert_status 200 'tools/call lookup_order'
ORDER_JSON="$(assert_rpc_result 'tools/call lookup_order')"
printf '%s' "$ORDER_JSON" | jq -e '.result.isError != true' >/dev/null \
  || fail 'lookup_order returned isError=true.'
ORDER_TEXT="$(printf '%s' "$ORDER_JSON" | jq -er '.result.content[]? | select(.type == "text") | .text' | head -n 1)"
printf '%s' "$ORDER_TEXT" | grep -Eiq 'Order ID:[[:space:]]*1' \
  || fail 'lookup_order response did not identify seeded order 1.'
pass 'tools/call executes lookup_order over HTTP and returns order 1'

# 5. Discover the static resource catalog.
rpc resources/list 4 '{}'
assert_status 200 'resources/list'
RESOURCES_JSON="$(assert_rpc_result 'resources/list')"
printf '%s' "$RESOURCES_JSON" | jq -e \
  '.result.resources | any(.uri == "knowledge://policies")' >/dev/null \
  || fail 'resources/list did not expose knowledge://policies.'
pass 'resources/list publishes the policy catalog resource'

# 6. Discover and read a real policy document through the resource URI template.
rpc resources/templates/list 5 '{}'
assert_status 200 'resources/templates/list'
TEMPLATES_JSON="$(assert_rpc_result 'resources/templates/list')"
printf '%s' "$TEMPLATES_JSON" | jq -e \
  '.result.resourceTemplates | any(.uriTemplate == "knowledge://policy/{policyId}")' >/dev/null \
  || fail 'resources/templates/list did not expose the policy URI template.'
pass 'resources/templates/list publishes the allow-listed policy template'

rpc resources/read 6 "$(jq -cn '{uri:"knowledge://policy/refund-policy"}')"
assert_status 200 'resources/read refund-policy'
POLICY_JSON="$(assert_rpc_result 'resources/read refund-policy')"
POLICY_TEXT="$(printf '%s' "$POLICY_JSON" | jq -er '.result.contents[]? | select(.uri == "knowledge://policy/refund-policy") | .text' | head -n 1)"
printf '%s' "$POLICY_TEXT" | grep -Fq '# Refund Policy' || fail 'Refund Policy resource content is missing its heading.'
printf '%s' "$POLICY_TEXT" | grep -Eiq '30 calendar days' || fail 'Refund Policy resource content did not include the documented return window.'
pass 'resources/read serves the Refund Policy Markdown from the classpath'

# 7. Discover and render the reusable investigation prompt.
rpc prompts/list 7 '{}'
assert_status 200 'prompts/list'
PROMPTS_JSON="$(assert_rpc_result 'prompts/list')"
printf '%s' "$PROMPTS_JSON" | jq -e \
  '.result.prompts | any(.name == "investigate-order")' >/dev/null \
  || fail 'prompts/list did not expose investigate-order.'
pass 'prompts/list publishes investigate-order'

rpc prompts/get 8 "$(jq -cn '{name:"investigate-order", arguments:{orderId:"1"}}')"
assert_status 200 'prompts/get investigate-order'
PROMPT_JSON="$(assert_rpc_result 'prompts/get investigate-order')"
PROMPT_TEXT="$(printf '%s' "$PROMPT_JSON" | jq -er '.result.messages[]? | .content.text' | head -n 1)"
printf '%s' "$PROMPT_TEXT" | grep -Fq 'Investigate order 1' || fail 'investigate-order prompt did not resolve order ID.'
printf '%s' "$PROMPT_TEXT" | grep -Fq 'lookup_order' || fail 'investigate-order prompt omitted lookup instructions.'
printf '%s' "$PROMPT_TEXT" | grep -Fq 'Do not create, cancel, dispatch, deliver, charge, or refund' \
  || fail 'investigate-order prompt omitted its read-only constraints.'
pass 'prompts/get renders a parameterized read-only investigation procedure'

printf '\n## MCP smoke-test result\n\n' | { command -v glow >/dev/null 2>&1 && glow - || cat; }
printf 'All MCP Streamable HTTP checks passed.\n'
