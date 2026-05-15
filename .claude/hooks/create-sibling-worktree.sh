#!/usr/bin/env bash
#
# WorktreeCreate hook — places git worktrees the Claude Code harness or its
# subagents create as SIBLINGS of the primary worktree (../push-me-<name>)
# instead of the harness default .claude/worktrees/<name>.
#
# Why: worktrees nested under .claude/ get indexed by the IDE as extra projects
# in the same window, cluttering navigation. Siblings keep one project per
# window. See CLAUDE.md § Worktree setup and CONTRIBUTING.md § Creating a new
# worktree.
#
# Contract (Claude Code WorktreeCreate hook — https://code.claude.com/docs/en/hooks):
#   stdin  — JSON event. Field names differ between doc pages, so this hook
#            parses defensively: name from worktree_name / name / basename of
#            worktree_path; base from base_ref / base_branch.
#   stdout — the absolute path of the created worktree; the harness uses it as
#            the session working directory.
#   exit 0 with a path on stdout = success
#   empty stdout or any non-zero exit = worktree creation fails
# The hook fully replaces the default `git worktree` logic, so it runs
# `git worktree add` itself. It mirrors the documented default branch naming
# (worktree-<name>) so the harness's automatic worktree+branch cleanup still
# matches. Only the *location* changes.
#
set -euo pipefail

event="$(cat)"

# Read a string field from the event JSON. jq when available (fast path),
# python3 otherwise — both ship with current macOS. Missing key -> empty string.
json_get() {
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$event" | jq -r --arg k "$1" '.[$k] // empty'
  else
    printf '%s' "$event" | python3 -c \
      "import sys, json; print(json.load(sys.stdin).get(sys.argv[1], ''))" "$1"
  fi
}

# Worktree name — try the documented field names in order, then fall back to the
# basename of any suggested path, then to a timestamp so creation never hard-fails.
name="$(json_get worktree_name)"
[ -n "$name" ] || name="$(json_get name)"
suggested_path="$(json_get worktree_path)"
[ -n "$name" ] || { [ -n "$suggested_path" ] && name="$(basename "$suggested_path")"; }
[ -n "$name" ] || name="auto-$(date +%Y%m%d-%H%M%S)"

# Base ref to branch from — empty means branch off the current HEAD.
base_ref="$(json_get base_ref)"
[ -n "$base_ref" ] || base_ref="$(json_get base_branch)"

# Resolve the PRIMARY worktree root even when invoked from a linked worktree:
# --git-common-dir always points at the shared .git, whose parent is that root.
cwd="$(json_get cwd)"
[ -n "$cwd" ] || cwd="$PWD"
primary_git_dir="$(git -C "$cwd" rev-parse --path-format=absolute --git-common-dir)"
primary_root="$(dirname "$primary_git_dir")"
repo_name="$(basename "$primary_root")"
parent_dir="$(dirname "$primary_root")"

worktree_path="$parent_dir/${repo_name}-${name}"
branch_name="worktree-${name}"

# git's own stdout goes to stderr so it doesn't pollute the path the harness reads.
if [ -n "$base_ref" ]; then
  git -C "$primary_root" worktree add "$worktree_path" -b "$branch_name" "$base_ref" >&2
else
  git -C "$primary_root" worktree add "$worktree_path" -b "$branch_name" >&2
fi

printf '%s\n' "$worktree_path"
