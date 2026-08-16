# ADR 0014 — Worktree lifecycle: sibling layout + auto-cleanup of merged worktrees

- **Status:** Accepted
- **Date:** 2026-06-07
- **Supersedes:** —

## Context

This repo is worked on heavily through git worktrees: the Claude Code harness
and its subagents spin one up per task, the `delegate` skill (named
`overnight-work` when this ADR was written; a user-level Claude Code skill — it
lives in `~/.claude/skills/`, **not** in this repo) opens one worktree + branch +
PR per task, and humans create them by hand. Two ends of that lifecycle need a
policy.

**Creation.** The harness default places worktrees under `.claude/worktrees/<name>`.
Nested there, the IDE indexes each as a separate project and clutters navigation.

**Teardown.** GitHub's `deleteBranchOnMerge` deletes the *remote* branch when a PR
merges, and the harness has its own automatic cleanup for the *unchanged* scratch
worktrees it created (branches named `worktree-<name>`). Neither covers the common
case: a worktree whose PR has **merged** but which has local commits — exactly the
`delegate` / manual-PR shape. Those linger until removed by hand (one session
removed 17 worktrees + 17 branches that had accumulated this way). And the obvious
"is it merged?" check is a trap: PRs routinely land via merge methods that **rewrite
SHAs** (squash or rebase), so `git branch -d` / `merge-base --is-ancestor` report the
branch as not merged.

## Decision drivers

1. **A destructive automation must never lose unmerged work.** Removal has to key
   on a positive, branch-specific "this was merged" signal — never a heuristic.
2. **Complement the harness, don't fight it.** The harness already cleans unchanged
   scratch worktrees; this should cover the gap (merged-PR worktrees), not overlap.
3. **Don't break verified commits.** The remote (CCR) environment owns
   `core.hooksPath` globally to install its commit-signing / `Co-authored-by` hooks.
4. **Self-arming across environments** — fresh clone, terminal `git pull`, and the
   remote env — without manual steps.
5. **Mechanical, not a judgement call.** The teardown decision is deterministic;
   it should be plain code, auditable and free.

## Options considered

**Layout**
- *Nested under `.claude/worktrees/` (harness default).* Rejected — IDE clutter.
- *Siblings of the primary worktree (`../push-me-<topic>`).* Chosen.

**Teardown trigger**
- *Manual script after merge.* Rejected — relies on remembering.
- *Scheduled cron / routine.* Rejected — acts unwatched (could remove a worktree
  mid-review) and needs `gh` auth in a headless context.
- *`SessionEnd` / a harness hook.* Rejected — session end ≠ merge; at task end the
  PR is still open and the work unmerged, so cleanup there is unsafe.
- *`post-merge` git hook.* Chosen — it fires on the `git pull` of `develop` that
  follows a merge (the moment a branch becomes locally stale), works for terminal
  pulls outside Claude, and there is no equivalent harness event. `pull.rebase` is
  unset, so pulls merge and `post-merge` fires (no `post-rewrite` needed).

**"Is it merged?" signal**
- *Git ancestry (`merge-base --is-ancestor`).* Rejected — both squash and GitHub's
  rebase-merge rewrite SHAs.
- *Branch name in the set of merged PRs.* Rejected — two data-loss traps:
  *branch-name reuse* (`deleteBranchOnMerge` frees the name; a new worktree reusing
  it would be classified as merged) and *post-merge local commits* (a clean tree,
  so the dirty guard misses them).
- *The branch's own PR landed this exact commit.* Chosen.

**Hook installation**
- *`git config core.hooksPath .githooks` (the usual convention).* Rejected — a
  repo-local `core.hooksPath` overrides the remote env's global one and silently
  breaks verified commits.
- *Copy into `$(git rev-parse --git-common-dir)/hooks`.* Chosen — CCR's hooks are
  passthroughs that chain to that same dir, so the hook fires in both environments
  with signing intact.

**Teardown implementation**
- *A Claude command invoked from the hook.* Rejected — non-deterministic, slow, and
  token-costly for a destructive operation that needs none of it.
- *Deterministic bash.* Chosen.

## Decision

1. **Layout.** Worktrees are **siblings of the primary worktree** (`../push-me-<topic>`),
   never nested under `.claude/`. The committed `WorktreeCreate` hook
   (`.claude/hooks/create-sibling-worktree.sh`) applies this to harness/subagent
   worktrees automatically.

2. **Teardown.** `scripts/cleanup-merged-worktrees.sh`, triggered by the committed
   `.githooks/post-merge` hook (only on the `develop` branch; always exits 0), removes
   a linked worktree **and** its local branch **only when** a `MERGED` PR for that head
   landed **this exact commit** (the PR's `headRefOid` equals the worktree's tip) **and**
   no PR for the head is still `OPEN`. It also `git fetch --prune`s dead `origin/*` refs.

3. **Boundaries.** It never touches: the primary worktree; protected branches
   (`develop`, `gh-pages`, `feat/gh-pages-*` — the `PROTECTED_BRANCHES` list at the top
   of the script); detached worktrees; worktrees with uncommitted changes (kept with a
   warning, never `--force`); and **orphan local branches** with no worktree (e.g.
   `backup/*`). **Fail-safe:** any `gh` failure or a tip that doesn't match a merged PR
   yields *keep* — it never deletes without a positive "merged at this commit" signal.
   `--dry-run` is side-effect-free (skips `fetch --prune` and `worktree prune`).

4. **Relationship to harness cleanup.** The two are complementary: the harness cleans
   *unchanged* scratch worktrees (`worktree-<name>`, no PR); this script cleans
   *merged-PR* worktrees. Scratch worktrees have no PR, so this script keeps them and
   leaves them to the harness. Removal is idempotent, so a rare overlap is harmless.

5. **Installation.** The hook is installed by **copy** into `.git/hooks/` via
   `scripts/install-hooks.sh`, re-armed every session by a committed `SessionStart`
   hook in `.claude/settings.json` — **not** via `core.hooksPath`.

## Consequences

- Merged-PR worktrees disappear on the next `develop` pull; no manual debt.
- Creation is a *harness* hook (a harness event); teardown is a *git* hook (a git
  event that also covers terminal pulls). The asymmetry is intentional, not accidental.
- **Revisit when:** the remote env stops owning `core.hooksPath` (then the standard
  `core.hooksPath .githooks` convention becomes viable); the merge method stops rewriting
  SHAs — today PRs land by **rebase**, which rewrites them exactly as squash did, so the
  merged-at-this-commit query is here to stay. It could only go if squash **and** rebase
  were disabled outright, making *every* PR a merge commit whose SHAs survive (the
  `OPEN`-PR guard stays either way); all three methods remain enabled, so an occasional
  merge-commit landing is not that signal. Or `pull.rebase` is turned on (add a
  `post-rewrite` hook). A worktree that is *changed but never got a merged PR* is out of
  scope — it lingers, by design, for manual cleanup.

## Invariants

Guarded by a **behaviour test**, not a grep: `scripts/test-cleanup-merged-worktrees.sh`
(CircleCI job `worktree-cleanup-test`) is mutation-tested to fail if the removal
decision regresses — merged-at-this-commit, branch-name reuse, post-merge commits,
open PR, `gh` failure, protected, dirty, and dry-run-is-side-effect-free.

## Cross-references

- `CLAUDE.md` § *Worktree setup* (write-time invariants).
- `CONTRIBUTING.md` § *Creating a new worktree* / § *Cleaning up merged worktrees* (procedure).
- `scripts/cleanup-merged-worktrees.sh`, `.githooks/post-merge`, `scripts/install-hooks.sh`.
- Harness creation hook: `.claude/hooks/create-sibling-worktree.sh`.
