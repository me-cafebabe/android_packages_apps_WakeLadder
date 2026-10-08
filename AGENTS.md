# AGENTS.md — Wake Ladder

Android alarm app that turns one "wake me at HH:MM" input into a **ladder** of
alarms (default 4 rungs, 20 min apart, ending at the target). Built for waking
up on days with too little sleep. Package: `dev.me_cafebabe.wakeladder`.

## Build & test

- Dev build (Gradle): `./gradlew assembleDebug`, unit tests: `./gradlew testDebugUnitTest`,
  lint: `./gradlew lintDebug` (warnings are errors).
- Platform build: `Android.bp` (`android_app` module `WakeLadder`) — uses prebuilt AndroidX
  from `prebuilts/sdk/current/androidx`; keep dependencies to what is available there.
- Gradle needs the Android SDK (`local.properties` -> `sdk.dir`, gitignored) and
  `compileSdk` 37 platform. Behind a proxy set `systemProp.https.proxyHost` in `~/.gradle/gradle.properties`.
- minSdk 34, targetSdk 36, Kotlin + Jetpack Compose (Material 3). No Hilt, no Room.

## Architecture (`app/src/main/kotlin/dev/me_cafebabe/wakeladder`)

- `domain/` — pure Kotlin, no Android imports, fully unit-tested: settings model, `LadderPlanner`
  (target -> rung times), `PlanReducer` (state transitions), style resolution.
- `data/` — `AppStore`: DataStore<Preferences> in **device-protected storage** (alarms must work
  before first unlock), JSON-encoded settings + plan (`org.json`).
- `alarm/` — `AlarmScheduler` (AlarmManager `setAlarmClock`), receivers (alarm, boot, time/zone,
  power-off status), `RingService` (foreground, audio/vibration), `PowerOffAlarmGateway`.
- `ui/` — Compose screens, `RingActivity` (lock-screen UI), dismissal tasks.

## Rules

- Keep logic in `domain/` and inject `Clock`/gateways so it stays testable on the JVM.
- Never make an early rung's dismissal cancel later rungs; only an explicit "I'm up" does.
- Ringing must never fail silent: ringtone chain is chosen -> system default alarm -> bundled tone.
- All user-visible text goes in `res/values*/strings.xml`; keep `values-zh-rCN` and
  `values-zh-rTW` in sync with `values`.
- Power-off alarm follows DeskClock's contract (`org.codeaurora.poweroffalarm.*` broadcasts to
  `com.qualcomm.qti.poweroffalarm`); hide the feature when that package is absent.
- No license headers yet (undecided). Do not add `lineage`/`LineageOS` to identifiers.
- Commit every change (small, conventional-commit messages).
