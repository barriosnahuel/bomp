# ADR 0028 — Audio trim in the add flow: Media3 Transformer, fallback to the original

- **Status:** Accepted
- **Date:** 2026-08-16
- **Supersedes:** —
- **Relates to:** [ADR 0022](0022-hybrid-playback-engines-media3.md) (which routed "future: trimmer
  (spec 009)" to Media3 and assigned the add-flow preview to `MediaPlayer`),
  [ADR 0020](0020-shared-envelope-waveform.md) (its revisit criterion — "a third surface needs a
  materially different envelope" — fires here), [ADR 0019](0019-in-app-bomp-recorder.md) (the
  `cacheDir` temp-clip + FileProvider handoff precedent this reuses).

## Context

An audio shared in from WhatsApp/Telegram is routinely minutes long. A Bomp is a sticker: the useful
part is a couple of seconds inside it. Until now the only options were "save the whole thing" or
"go trim it in another app and come back" — which is the same coupling to a second app that
ADR 0019 removed for capture, still standing for import.

The backlog spec (`009-audio-edition-trimmer.md`) designs a full-screen precision editor with
pinch-zoom, auto-scroll, and an edit-existing fork ("replace original" / "save as copy"). This ADR
covers a deliberately narrower slice: **trim at creation time only, before the audio is saved.**
That is the slice that unblocks the long-import problem; editing an already-saved Bomp is a
different decision (it mutates persisted content and needs the replace-vs-copy fork) and is not
taken here.

## Decision drivers

1. **Never lose the user's audio.** A trim is an enhancement on top of a save flow that works today.
   No codec, OEM, or muxer failure may cost the user the audio they were saving.
2. **AEP compliance.** `MediaExtractor` and `MediaMuxer` — the classic remux pair — are on the
   Apps Experience Program prohibited list (ADR 0022). A new cut engine may not call them from our
   code. What Media3 does inside its own muxer is Media3's business, exactly as ADR 0022 already
   accepted when it swapped our `MediaExtractor` call-sites for `MediaExtractorCompat`.
3. **Don't touch the shipped playback core.** `PlayerControllerImpl` is the tap-latency-critical,
   most regression-sensitive file in the app.
4. **Reuse the add flow, don't rebuild it.** Naming, validation, tagging, persistence and the
   duplicate-name hint all already live in `AddButtonScreen` / `saveNewButtonAsync`.

## Decision

### D1 — Cut engine: Media3 `Transformer` with a `ClippingConfiguration`

`androidx.media3:media3-transformer` (already on the same `media3` version catalog entry) exports the
selected range to a new file. Transformer transmuxes when the input codec already matches the output
and transcodes when it doesn't, so **one code path covers the whole input matrix** (MP3, M4A/AAC,
OPUS/OGG, WAV) with no per-format branching and no `minSdk` 23 muxer gap — which is precisely what
the spec's § 8.4 matrix existed to work around, and what ADR 0022 meant by "born on the modern base".
`media3-effect` and `media3-muxer` arrive transitively; nothing else is added to the graph.

Output is always **AAC in an MP4 container, `.m4a`** (`setAudioMimeType(MimeTypes.AUDIO_AAC)`).
That is the one container Transformer's default muxer writes, and it is the extension the save
pipeline already resolves (`AUDIO_EXT_BY_MIME["audio/mp4"] = "m4a"`, in production since the
recorder). Cut precision therefore quantizes to the encoder frame (~25 ms), which is inaudible in a
2-second sticker.

### D2 — Failure mode: `TrimOutcome.FallbackToOriginal`, never an error

Exactly two outcomes, deliberately not a wider sealed hierarchy: `Trimmed(uri)` and
`FallbackToOriginal(reason)`. Any export failure — unsupported codec, OEM `MediaCodec` bug, no disk
space, timeout — resolves to the **original, untrimmed URI** flowing into the unchanged
`saveNewButtonAsync`, plus a `Tracker.track` non-fatal carrying the reason, plus the user being told
their audio was saved whole — on the **success overlay's subtitle and its screen-reader announcement**,
not a snackbar: the overlay is the last thing on screen before the flow closes, so a snackbar racing it
would be missed by design. The user never lands on a dead end and never loses
the audio (driver 1). The real per-codec failure matrix then becomes observable in BigQuery instead
of guessed at up front.

### D3 — Range preview: the add flow's existing `MediaPlayer`, not `ClippingMediaSource`

The spec proposed Media3 `ClippingMediaSource` for a jitter-free preview loop. We use
`PlayerController.startPlayingUri(uri, startPositionMs)` — the `MediaPlayer` engine ADR 0022 already
assigns to *"add/edit preview — quick verification taps in the add flow"* — and stop at the range end
by watching the controller's existing ~100 ms progress `StateFlow`.

This is not a downgrade dressed up as pragmatism: the preview is a *check what I selected* tap, so
ADR 0022's latency argument applies to it exactly as it does to `AudioPreview` right beside it; the
≤ 100 ms overshoot at the range end is a preview artifact only — the **exported cut is frame-exact,
because Transformer, not the poller, does the cutting** (D1). And it keeps driver 3 intact: the
trimmer adds no new playback engine, no new session, and not one line inside `PlayerControllerImpl`.

### D4 — Where it lives: inline in the add screen, Create mode only

A collapsed "Trim this audio" text button sits under the existing `AudioPreview`; opening it reveals
the waveform editor in place. Not a new full-screen destination: the add flow has **two hosts** —
the share-sheet `AddButtonActivity` trampoline and the Nav3 `NameSoundDestination` (ADR 0024 D4) — so
a separate screen would need a route in one host and an Activity-local swap in the other, two
implementations of one editor. Inline keeps a single one, and keeps the trim decision visible next to
the audio it applies to.

**Create mode only.** In Edit mode the audio is already persisted and re-cutting it is the spec's
replace-vs-copy fork — an unmade product decision, not a gap to paper over. `AddButtonMode.Edit`
renders no trim affordance at all.

### D5 — Selection model: clamped range, no invalid state

`TrimSelection` holds start/end **fractions** of the clip and clamps every drag so the range can
never fall below `MIN_TRIM_MS` (1 s, matching the recorder's floor in ADR 0019). The spec's §7 asked
for a disabled Save with an error when both handles meet; a clamp is strictly better — the invalid
state is unreachable, so there is no error to explain and no disabled primary action to recover
from. Pure Kotlin, no Android types, so every transition is JVM-unit-tested.

### D6 — Waveform: reuse the extractor, not the renderer

`WaveformExtractor.extract(context, uri, barCount)` — the URI overload the recorder review already
uses — supplies the envelope, and `WAVEFORM_MIN_BAR` / `normalizeEnvelope` stay single-sourced
(ADR 0020's grep guard). The **renderer** is a separate `TrimWaveform`: ADR 0020's revisit criterion
is "a third surface needs a materially different envelope", and a two-handle range selector is that —
its selected span, its dimmed outside regions and its two draggable thumbs are not expressible as
parameters on a one-progress-boundary component without turning `EnvelopeWaveform` into a flag bag,
which is the outcome that ADR explicitly guards against.

### D7 — Temp file lifecycle: `cacheDir/trims/`, OS-evictable

The exported cut lands in `cacheDir/trims/` and is handed to the save pipeline as a FileProvider
`content://` URI — byte-for-byte the recorder's handoff (ADR 0019), so it clears the same inbound
validator with no fork. The directory is purged on each new export, and the file is dead weight the
moment `saveNewButtonAsync` has copied it into `Music/`. No draft persistence: unlike a recording,
the source audio is not transient — backing out and re-sharing reproduces it exactly.

## Options considered (and rejected)

- **`MediaExtractor` + `MediaMuxer` remux matrix** (the spec's § 8.4) — cheapest cut for AAC input,
  but both APIs are AEP-prohibited (driver 2), `MediaMuxer` cannot write MP3 or write OGG below
  API 29, and the workaround is a per-format branch tree with a transcode fallback anyway. Rejected:
  Transformer collapses the whole matrix into one call.
- **A waveform library (Amplituda / WaveformSeekBar)** — the spec's assumption that we had no
  decoder. We do: `WaveformExtractor` ships in production for the Vault and recorder review.
  Rejected as an APK-growing duplicate of working code.
- **Media3 `ClippingMediaSource` for the preview** — jitter-free, but adds a second engine to a
  latency-sensitive add flow that ADR 0022 already assigned to `MediaPlayer`, for a ≤ 100 ms
  overshoot the user hears in a preview and never in the saved file. Rejected (D3).
- **Full-screen editor with pinch-zoom + auto-scroll** (the spec's § 4) — better for millisecond
  precision on a long file, but doubles the host wiring (D4) and its precision exceeds what a
  frame-quantized AAC cut can honor. Deferred, not refused.
- **Trim on Edit as well, with a replace/copy fork** — the spec's Scenario B. Needs a product
  decision on what "replace" does to a Bomp already shared or tagged. Deferred (D4).

## Consequences

- **New runtime dependency** `androidx.media3:media3-transformer` — same version catalog ref as the
  three media3 artifacts already shipped; `app_third_party_notices.txt` covers it under the existing
  Media3 entry. APK growth must be re-measured at the next release-size pass.
- **Transformer needs a `Looper` thread** — it is built and started on `Dispatchers.Main` and its
  listener fires there; the encode itself runs on Transformer's own background threads. A
  `suspendCancellableCoroutine` bridges it, and cancellation calls `Transformer.cancel()` so a user
  who backs out mid-export doesn't leak an encoder.
- **The cut is not unit-testable end to end** — `MediaCodec` has no JVM/Robolectric implementation.
  Tests cover the selection algebra, the "is a cut even needed" gate, and the outcome mapping;
  the actual cut is verified on a device with real WhatsApp/Telegram audio.
- **Saving now has a variable-length step.** A multi-minute source transcodes for seconds, so the
  save button's existing `Loading` state covers the export too, and the export is bounded by a
  timeout that resolves to fallback rather than hanging.

## Invariants

- A trim failure **always** saves the original audio. There is no path where the user taps Save and
  ends with nothing.
- The trimmer adds no code to `PlayerControllerImpl` and no second playback engine to the add flow.
- Trim is offered in `AddButtonMode.Create` only; `Edit` is untouched.
- A selection can never be shorter than `MIN_TRIM_MS`, so Save is never disabled by the trimmer.
- The waveform floor and normalization stay single-sourced in `WaveformNormalization.kt` (ADR 0020).

## Revisit criteria

- **Edit-existing trim** — once the replace-vs-copy product decision is made, D4's Create-only
  restriction is the thing to lift.
- **Full-screen editor** — if fallback telemetry or user feedback shows people fighting the inline
  editor's precision on long files, revisit D4 with the spec's zoom + auto-scroll design.
- **Fallback rate** — if `trim_fallback` non-fatals concentrate on one codec, that codec earns a
  targeted path (or a pre-flight rejection) instead of a silent whole-file save.
- **Preview jitter** — if the ≤ 100 ms end overshoot reads as sloppy on device, D3 is the decision to
  reopen (a clipped Media3 session), not D1.

## Cross-references

- Backlog spec: `../push-me-backlog/backlog/009-audio-edition-trimmer.md` (the full-screen design and
  cost model this slice narrows).
- Media3 Transformer: https://developer.android.com/media/media3/transformer/getting-started
