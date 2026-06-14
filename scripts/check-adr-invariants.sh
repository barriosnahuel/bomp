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
# ADR 0010 — docs/adr/0010-button-typology.md
# Name-ban on banned button composables in component code: OutlinedButton,
# ElevatedButton, FilledTonalButton. They default to the secondary* roles, which
# collapse to ~1.1-1.3:1 on `surface` (the #1170 dark-mode CTA bug). Filled-primary
# is `Button` (primaryContainer); secondary is `TextButton` (primary). Honest as a
# name-ban now that GratitudeSection migrated off its recolored FilledTonalButton.
# Reuses $COLOR_DIRS (feature/ + ui/); ui/theme/ exempt; escape hatch `// button-ok`.
# ============================================================================
bad_button=$(
    grep -rnE --include="*.kt" '(OutlinedButton|ElevatedButton|FilledTonalButton)\(' $COLOR_DIRS 2>/dev/null \
        | grep -v '/ui/theme/' | grep -vF '// button-ok' || true
)
if [ -n "$bad_button" ]; then
    fail "ADR 0010 broken: banned button composable in component code:
$bad_button
Use Button (filled-primary, primaryContainer) or TextButton (secondary, primary) per the typology. OutlinedButton/ElevatedButton/FilledTonalButton default to secondary* roles that collapse to ~1:1 on surface. Supersede ADR 0010, or justify a one-off with a trailing // button-ok. See CLAUDE.md § Design system → Button typology."
fi

# ============================================================================
# ADR 0013 — docs/adr/0013-single-sdk-default-robolectric.md
# Invariant: a multi-SDK Robolectric matrix — @Config(sdk = [a, b, ...]) with two
# or more levels — is only for a test guarding a real Build.VERSION.SDK_INT branch,
# and must be justified inline with a `// sdk-boundary:` comment. Single-SDK pins
# and the AbstractRobolectricTest base-class default are unrestricted. Stops the
# cargo-culted 3-SDK matrix that tripled CI time on SDK-independent logic (the
# matrix that made the #1186 hang 3x more expensive to reproduce).
# ============================================================================
bad_matrix=$(
    grep -rnE --include='*.kt' 'sdk[[:space:]]*=[[:space:]]*\[[^]]*,[^]]*\]' $TEST_DIRS 2>/dev/null \
        | grep -vF '// sdk-boundary:' || true
)
if [ -n "$bad_matrix" ]; then
    fail "ADR 0013 broken: multi-SDK @Config matrix without a // sdk-boundary: justification:
$bad_matrix
A 2+ SDK matrix is only for a test guarding a real Build.VERSION.SDK_INT branch. Collapse to a single SDK (inherit the AbstractRobolectricTest default), or justify the boundary inline with a trailing // sdk-boundary: <branch> comment. See CLAUDE.md § Robolectric SDK config / ADR 0013."
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
# ADR 0015 — docs/adr/0015-macrobenchmark-seeding-architecture.md
# Invariant: replaceSyntheticCorpus is a benchmark-only seeding primitive. It is
# declared in SoundsRepository (model/src/main) and called only from the
# benchmark build type (app/src/benchmark); the model unit test exercises it.
# Any other production/debug/release reference fails — this grep stands in for
# the @VisibleForTesting fence Lint can't provide for a cross-module caller.
# ============================================================================
rogue_seed=$(
    grep -rln --include="*.kt" 'replaceSyntheticCorpus' \
        app commons_android commons_file model 2>/dev/null \
        | grep -vE '/src/benchmark/|/src/test/|/src/androidTest/' \
        | grep -vE 'model/src/main/.*/SoundsRepository\.kt$' || true
)
if [ -n "$rogue_seed" ]; then
    fail "ADR 0015 broken: replaceSyntheticCorpus referenced outside the benchmark seeder:
$rogue_seed
It is a benchmark-only primitive — only app/src/benchmark may call it (declared in SoundsRepository, tested in model/src/test). Supersede ADR 0015 or move the call site."
fi

# ============================================================================
# Comment-block length budget — see CLAUDE.md § "Comments & KDoc"
# KDoc/comments carry what the code can't say: the contract, invariants & gotchas
# you'd break unknowingly, and grep-anchor markers. Decision *rationale* (why this
# design, rejected alternatives, revisit criteria) belongs in docs/adr/*.md,
# referenced by a one-line pointer — not re-derived in a comment that then drifts
# from the ADR (two sources of truth). This guard is the blunt backstop against an
# egregiously long single comment block creeping back; the 25-26 line band is where
# legit multi-key contracts live, so anything LONGER must be acknowledged. Escape
# hatch for a genuinely-legit long block (a multi-key contract, a triage table): a
# `long-comment-ok` marker anywhere inside the block. Counts a contiguous run of
# comment lines (`//`, `/* */`, `/** */`); the 5-line license header is well under.
# ============================================================================
COMMENT_BLOCK_MAX=26
COMMENT_DIRS="app/src commons_android/src commons_file/src model/src macrobenchmark/src"
long_comments=$(
    find $COMMENT_DIRS -name '*.kt' 2>/dev/null | while IFS= read -r f; do
        awk -v MAX="$COMMENT_BLOCK_MAX" '
            function flush() {
                if (blocklen > MAX && !blockok) print FILENAME ":" blockstart " (" blocklen " comment lines)"
                blocklen = 0; blockok = 0; blockstart = 0
            }
            {
                t = $0; sub(/^[ \t]+/, "", t); iscomment = 0; afterclose = 0
                if (inblock) { iscomment = 1; if (index(t, "*/") > 0) afterclose = 1 }
                else if (t ~ /^\/\*/) { iscomment = 1; if (index(substr(t, 3), "*/") == 0) inblock = 1 }
                else if (t ~ /^\/\//) iscomment = 1
                if (iscomment) {
                    if (blocklen == 0) blockstart = FNR
                    blocklen++
                    # marker must be a standalone token, not a substring of a longer identifier
                    if (t ~ /(^|[^[:alnum:]_-])long-comment-ok([^[:alnum:]_-]|$)/) blockok = 1
                    if (afterclose) inblock = 0
                } else if (blocklen > 0) flush()
            }
            END { if (blocklen > 0) flush() }
        ' "$f"
    done || true
)
if [ -n "$long_comments" ]; then
    fail "Comment hygiene: comment block longer than $COMMENT_BLOCK_MAX lines without a long-comment-ok marker:
$long_comments
KDoc = contract + invariants/gotchas + grep anchors; decision rationale belongs in docs/adr/*.md (one-line pointer), how-comments stay <= 2-3 lines. Trim the block to a pointer, or — for a genuinely-legit long block — acknowledge it with a \`long-comment-ok\` marker inside the block. See CLAUDE.md § Comments & KDoc."
fi

# ============================================================================
# runBlocking-await ratchet — see CONTRIBUTING.md § "Awaiting multiple async inputs"
# An unbounded `runBlocking { … .first/.collect/.await/.single … }` in a test hangs
# forever if the value never arrives; CI only kills it after the 10-min no-output
# timeout, with a useless generic message (the #1186 hang). withTimeout makes the
# await fail in seconds, by name. This guard freezes the baseline: a NEW file may not
# introduce one, and a grandfathered file may not grow past its count. It does NOT
# sweep the existing offenders — that cleanup ratchets the counts down (handoff: sweep
# the runBlocking-await baseline). Single-line awaits only; multi-line `runBlocking {`
# openings are out of scope (most are legit `runBlocking { repo.save() }` setup →
# false positives). Escape hatch: a trailing `// await-ok` on the line.
# ============================================================================
RUNBLOCKING_AWAIT_BASELINE="27 app/src/test/java/com/github/barriosnahuel/vossosunboton/ui/home/SoundsViewModelCollectionsTest.kt
19 app/src/test/java/com/github/barriosnahuel/vossosunboton/ui/home/SoundsViewModelAnalyticsTest.kt
1 app/src/test/java/com/github/barriosnahuel/vossosunboton/ui/home/SoundsViewModelLifecycleTest.kt
1 app/src/test/java/com/github/barriosnahuel/vossosunboton/ui/home/LandingScreenTest.kt
1 app/src/androidTest/java/com/github/barriosnahuel/vossosunboton/TestData.kt"
runblocking_await_violations=$(
    find $TEST_DIRS -name '*.kt' 2>/dev/null | while IFS= read -r f; do
        actual=$(awk '/runBlocking[[:space:]]*\{.*\.(first|collect|await|single)/ && !/withTimeout/ && !/\/\/ await-ok/ {c++} END {print c + 0}' "$f")
        baseline=$(awk -v f="$f" '$2 == f {print $1; ok = 1} END {if (!ok) print 0}' <<<"$RUNBLOCKING_AWAIT_BASELINE")
        if [ "$actual" -gt "$baseline" ]; then
            echo "$f (found $actual, baseline $baseline)"
        fi
    done || true
)
if [ -n "$runblocking_await_violations" ]; then
    fail "Unbounded runBlocking await over baseline in test sources:
$runblocking_await_violations
A test's runBlocking { … .first/.collect/.await/.single … } must be bounded by withTimeout(…) so it fails in seconds, not after the 10-min CI no-output timeout (the #1186 hang). Wrap the await in withTimeout(TIMEOUT_MS) — see CONTRIBUTING.md § Awaiting multiple async inputs — or justify a one-off with a trailing // await-ok. The grandfathered baseline only shrinks (handoff: sweep the runBlocking-await baseline), never grows."
fi

# ============================================================================
if [ "$errors" -gt 0 ]; then
    echo
    echo "$errors ADR invariant(s) violated. See messages above for which ADR(s) to revisit." >&2
    exit 1
fi
echo "✅ All ADR invariants pass."
