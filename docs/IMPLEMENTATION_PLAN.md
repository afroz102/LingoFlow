# Implementation Plan — InstantTranslate / LingoFlow V1

> **Backend update — 2026-10-04:** The user requested our own backend without authentication.
> [BACKEND_SETUP.md](BACKEND_SETUP.md) describes the current Workers/SQLite test implementation.
> Firebase, Supabase, App Check, and “no app-owned backend/database” statements below are historical;
> the selection workflow, language requirements, and unpassed quality/release gates remain applicable.

| Field | Value |
|---|---|
| Status | Actionable build plan derived from the existing document set. Living document — update the stage sections below as work completes, don't let them drift from reality. |
| Date | 2026-08-16 (last reviewed 2026-08-17) |
| Scope | Turns [Technical plan](TECHNICAL_PLAN.md)'s staged plan and [Validation plan](VALIDATION_PLAN.md)'s gates into concrete engineering steps |
| Current state (2026-08-17) | Stage 0A engineering-complete (physical-device matrix still open). Stage 0B **not done** — see the callout in §3. Stage 1 items 1–6 implemented ahead of Stage 0B, a deviation from this plan's own ordering (also flagged in §3). Items 7–11 are blocked on Stage 0B. See [README](../README.md) for the authoritative up-to-date status. |

This document does not redefine scope, requirements, or gates — those remain owned by [Product requirements](PRODUCT_REQUIREMENTS.md), [Technical plan](TECHNICAL_PLAN.md), and [Validation plan](VALIDATION_PLAN.md). It answers a narrower question: **in what order do I actually create files, projects, and infrastructure to get from zero to a released V1?**

---

## 0. Pre-flight (before any code) — done

| Task | Why | Blocks |
|---|---|---|
| `git init` the repository, add an Android `.gitignore` | Nothing is under version control yet | Everything |
| Freeze the subset of [PRD §15 open decisions](PRODUCT_REQUIREMENTS.md#15-open-decisions) that Stage 0A needs: minimum SDK (provisional API 23 is fine to start), application/package ID, Process Text action label placeholder | Stage 0A scaffolding needs a package name and manifest even if the label wording is revisited later | Stage 0A |
| Decide the working package ID (e.g. `com.lingoflow.instanttranslate` — placeholder, not a naming decision) | Needed to create the Gradle project | Stage 0A |
| Confirm Android Studio + Kotlin + AGP versions to standardize on | Local dev environment | Stage 0A |

**Do not** freeze the cloud-model ID, final min-SDK, input length limits, or budgets yet — those are explicitly gated on later benchmarks per PRD §15.

---

## 1. Repository/module layout

[Technical plan §1](TECHNICAL_PLAN.md#1-technical-objective) mandates **one application module** for the shipped product — no DI framework, no database, no multi-module split. [Validation plan §2.2](VALIDATION_PLAN.md#22-controlled-host-fixtures) separately requires a **controlled host test app** that is not part of the product. That's a second, unshipped Gradle module/app used only for compatibility testing.

```
LingoFlow/
├── app/                                # the shipping InstantTranslate module (Technical Plan §3 boundaries)
│   └── src/main/java/.../
│       ├── textaction/                 # Text-action adapter (Process Text intent filter, validation)
│       ├── coordinator/                # Translate-selection coordinator
│       ├── direction/                  # Direction policy (script detection, ambiguity)
│       ├── provider/                   # Translation provider boundary (typed contract)
│       │   └── gemini/                 # Gemini/Firebase AI Logic adapter (Stage 1 only)
│       ├── cloud/                      # Cloud readiness + disclosure gate
│       ├── ui/                         # Setup/status Activity, Process Text result Activity
│       └── prefs/                      # Local, non-content preferences
│       └── res/values/, values-hi/
├── testhost/                           # dev-only controlled host app (Validation Plan §2.2), never published
├── benchmark/                          # frozen corpus, compatibility matrix, benchmark result records
└── (existing planning docs, unchanged)
```

This mirrors [Technical plan §3](TECHNICAL_PLAN.md#3-v1-logical-boundaries)'s package boundaries directly — each folder above is one of the documented responsibilities (text-action adapter, coordinator, direction policy, provider boundary, cloud readiness, result presenter, local preferences). Do not extract a shared Gradle module; §11 explicitly defers that until a second product surface exists.

---

## 2. Stage 0A — Process Text platform spike (→ Gate 0) — engineering done, Gate 0 not formally closed

Goal: prove the Android integration with **zero ML/cloud code**, using a deterministic stub, per [Technical plan §10](TECHNICAL_PLAN.md#10-staged-implementation-plan) and [Validation plan §2](VALIDATION_PLAN.md#2-gate-0--android-workflow-viability).

1. Scaffold the `app` module: single exported `PROCESS_TEXT` Activity (`text/plain`, `category.DEFAULT`), satisfying FR-01–FR-03.
2. Implement input validation: reject wrong action/MIME/missing extra/oversized/whitespace-only input before any other work (FR-02, FR-11).
3. Flatten incoming `CharSequence` to an immutable plain `String`; never relay incoming extras (FR-03).
4. Plug in a **deterministic local stub translator** (e.g., fixed dictionary or trivial transform) behind the same `translate()` contract the real provider will later implement — this is what keeps Stage 0A honest proof of platform behavior, not model quality.
5. Build the result Activity: original text, stub "translation", direction indicator, explicit Copy.
6. Add explicit Replace, gated on the read-only signal; treat missing/invalid read-only as read-only (FR-09).
7. Handle malformed input, cancellation, rotation, and process death with no host modification.
8. Scaffold `testhost`: read-only `TextView`, single/multiline editable `EditText`, password field (negative case), a Compose text surface, a WebView-hosted selection, and a deliberately custom selection implementation (negative case) — per Validation Plan §2.2.
9. On Android 11+, build two `testhost` variants: with and without the `<queries>` Process Text package-visibility declaration; separately test `forceQueryable` only as an experiment, not a default (§2.3).
10. Build the compatibility matrix as a tracked spreadsheet/CSV in `benchmark/` using the [Validation Plan §9 row template](VALIDATION_PLAN.md#9-result-templates): device, OS, host app, field type, keyboard, action location, Copy/Replace result.
11. Run the matrix across the OS bands, device families, host apps, and keyboards listed in [Validation Plan §2.3](VALIDATION_PLAN.md#23-compatibility-matrix), plus all platform input cases in §2.4 (emoji, Devanagari matras, malformed parcels, rapid launches, rotation-mid-flow, etc.).
12. Run the network/log/storage audit procedure from [Privacy and security §8](PRIVACY_AND_SECURITY.md#8-verification-procedure), adapted for the stub (no network yet — this proves no accidental persistence/logging of selected text at the platform layer).
13. Check results against [Gate 0 pass criteria](VALIDATION_PLAN.md#25-gate-0-pass-criteria). If a target host segment fails, stop and revisit product scope before touching the cloud provider — do not compensate with a prohibited mechanism (Accessibility/overlay).
14. Update the PRD's supported-host statement to match observed reality.

**Exit artifact:** a filled compatibility matrix + a written Gate 0 pass/fail note in `benchmark/`.

---

## 3. Stage 0B — Freeze the benchmark protocol — **partially backfilled (2026-09-09); blocked on the project owner**

Do this **before** writing any cloud code, per [Technical plan §10 Stage 0B](TECHNICAL_PLAN.md#10-staged-implementation-plan).

> **Sequencing deviation (recorded 2026-08-17):** Stage 1 items 1–6 (Firebase wiring, the
> `provider/gemini/` adapter, `cloud/` disclosure + connectivity gates, coordinator wiring, and
> failure-state mapping) were implemented before any item below was done. This violated this
> section's own ordering. It didn't corrupt the provider code (which doesn't read the corpus or
> harness), but it left Stage 1 items 7–9 blocked on this section. **Decision: backfill Stage 0B
> in full before continuing to Stage 1 items 7–11.**
>
> **Backfill status (2026-09-09):** items 2, 3 and 6 are done — the corpus, the frozen
> host/keyboard matrix and the timing harness all exist and are checked in. Items 1 and 4
> (reference devices, blinded reviewers) need the project owner and cannot be done from
> engineering; item 5 needs a product signature. The exit artifact recording all of this is
> [`benchmark/stage_0b_frozen_protocol.md`](../benchmark/stage_0b_frozen_protocol.md). Stage 1
> items 7–11 stay blocked until items 1, 4 and the corpus source-text review are closed.

1. Acquire/reserve the physical reference devices for the low/mid/high tiers ([Validation Plan §3.2](VALIDATION_PLAN.md#32-reference-devices)). — **Not done; needs the project owner.** Empty table to fill in at `benchmark/stage_0b_frozen_protocol.md` §1.
2. Lock the app/editor/keyboard matrix used in Stage 0A as the frozen set for cloud benchmarking too. — **Done.** Frozen verbatim at `benchmark/stage_0b_frozen_protocol.md` §2; coverage actually achieved is still 2/5 OS bands, 1/5 device families, 2/10 hosts, 0/4 keyboards.
3. Build the versioned 450–700 item English/Devanagari-Hindi/Hinglish corpus ([Validation Plan §4.1](VALIDATION_PLAN.md#41-corpus)) as a structured file (JSON/CSV) in `benchmark/corpus/`, including the adversarial meaning-reversal subset. — **Drafted, not frozen.** `benchmark/corpus/corpus_v1.jsonl`: 458 items, 244 in the protected critical subset, structurally gated by `benchmark/validate_corpus.py`. The Hindi/Hinglish *source* text has not been checked by a native speaker; that pass depends on item 4 and must happen before the corpus is frozen (see `benchmark/corpus/README.md` "Status").
4. Recruit two independent bilingual reviewers for blinded scoring (§4.2). — **Not done; needs the project owner.** This is on the critical path twice over: the reviewers must validate the corpus source text before they score any model output.
5. Adopt the provisional budgets in [Validation Plan §3.6](VALIDATION_PLAN.md#36-provisional-v1-budgets) as-is, or explicitly revise them with sign-off — never silently. — **Proposed as-is, unsigned.** Recommendation and signature block at `benchmark/stage_0b_frozen_protocol.md` §5.
6. Build the automated timing harness (UI Automator/Espresso + trace markers) implementing the `T_action` … `T_render` timestamps from §3.4, with a hard rule that no selected text or hash ever enters a trace label. — **Built, compile-verified, never run on a device.** `timing/TranslationTimeline.kt`, the `TranslationLatencyBenchmark` UI Automator test, and `benchmark/collect_timings.py`; documented at [`benchmark/TIMING_HARNESS.md`](../benchmark/TIMING_HARNESS.md). The privacy rule is enforced by type signature — the mark API accepts only enums, so no caller can pass text. **Finding: `T_attested` and `T_first_output` cannot be produced** with the current SDK usage, which merges three of §3.4's derived intervals; see that document's "What cannot be measured yet".

**Exit artifact:** [`benchmark/stage_0b_frozen_protocol.md`](../benchmark/stage_0b_frozen_protocol.md) — records which of the above are frozen, which are drafted, and which are blocked on the project owner. Not yet complete: see its closing table.

---

## 4. Stage 1 — Gemini cloud spike and model bake-off (→ Gate 1)

**Status: items 1–3, 5, 6 done. Item 4 partially done (see note). Items 7–11 blocked on §3.**

1. Create the Firebase project; enable AI Logic; configure App Check (Play Integrity for release config, the documented debug provider for local dev only). — Code-side wiring (`InstantTranslateApplication`) is done; the actual Firebase project/`google-services.json` still has to be created by whoever owns the Google account (see README "Setting up Firebase for Stage 1") — this can't be scripted.
2. Add the Firebase AI Logic SDK to `app`. No raw Gemini API key anywhere in the APK (FR-06). — Done.
3. Implement the `provider/` boundary exactly as the typed contract in [Technical plan §3](TECHNICAL_PLAN.md#3-v1-logical-boundaries): `translate(text, sourceHint, target) → translated text | typed failure`, with **no** Firebase/Gemini types leaking outside `provider/gemini/`. — Done as `translate(text, direction: Direction)`; `Direction` is the sourceHint+target pair collapsed into one enum since Stage 0A only supports the two unambiguous directions (see `direction/Direction.kt`).
4. Implement `cloud/`: disclosure gate (blocks the first content-bearing request until acknowledgement), connectivity/quota/timeout state machine, bounded retry with jitter only where safe. — Disclosure gate and connectivity pre-check are done. **The automatic bounded-retry-with-jitter is not implemented** — only a manual, user-tapped Retry button exists in the result UI. [Technical plan §6](TECHNICAL_PLAN.md#6-provider-lifecycle-and-latency) says to avoid *automatic* retry specifically for validation/quota/safety failures, which doesn't excuse skipping it for the safe case this line is written for (e.g. a single dropped connection on an otherwise-fine request). Add automatic bounded retry with jitter for that narrow case before calling this item done.
5. Wire the coordinator to call the provider only after disclosure + validation, per the [runtime state model](TECHNICAL_PLAN.md#5-runtime-state-model). — Done.
6. Map provider failures (offline, `RESOURCE_EXHAUSTED`, timeout, invalid response) to content-free typed errors surfaced by the result Activity. — Done (`GeminiExceptionMapper`, `FailureReason`, `ErrorKind`).
7. Instrument the full timing chain from Stage 0B's harness against real network calls. — **Blocked: Stage 0B's harness doesn't exist yet.** Do not attempt this until §3 is backfilled.
8. Run the model bake-off: the lowest-latency stable Gemini Flash-Lite model eligible for the free tier, plus one stronger Flash comparator, plus the Stage 0A stub as a platform-overhead baseline — across the text lengths, network profiles, and provider states in [Validation Plan §3.3](VALIDATION_PLAN.md#33-workloads-networks-and-states).
9. Run the frozen corpus through both blinded reviewers; score per [§4.2](VALIDATION_PLAN.md#42-review-method); tag critical meaning reversals, hallucinations, omissions, script issues separately.
10. Fill the [weighted decision scorecard](VALIDATION_PLAN.md#37-weighted-decision-scorecard); pin the winning model ID, SDK version, and prompt/schema version in a decision record.
11. Check against [Gate 1](VALIDATION_PLAN.md#3-gate-1--gemini-cloud-and-model-bake-off) — quality ≥4.0/5 per direction, zero unresolved score-1s, zero critical reversals in the protected subset, latency/quota/resource budgets met or explicitly revised.

**Exit artifact:** pinned model ID + decision record with rejected alternatives and raw scores.

---

## 5. Stage 2 — V1 alpha (→ Gate 2)

1. Build `direction/`: Devanagari heuristic routing, ambiguity classification for short/mixed/Latin text, user-correctable direction control (FR-05, FR-12).
2. Implement all UI states in the result Activity: disclosure-required, loading, success, ambiguity, each cloud-failure type (FR-07, FR-10).
3. Implement `prefs/`: direction preference, Hindi output script (Devanagari/Roman), disclosure-acknowledgement version — via `DataStore`/`SharedPreferences` only, no database (FR-14).
4. Harden lifecycle: rotation, backgrounding, process death, cancellation, concurrent-request handling — no crash, no host modification on any failure path.
5. Add bilingual UI strings (`values/`, `values-hi/`) and the help/setup screen (FR-13).
6. Accessibility pass: screen reader labels, scalable text, contrast, touch targets.
7. Privacy hardening: confirm no source/result text reaches logs, saved instance state, Recents, or crash breadcrumbs — re-run the Privacy and Security §8 audit against the real cloud path this time.
8. Run 1,000 repeated automated workflow executions for crash/ANR-free evidence.
9. Check against [Gate 2](VALIDATION_PLAN.md#7-gate-2--v1-alpha).

---

## 6. Stage 3 — Release hardening (→ Gate 3)

1. Finalize the full compatibility and repeated-run matrices.
2. Audit the merged manifest; justify or remove every dependency/component/permission.
3. Run the complete network/log/storage/idle audit from Privacy and Security §8 on a release build.
4. Final bilingual sign-off on the release-scope corpus.
5. **Resolve the unpaid/paid/on-device privacy posture decision** — this is a hard release gate, not an engineering task, and it's explicitly unresolved in every source doc. See §7 below.
6. Prepare Play Store Data Safety form, privacy notice, and Gemini/Google attribution wording (subject to provider/legal review per PRD FR-01).
7. Publish the final supported-host statement matching observed Stage 0A/3 evidence.
8. Check against [Gate 3](VALIDATION_PLAN.md#8-gate-3--release).

---

## 7. Decisions that block release regardless of engineering progress

These aren't implementation tasks — they're product/legal calls that several docs flag as blocking. Surface them early since they can invalidate engineering work done under the wrong assumption:

| Decision | Where it's discussed | Must be resolved by |
|---|---|---|
| Paid Gemini processing vs. on-device vs. explicitly non-private positioning | [Cloud and Hinglish decision](CLOUD_AND_HINGLISH_DECISION.md#privacy-conflict), [Privacy and security §10](PRIVACY_AND_SECURITY.md#10-v1v2-cloud-boundary) | Before external beta |
| Public product name (InstantTranslate vs. LingoFlow vs. other) | [README](../README.md#current-decision-snapshot) | Before store assets |
| Process Text action label wording | [PRD §15](PRODUCT_REQUIREMENTS.md#15-open-decisions) | Provider/legal review |
| Distribution markets and legal review | [PRD §15](PRODUCT_REQUIREMENTS.md#15-open-decisions) | Before external beta |

Recommendation: raise the privacy-posture decision with stakeholders in parallel with Stage 0A/0B, since it doesn't block platform or benchmark work but does block Stage 3 and shapes whether Stage 1's "free tier" framing survives to release.

---

## 8. Immediate next actions (updated 2026-09-09)

§0, §1, §2 are done; §3 items 2, 3 and 6 are backfilled; §4 items 1–6 are done. **Everything that
can be done from engineering alone on the Gate 1 path is now done.** The remaining critical-path
work needs the project owner, and no amount of further coding substitutes for it.

Owner-blocked, and blocking Gate 1:

1. **Reserve the three reference devices** (§3 item 1). Table to fill in at
   `benchmark/stage_0b_frozen_protocol.md` §1. Without frozen hardware, latency numbers are not
   comparable between runs and the timing harness has nothing to run on.
2. **Recruit the two blinded bilingual reviewers** (§3 item 4). They are needed twice: first to
   confirm the corpus's Hindi/Hinglish source text reads naturally — until that pass happens the
   corpus is a draft, not a frozen artifact — and then to score model output.
3. **Create the Firebase project** and drop `google-services.json` into `app/` per README "Setting
   up Firebase for Stage 1". Nothing in Stage 1 has ever been exercised against a live model.
4. **Sign off the §3.6 budgets** (§3 item 5), as-is or explicitly revised. Recommendation and
   signature block at `benchmark/stage_0b_frozen_protocol.md` §5. Sign before seeing comparative
   results, not after.

Engineering work that does not depend on the above:

5. Add the automatic bounded-retry-with-jitter gap noted in §4 item 4 — still the one acknowledged
   code gap in Stage 1.
6. Decide whether the model bake-off needs time-to-first-token. If it does, the provider must move
   to `generateContentStream` before benchmarking, because `T_first_output` cannot otherwise be
   measured at all (`benchmark/TIMING_HARNESS.md`, "What cannot be measured yet"). Decide this
   deliberately now rather than discovering it mid-bake-off.
7. Dry-run the timing harness against the offline path on any device or emulator. It compiles but
   has never been run; the UI Automator selection sequence should be expected to need adjustment on
   first contact with real hardware. The offline path exercises the whole chain without needing
   Firebase, which separates harness bugs from provider bugs.

Then, and only then:

8. Resume Stage 1 items 7–11 (timing instrumentation against real calls, the model bake-off,
   blinded review, the decision scorecard, Gate 1 check).
9. Raise the privacy-posture decision (§7) with whoever owns product/legal sign-off — it doesn't
   block engineering but does block Stage 3.
10. Close Gate 0 formally: run the physical-device compatibility matrix (§2 items 10–11) still open
    per `benchmark/gate_0_result.md`. It needs the same hardware as item 1, so reserve once and use
    it for both.
