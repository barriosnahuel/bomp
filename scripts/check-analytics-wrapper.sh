#!/usr/bin/env bash
#
# Verifies nothing bypasses the AnalyticsTracker wrapper.
#
# Firebase Analytics must flow through the wrapper (commons_android/.../analytics):
#   * FirebaseAnalytics.getInstance(...) only inside AnalyticsTrackerProvider.
#   * .logEvent(...) only inside FirebaseAnalyticsTracker.
# See CLAUDE.md § Analytics events.
#
# Single source of truth for the CircleCI `analytics-wrapper-guard` job and the
# .githooks/pre-push hook. Run locally before pushing:
#   ./scripts/check-analytics-wrapper.sh
set -euo pipefail

cd "$(dirname "$0")/.."

# Production sourcesets only — tests legitimately mock FirebaseAnalytics inside the wrapper's own tests.
MAIN_DIRS="app/src/main commons_android/src/main commons_file/src/main model/src/main"

# Any FirebaseAnalytics.getInstance(...) call must originate inside the wrapper provider.
if grep -rnE 'FirebaseAnalytics\s*\.\s*getInstance' \
    --include='*.kt' --include='*.java' \
    --exclude='AnalyticsTrackerProvider.kt' \
    --exclude='FirebaseAnalyticsTracker.kt' \
    $MAIN_DIRS; then
    echo "ERROR: Direct FirebaseAnalytics.getInstance(...) call detected outside the wrapper. Use AnalyticsTrackerProvider.get(context)."
    exit 1
fi

# And only FirebaseAnalyticsTracker.kt is allowed to call .logEvent(.
if grep -rnE '\.logEvent\s*\(' \
    --include='*.kt' --include='*.java' \
    --exclude='FirebaseAnalyticsTracker.kt' \
    $MAIN_DIRS; then
    echo "ERROR: Direct .logEvent(...) call detected outside FirebaseAnalyticsTracker.kt. Add an AnalyticsEvent subclass and call tracker.log(...) instead."
    exit 1
fi

echo "✅ AnalyticsTracker wrapper not bypassed."
