# Benchmark tools and evidence

The current app is LingoBoard 1.0.1: multilingual translation with Auto → English defaults,
larger keys, light/dark themes, icon controls and screenshots enabled. Earlier UI and
direction-enum findings are historical. The frozen corpus/timing
protocol needs adaptation; the old latency instrumentation is explicitly skipped.

The [validation plan](../docs/VALIDATION_PLAN.md) defines the quality, compatibility, resource
and release gates. Passing a connectivity smoke does not pass those gates.

| File | Purpose |
|---|---|
| `lingoboard_1.0.1_android_smoke.json` | Current versioned APK, keyboard, language selector, screenshot and live UI evidence |
| `lingoboard_multilingual_http_smoke.json` | Ten real multilingual requests; transport/script checks, not human quality scores |
| `keyboard_smoke.json` | Earlier 0.7 keyboard translation, UI/build checks, denied-overlay and live translation evidence |
| `reading_writing_android_smoke.json` | Earlier API 30/34 floating reading/writing, clipboard, basic keyboard and live transport evidence |
| `reading_writing_http_smoke.json` | Earlier Roman-only hosted V1 modes, script rejection and semantic smoke flags |
| `compatibility_matrix.csv` | Historical API 30/34 host/field discovery rows; physical coverage remains open |
| `gate_0_result.md` | Platform-spike findings, fixes, privacy/lifecycle checks and consolidated scorecard |
| `stage_0b_frozen_protocol.md` | Frozen matrix, draft corpus, reference-device/reviewer/budget decisions still pending |
| `corpus/` | 458-item English/Hindi/Hinglish draft corpus and generated coverage report |
| `TIMING_HARNESS.md` | On-device selection-to-render timing procedure and measurement limitations |
| `backend_smoke_result.json` | Dated content-free hosted HTTP smoke evidence |
| `hinglish_android_smoke_result.json` | Live API 34 transport and result UI direction-selector evidence |
| `hinglish_smoke_result.json` | Hosted automatic routing, mixed Hinglish, negation and output-script evidence |
| `backend_android_smoke_result.json` | Dated live Android transport evidence, separate from full cross-app UI testing |

`validate_corpus.py` checks balance, critical subsets, slices, protected tokens and input caps.
`collect_timings.py` aggregates recorded runs into sliced P50/P95/P99 and rejects malformed or
out-of-order marks. Both fail rather than repairing input silently.
The hosted HTTP smoke tool is `backend/smoke.mjs`; see [setup](../docs/BACKEND_SETUP.md#live-verification).
Do not hand-edit evidence to force a pass. Preserve build/device/date context for historical rows.
