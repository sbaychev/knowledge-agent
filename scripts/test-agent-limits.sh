#!/usr/bin/env bash
set -euo pipefail

printf '# Agent execution and tool-limit tests\n\n' | glow -

set -o pipefail

mvn \
  -Dtest=AgentExecutionConfigurationTest,AgentToolCallingConfigurationTest,BoundedToolCallingAdvisorTest,AgentExecutionDeadlineTest,AgentObservabilityTest \
  test 2>&1 | glow -

  chmod +x scripts/test-agent-limits.sh
  ./scripts/test-agent-limits.sh
