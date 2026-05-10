#!/usr/bin/env bash
# Verifies the invariants declared by each ADR under docs/adr/.
# When this script fails, the failure message names the ADR you broke;
# either revert the offending change or supersede the ADR with a successor.
# Run locally before pushing: ./scripts/check-adr-invariants.sh
set -euo pipefail

cd "$(dirname "$0")/.."

errors=0
fail() {
    echo "❌ $1" >&2
    errors=$((errors + 1))
}

MAIN_DIRS="app/src/main commons_android/src/main commons_file/src/main model/src/main"

# ============================================================================
# ADR 0001 — docs/adr/0001-local-ui-test-suite.md
# Invariant: connectedAndroidTest is NOT wired into CircleCI. The instrumented
# UI/functional suite is intentionally local-only.
# ============================================================================
if grep -qE "connected(Debug)?AndroidTest" .circleci/config.yml 2>/dev/null; then
    fail "ADR 0001 broken: connectedAndroidTest wired into .circleci/config.yml. The instrumented UI suite is intentionally local-only — supersede ADR 0001 or revert."
fi

# ============================================================================
# ADR 0002 — docs/adr/0002-no-hilt-manual-viewmodel-factory.md
# Invariant: no DI framework imports in any production sourceset.
# ============================================================================
if grep -rnE --include="*.kt" --include="*.java" \
        'import (dagger\.hilt|org\.koin|javax\.inject)' $MAIN_DIRS 2>/dev/null; then
    fail "ADR 0002 broken: DI framework import in src/main (Hilt / Koin / javax.inject). Supersede ADR 0002 or revert — manual viewModelFactory is the project convention."
fi

# ============================================================================
# ADR 0003 — docs/adr/0003-channel-for-one-shot-ui-events.md
# Invariant: no MutableSharedFlow constructed in src/main (would silently
# introduce a parallel event-passing pattern next to Channel<T>).
# ============================================================================
if grep -rnE --include="*.kt" '\bMutableSharedFlow\b' $MAIN_DIRS 2>/dev/null; then
    fail "ADR 0003 broken: MutableSharedFlow appears in src/main. Project convention: Channel<T> + receiveAsFlow() for one-shot events, StateFlow for state. Supersede ADR 0003 if the migration is intentional."
fi

# ============================================================================
# ADR 0004 — docs/adr/0004-datastore-sync-api-cache-prime.md
# Invariant: runBlocking only appears in the documented analytics cache-prime
# files (the no-runBlocking-in-production rule's only documented exception).
# Allowlist below; rename / move = update the allowlist + ADR.
# ============================================================================
ALLOWED_RUNBLOCKING="commons_android/src/main/java/com/github/barriosnahuel/vossosunboton/commons/android/analytics/DataStoreFirstFlagStore.kt
commons_android/src/main/java/com/github/barriosnahuel/vossosunboton/commons/android/analytics/DataStoreCounterStore.kt
commons_android/src/main/java/com/github/barriosnahuel/vossosunboton/commons/android/analytics/AnalyticsTrackerProvider.kt"

unexpected_runblocking=$(
    grep -rl --include="*.kt" 'runBlocking\s*[({]' $MAIN_DIRS 2>/dev/null \
        | grep -vxF "$ALLOWED_RUNBLOCKING" || true
)
if [ -n "$unexpected_runblocking" ]; then
    fail "ADR 0004 broken: runBlocking outside the analytics cache-prime allowlist:
$unexpected_runblocking
Supersede ADR 0004 or move the call site. The no-runBlocking rule is in CLAUDE.md § Project-specific overrides → Threading."
fi

# ============================================================================
# ADR 0005 — docs/adr/0005-no-kotlin-assert-in-tests.md
# Invariant: no bare kotlin.assert(...) in any test sourceset. The JVM only
# evaluates the condition under -ea, so a misused assert silently no-ops on
# any runner without that flag (see PR #1117). Mirror of the CircleCI job
# `test-assertion-guard`; kept here so contributors fail fast locally.
# ============================================================================
TEST_DIRS="app/src/test app/src/androidTest commons_android/src/test commons_file/src/test model/src/test"
if grep -rnE --include='*.kt' '(^|[^[:alnum:]_])assert[[:space:]]*\(' $TEST_DIRS 2>/dev/null; then
    fail "ADR 0005 broken: bare kotlin.assert(...) in test sources. Use Truth assertThat(...), JUnit assertEquals(...), or the Compose UI Test API (assertCountEquals, assertIsDisplayed). See CLAUDE.md § Test assertions."
fi

# ============================================================================
if [ "$errors" -gt 0 ]; then
    echo
    echo "$errors ADR invariant(s) violated. See messages above for which ADR(s) to revisit." >&2
    exit 1
fi
echo "✅ All ADR invariants pass."
