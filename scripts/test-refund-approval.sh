#!/usr/bin/env bash
set -euo pipefail

TESTS='RefundApprovalIntegrationTest,RefundApprovalExpiryIntegrationTest,RefundApiIntegrationTest'

printf '# Refund approval workflow tests\n\n' \
  | { command -v glow >/dev/null 2>&1 && glow - || cat; }

printf 'Running focused JUnit suite: %s\n\n' "$TESTS"

if command -v glow >/dev/null 2>&1; then
  mvn --batch-mode --no-transfer-progress \
      -Dtest="$TESTS" test 2>&1 | glow -
else
  mvn --batch-mode --no-transfer-progress \
      -Dtest="$TESTS" test
fi
