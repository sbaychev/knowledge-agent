#!/usr/bin/env bash
set -euo pipefail

# Focused regression suite for durable agent execution lifecycle
# and HTTP-to-agent application orchestration.

TESTS='AgentChatApplicationServiceTest,AgentExecutionPersistenceIntegrationTest'

printf '# Agent execution lifecycle tests\n\n' \
  | { command -v glow >/dev/null 2>&1 && glow - || cat; }

printf 'Running focused JUnit suite: %s\n\n' "$TESTS"

if command -v glow >/dev/null 2>&1; then
  mvn --batch-mode --no-transfer-progress \
      -Dtest="$TESTS" test 2>&1 | glow -
else
  mvn --batch-mode --no-transfer-progress \
      -Dtest="$TESTS" test
fi
