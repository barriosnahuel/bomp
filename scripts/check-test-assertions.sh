#!/usr/bin/env bash
#
# Verifies no bare kotlin.assert(...) in test sources.
#
# `kotlin.assert(cond) { msg }` is a no-op without JVM `-ea` — see ADR 0006 and
# CLAUDE.md § Test assertions. Tests must use Truth (`assertThat`), JUnit
# (`assertEquals` etc.), or the Compose UI Test API (`assertCountEquals`).
#
# Single source of truth for the CircleCI `test-assertion-guard` job and the
# .githooks/pre-push hook. Run locally before pushing:
#   ./scripts/check-test-assertions.sh
set -euo pipefail

cd "$(dirname "$0")/.."

TEST_DIRS="app/src/test app/src/androidTest commons_android/src/test commons_file/src/test model/src/test"

if grep -rnE '(^|[^[:alnum:]_])assert[[:space:]]*\(' \
    --include='*.kt' \
    $TEST_DIRS; then
    echo "ERROR: Bare assert(...) detected in test sources. kotlin.assert is a no-op without JVM -ea — use assertThat(...) (Truth), assertEquals(...) (JUnit), or assertCountEquals(...) (Compose UI Test). See CLAUDE.md § Test assertions."
    exit 1
fi

echo "✅ No bare kotlin.assert(...) in test sources."
