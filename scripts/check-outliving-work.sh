#!/usr/bin/env bash
#
# Verifies every JVM test that mounts a work-starting host neutralises that work.
#
# Mounting one of these hosts starts a decode on Dispatchers.IO that nothing joins.
# When it fails it calls Tracker — by then the teardown has undone the global mock,
# so the exception escapes into the coroutines exception collector and is reported
# against whichever Compose test drains it next, an innocent one. See CONTRIBUTING.md
# § Work that outlives a test.
#
# A mount must be paired with one of: stubbing the source (mockkObject(WaveformExtractor))
# or taking the screen out of the composition before teardown (disposeAddButtonScreen).
# Substituting the analytics tracker does NOT count: AnalyticsTrackerProvider wraps
# Firebase Analytics, while the mechanism runs through Tracker (Crashlytics), and the
# fake stops neither the decode nor the report.
#
# Single source of truth for the CircleCI `outliving-work-guard` job and the
# .githooks/pre-push hook. Run locally before pushing:
#   ./scripts/check-outliving-work.sh
set -euo pipefail

cd "$(dirname "$0")/.."

# Composables whose mount starts async work whose failure path reaches Tracker.
# Add a host here when you introduce one; the guard only sees what it is told about.
HOSTS='RecorderHost|ImmersiveListenHost|AddButtonScreen'

# Word boundary is load-bearing twice over: without it `AddButtonScreen(` also matches
# `disposeAddButtonScreen(`, which is the mitigation, not the problem. Anchoring to the
# start of the line is equally wrong — hosts get mounted inline inside `AppTheme { … }`.
MOUNT="\\b($HOSTS)[[:space:]]*\\("
MITIGATIONS='mockkObject\(WaveformExtractor\)|disposeAddButtonScreen'

errors=0

while IFS= read -r file; do
    [ -n "$file" ] || continue

    # A trailing `// outliving-ok` on the mount line opts that mount out, like the
    # `// alpha-ok` / `// button-ok` / `// await-ok` hatches elsewhere in this repo.
    mounts=$(grep -nE "$MOUNT" "$file" | grep -cv 'outliving-ok' || true)
    [ "$mounts" -gt 0 ] || continue

    if ! grep -qE "$MITIGATIONS" "$file"; then
        host=$(grep -oE "($HOSTS)[[:space:]]*\\(" "$file" | head -1 | tr -d ' (')
        echo "ERROR: $file mounts $host without neutralising the work it starts."
        errors=$((errors + 1))
    fi
done <<EOF
$(grep -rlE "$MOUNT" --include='*.kt' app/src/test || true)
EOF

if [ "$errors" -gt 0 ]; then
    echo ""
    echo "Mounting one of these hosts starts a decode on Dispatchers.IO that outlives the test and"
    echo "reports through Tracker after the teardown undid its mock — failing an unrelated test later."
    echo "Fix by stopping the work, not by re-mocking Tracker:"
    echo "  * stub the source in @Before:  mockkObject(WaveformExtractor) + coEvery { extract(...) } returns null"
    echo "  * or take the screen out of the composition from the subclass @After: disposeAddButtonScreen(...)"
    echo "See CONTRIBUTING.md § Work that outlives a test."
    exit 1
fi

echo "✅ Every JVM test mounting a work-starting host neutralises that work."
