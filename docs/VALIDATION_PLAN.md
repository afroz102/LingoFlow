# Validation plan — reading/writing V1

Updated 2026-10-04. The current scope is English ↔ Roman Hindi/Hinglish; read results always
English. Earlier preview/Replace, Devanagari conversion and no-overlay gates are superseded.
Historical records remain dated, not evidence for features that have changed.

## Automated checks

```sh
npm --prefix backend test
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :testhost:assembleWithQueriesDebug
```

Backend tests exercise atomic SQLite/D1 quota, concurrency/persistence, bounded bodies/deadlines,
input/script/direction rejection before quota/model calls, literal prompt data, one-call AUTO
resolution, reading instructions, Roman-only output and content-free failure categories.
Android JVM tests exercise coordinator gating, script/input bounds, reading direction, output
validation, resolved AUTO direction, malformed response, cancellation and explicit retry limits.

`ReadingWritingSmokeTest` uses a different-UID dev host. Its non-live case verifies background
copy → confirmation (no loading/request), Close, duplicate/sensitive suppression, landscape overlay controls and Stop.
Opt-in live cases verify confirmed-copy English result, own-copy suppression, Minimize/reopen,
other-keyboard focused clipboard fallback, automatic writing result contract with unchanged
prefix/suffix, actual standard Android editor selection-menu replacement, read-only no replacement and unsupported-script error. These are controlled
fixtures, not proof that a target app supports the same path.

An additional non-live case exercises Roman keyboard typing/case/numbers/backspace and
password-editor clipboard suppression.

`BackendTranslationSmokeTest` is opt-in live transport for AUTO English/Hinglish and both
Hinglish/English reading. `backend/smoke.mjs` makes five model calls and retains content-free
status/timing/validation flags. Run suites in separate UTC minutes (10 shared requests/minute).

## Physical target-app matrix

Test Game of Khans, Discord, WhatsApp, Telegram and Instagram messaging on actual devices.
For each app, record app version, phone/OEM, Android version and all of these separately:

| Surface | Verify |
|---|---|
| Editable text | Action discovery; successful replacement of only selected range; host cursor/format behavior |
| Selectable read-only text | Action discovery; English floats; no host/clipboard modification |
| Copy-only messages | Actual copy produces prompt with selected Lingo keyboard; confirmation only then sends |
| Other keyboard | No automatic claim; bubble focus reads copy after tap and returns game/chat focus |
| Overlay | App permits it; card drag, Copy, Minimize, Close, Stop; portrait/landscape and system bars |
| Keyboard | Typing, numeric/password fields, emoji backspace, editor actions, switch-back, selection retention |
| Lifecycle | Loading cancel, new copy during request, permission revoke, screen lock, process kill, app switch |

Game of Khans may render custom text and can pause when focus is acquired. Do not infer its
behavior from the controlled host. Apps can omit Process Text, restrict replacements or hide
overlays. Android 10 Go does not generally allow new overlay permission grants. No claim of
all Android versions/phones; API 23 minimum is provisional. At least recent Pixel plus Samsung,
Xiaomi/Redmi and another OEM are needed before broader support statements.

## Semantic quality

Evaluate writing and reading separately with English→Roman Hindi, informal Hinglish→English,
already-English reading unchanged, negation, names/numbers, timing, requests versus commands,
sarcasm, game/alliance/raid vocabulary, abbreviations, slang, emoji, mixed English-Hindi and short
ambiguous messages. Use at least two fluent bilingual reviewers with blind outputs and record
material meaning changes separately from style. Gemini smoke flags are not human-quality scoring.

The historical 458-item corpus includes Devanagari directions. Retain it as historical evidence;
do not score the current V1 against removed directions or its outdated assertions. A frozen V1
corpus should use the existing Roman pairs plus reviewed real gaming/messaging examples with
explicit direction/mode and protected-token assertions. Do not collect private chats without consent.

## Performance, privacy and release

Measure selection action→replacement and selection/copy confirmation→floating result separately.
Measure cold/warm, Wi-Fi/mobile/offline, long text, multiple copies, rotation and throttling.
Report P50/P95 and failures. Measure idle-session battery/memory and keyboard typing reliability
on physical phones. The old `TranslationLatencyBenchmark` is skipped until it waits for an actual
completed overlay frame; old T_RENDER timing must not imply measured overlay responsiveness.

Verify no source/result/keystroke content in logs, disk, saved state, backups, screenshots or
Recents. Copy is the explicit exception for Android clipboard output. Inspect APK/config for
server secrets. Read-only must never return replacement data, and writing failure/cancel must
never return text. Confirm clipboard prompts do not send data before user confirmation.

Public distribution requires a fresh foreground-service/IME/privacy/store review, physical
compatibility, accessibility/text scaling and model-quality evidence. There is no billing upgrade,
per-user authentication or guaranteed free quota availability in this testing backend.

## Evidence

Current [hosted smoke](../benchmark/reading_writing_http_smoke.json) records the new contract.
[Android V1 results](../benchmark/reading_writing_android_smoke.json) record actual passing
instrumentation status, build/device/version and scope: 7 test instances on Android 14/API 34
(including live UI and transport) and 5 on Android 11/API 30 (including clipboard prompts,
native selection replacement, focused-overlay reading and keyboard/password checks). Backend
unit tests: 19 passed. Android JVM tests: 22 passed. App/host lint and APK builds passed.
The hosted smoke passed all 11 checks, including five real translations. Historical [Gate 0](../benchmark/gate_0_result.md),
[Hinglish HTTP](../benchmark/hinglish_smoke_result.json),
[Hinglish Android](../benchmark/hinglish_android_smoke_result.json),
[backend HTTP](../benchmark/backend_smoke_result.json) and
[backend Android](../benchmark/backend_android_smoke_result.json) remain historical.
