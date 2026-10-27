#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
CHAT_ENDPOINT="${BASE_URL%/}/api/v1/agent/chat"
REQUEST_TIMEOUT_SECONDS="${REQUEST_TIMEOUT_SECONDS:-90}"
CORRELATION_PREFIX="agent-eval-$(date +%s)"
RESULTS_FILE="$(mktemp)"

cleanup() {
  rm -f "$RESULTS_FILE"
}
trap cleanup EXIT

FAILURES=0

post_chat() {
  local message="$1"
  local conversation_id="${2:-}"
  local correlation_id="$3"
  local payload response_file http_status

  if [[ -n "$conversation_id" ]]; then
    payload="$(jq -n \
      --arg message "$message" \
      --arg conversationId "$conversation_id" \
      '{message: $message, conversationId: $conversationId}')"
  else
    payload="$(jq -n \
      --arg message "$message" \
      '{message: $message}')"
  fi

  response_file="$(mktemp)"
  http_status="$(curl -sS \
    --connect-timeout 5 \
    --max-time "$REQUEST_TIMEOUT_SECONDS" \
    -o "$response_file" \
    -w '%{http_code}' \
    -X POST "$CHAT_ENDPOINT" \
    -H 'Content-Type: application/json' \
    -H "X-Correlation-ID: $correlation_id" \
    -d "$payload")"

  if [[ "$http_status" -lt 200 || "$http_status" -ge 300 ]]; then
    printf 'Request failed: HTTP %s (%s)\n' "$http_status" "$correlation_id" >&2
    if jq '.' "$response_file" >/dev/null 2>&1; then
      jq '.' "$response_file" | glow - >&2
    else
      cat "$response_file" >&2
    fi
    rm -f "$response_file"
    return 1
  fi

  if ! jq -e '.answer | strings' "$response_file" >/dev/null; then
    printf 'Response did not contain a string .answer field (%s).\n' "$correlation_id" >&2
    jq '.' "$response_file" | glow - >&2
    rm -f "$response_file"
    return 1
  fi

  cat "$response_file"
  rm -f "$response_file"
}

show_answer() {
  local heading="$1"
  local response="$2"

  printf '# %s\n\n' "$heading" | glow -
  printf '%s\n' "$response" | jq -r '.answer' | glow -
  printf '\n'
}

assert_contains() {
  local check_name="$1"
  local answer="$2"
  local pattern="$3"

  if printf '%s\n' "$answer" | grep -Eiq "$pattern"; then
    printf '%s\tPASS\n' "$check_name" >> "$RESULTS_FILE"
    printf 'PASS  %s\n' "$check_name"
  else
    printf '%s\tFAIL\n' "$check_name" >> "$RESULTS_FILE"
    FAILURES=$((FAILURES + 1))
    printf 'FAIL  %s\n' "$check_name"
  fi
}

assert_not_contains() {
  local check_name="$1"
  local answer="$2"
  local pattern="$3"

  if printf '%s\n' "$answer" | grep -Eiq "$pattern"; then
    printf '%s\tFAIL\n' "$check_name" >> "$RESULTS_FILE"
    FAILURES=$((FAILURES + 1))
    printf 'FAIL  %s\n' "$check_name"
  else
    printf '%s\tPASS\n' "$check_name" >> "$RESULTS_FILE"
    printf 'PASS  %s\n' "$check_name"
  fi
}

printf 'Running live agent evaluation against %s\n\n' "$CHAT_ENDPOINT"
printf 'The checks validate factual content from the local seed data; natural-language wording may vary.\n\n'

# Case 1: one request should gather all relevant transactional facts.
SUMMARY_RESPONSE="$(post_chat \
  'Give me a complete status summary for order 1, including its items, shipment, payment and refund history.' \
  '' \
  "${CORRELATION_PREFIX}-summary")"

CONVERSATION_ID="$(printf '%s\n' "$SUMMARY_RESPONSE" | jq -er '.conversationId | strings | select(length > 0)')"
SUMMARY_ANSWER="$(printf '%s\n' "$SUMMARY_RESPONSE" | jq -er '.answer | strings')"

printf 'Response metadata:\n'
printf '%s\n' "$SUMMARY_RESPONSE" | jq '{conversationId: .conversationId}' | glow -
show_answer 'Combined order summary' "$SUMMARY_RESPONSE"

assert_contains 'summary reports shipped order' "$SUMMARY_ANSWER" 'shipped'
assert_contains 'summary reports expected total' "$SUMMARY_ANSWER" '720([.]00)?'
assert_contains 'summary includes laptop SKU' "$SUMMARY_ANSWER" 'SKU-LAPTOP-001'
assert_contains 'summary includes docking station SKU' "$SUMMARY_ANSWER" 'SKU-DOCK-001'
assert_contains 'summary includes shipment tracking number' "$SUMMARY_ANSWER" 'DHL-DEMO-ORDER-1'
assert_contains 'summary reports captured payment' "$SUMMARY_ANSWER" 'captured'
assert_contains 'summary explains empty refund history' "$SUMMARY_ANSWER" 'no refunds?|no refund records?|zero refunds?|refund history.{0,20}(none|empty)|no refund transactions?'

# Case 2: test conversation context resolution across separate HTTP requests.
FOLLOWUP_RESPONSE="$(post_chat \
  'What is its tracking number?' \
  "$CONVERSATION_ID" \
  "${CORRELATION_PREFIX}-followup")"
FOLLOWUP_ANSWER="$(printf '%s\n' "$FOLLOWUP_RESPONSE" | jq -er '.answer | strings')"
show_answer 'Conversation follow-up' "$FOLLOWUP_RESPONSE"
assert_contains 'follow-up resolves the order and returns tracking number' "$FOLLOWUP_ANSWER" 'DHL-DEMO-ORDER-1'

# Case 3: payment lookup must provide current transactional facts.
PAYMENT_RESPONSE="$(post_chat \
  'What is the payment status and amount for order 1?' \
  '' \
  "${CORRELATION_PREFIX}-payment")"
PAYMENT_ANSWER="$(printf '%s\n' "$PAYMENT_RESPONSE" | jq -er '.answer | strings')"
show_answer 'Payment lookup' "$PAYMENT_RESPONSE"
assert_contains 'payment lookup reports captured status' "$PAYMENT_ANSWER" 'captured'
assert_contains 'payment lookup reports expected amount' "$PAYMENT_ANSWER" '720([.]00)?'

# Case 4: no refund records must not be hallucinated.
REFUND_RESPONSE="$(post_chat \
  'List the refund history for order 1. If there are no refunds, state that clearly.' \
  '' \
  "${CORRELATION_PREFIX}-refunds")"
REFUND_ANSWER="$(printf '%s\n' "$REFUND_RESPONSE" | jq -er '.answer | strings')"
show_answer 'Refund-history lookup' "$REFUND_RESPONSE"
assert_contains 'refund lookup reports no refund records' "$REFUND_ANSWER" 'no refunds?|no refund records?|zero refunds?|refund history.{0,20}(none|empty)|no refund transactions?'

# Case 5: unknown order IDs must be reported as unknown, not guessed.
UNKNOWN_RESPONSE="$(post_chat \
  'Does order 999999 exist? Report only what the system can confirm.' \
  '' \
  "${CORRELATION_PREFIX}-unknown-order")"
UNKNOWN_ANSWER="$(printf '%s\n' "$UNKNOWN_RESPONSE" | jq -er '.answer | strings')"
show_answer 'Unknown-order handling' "$UNKNOWN_RESPONSE"
assert_contains 'unknown order is explicitly reported' "$UNKNOWN_ANSWER" 'not found|does not exist|no order|unknown order'

# Optional Case 6: enable only when the service is running with the `rag` profile.
if [[ "${EVALUATE_RAG:-false}" == "true" ]]; then
  RAG_RESPONSE="$(post_chat \
    'What is the return window according to the documented policy?' \
    '' \
    "${CORRELATION_PREFIX}-rag-return-window")"
  RAG_ANSWER="$(printf '%s\n' "$RAG_RESPONSE" | jq -er '.answer | strings')"
  show_answer 'RAG policy retrieval' "$RAG_RESPONSE"
  assert_contains 'RAG answer gives the documented 30-calendar-day window' "$RAG_ANSWER" '30[[:space:]]+calendar[[:space:]]+days'
  assert_contains 'RAG answer identifies the Refund Policy source' "$RAG_ANSWER" 'Refund Policy'
  assert_contains 'RAG answer identifies policy version 1.0' "$RAG_ANSWER" 'version[[:space:]]+1[.]0'
  assert_not_contains 'RAG answer does not expose raw retrieval markers' "$RAG_ANSWER" 'SOURCE(_ID)?:|EXCERPT:'

  # A relevant document may not contain every requested detail; the agent must not invent one.
  ADDRESS_RESPONSE="$(post_chat \
    'What return address does the Refund Policy specify?' \
    '' \
    "${CORRELATION_PREFIX}-rag-unsupported-detail")"
  ADDRESS_ANSWER="$(printf '%s\n' "$ADDRESS_RESPONSE" | jq -er '.answer | strings')"
  show_answer 'RAG question not answered by the corpus' "$ADDRESS_RESPONSE"
  assert_contains 'RAG says the return address is not documented' "$ADDRESS_ANSWER" 'not specified|does not specify|does not state|not provided|not included|not documented|cannot find|could not find|no information'
  assert_not_contains 'RAG missing-detail answer does not invent an address-like value' "$ADDRESS_ANSWER" '[0-9]+[[:space:]]+[[:alnum:]]+[[:space:]]+(street|st[.]?|road|rd[.]?|avenue|ave[.]?|boulevard|blvd[.]?)'
else
  printf 'Skipping RAG evaluation (set EVALUATE_RAG=true when the application is running with the rag profile).\n'
fi

printf '\n## Evaluation results\n\n' | glow -
jq -Rn '
  [inputs | split("\t") | {check: .[0], status: .[1]}] as $checks
  | {
      summary: {
        passed: ([$checks[] | select(.status == "PASS")] | length),
        failed: ([$checks[] | select(.status == "FAIL")] | length),
        total: ($checks | length)
      },
      checks: $checks
    }
' "$RESULTS_FILE" | glow -

if [[ "$FAILURES" -gt 0 ]]; then
  printf 'Evaluation failed: %s check(s) did not pass. Review the answers above.\n' "$FAILURES" >&2
  exit 1
fi

printf 'Evaluation passed: all checks satisfied.\n'
