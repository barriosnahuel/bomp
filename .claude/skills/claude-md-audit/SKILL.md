---
name: claude-md-audit
description: Audit CLAUDE.md against the 40K size budget; propose moves of reference-time content to CONTRIBUTING.md or new/existing ADRs, then apply with user approval. Use when CLAUDE.md approaches 40K chars or feels bloated.
---

# claude-md-audit

Codified procedure for trimming CLAUDE.md back under the size budget without losing write-time utility. The routing rule lives in CLAUDE.md § "What goes in this file" — apply it here mechanically.

## Procedure

### 1. Measure
- `wc -c CLAUDE.md` — anchor the audit to this number.
- Report it back to the user alongside the budget (hard fail at 40K, target ≤ 35K for headroom) and the delta to target.

### 2. Walk the file with the routing axis
For each `##` section, classify:

- **Write-time → KEEP** — invariants, hard nos, project-specific overrides on platform docs, small canonical snippets (≤ 5 lines) the agent will reach for inline, decision tables (role → intent, label → use).
- **Reference-time → MOVE to CONTRIBUTING.md § X** — setup procedures, run commands, debugging walkthroughs, operator workflows, ASCII art, multi-step verification flows.
- **Decision + rationale → MOVE to docs/adr/XXXX-*.md** — *why* the project diverges, alternatives considered, revisit criteria. Mirror format of `docs/adr/0001-*.md` through the latest. Increment the next ADR number.
- Sections that mix both: **split**. Keep the invariant in CLAUDE.md; link out for the procedure / rationale.

### 3. Sections never to trim
Flag and stop if proposed changes touch these — they are pure write-time invariant content and trimming them costs more than the chars they save:

- § Sources of truth
- § Project-specific overrides
- § Features — test coverage workflow (the three-axis planning gate is a write-time forcing rule; trimming it reopens the manual-testing leak)
- § Security boundaries
- § Accessibility (WCAG 2.2 AA)
- § Design system (role → intent table)
- § Analytics events (hard rules)
- § Error tracking (hard rules + RuntimeException wrap snippet)
- § Copyright headers (the block IS the invariant)

User must explicitly approve any exception.

### 4. Propose, do not execute yet
Print a section-by-section table to the user:

| Section | Current chars | Action | Δ chars |
|---|---|---|---|
| ... | ... | KEEP / TRIM / MOVE TO CONTRIBUTING.md § X / NEW ADR | ... |

Wait for explicit approval before any edit. If plan mode is active, write the plan to the plan file.

### 5. Execute (after approval)
- Move content to target file. Match target's heading hierarchy and writing style (English; see CLAUDE.md § Repo writing language).
- In CLAUDE.md, replace moved content with a one-line link: `... see CONTRIBUTING.md § X.` or `... see ADR XXXX`.
- New ADRs: include an `## Invariants` section if grep-enforceable, and **extend `scripts/check-adr-invariants.sh` in the same PR**.
- Cross-refs: every `see CONTRIBUTING.md § X` must match an existing heading; every `see ADR XXXX` must map to a real file under `docs/adr/`. Grep verify.

### 6. Re-measure and verify
- `wc -c CLAUDE.md` — must be under 35K (target) or at minimum under 40K.
- `bash scripts/check-adr-invariants.sh` — must pass.
- `./gradlew check -x test` — sanity (meta-only changes shouldn't break the build).
- **Smoke read**: read CLAUDE.md as a fresh agent. The file still answers: "can I use Hilt?" / "what dispatcher?" / "how do I track errors?" / "top-bar accent color?" / "inbound URI policy?" — if yes, trim preserved write-time utility.

### 7. Commit + PR
Single PR with `a:docs` label. Frame the description around *what the agent gains* (or doesn't lose), not what content moved where.
