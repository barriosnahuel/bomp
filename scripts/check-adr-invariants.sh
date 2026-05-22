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
# ADR 0005 — docs/adr/0005-unified-audio-player.md
# Invariant: the only MediaPlayer() constructor in src/main lives inside
# feature/playback/PlayerControllerImpl.kt. New audio surfaces must route
# through PlayerController, not allocate their own player.
# ============================================================================
ALLOWED_MEDIAPLAYER="app/src/main/java/com/github/barriosnahuel/vossosunboton/feature/playback/PlayerControllerImpl.kt"
unexpected_mediaplayer=$(
    grep -rlE --include="*.kt" 'MediaPlayer\s*\(\s*\)' $MAIN_DIRS 2>/dev/null \
        | grep -vxF "$ALLOWED_MEDIAPLAYER" || true
)
if [ -n "$unexpected_mediaplayer" ]; then
    fail "ADR 0005 broken: MediaPlayer() constructor outside PlayerControllerImpl.kt:
$unexpected_mediaplayer
Route through PlayerController.startPlayingUri / startPlayingSound, or supersede ADR 0005."
fi

# ============================================================================
# ADR 0006 — docs/adr/0006-no-kotlin-assert-in-tests.md
# Invariant: no bare kotlin.assert(...) in any test sourceset. The JVM only
# evaluates the condition under -ea, so a misused assert silently no-ops on
# any runner without that flag (see PR #1117). Mirror of the CircleCI job
# `test-assertion-guard`; kept here so contributors fail fast locally.
# ============================================================================
TEST_DIRS="app/src/test app/src/androidTest commons_android/src/test commons_file/src/test model/src/test"
if grep -rnE --include='*.kt' '(^|[^[:alnum:]_])assert[[:space:]]*\(' $TEST_DIRS 2>/dev/null; then
    fail "ADR 0006 broken: bare kotlin.assert(...) in test sources. Use Truth assertThat(...), JUnit assertEquals(...), or the Compose UI Test API (assertCountEquals, assertIsDisplayed). See CLAUDE.md § Test assertions."
fi

# ============================================================================
# ADR 0008 — docs/adr/0008-stable-sound-id.md
# Invariant 1: StoredSound declares a non-nullable `id` field. Catches an
# accidental revert of the schema that breaks persistence identity.
# Invariant 2: SoundsRepository does NOT use the three name-keyed identity
# idioms (associateBy { it.name, filterNot { it.name ==, firstOrNull {
# it.name ==). These are the *persistence-identity* patterns; textual `name`
# use elsewhere (validation, search, display) is intentionally untouched.
# ============================================================================
STORED_SOUND="model/src/main/java/com/github/barriosnahuel/vossosunboton/model/data/StoredSound.kt"
if ! grep -qE '^\s*val id: String' "$STORED_SOUND" 2>/dev/null; then
    fail "ADR 0008 broken: StoredSound.kt does not declare \`val id: String\`. Persistence identity must be the stable internal id, not the display name. Supersede ADR 0008 or restore the field."
fi

SOUNDS_REPO="model/src/main/java/com/github/barriosnahuel/vossosunboton/model/data/manager/SoundsRepository.kt"
if grep -nE 'associateBy \{ it\.name|filterNot \{ it\.name ==|firstOrNull \{ it\.name ==' "$SOUNDS_REPO" 2>/dev/null; then
    fail "ADR 0008 broken: SoundsRepository.kt contains a name-keyed identity idiom (associateBy / filterNot / firstOrNull on it.name). Persistence identity must key by Sound.id. Supersede ADR 0008 or revert."
fi

# ============================================================================
# Design system — no raw color literals or magic alpha values in component code
# (CLAUDE.md § Design system → "Rules for component authors"). An inline
# Color(0x...) hex or a bare numeric .copy(alpha = N) is a magic number: not a
# semantic role from AppTheme.kt, not greppable, contrast never reviewed. This
# pattern recurred across three handoffs — the guard stops it at CI. ui/theme/
# is exempt (it *defines* the palette and the shared named alphas). Escape hatch
# for a justified one-off: a trailing `// alpha-ok` comment on the line.
# ============================================================================
COLOR_DIRS="app/src/main/java/com/github/barriosnahuel/vossosunboton/feature app/src/main/java/com/github/barriosnahuel/vossosunboton/ui"

bad_hex=$(
    grep -rnE --include="*.kt" 'Color\(0x' $COLOR_DIRS 2>/dev/null \
        | grep -v '/ui/theme/' | grep -vF '// alpha-ok' || true
)
if [ -n "$bad_hex" ]; then
    fail "Design system: raw Color(0x...) hex literal in component code:
$bad_hex
Use a semantic role from AppTheme.kt (CLAUDE.md § Design system). ui/theme/ defines the palette."
fi

bad_alpha=$(
    grep -rnE --include="*.kt" '\.copy\(alpha = [0-9.]' $COLOR_DIRS 2>/dev/null \
        | grep -v '/ui/theme/' | grep -vF '// alpha-ok' || true
)
if [ -n "$bad_alpha" ]; then
    fail "Design system: magic numeric .copy(alpha = N) in component code:
$bad_alpha
Name the alpha (shared: ui/theme/Alpha.kt; one-off: a private const next to the Composable) or justify inline with a trailing // alpha-ok. See CLAUDE.md § Design system."
fi

# ============================================================================
# CLAUDE.md size budget — see CLAUDE.md § "What goes in this file"
# Loaded into every Claude Code context window; performance degrades above 40K.
# Measured in bytes (wc -c): portable and deterministic, and a conservative
# proxy for the char budget since UTF-8 bytes >= chars (fails early, never late).
# ============================================================================
CLAUDE_MD_SIZE=$(wc -c < CLAUDE.md | tr -d ' ')
CLAUDE_MD_FAIL=40000
if [ "$CLAUDE_MD_SIZE" -gt "$CLAUDE_MD_FAIL" ]; then
    fail "CLAUDE.md is $CLAUDE_MD_SIZE bytes (hard limit: $CLAUDE_MD_FAIL). Claude Code performance degrades above 40K. Run /claude-md-audit to move reference-time content to CONTRIBUTING.md or docs/adr/."
fi

# ============================================================================
if [ "$errors" -gt 0 ]; then
    echo
    echo "$errors ADR invariant(s) violated. See messages above for which ADR(s) to revisit." >&2
    exit 1
fi
echo "✅ All ADR invariants pass."
