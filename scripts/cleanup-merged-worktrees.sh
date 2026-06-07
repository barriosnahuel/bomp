#!/usr/bin/env bash
#
# Removes linked worktrees (and their local branch) whose PR has already been
# MERGED on GitHub, plus prunes dead remote-tracking refs.
#
# Why this exists: overnight-work creates one worktree + branch + PR per task
# and never tears them down. GitHub's `deleteBranchOnMerge` already removes the
# *remote* branch on merge, but the local worktree + local branch linger. We
# can't lean on `git branch -d` to detect "merged" because PRs land as **squash**
# merges, which rewrite SHAs — so `merge-base --is-ancestor` reports false.
#
# Removal signal (see pr_verdict): for the branch's OWN PR, REMOVE only when a
# MERGED PR landed THIS exact commit (its headRefOid == the local tip) AND no PR
# for the head is still OPEN. Matching by branch name alone is unsafe — a reused
# name (its old PR merged) or commits added locally after the merge would both be
# mis-classified as "merged" and force-deleted.
#
# Safety properties (this is mechanical, not a judgement call — keep it that way):
#   * Fail-safe: any gh failure / absent-or-mismatched PR yields KEEP. We never
#     delete without a positive "merged at this commit" signal.
#   * Never touches the primary worktree, protected branches, detached worktrees,
#     or worktrees with uncommitted changes (skipped with a warning, never --force).
#   * Idempotent and non-fatal: always exits 0 so a `post-merge` hook can call it
#     without ever blocking a `git pull`.
#   * --dry-run is side-effect-free: it skips `git fetch --prune` and
#     `git worktree prune` (both mutate .git), so a preview changes nothing.
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
# deletes the head branch on merge). Best-effort — offline is fine. Skipped in
# dry-run: it mutates .git (removes stale origin/* refs), and --dry-run promises
# to change nothing.
if [ "$dry_run" -eq 0 ]; then
  git fetch --prune --quiet 2>/dev/null || echo "⚠ git fetch --prune skipped (offline?)"
fi

# Per-branch safety verdict. We DON'T trust "a PR with this branch name was
# merged" — that's two distinct data-loss traps:
#   * branch-name REUSE: a name whose old PR merged is freed by deleteBranchOnMerge;
#     a new worktree reusing the name would be wrongly classified as merged;
#   * post-merge LOCAL commits: work committed on the branch AFTER its PR merged.
# So REMOVE only when this exact commit is the one a MERGED PR landed (headRefOid
# == local tip) AND no PR for this head is still OPEN. Any doubt ⇒ KEEP.
# FAIL-SAFE: a failed/absent gh query yields KEEP, never a removal.
pr_verdict() {
  local path="$1" branch="$2" tip v
  tip="$(git -C "$path" rev-parse HEAD 2>/dev/null)"
  [[ "$tip" =~ ^[0-9a-f]{40,64}$ ]] || { echo "KEEP (cannot resolve tip)"; return 0; }
  # One gh call per candidate. tip is a validated hex SHA, safe to interpolate.
  if ! v="$(gh pr list --head "$branch" --state all --limit 20 --json state,headRefOid \
        --jq "if any(.[]; .state==\"OPEN\") then \"KEEP\" elif any(.[]; .state==\"MERGED\" and .headRefOid==\"$tip\") then \"REMOVE\" else \"KEEP\" end" 2>/dev/null)"; then
    echo "KEEP (gh query failed — offline/unauthenticated?)"; return 0
  fi
  if [ "$v" = "REMOVE" ]; then
    echo "REMOVE"
  else
    echo "KEEP (no merged PR at this commit / open PR / extra local commits)"
  fi
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

  # Cheap, local gates first — no network for these.
  if [ -z "$branch" ]; then
    echo "  ↳ keep  $short  (detached HEAD)"; kept=$((kept + 1)); return 0
  fi
  if is_protected "$branch"; then
    echo "  ↳ keep  $short  (protected branch: $branch)"; kept=$((kept + 1)); return 0
  fi
  if [ -n "$(git -C "$path" status --porcelain 2>/dev/null)" ]; then
    echo "  ⚠ keep  $short  (uncommitted changes — commit/stash first; never force-removed)"
    kept=$((kept + 1)); return 0
  fi

  # Network gate last, only for survivors.
  local verdict; verdict="$(pr_verdict "$path" "$branch")"
  if [ "$verdict" != "REMOVE" ]; then
    echo "  ↳ keep  $short  ($branch: $verdict)"; kept=$((kept + 1)); return 0
  fi

  if [ "$dry_run" -eq 1 ]; then
    echo "  ✓ would remove  $short  + branch $branch  (merged at this commit)"
    removed=$((removed + 1)); return 0
  fi

  if git worktree remove "$path" 2>/dev/null && git branch -D "$branch" >/dev/null 2>&1; then
    echo "  ✓ removed  $short  + branch $branch  (merged at this commit)"
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

# Skipped in dry-run: it mutates .git (removes admin files for gone worktrees).
if [ "$dry_run" -eq 0 ]; then
  git worktree prune 2>/dev/null || true
fi

echo "── summary: ${removed} $([ "$dry_run" -eq 1 ] && echo 'removable' || echo 'removed'), ${kept} kept ──"
exit 0
