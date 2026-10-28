#!/usr/bin/env bash
set -euo pipefail

printf '# Agent execution and tool-limit tests\n\n' | glow -

set -o pipefail

mvn \
  -Dtest=AgentToolCallingConfigurationTest,BoundedToolCallingAdvisorTest,AgentExecutionDeadlineTest,AgentObservabilityTest \
  test 2>&1 | glow -
