#!/usr/bin/env bash
#
# Behaviour test for scripts/cleanup-merged-worktrees.sh.
#
# Pure bash + git, no network and no extra tooling (no bats, no jq, no real gh):
# it builds a throwaway git repo with real linked worktrees, shims `gh` on PATH
# to return canned "<STATE> <headRefOid>" lines per branch, then asserts the
# keep/remove decision for each scenario — guarding the data-loss findings from
# the PR #1199 review (branch-name reuse, post-merge local commits) plus the
# fail-safe, dry-run-is-side-effect-free, protected and dirty paths.
#
# Run locally or in CI:  ./scripts/test-cleanup-merged-worktrees.sh

set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLEANUP="$SCRIPT_DIR/cleanup-merged-worktrees.sh"

fails=0
pass() { echo "  ✓ $1"; }
fail() { echo "  ✘ $1"; fails=$((fails + 1)); }

# assert_contains <label> <haystack> <needle>
assert_contains() {
  case "$2" in
    *"$3"*) pass "$1" ;;
    *)      fail "$1 — expected to find: [$3]" ; printf '    in output:\n%s\n' "$2" ;;
  esac
}
assert_not_contains() {
  case "$2" in
    *"$3"*) fail "$1 — did NOT expect: [$3]" ;;
    *)      pass "$1" ;;
  esac
}

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# ── gh shim ────────────────────────────────────────────────────────────────
# Emulates: gh pr list --head <b> ... --json state,headRefOid --jq '<STATE> <OID>'.
# Reads a fixture file per head ('/'→'__'); missing ⇒ no PRs; '__FAIL__' ⇒ exit 1.
bindir="$tmp/bin"
mkdir -p "$bindir"
fixtures="$tmp/fixtures"
mkdir -p "$fixtures"
cat >"$bindir/gh" <<'SHIM'
#!/usr/bin/env bash
head=""
while [ $# -gt 0 ]; do
  case "$1" in
    --head) head="${2:-}"; shift 2 ;;
    *) shift ;;
  esac
done
fix="$GH_FIXTURE_DIR/${head//\//__}"
[ -f "$fix" ] || exit 0
[ "$(cat "$fix")" = "__FAIL__" ] && exit 1
cat "$fix"
exit 0
SHIM
chmod +x "$bindir/gh"

# ── build a throwaway repo with real worktrees ──────────────────────────────
repo="$tmp/main"
git -c init.defaultBranch=develop init -q "$repo"
cd "$repo"
git config user.email t@t.t; git config user.name t
git commit -q --allow-empty -m "root"

# Helper: make a linked worktree on a new branch with one commit; echo its tip.
mk_wt() { # <branch> <dirname>
  git worktree add -q -b "$1" "$tmp/$2" develop >/dev/null
  git -C "$tmp/$2" commit -q --allow-empty -m "work on $1"
  git -C "$tmp/$2" rev-parse HEAD
}

t_match="$(mk_wt feat/merged-match merged-match)"          # merged at this commit → REMOVE
echo "MERGED $t_match" >"$fixtures/feat__merged-match"

t_extra="$(mk_wt feat/merged-extra merged-extra)"          # PR merged an OLDER commit
git -C "$tmp/merged-extra" commit -q --allow-empty -m "post-merge local commit"
echo "MERGED $t_extra" >"$fixtures/feat__merged-extra"     # fixture oid != current tip → KEEP

mk_wt feat/name-reuse name-reuse >/dev/null               # old PR's oid, unrelated to tip
echo "MERGED 0000000000000000000000000000000000000000" >"$fixtures/feat__name-reuse"

t_open="$(mk_wt feat/open-pr open-pr)"                     # OPEN wins even if also merged here
printf 'OPEN %s\nMERGED %s\n' "$t_open" "$t_open" >"$fixtures/feat__open-pr"

mk_wt feat/no-pr no-pr >/dev/null                         # no fixture → no PRs → KEEP

t_down="$(mk_wt feat/gh-down gh-down)"                     # gh fails → KEEP
echo "__FAIL__" >"$fixtures/feat__gh-down"

git worktree add -q -b gh-pages "$tmp/ghpages" develop >/dev/null   # protected → KEEP
git worktree add -q -b feat/gh-pages-x "$tmp/ghpages-x" develop >/dev/null  # protected glob

t_dirty="$(mk_wt feat/dirty-merged dirty)"                # merged here BUT dirty → KEEP
echo "MERGED $t_dirty" >"$fixtures/feat__dirty-merged"
echo "uncommitted" >"$tmp/dirty/scratch.txt"

run() { PATH="$bindir:$PATH" GH_FIXTURE_DIR="$fixtures" bash "$CLEANUP" "$@" 2>&1; }

# ── dry-run assertions ──────────────────────────────────────────────────────
echo "▶ dry-run decisions"
out="$(run --dry-run)"
assert_contains     "merged at this commit → would remove"      "$out" "would remove  merged-match"
assert_contains     "post-merge extra commit → keep (#2)"        "$out" "keep  merged-extra"
assert_not_contains "post-merge extra commit not removed (#2)"   "$out" "would remove  merged-extra"
assert_contains     "branch-name reuse → keep (#1)"              "$out" "keep  name-reuse"
assert_not_contains "branch-name reuse not removed (#1)"         "$out" "would remove  name-reuse"
assert_contains     "open PR → keep"                             "$out" "keep  open-pr"
assert_contains     "no PR → keep"                               "$out" "keep  no-pr"
assert_contains     "gh failure → keep (fail-safe #4)"           "$out" "keep  gh-down"
assert_contains     "gh failure surfaced"                        "$out" "gh query failed"
assert_contains     "protected branch → keep"                    "$out" "keep  ghpages"
assert_contains     "protected glob → keep"                      "$out" "keep  ghpages-x"
assert_contains     "dirty worktree → keep"                      "$out" "keep  dirty"
assert_contains     "dirty reason is uncommitted, not merged"    "$out" "uncommitted changes"

# dry-run must mutate nothing: every worktree still present.
echo "▶ dry-run is side-effect-free"
for d in merged-match merged-extra name-reuse open-pr no-pr gh-down ghpages ghpages-x dirty; do
  if [ -d "$tmp/$d" ]; then pass "worktree still present: $d"; else fail "worktree vanished in dry-run: $d"; fi
done

# ── real run: only the merged-at-this-commit worktree is removed ────────────
echo "▶ real run removes only the safe case"
run >/dev/null
[ ! -e "$tmp/merged-match" ] && pass "merged-match worktree removed" || fail "merged-match worktree NOT removed"
git show-ref --verify --quiet refs/heads/feat/merged-match \
  && fail "merged-match branch still exists" || pass "merged-match branch deleted"
for d in merged-extra name-reuse open-pr no-pr gh-down ghpages ghpages-x dirty; do
  if [ -d "$tmp/$d" ]; then pass "kept worktree intact: $d"; else fail "kept worktree wrongly removed: $d"; fi
done

echo
if [ "$fails" -eq 0 ]; then
  echo "✅ all cleanup-merged-worktrees behaviour tests passed"
  exit 0
else
  echo "❌ $fails assertion(s) failed"
  exit 1
fi
