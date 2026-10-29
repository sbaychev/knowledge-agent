#!/usr/bin/env bash
set -euo pipefail

# Reproduce the CI verification step locally. Extra arguments are passed to Maven.
MVN_ARGS=(--batch-mode --no-transfer-progress clean verify)
MVN_ARGS+=( "$@" )

printf '# Knowledge Agent verification\n\n' | { command -v glow >/dev/null 2>&1 && glow - || cat; }
printf 'Running: mvn'
printf ' %q' "${MVN_ARGS[@]}"
printf '\n\n'

if command -v glow >/dev/null 2>&1; then
  mvn "${MVN_ARGS[@]}" 2>&1 | glow -
else
  mvn "${MVN_ARGS[@]}"
fi
