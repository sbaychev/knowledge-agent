#!/usr/bin/env bash
set -euo pipefail

# Focused regression suite for agent resource limits, rejection, timeouts,
# metrics and the bounded Spring AI tool-calling advisor.

TESTS='AgentExecutionConfigurationTest,AgentToolCallingConfigurationTest,BoundedToolCallingAdvisorTest,AgentExecutionDeadlineTest,AgentObservabilityTest'

printf '# Agent execution and tool-limit tests\n\n' | { command -v glow >/dev/null 2>&1 && glow - || cat; }
printf 'Running focused JUnit suite: %s\n\n' "$TESTS"

if command -v glow >/dev/null 2>&1; then
  mvn --batch-mode --no-transfer-progress -Dtest="$TESTS" test 2>&1 | glow -
else
  mvn --batch-mode --no-transfer-progress -Dtest="$TESTS" test
fi
