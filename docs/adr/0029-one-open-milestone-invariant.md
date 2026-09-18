# ADR 0029 — Exactly one open milestone, opened by the cut

- **Status:** Accepted
- **Date:** 2026-09-17
- **Amends:** [ADR 0023](0023-monthly-sequential-release-tags.md) — only the milestone-lifecycle clause of § *Decision* ("The month's milestone is created lazily — when its first PR opens — and assigned to every PR at creation. The cut closes it."). Milestones are now opened **by the cut**, not by the next PR, and a PR takes **the open milestone** rather than "the current month's". The rest of ADR 0023 — the `vYYYY.MM.N` scheme, the milestone-at-PR-creation linkage, the no-release-month rename, the forward-only frontier — stands.

## Context

ADR 0023 names the milestone after the release tag and tells every PR to take "the current month's
`vYYYY.MM.N`" at creation. That phrasing has a referent bug: the milestone stands for a **release**,
but it is selected by the **calendar month**. Those two agree only while the cut lands at month end.

The gap already cost one mislabelled PR. On 2026-08-21 the `v2026.08.1` release was published at
02:42:00 UTC and its milestone closed 23 seconds later. PR #1316 was opened at 03:05:31 UTC — 23
minutes *after* the cut — and was assigned `v2026.08.1` 18 seconds later, by the rule, correctly
applied. It merged on 2026-09-05. Its commit is **not** an ancestor of the `v2026.08.1` tag, so a PR
that shipped in no release at all is filed under one that had already shipped. (A sweep of every
closed milestone confirms #1316 is the only such case in the repo's history.)

Two windows opened at that cut, and they are distinct. August's cut on the 21st left a **ten-day
window in which the old rule returns a wrong answer** — any PR opened between the cut and 31 August
takes an already-shipped milestone. Separately, no milestone was open **at all** from 2026-08-21
02:42 until `v2026.09.1` was created on 2026-09-03 00:48: **thirteen days** during which a PR had
nothing correct to take, so the rule's "creating it if missing" escape was the only path — and it
points at the wrong month.

During either window the right answer is genuinely **unknowable**: a PR opened on 21 August belongs
to `v2026.08.2` if a second August release gets cut, and to `v2026.09.1` if it does not. This is the
one case ADR 0023's central driver — a milestone name *"knowable the moment the month starts"* — does
not cover. The name is knowable at the start of the month, and stops being knowable the moment a
release is cut.

**The correct practice already existed; it just wasn't written down.** At the July cut the next
milestone was created *before* the current one closed — `v2026.08.1` created 2026-07-16 00:00:16
UTC, `v2026.07.1` closed 00:00:25, nine seconds later. August did not repeat it. So this ADR is not
inventing a mechanism: it is promoting an unwritten practice to a rule, which is precisely the kind
of knowledge that evaporates when it lives only in whoever cut the last release.

Nothing caught the failure. The rule was satisfied, so following it harder would not have helped.
GitHub allows assigning closed milestones without warning. ADR 0023 § *Enforcement* delegates to
review — but what review catches is a **missing** milestone (*"a PR without milestone is visible at a
glance"*), and a wrong milestone looks exactly like a right one.

## Decision

- **Exactly one milestone is open at any time, and every PR is assigned that one** — never a closed
  milestone. This replaces "the current month's" as the selection rule, removing the calendar from a
  decision that was always about releases.
- **The cut is a single step with three ordered parts**, in § *Creating the GitHub release*:
  1. **Create** the next milestone — `vYYYY.MM.<N+1>`, the cut month with the counter incremented
     (cutting `v2026.09.1` creates `v2026.09.2`). Creating *before* closing means no instant passes
     with nothing open to assign, which is the order July already used.
  2. **Move every still-open PR** off the closing milestone onto the new one. A PR in flight at the
     cut did not ship in it, so leaving it behind reproduces #1316's damage by a different route —
     and GitHub's only signal is a closed milestone with `open_issues > 0`, which nobody watches.
  3. **Close** the release's milestone, now empty of open PRs.
- **A month that ends with no release still renames its open milestone to the next month**
  (`v2026.09.2` → `v2026.10.1`), unchanged from ADR 0023. The rename absorbs the counter guess, so
  naming the new milestone for the cut month costs nothing when the month holds only one release.
- **If no milestone is open** (project start, or one closed out of band), create one named for the
  **next counter after the month's last cut** — `.1` only when the month has not cut anything yet.
  Never reuse a name that already tags a release.
- **If two are open**, close or rename the spurious one before assigning; the invariant is one.

## Trade-off accepted

The milestone's name now carries a **prediction** — that the next release lands in the same month —
where ADR 0023's name carried a fact about the calendar. That prediction is wrong most months, and
each wrong one costs a rename.

Taking that cost buys the thing ADR 0023 could not give: a referent with no ambiguity. "The open
milestone" is a question with exactly one answer at every instant, checkable in one API call, and
unfalsifiable by the passage of time — whereas "the current month's" silently changes meaning the
moment a release is cut. The rename was already in ADR 0023 for no-release months, so this adds no
new mechanism; it widens the use of one that exists.

**What this does not buy: an automatic answer for a PR that is open across the cut.** Step 2 above
handles it by hand, and a hand step can be skipped. That residue is deliberate — the alternative is a
mechanical guard.

**A mechanical guard was considered and rejected for now** — failing a PR whose milestone is closed
(checkable with `gh pr view --json milestone` plus the milestone's state) would catch this class
whatever its cause, including a skipped step 2. It is not adopted because ADR 0023 § *Enforcement*
deliberately keeps this scheme guard-free (*"the scheme is low-frequency"*), and one incident does
not yet outweigh that. See § *Revisit criteria*.

## Enforcement

`CONTRIBUTING.md` § *Creating the GitHub release* (step 6) and § *Labels & milestone examples*, plus
CLAUDE.md § *Labels and milestone*, are canonical and human-checked at cut. No grep guard, per ADR
0023 § *Enforcement* and the trade-off above.

## Revisit criteria

- **If a second PR ever lands with a closed or wrong milestone, adopt the mechanical guard.** One
  incident is a process gap; two is evidence that human checking does not hold here, and the guard's
  ceremony becomes the cheaper side of the trade. The likeliest source of that second incident is a
  skipped step 2, since it is the one part of the cut with no artifact to show it was done.
- If the project routinely cuts more than one release per month, the counter-increment naming stops
  being a prediction and the rename disappears on its own — this ADR's trade-off then costs nothing.
- If releases become fully automated end to end, the cut can create the milestone and sweep the open
  PRs programmatically, and the human-checked enforcement above can be dropped.
