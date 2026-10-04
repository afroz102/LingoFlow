# Validation plan — keyboard translation V1

Updated 2026-10-04. The current scope is LingoBoard 1.0.2, with 45 languages/67 script options. Source defaults
to Auto; target defaults to English. Keyboard translation no longer needs overlays. Earlier preview/Replace flows and direction enums are superseded.
Historical records remain dated, not evidence for features that have changed.

## Automated checks

```sh
npm --prefix backend test
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :testhost:assembleWithQueriesDebug
```

Backend tests exercise atomic SQLite/D1 quota, concurrency/persistence, bounded bodies/deadlines,
input/script/direction rejection before quota/model calls, literal prompt data, one-call AUTO
resolution, reading instructions, Roman-only output and content-free failure categories.
Android JVM tests exercise coordinator gating, script/output and input bounds, reading direction, output
validation, resolved AUTO direction, malformed response, cancellation and explicit retry limits.

`KeyboardTranslationSmokeTest` uses a different-UID host with overlay permission explicitly denied.
Non-live cases verify one-shot Shift/Caps Lock, three symbol pages and secondary shortcuts, emoji/code-point backspace,
hold deletion, isolated translation draft, direction swap, Close, password controls, blank
input, cloud-disclosure gating and landscape controls. Opt-in live cases check both writing
directions and insertion without sending, copied reading with confirmation, reading while typing
a reply, own-result suppression, active editor selection and read-only selection-action handoff.
`KeyboardLayoutRenderTest` measures portrait/landscape heights and renders the actual view using
synthetic samples for visual review; it does not capture private screen content.

The six keyboard JVM tests cover selection replacement/reversed ranges, whole emoji deletion,
input limits, 50 distinct emoji/symbol coverage and changed-editor/selection insertion guards.

`ReadingWritingSmokeTest` uses a different-UID dev host. Its non-live case verifies background
copy → confirmation (no loading/request), Close, duplicate/sensitive suppression, landscape overlay controls and Stop.
Opt-in live cases verify confirmed-copy English result, own-copy suppression, Minimize/reopen,
other-keyboard focused clipboard fallback, automatic writing result contract with unchanged
prefix/suffix, actual standard Android editor selection-menu replacement, read-only no replacement and bounded-input errors. These are controlled
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
| Keyboard writing | Separate draft stays out of chat; direction swap; one insertion on success; no automatic Send |
| Keyboard reading | Copy → composer → message icon → confirm; selected target inside IME; keep typing while result stays; no overlays |
| Active editor selection | Translate selection appears; English card; original range unchanged |
| Read-only keyboard handoff | Selection action → composer within 60 seconds; no replacement or overlay |
| Editable text | Action discovery; successful replacement of only selected range; host cursor/format behavior |
| Selectable read-only text | Action discovery; English floats; no host/clipboard modification |
| Copy-only messages | Actual copy produces prompt with selected LingoBoard; confirmation only then sends |
| Other keyboard | No automatic claim; bubble focus reads copy after tap and returns game/chat focus |
| Overlay | App permits it; card drag, Copy, Minimize, Close, Stop; portrait/landscape and system bars |
| Keyboard | Touch/hold typing, Shift/lock, three symbol pages, 50 emoji, numeric/password fields, draft cursor editing, editor actions, switch-back, selection retention |
| Lifecycle | Loading cancel, new copy during request, permission revoke, screen lock, process kill, app switch |

Game of Khans may render custom text and can pause when focus is acquired. Do not infer its
behavior from the controlled host. Apps can omit Process Text, restrict replacements or hide
overlays. Android 10 Go does not generally allow new overlay permission grants. No claim of
all Android versions/phones; API 23 minimum is provisional. At least recent Pixel plus Samsung,
Xiaomi/Redmi and another OEM are needed before broader support statements.

## Semantic quality

Evaluate writing and reading across all enabled languages/scripts, with native and romanized
input, explicit source choices and ambiguous auto-detection. Include English→Roman Hindi, informal Hinglish→English,
already-English reading unchanged, negation, names/numbers, timing, requests versus commands,
sarcasm, game/alliance/raid vocabulary, abbreviations, slang, emoji, mixed English-Hindi and short
ambiguous messages. Use at least two fluent bilingual reviewers with blind outputs and record
material meaning changes separately from style. Gemini smoke flags are not human-quality scoring.

The historical 458-item corpus includes Devanagari directions. Retain it as historical evidence;
do not score current requests against obsolete direction enums or its outdated assertions. A new
corpus should cover multilingual pairs plus reviewed real gaming/messaging examples with
explicit direction/mode and protected-token assertions. Do not collect private chats without consent.

## Performance, privacy and release

Measure keyboard draft→insertion, reading confirmation→inline result, and optional floating flows separately.
Measure cold/warm, Wi-Fi/mobile/offline, long text, multiple copies, rotation and throttling.
Report P50/P95 and failures. Measure idle-session battery/memory and keyboard typing reliability
on physical phones. The old `TranslationLatencyBenchmark` is skipped until it waits for an actual
completed overlay frame; old T_RENDER timing must not imply measured overlay responsiveness.

Verify no source/result/keystroke content in logs, disk, saved state or backups. Screenshots
and Recents previews are explicitly allowed. Copy puts the requested result on Android clipboard. Inspect APK/config for
server secrets. Read-only must never return replacement data, and writing failure/cancel must
never return text. Confirm clipboard prompts do not send data before user confirmation.

Public distribution requires a fresh foreground-service/IME/privacy/store review, physical
compatibility, accessibility/text scaling and model-quality evidence. There is no billing upgrade,
per-user authentication or guaranteed free quota availability in this testing backend.

## Evidence

Current [LingoBoard 1.0.2 evidence](../benchmark/lingoboard_1.0.2_android_smoke.json): 32 Android
JVM tests, APK builds and app lint (zero errors) passed. The final APK passed the full 20-case
Android 11/API 30 suite with seven live Gemini requests. Android 14/API 34 passed 21 core cases
before the final header-accessibility/setup-launch-theme changes, then eight focused cases on
the final APK, including live reading while typing, chip swap/persistence, dim setup, native
light/dark portrait/landscape rendering, cursor/key-grid retention and fast release/cancel/long-press.
The final hash and the earlier core APK hash are retained in the record; that core APK is kept
locally. Header descriptions expose the full selected language, and language changes replace
only those chip nodes to avoid stale IME accessibility labels. The backend was unchanged.
No physical-phone responsiveness or actual target-app compatibility is claimed.


Earlier [LingoBoard 1.0.1 evidence](../benchmark/lingoboard_1.0.1_android_smoke.json): 31 Android
JVM tests, 23 backend tests, APK builds and app lint (zero errors) passed. Core keyboard suites
passed 16 cases on API 34 and eight on API 30, including eight live Gemini requests. Final
icon padding and picker ordering were then checked on the final APK by focused five-case
API 34 and four-case API 30 suites. These verify language picker persistence, rapid typing,
screenshot capture, in-place cursor/emoji editing and native light/dark portrait/landscape renders.
The final APK hash is retained in the record; core-suite APK bytes preceding the visual-only
polish were not retained. The [multilingual HTTP smoke](../benchmark/lingoboard_multilingual_http_smoke.json)
passed ten real Gemini requests including Indian native/Roman inputs and the requested global
languages. Its flags check transport, language metadata and a few script/word markers; they
are not a human semantic-quality score. No physical-phone validation is claimed.


Earlier 0.7 [keyboard evidence](../benchmark/keyboard_smoke.json): 27 Android JVM tests, app/host
lint and APK builds passed. Android 14/API 34 passed the 13-case main suite plus two focused
landscape/hold-deletion checks (15 test instances, including a repeated typing case). Android
11/API 30 passed five keyboard cases. The passing runs include seven live Gemini requests,
with writing insertion, reading cards and continued chat typing while overlays are denied.
Native-view previews in `docs/images/` were visually reviewed using synthetic samples.
These are emulator/fixture checks; physical target-app testing remains open.

Earlier [hosted smoke](../benchmark/reading_writing_http_smoke.json) records the new contract.
[Earlier floating-flow Android results](../benchmark/reading_writing_android_smoke.json) record actual passing
instrumentation status, build/device/version and scope: 7 test instances on Android 14/API 34
(including live UI and transport) and 5 on Android 11/API 30 (including clipboard prompts,
native selection replacement, focused-overlay reading and keyboard/password checks). Backend
unit tests: 19 passed. Android JVM tests: 22 passed. App/host lint and APK builds passed.
The hosted smoke passed all 11 checks, including five real translations. Historical [Gate 0](../benchmark/gate_0_result.md),
[Hinglish HTTP](../benchmark/hinglish_smoke_result.json),
[Hinglish Android](../benchmark/hinglish_android_smoke_result.json),
[backend HTTP](../benchmark/backend_smoke_result.json) and
[backend Android](../benchmark/backend_android_smoke_result.json) remain historical.
