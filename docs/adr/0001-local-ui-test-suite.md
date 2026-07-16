# ADR 0001 — Local-only instrumented UI test suite

- **Status:** Accepted
- **Date:** 2026-04-26
- **Supersedes:** —

## Context

The off-device test suite (`src/test/`, Robolectric + Compose UI Test + JUnit4) covers
ViewModels, composition logic, share intents at the unit level, theme contrast, and
smoke-renders for every Activity and full-screen Composable. CircleCI runs `./gradlew test`
on every PR and that catches the bulk of regressions cheaply.

What it does **not** cover:

- Real `MediaPlayer` / `SoundPool` playback (Robolectric shadows don't drive the audio
  pipeline).
- Real swipe gestures with haptic feedback (`HapticFeedbackConstants.CONFIRM/REJECT`,
  API 30+).
- The system Share chooser dialog (cross-process UI, only visible from
  outside the app's window).
- The Web browser launched by the About screen's "Source code" button.
- Deep-link entry points (`push-me://open/home`, `/explore`) including the fallback when
  no bundled audios exist.
- Inter-Activity navigation that crosses process boundaries (e.g. `AddButtonActivity`
  invoked via an external `ACTION_SEND`).
- WCAG checks driven by the live accessibility tree.

Manual QA filled this gap. That's slow and skipped under deadline pressure.

## Decision drivers

1. **Do not slow CI.** `connectedAndroidTest` needs an emulator and a few minutes per run;
   CircleCI today is `compile → detekt/ktlint/spotless/lint → test → bundle` and is fast
   enough to keep contributor friction low.
2. **One command, no manual steps.** A developer with the SDK CLI installed should be able
   to run the entire suite with one Gradle invocation.
3. **Deterministic.** No flakiness on real audio files (we don't depend on the user's
   `model/src/debug/res/raw/` being populated); the suite ships its own ~200ms silent MP3.
4. **Accessibility is part of the feature, not a bolt-on.** Every screen test should
   exercise its own a11y assertions, not be deferred to a separate sweep that's easy to
   skip.

## Considered options

### Option A — Roborazzi (off-device screenshot tests)
Snapshot diffs of every screen, no emulator required. Fast to run.
- ✗ Doesn't drive `MediaPlayer`/`SoundPool` or system intents.
- ✗ Golden image management is high-friction (binary blobs in PRs, locale/theme drift).
- ✗ Asserts pixels, not behavior. Swipe-to-pin or undo-snackbar interactions can't be
  expressed.

### Option B — Maestro / Appium scripted UI flows
External tool, drives the emulator via accessibility services.
- ✗ YAML/Groovy DSL outside the Kotlin/Gradle workflow — context switch.
- ✗ No type-safe access to the app's source (semantics, content descriptions are strings).
- ✗ Adds a second CLI/binary contributors must install.

### Option C — Real TalkBack scripted via UI Automator
Closest to "validated by a screen reader user".
- ✗ Notoriously brittle: TalkBack focus order can change between Android releases.
- ✗ Requires per-API tweaking; high maintenance for low payoff.
- ✗ Doesn't catch contrast or touch-target failures by itself.

### Option D — Native Compose UI Test + Espresso + UI Automator + ATF (chosen)
- ✓ Built into the Android tooling we already use.
- ✓ Type-safe Kotlin; tests live next to the code under test.
- ✓ `AccessibilityChecks.enable()` from `espresso-accessibility` runs Google's
  Accessibility Test Framework on every Espresso/UI Automator interaction (contrast, touch
  target, content description, duplicate labels).
- ✓ Compose UI Test exposes `assertContentDescriptionEquals`, `assertHasClickAction`,
  semantics matchers — used per-screen for the things ATF can't see (Compose-only
  interactions don't currently auto-trigger ATF).
- ✓ UI Automator handles cross-process UI (Share chooser dialog).
- ✓ Espresso Intents stubs the browser launch and the `ACTION_SEND` outbound chooser, so
  no real Chrome / no real "Send via" dialog blocks the suite.

## Decision

Adopt **Option D**, isolated under `app/src/androidTest/`:

- Gradle source set already excluded from `./gradlew test` and `./gradlew check`.
- Not invoked by `.circleci/config.yml` (the `test` job runs `./gradlew test` only).
- A single command runs the suite: `./scripts/run-instrumented-tests.sh` (wraps
  `./gradlew :app:connectedDebugAndroidTest`).
- Operator workflow: run `./scripts/setup-test-emulator.sh` once to create the AVD, then
  `./scripts/run-instrumented-tests.sh` for each pass. The wrapper cold-boots the AVD with
  wiped userdata before every run — see *Cold boot per run* below — and pins the emulator
  serial so the run never spills onto a physical device that happens to be attached.
- Each screen test file owns its accessibility assertions. There is no
  `AccessibilitySweepTest` collector — a11y is part of the feature, not a separate concern.

### Cold boot per run

The suite must run on a freshly cold-booted emulator, not a warm one. A long-lived AVD
degrades across back-to-back runs: `system_server`'s `InputManagerService` watchdog
eventually ANRs, `Choreographer` skips hundreds of frames, and the instrumentation loses
contact with the app process. The symptoms are misleading — they look like per-test flakes
(`ComposeTimeoutException`, `ComposeNotIdleException`) or escalate to `Process crashed` and
the run failing to start at all — but the root cause is accumulated emulator state, not the
tests or the app (a single run from a clean cold boot finishes ~5× faster and green; the
same tests pass on physical devices). `./scripts/run-instrumented-tests.sh` enforces this:
it kills the AVD, relaunches it with `-no-snapshot -wipe-data`, waits for boot, then runs
the suite — repeating the cold boot before each pass when `RUNS` > 1. Do **not** invoke
`./gradlew :app:connectedDebugAndroidTest` directly against an already-running emulator.

The cold boot also passes `-gpu host -cores 6 -memory 4096`, overriding the AVD's
`hardware-qemu.ini` so the guest renders on the host GPU (MoltenVK/Vulkan on Apple Silicon)
instead of in software and stops starving on too few cores/RAM — the standard AVD defaults
(software GPU, ~2-4 cores, 2 GB) are a primary contributor to the very watchdog ANRs and
frozen frames this cold boot fights. All three are env-overridable (`EMULATOR_GPU`,
`EMULATOR_CORES`, `EMULATOR_MEMORY`); fall back to `EMULATOR_GPU=auto` if `host` misbehaves on
a given machine. `-no-audio` is deliberately *not* added: this is a soundboard whose suite
exercises playback, so the guest keeps its audio device.

The wrapper resets *guest* (emulator) state — it cannot reset *host* state. The emulator is
a VM, and a saturated host (high load average from other heavy work, or from running the
suite dozens of times back-to-back) makes even a freshly booted guest janky enough to flake
timing-sensitive tests. If a cold-booted run is still slow or flaky, check the host load
before suspecting a test: close other heavy work and re-run, rather than chasing a
non-existent test bug.

### Bounded termination — the stall watchdog

The *Cold boot per run* note above ends with "or the run failing to start at all". In its
worst form that was literal: past roughly the hundredth test, the emulator would wedge and
the run would **never end**. Not slowly — never. Hours later someone would notice by hand.

Nothing in the stack was in a position to notice on its own:

- **ddmlib** (under AGP) waits on the instrumentation's output with `maxTimeToOutputResponse`,
  whose default is *wait indefinitely*. A frozen emulator whose adb socket stays open sends
  neither data nor EOF, so ddmlib blocks forever — no exception, no timeout.
- **Gradle** waits on ddmlib. **The wrapper** waited on Gradle. Each layer faithfully waited
  on the one below, and the bottom one never came back.
- **Nobody printed anything.** `connectedDebugAndroidTest` emits no per-test output, so a
  healthy run and a hung one produced the same thing from the outside: silence. There was no
  signal to miss, so neither a human nor an agent could tell the two apart — which is why the
  hang could go unnoticed for hours rather than minutes.

The wrapper therefore guarantees **bounded termination**: every run ends, and announces which
kind of ending it was. Three pieces:

1. **A progress heartbeat.** `AndroidJUnitRunner` narrates itself to logcat under the
   `TestRunner` tag (`run started: N tests`, then `started:` / `finished:` / `failed:` per
   test). That stream is the only per-test progress signal that exists, and the wrapper
   parses it into a running `N/total` counter — on stdout for whoever is watching, and to a
   heartbeat file for the watchdog and the run history. The suite size comes from the runner
   itself rather than being hardcoded, so it can't drift.
2. **Two stall clocks, because a run has two regimes.** This is the part that is easy to get
   wrong, and getting it wrong is worse than the original bug.
   - *Build phase* (`BUILD_STALL_TIMEOUT_SECONDS`, default 900 s): Gradle configures,
     compiles, dexes and installs two APKs. Its output is not a tty, so there is no progress
     bar — a cold daemon, a cold Kotlin/Compose compile, or dependency resolution on a slow
     network are each **legitimately silent for minutes**. No TestRunner line exists yet, by
     definition.
   - *Test phase* (`STALL_TIMEOUT_SECONDS`, default 360 s): from `run started:` onward, the
     runner narrates every test, so silence is meaningful — the guest really has stopped
     executing.

   A single clock tight enough for the test phase kills healthy builds, and it **cascades**:
   killing a run leaves the next one with a cold daemon, whose slow silent build trips the
   same clock — one real stall manufacturing a suite of fake ones. `HARD_CAP_SECONDS`
   (default 2700 s) backstops the whole run.
3. **A distinguishable exit code.** `124` (the `timeout(1)` convention) means the emulator
   hung; `3` means the build or install never reached the tests (compile error,
   `INSTALL_FAILED`, no device — there is no test report to read); `2` means the emulator
   never booted. **Only `1` means a test is genuinely red.** Conflating any of these with `1`
   is the expensive mistake — it sends you debugging a test that never actually ran.

Before killing anything, the wrapper dumps a diagnostics bundle (heartbeat, TestRunner
capture, Gradle log, emulator log, full logcat) and probes the device with a bounded
`adb shell`. That probe answers the one question the outside world cannot otherwise ask:
**did the emulator freeze, or is the device fine and Gradle/ddmlib the stuck party?** Those
are different bugs with different fixes.

#### The hang was the host sleeping, not the emulator

Building the watchdog is what finally identified the original bug, and it was **not an
emulator bug at all**. The wrapper's first stalls fired with `idle ≈ 902s` — and `pmset -g log`
showed the machine had entered `Idle Sleep` for exactly 900 seconds in that window. The
supervision loop, which polls every 5 s, had ticked **once**: it was suspended along with
everything else. The liveness probe agreed — the emulator answered `adb shell` perfectly the
moment it woke.

A full suite takes longer than macOS's default idle-sleep timer, so **starting a run and
walking away is precisely the situation that suspends the host mid-run.** The guest freezes
with it, the instrumentation connection does not survive the wake, and the run hangs forever.
From the outside it is indistinguishable from a wedged emulator — which is why it was
misdiagnosed as one for so long, and why "it dies somewhere past test 100" was really "it dies
about fifteen minutes in, whatever test that happens to be".

Two mitigations, in the order that matters:

1. **`caffeinate -i` for the lifetime of the run** — hold off idle sleep, so the failure does
   not happen. With it, the same suite that stalled twice runs 134/134 green. Armed before the
   first cold boot, not just around Gradle, so run 1's boot is covered too — but `-i` only stops
   *idle* sleep, so a lid close still suspends, which is why mitigation 2 is not redundant.
2. **The watchdog discounts a host sleep instead of charging it to the emulator.** It cannot
   observe the suspend directly, but it can see its shadow: a loop that sleeps 5 s and finds
   900 s of wall clock gone did not stall — it was not *running*. That gap is subtracted from
   **both** the idle budget and the hard cap (charging it to either would kill a healthy run on
   wake), and the device is re-armed with a fresh budget, so if the emulator genuinely did not
   survive the wake it is still caught — but the diagnosis names the host.

This is the whole point of the exit-code contract stated in the negative: a watchdog that
confidently blames the wrong component is worse than one that says nothing.

**Calibrating the clocks is the whole risk.** A stall timeout below the slowest legitimate
test converts slowness into a false stall, and a watchdog that kills healthy runs is strictly
worse than the hang it replaces. So the behaviour test (`scripts/test-run-instrumented-tests.sh`,
CI job `instrumented-watchdog-test`) asserts the *healthy* paths at least as hard as the stall
paths — a clean pass, honest test failures, and a build that stays silent for longer than the
test-phase clock must all survive untouched. It also drives the failure modes of the watchdog's
own machinery: a Gradle that must be killed for real rather than orphaned behind a subshell, and
a logcat stream that dies mid-run (the adb server is machine-wide — an IDE starting up or a
flaky USB device restarts it, freezing our capture while the emulator runs on perfectly well;
that must be recovered from, never blamed on the device).

Reference points from a measured healthy run on the reference machine: 134 tests in ~4 min of
test phase, with the slowest single test at 6 s — two orders of magnitude under the 360 s
test-phase clock.

AGP's own timeouts are kept as defence in depth for anyone bypassing the wrapper, with their
scope stated precisely rather than generously:

- `installation.timeOutInMs` bounds `installDebug` and ddmlib's device-detection shell
  commands. It is **not** confirmed to bound the APK install on the UTP connected-test path,
  which uses a separate `installApkTimeout` knob.
- The runner's `timeout_msec` bounds a single test deadlocking *on-device*: AndroidJUnitRunner
  kills it and moves on. Note the consequence — the test is then reported as **failed**
  (exit 1), not as a stall. That is the right call (a test that deadlocks the app is a real
  bug, not a wedged emulator), but it means `timeout_msec` must stay *below*
  `STALL_TIMEOUT_SECONDS`: raise it above and the watchdog starts killing runs the runner was
  about to handle by itself.

Neither can save a frozen emulator: a wedged guest cannot run the code that would enforce its
own timeout. That is precisely why the watchdog lives *outside* the device — and why every adb
call it makes once the device is suspect is itself bounded, in pure bash rather than with
`timeout(1)`, which is GNU coreutils and absent on the macOS hosts this suite runs on.

### Not every red is the emulator — deterministic vs. degradation reds

The *Cold boot per run* note above explains the reds that **are** the emulator. There is a
second class that looks similar but is the opposite — a real code/data bug — and must not be
re-run away. Triage by determinism and timing before blaming the AVD:

- **Degradation reds** (emulator): `ComposeTimeoutException`, `ComposeNotIdleException`,
  `Process crashed`. Non-deterministic, vary run to run, often on a slow run; a clean cold
  boot makes them pass. Re-running is the right move.
- **Deterministic reds** (code/data): `Failed to inject touch input. Reason: Expected
  exactly '1' node but found '2'` and `assertCountEquals` count mismatches. These fail
  **instantly and identically every run**, on a fresh cold boot too. The `inject touch`
  wording is misleading: Compose resolves the single target node *before* sending any input,
  so the assertion throws in the test process and the emulator's input pipeline is never
  invoked. The cause is matcher ambiguity — two real nodes in the semantics tree (read the
  node dump: two distinct list rows) — which means a **test-isolation / seeding bug**, not
  the AVD. Re-running never helps; fix the data the test renders.

A worked instance: a one-time persistence migration in `SoundsViewModel.init` re-armed by
the test fixture resurrected a second MY_SOUNDS row, turning every "exactly one play/share
button" assertion into a `found '2' nodes` red that read, wrongly, as "the emulator can't
inject gestures."

### History note: dynamic feature workaround (removed)

The `:feature_addbutton` module used to be a dynamic feature with
`<dist:install-time />`. AGP/UTP only installed `app-debug.apk` (base, no
feature splits) before the connected test run, so `AddButtonActivity` tests
crashed with `ClassNotFoundException`. The first iteration of this suite
worked around it with `scripts/run-ui-tests.sh`: built the bundle, fused base +
feature into a universal APK with bundletool, and ran `am instrument` directly
to bypass UTP's reinstall.

That whole workaround was deleted when `:feature_addbutton` was promoted into
`:app` (creating buttons is core to the product, not a freemium add-on). The
suite is now the standard `:app:connectedDebugAndroidTest` Gradle task with the
standard HTML/XML report — `./scripts/run-instrumented-tests.sh` only wraps it
with emulator lifecycle management (see *Cold boot per run*), it does not touch
the build or install path. Reintroduce a bundletool-style workaround only if a
dynamic feature returns for genuinely on-demand functionality.

## Consequences

### Positive

- Functional regressions in audio playback, swipes, intents, and deep links are caught
  automatically before push.
- A11y regressions surface during normal test runs, not at release time.
- `CLAUDE.md` is updated so AI-driven contributions know to invoke this suite when a
  change touches functional behavior (not just CHANGELOG/copy/docs).

### Negative / tradeoffs

- Requires an emulator to run the suite. Developers without one need to run
  `./scripts/setup-test-emulator.sh` once (~5 minutes including system image download).
- Each run pays a cold-boot cost (~30–60 s) because the wrapper wipes userdata and reboots
  the AVD. This is deliberate: a warm run is faster to *start* but unreliable to *finish*
  (see *Cold boot per run*), and a clean run completes the whole suite in ~3 min anyway.
- Compose-driven interactions don't auto-trigger ATF — explicit semantics assertions are
  required per screen test. This is documented in `AbstractUiTest`'s KDoc and enforced by
  PR review (the per-screen test is supposed to include a11y bullets in the test plan).
- Adds ~6 androidTest dependencies (Espresso, Espresso Intents, Espresso Accessibility,
  UI Automator, AndroidX test runner/rules) and one ~1KB MP3 asset.

### Out of scope

- Pixel-level visual regression (Roborazzi/Paparazzi) — can be added later as a
  complement without disturbing this setup.
- Performance testing (frame timing, jank) — not in scope.
- Full TalkBack-scripted runs — explicitly rejected in Option C above.

## Invariants

Enforced by `scripts/check-adr-invariants.sh` (CircleCI job `adr-invariants`):

- `connected(Debug)?AndroidTest` must NOT appear in `.circleci/config.yml`. The instrumented suite is intentionally local-only; if you wire it into CI, supersede this ADR or revert.
