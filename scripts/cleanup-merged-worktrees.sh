#!/usr/bin/env bash
#
# Removes linked worktrees (and their local branch) whose PR has already been
# MERGED on GitHub, plus prunes dead remote-tracking refs.
#
# Why this exists: overnight-work creates one worktree + branch + PR per task
# and never tears them down. GitHub's `deleteBranchOnMerge` already removes the
# *remote* branch on merge, but the local worktree + local branch linger. We
# can't lean on `git branch -d` to detect "merged" because PRs land as **squash**
# merges, which rewrite SHAs — so `merge-base --is-ancestor` reports false. The
# only reliable signal is the PR's own MERGED state, queried via `gh`.
#
# Safety properties (this is mechanical, not a judgement call — keep it that way):
#   * Fail-safe: if `gh` is unavailable / unauthenticated / offline, the merged
#     set is empty and NOTHING is removed. We never delete without a positive
#     MERGED signal.
#   * Never touches the primary worktree, protected branches, detached worktrees,
#     or worktrees with uncommitted changes (skipped with a warning, never --force).
#   * Idempotent and non-fatal: always exits 0 so a `post-merge` hook can call it
#     without ever blocking a `git pull`.
#
# Usage:
#   scripts/cleanup-merged-worktrees.sh [--dry-run]
#
# Invoked automatically by .githooks/post-merge (installed via
# scripts/install-hooks.sh). See CONTRIBUTING.md § Cleaning up merged worktrees.

set -uo pipefail

# Branches that must never be removed even if a merged PR happens to point at
# them. Extend this list (and the glob below) rather than weakening the checks.
PROTECTED_BRANCHES=(develop gh-pages)
PROTECTED_GLOB='feat/gh-pages-*'

DEFAULT_BRANCH=develop

dry_run=0
case "${1:-}" in
  --dry-run) dry_run=1 ;;
  "") ;;
  *) echo "✘ Unknown argument: $1 (only --dry-run is supported)" >&2; exit 0 ;;
esac

is_protected() {
  local branch="$1"
  local p
  for p in "${PROTECTED_BRANCHES[@]}"; do
    [ "$branch" = "$p" ] && return 0
  done
  # shellcheck disable=SC2053  # glob match on purpose
  [[ "$branch" == $PROTECTED_GLOB ]] && return 0
  return 1
}

# Resolve the primary worktree root (parent of the shared .git common dir).
primary_root="$(dirname "$(git rev-parse --path-format=absolute --git-common-dir)")"

if [ "$dry_run" -eq 1 ]; then
  echo "🧹 cleanup-merged-worktrees (dry-run)"
  echo "   (dry-run: nothing will actually be removed)"
else
  echo "🧹 cleanup-merged-worktrees"
fi

# Scope 2: drop remote-tracking refs whose upstream branch is gone (GitHub
# deletes the head branch on merge). Best-effort — offline is fine.
git fetch --prune --quiet 2>/dev/null || echo "⚠ git fetch --prune skipped (offline?)"

# Source of truth: branches whose PR is MERGED. These persist in `gh` even after
# the remote branch is deleted. FAIL-SAFE: any failure ⇒ empty set ⇒ no removals.
merged_set=""
gh_ok=1
if ! merged_set="$(gh pr list --state merged --limit 200 --json headRefName --jq '.[].headRefName' 2>/dev/null)"; then
  gh_ok=0
fi
if [ "$gh_ok" -eq 0 ]; then
  echo "⚠ Could not query merged PRs via gh (offline / unauthenticated?). Removing nothing."
fi

is_merged() {
  local branch="$1"
  [ -n "$merged_set" ] || return 1
  grep -qxF -- "$branch" <<<"$merged_set"
}

removed=0
kept=0

# Walk every worktree. --porcelain emits a block per worktree:
#   worktree <path>\n (HEAD <sha>\n branch refs/heads/<name> | detached)\n ...
path="" branch=""
flush() {
  [ -n "$path" ] || return 0

  if [ "$path" = "$primary_root" ]; then
    return 0  # primary — silent, it's the one we're running from
  fi

  local short; short="$(basename "$path")"

  if [ -z "$branch" ]; then
    echo "  ↳ keep  $short  (detached HEAD)"; kept=$((kept + 1)); return 0
  fi
  if is_protected "$branch"; then
    echo "  ↳ keep  $short  (protected branch: $branch)"; kept=$((kept + 1)); return 0
  fi
  if ! is_merged "$branch"; then
    echo "  ↳ keep  $short  (PR not merged: $branch)"; kept=$((kept + 1)); return 0
  fi
  if [ -n "$(git -C "$path" status --porcelain 2>/dev/null)" ]; then
    echo "  ⚠ keep  $short  (MERGED but DIRTY — commit/stash first; never force-removed)"
    kept=$((kept + 1)); return 0
  fi

  if [ "$dry_run" -eq 1 ]; then
    echo "  ✓ would remove  $short  + branch $branch  (PR merged)"
    removed=$((removed + 1)); return 0
  fi

  if git worktree remove "$path" 2>/dev/null && git branch -D "$branch" >/dev/null 2>&1; then
    echo "  ✓ removed  $short  + branch $branch  (PR merged)"
    removed=$((removed + 1))
  else
    echo "  ✘ failed to remove  $short  (left untouched)"
    kept=$((kept + 1))
  fi
}

while IFS= read -r line; do
  case "$line" in
    worktree\ *) flush; path="${line#worktree }"; branch="" ;;
    branch\ refs/heads/*) branch="${line#branch refs/heads/}" ;;
    "") flush; path="" branch="" ;;
  esac
done < <(git worktree list --porcelain)
flush  # last block has no trailing blank line in some git versions

git worktree prune 2>/dev/null || true

echo "── summary: ${removed} $([ "$dry_run" -eq 1 ] && echo 'removable' || echo 'removed'), ${kept} kept ──"
exit 0
