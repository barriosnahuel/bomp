# Performance investigations

Durable records of performance investigations — most often triaging a weekly Firebase Performance
report alarm (a version-over-version cold-start or frozen/slow-frame "regression"). The reusable
**method** for running one is the `/perf-report-triage` skill (`.claude/skills/perf-report-triage/`);
this directory is where each **conclusion** lands so the next investigator (or future-you, three
reports from now) doesn't re-derive it.

## What goes here vs. stays private

This repo (`barriosnahuel/bomp`) is **public**. Field data identifies devices (`device_name`) and
precise timestamps — pseudonymous, but on a small user base a single device on a given day can be
linkable. So:

- **Here (public, anonymized):** the conclusion, the method used, and aggregate numbers. Refer to
  device **tiers** (flagship / mid / low-tier), never model names; no fine-grained dates.
- **Private (full fidelity):** raw per-device rows (models + dates + the SQL outputs) live in
  `../push-me-backlog/performance-investigations/` (a private, Drive-synced sibling — not committed
  here). Each public record links to its private counterpart by filename.

## Naming

`NNNN-<slug>.md`, zero-padded, monotonic (`0001-…`, `0002-…`). The number is identity, not priority.

## Index

- [0001 — the 2.2.0 "startup regression" was a sampling artifact](0001-2.2.0-startup-false-alarm.md)
