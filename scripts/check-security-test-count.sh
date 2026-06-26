#!/usr/bin/env bash
#
# Strict-count guard for OWASP MASVS-tagged security tests.
#
# Each security boundary test in this repo carries a KDoc line on the form
# `OWASP MASVS-<area>-<n> / CWE-<id> (label)` directly above @Test
# (see CLAUDE.md § Security boundaries → Security test tagging).
#
# This script counts those markers across all test source roots and fails if
# the count differs from EXPECTED_COUNT. Strict equality on purpose:
#   * Drops below = a security test was removed (regression).
#   * Drops above = a new tag was added without bumping this number.
# Either way the change is visible in the diff, intentional, and reviewable.
#
# Updating EXPECTED_COUNT is a deliberate one-line edit that ships in the same
# PR as the test change. If the count check feels noisy, the answer is *not*
# to weaken the check — it's to bump the number and explain why in the PR.
#
# Wired into CircleCI as the `security-test-count-guard` job.

set -euo pipefail

EXPECTED_COUNT=32

SCAN_ROOTS=(
  app/src/test
  app/src/androidTest
  commons_android/src/test
  commons_android/src/androidTest
  commons_file/src/test
  commons_file/src/androidTest
  model/src/test
  model/src/androidTest
)

existing_roots=()
for root in "${SCAN_ROOTS[@]}"; do
  [ -d "$root" ] && existing_roots+=("$root")
done

if [ ${#existing_roots[@]} -eq 0 ]; then
  echo "❌ No test source roots found. Run from the repo root."
  exit 1
fi

actual_count=$(
  grep -rE "OWASP MASVS-" --include="*Test.kt" "${existing_roots[@]}" 2>/dev/null | wc -l | tr -d ' '
)

if [ "$actual_count" != "$EXPECTED_COUNT" ]; then
  echo "❌ Security-test count mismatch."
  echo "   Expected: $EXPECTED_COUNT"
  echo "   Actual:   $actual_count"
  echo
  if [ "$actual_count" -lt "$EXPECTED_COUNT" ]; then
    echo "A security test appears to have been removed. Restore it, or"
    echo "if the removal is intentional, lower EXPECTED_COUNT in this script"
    echo "and explain in the PR description."
  else
    echo "A new OWASP MASVS-tagged test was added but EXPECTED_COUNT was not"
    echo "bumped. Update EXPECTED_COUNT in this script to $actual_count and"
    echo "ship the bump in the same PR as the new test."
  fi
  exit 1
fi

echo "✅ Security-test count matches expected ($actual_count)."
