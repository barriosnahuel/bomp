# Performance investigations

Where the conclusions of performance investigations live — most often triaging a weekly Firebase
Performance report alarm (a version-over-version cold-start or frozen/slow-frame "regression"). The
reusable **method** for running one is the `/perf-report-triage` skill
(`.claude/skills/perf-report-triage/`).

## Where records go

This repo (`barriosnahuel/bomp`) is **public**. Field data identifies devices (`device_name`) and
precise timestamps — pseudonymous, but on a small user base a single device on a given day can be
linkable. So investigation records stay private by default:

- **Private (the record):** diagnosis, raw per-device rows, models, dates, and SQL outputs go in
  `../push-me-backlog/performance-investigations/` (a private, Drive-synced sibling — not committed
  here).
- **Public (what ships):** only the fix — its PR, its CHANGELOG entry, and an ADR when the fix carries
  a decision that needs one. No investigation record is added to this directory.

## Index

Historical public record, kept from before the private-by-default rule:

- [0001 — the 2.2.0 "startup regression" was a sampling artifact](0001-2.2.0-startup-false-alarm.md)
