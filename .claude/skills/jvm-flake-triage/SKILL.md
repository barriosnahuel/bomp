---
name: jvm-flake-triage
description: Triage a flaky JVM unit test (./gradlew test / :app:testDebugUnitTest) before believing the test name in the red. The dominant failure family here reports against an INNOCENT test, so the console summary names the victim and never the culprit. Use when a JVM test fails intermittently, when a red does not reproduce locally, when CI is redder than your machine, or on any "MockKException: can't find stub Tracker", "UncaughtExceptionsBeforeTest", or "Default FirebaseApp is not initialized" in unit tests.
---

# jvm-flake-triage

Codified procedure for a flaky **JVM** test in this repo (Robolectric + Compose UI Test, no emulator).
For emulator flakes go to CONTRIBUTING.md § *Local UI test suite → Is the red the emulator, or a real
bug?* and `scripts/flaky-report.sh` instead — that history covers instrumented runs only, and is of no
use here.

The reason this needs a procedure: the dominant family **misattributes the failure by construction**.
Work started by test A outlives it, fails, and reports through `Tracker` after A's teardown undid the
global mock; the escaping exception lands in the coroutines exception collector and is raised against
whichever Compose test drains it next. Reading the summary line and "fixing" the named test is the
default mistake, and it costs whole afternoons.

## 1. Read the stack, never the summary line

```bash
grep -l "<failure\|<error" app/build/test-results/testDebugUnitTest/*.xml
```

Open the XML and read the **full trace**. The frame under `Tracker.log` / `Tracker.track` is the real
culprit; the class in the filename is the victim. The console summary and the HTML report both name
the victim, so do not start from either.

If a whole Gradle worker died instead of a test failing, there may be no useful XML — treat that as
the same family and go to step 2 anyway.

## 2. Decide whether it is this family, before touching anything

It is this family when the trace shows any of:

- `MockKException: can't find stub Tracker(object Tracker)`
- `IllegalStateException: Default FirebaseApp is not initialized` from a unit test
- `UncaughtExceptionsBeforeTest`, especially with unrelated suppressed exceptions
- a red on a Compose test that passes in isolation and only fails in the full suite

It is **not** this family — do not apply this procedure — when the test awaits a value folded from
several flows and asserts too early, or when an unbounded `runBlocking { … .first() }` hangs. Those
are synchronisation races with their own fix in CONTRIBUTING.md § *Awaiting multiple async inputs*,
and `SoundsViewModelSearchTest` / `SoundsViewModelVisibilityTest` are past examples of them, already
addressed.

## 3. Find the source: what did the test start that nobody joins

Look for work reaching a real dispatcher rather than the test clock — typically a `LaunchedEffect` in
a mounted host calling a suspend function whose dispatcher defaults to `Dispatchers.IO`. Known sources
are catalogued in CONTRIBUTING.md § *Work that outlives a test*; `scripts/check-outliving-work.sh`
carries the list of hosts as code.

## 4. Prove the timing instead of sampling it

Do **not** try to establish anything by looping the suite. Measured on this repo: six back-to-back
full-suite runs were green both before and after a real fix, because a fast, idle machine lets the
late work land in time. The loop cannot distinguish "fixed" from "never reproduced here".

Write a throwaway probe instead, and delete it after. Set a flag at the end of the `@Test`, record in
the `Tracker` stub whether the call arrived before or after it, and print the verdict from the
subclass `@After` — which JUnit runs **before** the base class un-mocks `Tracker`:

```kotlin
private val bodyDone = AtomicBoolean(false)
private val verdict = AtomicReference("never arrived")

// in @Before, overriding the base's neutral stub:
every { Tracker.log(any()) } answers {
    if (firstArg<String>().startsWith("<module>.")) {
        verdict.set(if (bodyDone.get()) "AFTER the body — this is the bug" else "during the body")
    }
    nothing
}
```

"AFTER the body" is the proof. One run, seconds, and it answers the causal question that hundreds of
suite runs would only approximate.

## 5. Reproduce CI's scheduling when the probe says the work lands in time

A fast machine can make the work land safely even when CI would not. Starve the IO dispatcher so it
queues instead:

```bash
./gradlew :app:testDebugUnitTest --tests "*TheSuspectTest*" -PstarveTests=1 --rerun-tasks
```

Three things to know, all measured:

- **`--rerun-tasks` is mandatory.** Without it the task stays `UP-TO-DATE` and the flag does nothing,
  silently — you would conclude "does not reproduce" from a run that never happened.
- **Shrinking the pool alone changes nothing.** One IO thread is plenty when nobody competes for it.
  Starvation needs *contention*: occupy the dispatcher from the test
  (`repeat(4) { CoroutineScope(Dispatchers.IO).launch { Thread.sleep(4_000) } }`) so the host's work
  queues behind it. Pool of one **plus** an occupant is what flips the verdict to "AFTER the body".
- **Aim it at one test, never the suite.** Over the full suite it produces its own timeouts —
  `AddButtonScreen` tests whose preview metadata read serialises behind the single slot — and drowns
  the signal. It only applies to `:app`; the other modules have no such block.

## 6. Fix by stopping the work, not by re-mocking Tracker

Stub the source in `@Before` so the work never starts:

```kotlin
mockkObject(WaveformExtractor)
coEvery { WaveformExtractor.extract(any(), any<Sound>(), any(), any()) } returns null
every { WaveformExtractor.cached(any()) } returns null
```

Where the work belongs to the screen itself and has no single object to stub, use the other shape:
take the screen out of the composition from the subclass `@After` (`disposeAddButtonScreen`), which
runs before the base un-mocks `Tracker`.

Substituting the analytics tracker is **not** a fix for this: `AnalyticsTrackerProvider` wraps Firebase
Analytics, while this mechanism runs through `Tracker` (Crashlytics). Add the fake when the host also
logs a screen view — a separate, real problem — but never as the mitigation here.

## 7. Close the loop so the next one is caught for free

If the source was a host not yet known to the guard, add it to `HOSTS` in
`scripts/check-outliving-work.sh`. That is what keeps the family from coming back: the guard runs on
every push (pre-push hook) and every PR (CircleCI `outliving-work-guard`), in seconds.

Then verify the guard actually bites — remove the stub you just added and confirm it fails, then
restore. A guard that silently matches nothing is worse than no guard.
