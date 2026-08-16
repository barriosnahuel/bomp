# ADR 0027 — The Gradle daemon runs on JDK 21, pinned by Daemon JVM criteria

- **Status:** Accepted
- **Date:** 2026-08-16

## Context

Nothing in the repo stated which JDK builds this project. The modules declare `sourceCompatibility`/`targetCompatibility` `VERSION_21`, but that governs the *bytecode target*, not the JVM the daemon runs on — Gradle picked whatever `JAVA_HOME` or the launcher exposed.

That gap has a concrete cost, and it was paid: on a machine whose only JVM is Android Studio's bundled JBR (25.x today), `./gradlew detekt` cannot run at all. detekt 1.23.8 embeds Kotlin 2.0.21, and that compiler is capped below Java 25 in two independent places:

1. It rejects any `--jvm-target` above 22 — `Invalid value (25) passed to --jvm-target`. The `Detekt` task does not pick up the Kotlin compile tasks' target (AGP does propagate `compileOptions` to those), so the daemon's own version reaches the CLI invocation.
2. Forcing `jvmTarget = '21'` gets one step further and then dies in `JavaVersion.parse` with `IllegalArgumentException: 25.0.2`, before a single file is analysed. This is a **version ceiling, not a parsing quirk**: measured against the shaded `JavaVersion` in `kotlin-compiler-embeddable-2.0.21`, `"25"`, `"25.0.2"` and the JBR's `"25.0.2+-15348964-b329.117"` are all rejected, while `"24.0.1"` and `"21.0.12+8-LTS"` parse fine. No Java 25 install satisfies it, JBR or otherwise.

So no build-script setting fixes it on a 25 — the daemon itself has to run on a JVM detekt can host. The failure mode is also badly shaped for a contributor: the local guard silently gets skipped (`check -x detekt`) and detekt coverage quietly becomes CI-only, which is exactly the kind of erosion that is invisible until something slips through.

The file itself has a history: it was auto-generated into a PR by accident in April 2026 and dropped in `bef2edcc`, which gitignored the path and stated that adopting the daemon-JVM lock is *"a separate decision (its own PR with proper justification of why the team wants reproducible toolchain provisioning across contributors)"*. This ADR is that justification, so the guard entry is retired here alongside it.

## Decision drivers

1. **The constraint is external and versioned.** detekt's embedded compiler caps out below 25; that is a fact about the tool, not a preference, and it moves when detekt moves.
2. **A version is portable, a path is not.** Whatever fixes this must survive being committed — different machines put their JDKs in different places, and Linux CI shares nothing with a Mac's layout.
3. **The bytecode target does not change.** Compiled classes were already Java 21 (verified: `major version: 65`) even while the daemon ran on 25, so pinning the daemon aligns the toolchain with what the build already produced rather than altering output.
4. **A failure here should name itself.** An unpinned daemon fails deep inside a linter with a message about `--jvm-target`; a pinned one fails at startup saying it cannot find a Java 21 installation.

## Options considered

- **Upgrade detekt** so the ceiling moves instead of the JDK. The fix is upstream (Kotlin 2.1.20+), but reaching it means a detekt major that also migrates rules and config — a linter migration to solve an environment problem, and one that still leaves the JVM unpinned for everything else.
- **Run detekt on its own toolchain** (`javaLauncher`), leaving the daemon free. Narrower blast radius, but it fixes one task while every other tool keeps inheriting whatever JVM is around, and the repo still would not state which JDK builds it.
- **Pin the daemon** — chosen. One declaration covers detekt and everything else, and it is the piece that was actually missing.

## Decision

- **`gradle/gradle-daemon-jvm.properties` declares `toolchainVersion=21`** — Gradle's Daemon JVM criteria. It takes precedence over `JAVA_HOME` and `org.gradle.java.home` (verified locally: with both set, the criteria wins), so it holds regardless of what the shell or the IDE exposes.
- **21, not 17 nor newer.** 25 is out for the reasons above, and the modules already target 21, so a lower pin would run the build below what it declares.
- **No vendor is pinned.** Any Java 21 distribution satisfies the criteria, which is what keeps CI runners and contributor machines from needing a specific build.
- **`updateDaemonJvm` stays usable**, via `toolchainPlatforms = []` in the root `build.gradle`. Left at its default the task also resolves `toolchainUrl.*` entries and fails with *"Toolchain download repositories have not been configured"*; satisfying that would mean adding the foojay resolver, a plugin that resolves JDKs over the network at build time. Empty platforms keeps the task as the way to change the pin without taking on that dependency.
- **Auto-provisioning is deliberately absent.** A machine without a Java 21 installation gets a startup error naming the missing version, not a silent download.
- **Bytecode targets stay where they are.** `compileOptions` keeps `VERSION_21` per module; this ADR is about the JVM that *runs* the build.

## Enforcement

The properties file *is* the enforcement — Gradle refuses to start a non-conforming daemon, so there is nothing for a grep guard to check and no way to drift while still building. `CONTRIBUTING.md` § *Local setup* carries the human-facing half: the prerequisite, the symptom to recognise, and the Android Studio setting, since the IDE launches its own daemon and its bundled JBR runs several majors ahead.

## Revisit criteria

- **detekt gains a newer embedded compiler** (2.x, or a 1.23.x on Kotlin 2.1.20+) — the ceiling that forces 21 disappears, and the pin can follow the next LTS.
- **AGP or Gradle raise their minimum above 21** — the pin has to move with them; `VERSION_21` in `compileOptions` moves in the same change.
- **Contributors without a Java 21 install become common** — the trade-off flips, and the foojay resolver plus `toolchainUrl.*` entries buy auto-provisioning at the cost of that network dependency.
