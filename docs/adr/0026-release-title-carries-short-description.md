# ADR 0026 — Release title carries a short description (`vYYYY.MM.N - <short description>`)

- **Status:** Accepted
- **Date:** 2026-07-18
- **Amends:** [ADR 0023](0023-monthly-sequential-release-tags.md) — only the *release-title* clause of § *Decision* ("GitHub tags + release titles + CHANGELOG.md headers become `vYYYY.MM.N`"). The **tag** and the **CHANGELOG.md header** stay bare `vYYYY.MM.N`; only the GitHub release **title** (the release name, which GitHub keeps separate from the tag) gains a trailing description. The rest of ADR 0023 — the monthly counter, the milestone-at-creation linkage, the forward-only frontier — stands.

## Context

ADR 0023 collapsed the release title into the bare version string `vYYYY.MM.N`, matching the tag
and CHANGELOG header 1:1. That was the right call for the *tag* (it names the milestone, so it must
be knowable up front and stable) but it also stripped the title of a signal the project used to
carry: a short verbal description of what the release is about.

The app had that signal and dropped it. SemVer-era releases titled themselves
`v2.0.0 - Finally shipped!`, `v2.1.0 - Open testing launch!`, `v2.2.0 - Bring in any audio` — the
version plus a warm one-liner. It was abandoned at `v2.3.0` and the first CalVer cut
`v2026.07.1`, which read as the bare version with no hook. A releases list of bare version strings
tells a browsing reader nothing; the one-liner is the difference between a changelog and an
invitation.

This lands alongside the What's New voice work (§ *Copy guide → Store "What's New" voice*): the
store note, the GitHub release body, and now the release title all speak in the same warm,
first-person-adjacent voice. The title is the most-scanned surface of the three.

## Decision

- **The GitHub release *title* (name) becomes `vYYYY.MM.N - <short description>`** — the same version
  anchor, an em-dash-free ` - ` separator, then a warm one-line hook in the What's New voice
  (content-forward: say *what* shipped; e.g. `v2026.07.1 - Your Vault comes with you`).
- **The tag and the CHANGELOG.md header stay bare `vYYYY.MM.N`.** The tag is the git identifier and
  the milestone name (ADR 0023's linkage); it must not carry prose. In `gh release create <tag>
  --title "<name>"` the first arg (tag) is bare and only `--title` carries the description.
- **The short description derives from the release's hero / the What's New hook** — warm and
  informative, never technical. Authoring rules live in CONTRIBUTING.md § *Copy guide*.
- **English**, per the repo writing-language rule for release artifacts.

## Trade-off accepted

This partially reverses ADR 0023's "title == bare version" simplification. What the description buys
— a browsable releases list that reads as a story, consistent with the What's New voice — outweighs
the cost of a second, prose-bearing form of the title, because the version anchor is still present
and unchanged, the tag/header/milestone linkage is untouched, and the description lives only on the
cosmetic name where drift has no downstream effect.

## Enforcement

Same as ADR 0023/0025: `CONTRIBUTING.md` § *Versioning* and § *Creating the GitHub release* are
canonical, human-checked at cut via the pre-release checklist. No grep guard — the scheme is
low-frequency and the title is not machine-parsed.

## Revisit criteria

- If release cutting becomes fully automated with no human in the loop to write a hook, reconsider
  deriving the description mechanically from the CHANGELOG header or dropping it again.
- If a bare-version title is ever needed for tooling that parses the release name, move the
  description to the body and revert the title to bare `vYYYY.MM.N`.
