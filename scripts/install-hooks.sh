#!/usr/bin/env bash
#
# Installs the repo's git hooks by COPYING them from .githooks/ into the repo's
# actual hooks directory ($(git rev-parse --git-common-dir)/hooks).
#
# Why copy instead of `git config core.hooksPath .githooks`: the remote (CCR)
# environment sets core.hooksPath globally to install commit-signing /
# Co-authored-by hooks (~/.ccr-git-hooks). A repo-local core.hooksPath would
# override that and silently break verified commits. CCR's hooks are passthroughs
# that chain to $(git rev-parse --git-common-dir)/hooks/<name> — exactly where we
# copy to — so the hook fires in both the remote env and a plain local clone.
#
# Idempotent: re-running only rewrites a hook whose contents changed. A committed
# SessionStart hook (.claude/settings.json) re-runs this every Claude session, so
# a fresh clone self-arms; the installed copy then persists in .git/hooks for
# terminal `git pull`s. See CONTRIBUTING.md § Cleaning up merged worktrees.

set -euo pipefail

root="$(git rev-parse --show-toplevel)"
hooks_dir="$(git rev-parse --git-common-dir)/hooks"
src_dir="$root/.githooks"

if [ ! -d "$src_dir" ]; then
  echo "✘ $src_dir not found — run from inside the repo." >&2
  exit 1
fi

mkdir -p "$hooks_dir"

installed=0
for src in "$src_dir"/*; do
  [ -f "$src" ] || continue
  name="$(basename "$src")"
  dest="$hooks_dir/$name"
  if [ -f "$dest" ] && cmp -s "$src" "$dest"; then
    continue  # already up to date
  fi
  cp "$src" "$dest"
  chmod +x "$dest"
  echo "✓ installed hook: $name"
  installed=$((installed + 1))
done

if [ "$installed" -eq 0 ]; then
  echo "✓ git hooks already up to date."
fi

exit 0
