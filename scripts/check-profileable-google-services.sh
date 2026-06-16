#!/usr/bin/env bash
#
# Fail fast when the profiling build types still carry the scrubbed CI google-services dummy.
#
# The `benchmark` and `nonMinifiedRelease` build types install under applicationId `.debug` and reuse the
# real DEBUG Firebase config. The committed copies are scrubbed dummies (so CI compiles), seeded with the
# real key only via scripts/setup-worktree.sh (skip-worktree). If the profiling build runs against the
# dummy, Firebase init throws `IllegalArgumentException: Please set a valid API key` ~0.3s after launch, the
# process dies before Macrobenchmark can flush the profile, and the run fails with the cryptic
# `never flushed profiles in any process` — costing real diagnosis time.
#
# This guard catches that BEFORE the build. Used by scripts/generate-baseline-profile.sh; behaviour-tested
# by scripts/test-check-profileable-google-services.sh. Exits 0 (silent) when the configs are real.
#
# See CLAUDE.md § Worktree setup and CONTRIBUTING.md § Firebase config file.

set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

DEBUG="app/src/debug/google-services.json"
TARGETS=(
  "app/src/nonMinifiedRelease/google-services.json"
  "app/src/benchmark/google-services.json"
)

# True when the working file is byte-identical to the committed copy — i.e. still the scrubbed CI dummy,
# never seeded with the real key. Process substitution (not a pipe) so `set -o pipefail` can't trip on the
# SIGPIPE git-show takes when cmp stops early.
is_dummy() { cmp -s "$1" <(git show "HEAD:$1" 2>/dev/null); }

dummies=()
for t in "${TARGETS[@]}"; do
  if is_dummy "$t"; then dummies+=("$t"); fi
done

if [ "${#dummies[@]}" -eq 0 ]; then exit 0; fi

{
  echo "✘ The profiling build's google-services is the scrubbed CI dummy:"
  for d in "${dummies[@]}"; do echo "    $d"; done
  echo
  echo "  These build types install under applicationId '.debug' and would crash on Firebase init"
  echo "  (IllegalArgumentException: Please set a valid API key) ~0.3s after launch, so the process dies"
  echo "  before Macrobenchmark flushes the profile — surfacing as the cryptic 'never flushed profiles'."
  echo
  if is_dummy "$DEBUG"; then
    echo "  The real debug config is missing too ($DEBUG is the dummy). Set up the primary worktree's"
    echo "  google-services first (CONTRIBUTING.md § Firebase config file), then re-run."
  else
    echo "  Fix — seed each from the real debug config (skip-worktree FIRST so the real key never stages):"
    for d in "${dummies[@]}"; do
      echo "    git update-index --skip-worktree -- $d && cp $DEBUG $d"
    done
    echo "  Or, in a linked worktree, just run: ./scripts/setup-worktree.sh"
  fi
} >&2
exit 1
