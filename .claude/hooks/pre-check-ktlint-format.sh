#!/usr/bin/env bash
#
# PreToolUse(Bash) hook — runs `./gradlew ktlintFormat` BEFORE a Bash command
# that invokes a Gradle check/ktlint task, so the check runs on already-formatted
# sources and never fails on an auto-fixable ktlint violation.
#
# Why: every ktlint failure caught at check time is a wasted check cycle that
# auto-recovers (run ktlintFormat, re-run check). All standard ktlint rules are
# auto-fixable, so formatting first eliminates that class of failure at its
# source. CI being green guarantees ktlintFormat only touches files just edited,
# so there is no diff pollution. See CONTRIBUTING.md § Pre-check ktlint format.
#
# Contract (Claude Code PreToolUse hook — https://code.claude.com/docs/en/hooks):
#   stdin  — JSON event; the command lives under .tool_input.command.
#   exit 0 — always; this is a best-effort pre-format, never a gate. A non-zero
#            exit would block the user's command, which we must never do.
# Non-matching commands (the common case) no-op in microseconds: no Gradle, no
# subshell beyond the JSON parse.
#
set -euo pipefail

event="$(cat)"

# Cheap pre-filter on the RAW event: almost every Bash command never mentions
# gradlew, so skip the jq/python3 parse (a process spawn) for them and return in
# a couple of milliseconds. A command that merely echoes "gradlew" slips through
# to the precise grep below, which still decides correctly — no false positives.
case "$event" in
  *gradlew*) ;;
  *) exit 0 ;;
esac

# Read a nested string field from the event JSON. jq when available (fast path),
# python3 otherwise — both ship with current macOS. Missing key -> empty string.
json_get() {
  if command -v jq >/dev/null 2>&1; then
    printf '%s' "$event" | jq -r "$1 // empty"
  else
    printf '%s' "$event" | python3 -c \
      "import sys, json; d=json.load(sys.stdin)
for k in sys.argv[1].strip('.').split('.'): d=d.get(k, {}) if isinstance(d, dict) else {}
print(d if isinstance(d, str) else '')" "$1"
  fi
}

command_text="$(json_get '.tool_input.command')"

# Only act on a Gradle invocation that runs a check / ktlint*Check task. Plain
# `ktlintFormat` does not match (no "Check"), so the hook never re-triggers on
# its own format command.
case "$command_text" in
  *gradlew*) ;;
  *) exit 0 ;;
esac
printf '%s' "$command_text" \
  | grep -Eq '(^|[[:space:]:])(check|ktlint[[:alnum:]]*Check)([[:space:]]|$|-)' \
  || exit 0

# Format from the worktree root the session runs in. Guard on the wrapper so the
# hook degrades to a no-op if it ever runs somewhere without one.
[ -x ./gradlew ] || exit 0
./gradlew ktlintFormat -q >&2 || true

exit 0
