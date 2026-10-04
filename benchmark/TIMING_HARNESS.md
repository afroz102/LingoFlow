# Gate 1 timing harness

> Backend update — 2026-10-04: the active provider is our unauthenticated HTTP backend.
> The hosted path and Android transport smoke pass; the full cross-app latency harness
> still requires a separate run. See [current setup](../docs/BACKEND_SETUP.md).

[IMPLEMENTATION_PLAN.md §3](../docs/IMPLEMENTATION_PLAN.md#3-stage-0b--freeze-the-benchmark-protocol)
item 6: the automated harness that produces the
[VALIDATION_PLAN.md §3.4](../docs/VALIDATION_PLAN.md#34-timing-definitions) timestamps.

## Pieces

| Piece | Where | Does what |
|---|---|---|
| `TranslationTimeline` | [`app/src/main/java/com/lingoflow/instanttranslate/timing/TranslationTimeline.kt`](../app/src/main/java/com/lingoflow/instanttranslate/timing/TranslationTimeline.kt) | Records §3.4 marks on a monotonic clock and emits one content-free line per run. |
| `TranslationLatencyBenchmark` | [`app/src/androidTest/java/com/lingoflow/instanttranslate/timing/TranslationLatencyBenchmark.kt`](../app/src/androidTest/java/com/lingoflow/instanttranslate/timing/TranslationLatencyBenchmark.kt) | Drives real selections in the test host via UI Automator, records `T_action`, collects each run. |
| `collect_timings.py` | [`benchmark/collect_timings.py`](collect_timings.py) | Turns raw runs into the sliced P50/P95/P99 report. |

## The privacy rule, and how it is enforced

§3 item 6 sets a hard rule: **no selected text or hash may ever enter a trace label.**

This is enforced by type signature, not by discipline. `TranslationTimeline.mark` takes a
`TimingMark` enum and `complete` takes a `TimingOutcome` enum. Neither has an overload accepting a
caller-supplied string, so there is no code path — including a future careless one — by which
selected text, a translation, or a digest of either could be emitted. What leaves the app is a
mark name and a nanosecond reading.

The harness test does handle workload text (it has to type it into the host), but its output rows
carry only a corpus item id, direction, length bucket, character count and timestamps.

Instrumentation is gated at runtime on `BuildConfig.TIMING_ENABLED`, which is `true` for debug and
`false` for release. It is a separate flag from `BuildConfig.DEBUG` on purpose: measuring a
release-shaped build later means adding a build type that flips this one field, not
re-instrumenting the flow.

## Running it

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17     # or wherever your JDK 17 lives

./gradlew :testhost:installWithQueriesDebug        # the host that will be selected in
./gradlew :app:installDebug                        # the app under test

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lingoflow.instanttranslate.timing.TranslationLatencyBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.warmupRuns=10 \
  -Pandroid.testInstrumentationRunnerArguments.measuredRuns=100

adb pull /sdcard/Android/data/com.lingoflow.instanttranslate/files/latency_runs.jsonl
python3 benchmark/collect_timings.py latency_runs.jsonl --report benchmark/latency_report.md
```

Arguments: `warmupRuns` (default 10, §3.4's untimed stabilization runs), `measuredRuns`
(default 30; §3.4 asks for ≥100 warm runs per slice where quota permits, and ≥30 cold ones),
`itemsPerSlice` (default 1 corpus item per direction × length bucket).

The workload comes from the frozen corpus. `app/build.gradle.kts` copies
`benchmark/corpus/corpus_v1.jsonl` into the androidTest assets at build time rather than keeping a
second copy in the app tree, so the text that gets benchmarked and the text that gets scored can
never drift apart.

## What it measures

Six of §3.4's nine timestamps come from the app, one from the test, and two do not exist.

| Mark | Source |
|---|---|
| `T_action` | The instrumentation test, at the instant it taps the Process Text action. |
| `T_receive` | `ProcessTextActivity`, after the intent validates. |
| `T_direction` | `TranslateCoordinator`, after direction detection. |
| `T_client_ready` | `BackendTranslationProvider`, after endpoint validation. |
| `T_request_sent` | `BackendTranslationProvider`, immediately before the HTTPS backend request. |
| `T_response_end` | `BackendTranslationProvider`, after the response parses and validates. |
| `T_render` | `ResultActivity`, on the first message after the frame carrying the result. |

`T_action` is taken by tapping the **real** selection menu in the test host, not by firing a
synthetic intent at `ProcessTextActivity`. That matters: a synthetic intent skips the platform's
own selection-menu dispatch, which is exactly the interval `platform entry`
(`T_receive − T_action`) exists to measure. A harness that took the shortcut would report a
platform entry cost near zero and be wrong by however long the system actually takes.

## What cannot be measured yet

Three §3.4 intervals are **not** produced, and this is a finding about the current provider
implementation rather than an incomplete harness. Any Gate 1 decision record has to say so
explicitly rather than presenting a partial chain as the whole one.

**`T_attested` — App Check token ready.** Not applicable to this test version: there is no
App Check, authentication, session or token acquisition. Do not interpret its absence as a
zero-duration attestation measurement.

**`T_first_output` — first provider output.** Requires a streaming response.
`BackendTranslationProvider` waits for a complete backend response, so there is no first-token event
to mark. §3.4 already scopes this one to "when exposed by the SDK". This loses both
`provider/network first output` and `completion`, which are merged into `provider_round_trip`
(`T_request_sent → T_response_end`).

If either interval turns out to matter for the model decision — for instance if two candidates have
similar end-to-end times but different time-to-first-token, which would change how a future
streaming UI feels — the provider has to move to `generateContentStream` first. That is a product
and provider change, not a harness change, and it should be decided deliberately rather than
discovered during the bake-off.

`T_render` is also an approximation. It is marked on the first message after the frame carrying the
result was produced, which slightly over-reports against a true photometric measurement. §3.4
independently asks for a visual/high-speed sanity check, and that check is the thing that
calibrates this mark — do not treat the render number as exact without it.

## Verification status

Verified so far, on this machine:

- both modules and the androidTest APK compile (`:app:assembleDebugAndroidTest`);
- the 20 current unit tests pass with the custom backend adapter;
- the frozen corpus is present in the built test APK as `assets/corpus_v1.jsonl`; and
- `collect_timings.py` produces correct sliced percentiles from synthetic runs, and fails loudly
  on malformed input and on out-of-order marks.

**Not verified: the full cross-app timing harness has never been run against a device or emulator.**
The hosted backend and a two-direction Android transport smoke test now pass. Physical devices
are still unreserved, and the automated long-press/overflow selection sequence may need adjustment
on OEM skins. The transport smoke does not exercise that sequence or measure selection-to-render
latency. Resolve the disclosure before timed runs, and pace model calls below the shared
10/minute and 200/day test limits; the full benchmark requires an explicit quota plan.
