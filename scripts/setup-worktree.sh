#!/usr/bin/env bash
#
# Idempotently sets up a freshly-created push-me worktree:
#
#   * copies the real google-services.json files (debug + release) from the
#     primary worktree, where they live as skip-worktree overrides
#   * re-arms skip-worktree on this worktree so accidental edits don't reach
#     the index
#   * copies the bundled debug audio files (best-effort — not committed)
#
# Re-running is safe: if both google-services files are already skip-worktree
# AND byte-identical to the primary worktree's, the script exits without
# touching anything.
#
# Invoke from the worktree's root. Resolves the primary worktree via
# `git rev-parse --git-common-dir`, so it works from any linked worktree.
# Refuses to run in the primary itself — source and destination would collide.
#
# See CLAUDE.md § Worktree setup and CONTRIBUTING.md § Firebase config file.

set -euo pipefail

primary_git_dir="$(git rev-parse --path-format=absolute --git-common-dir)"
this_git_dir="$(git rev-parse --path-format=absolute --git-dir)"
primary_root="$(dirname "$primary_git_dir")"

if [ "$this_git_dir" = "$primary_git_dir" ]; then
  echo "✘ Refusing to run in the primary worktree ($primary_root)." >&2
  echo "  This script is for linked worktrees only (../push-me-<topic>/)." >&2
  exit 1
fi

CONFIGS=(
  "app/src/release/google-services.json"
  "app/src/debug/google-services.json"
)

needs_work=0
for rel in "${CONFIGS[@]}"; do
  src="$primary_root/$rel"
  if [ ! -f "$src" ]; then
    echo "✘ Primary worktree is missing $rel." >&2
    echo "  Set up the primary worktree first (CONTRIBUTING.md § Firebase config file)." >&2
    exit 1
  fi
  flag="$(git ls-files -v -- "$rel" | awk 'NR==1 {print $1}')"
  if [ "$flag" != "S" ] || ! cmp -s "$src" "$rel"; then
    needs_work=1
    break
  fi
done

if [ "$needs_work" -eq 0 ]; then
  echo "✓ google-services configs already set up (skip-worktree, matches primary)."
else
  # Order matters: arm skip-worktree FIRST (cheap, against the scrubbed dummy
  # currently on disk), then `cp` the real file over it. If the script is
  # interrupted between the two steps, the worst case is skip-worktree set on
  # the still-scrubbed dummy — a future `git add` won't stage anything
  # sensitive. The reverse order would leave a window where the real
  # `AIza…` key is on disk and git would happily stage it.
  for rel in "${CONFIGS[@]}"; do
    git update-index --skip-worktree -- "$rel"
    cp "$primary_root/$rel" "$rel"
  done
  echo "✓ google-services configs copied from primary and marked skip-worktree."
fi

# The `benchmark` build type uses applicationId `.debug` and reuses the real DEBUG Firebase config
# (bomp-debug — a scrubbed dummy would make FirebaseSessions fatal on a real device; see
# app/build.gradle). google-services resolves per build-type folder, so the debug file is copied into
# the benchmark folder; its `.debug` package already matches. Hard-fail on a missing source and stay
# idempotent, mirroring the CONFIGS loop above (skip-worktree first so the real key never stages).
bench_dest="app/src/benchmark/google-services.json"
bench_src="$primary_root/app/src/debug/google-services.json"
if [ ! -f "$bench_src" ]; then
  echo "✘ Primary worktree is missing app/src/debug/google-services.json (needed for the benchmark config)." >&2
  exit 1
fi
bench_flag="$(git ls-files -v -- "$bench_dest" | awk 'NR==1 {print $1}')"
if [ "$bench_flag" != "S" ] || ! cmp -s "$bench_src" "$bench_dest"; then
  git update-index --skip-worktree -- "$bench_dest"
  cp "$bench_src" "$bench_dest"
  echo "✓ benchmark google-services seeded from the debug config (skip-worktree)."
else
  echo "✓ benchmark google-services already set up."
fi

mkdir -p model/src/debug/res/raw
cp "$primary_root/model/src/debug/res/raw/"*.mp3 model/src/debug/res/raw/ 2>/dev/null || true
cp "$primary_root/model/src/debug/res/raw/"*.ogg model/src/debug/res/raw/ 2>/dev/null || true
echo "✓ Debug audio bundle copy attempted (best-effort)."
