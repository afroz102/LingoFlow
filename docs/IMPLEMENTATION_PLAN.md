# Implementation Plan — InstantTranslate / LingoFlow V1

| Field | Value |
|---|---|
| Status | Actionable build plan derived from the existing document set |
| Date | 2026-08-16 |
| Scope | Turns [Technical plan](TECHNICAL_PLAN.md)'s staged plan and [Validation plan](VALIDATION_PLAN.md)'s gates into concrete engineering steps |
| Precondition | No code, no Gradle project, no git repository currently exist |

This document does not redefine scope, requirements, or gates — those remain owned by [Product requirements](PRODUCT_REQUIREMENTS.md), [Technical plan](TECHNICAL_PLAN.md), and [Validation plan](VALIDATION_PLAN.md). It answers a narrower question: **in what order do I actually create files, projects, and infrastructure to get from zero to a released V1?**

---

## 0. Pre-flight (before any code)

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

## 2. Stage 0A — Process Text platform spike (→ Gate 0)

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

## 3. Stage 0B — Freeze the benchmark protocol

Do this **before** writing any cloud code, per [Technical plan §10 Stage 0B](TECHNICAL_PLAN.md#10-staged-implementation-plan).

1. Acquire/reserve the physical reference devices for the low/mid/high tiers ([Validation Plan §3.2](VALIDATION_PLAN.md#32-reference-devices)).
2. Lock the app/editor/keyboard matrix used in Stage 0A as the frozen set for cloud benchmarking too.
3. Build the versioned 450–700 item English/Devanagari-Hindi/Hinglish corpus ([Validation Plan §4.1](VALIDATION_PLAN.md#41-corpus)) as a structured file (JSON/CSV) in `benchmark/corpus/`, including the adversarial meaning-reversal subset.
4. Recruit two independent bilingual reviewers for blinded scoring (§4.2).
5. Adopt the provisional budgets in [Validation Plan §3.6](VALIDATION_PLAN.md#36-provisional-v1-budgets) as-is, or explicitly revise them with sign-off — never silently.
6. Build the automated timing harness (UI Automator/Espresso + trace markers) implementing the `T_action` … `T_render` timestamps from §3.4, with a hard rule that no selected text or hash ever enters a trace label.

**Exit artifact:** frozen corpus file, frozen device/host/keyboard list, frozen budgets, working timing harness — checked in before Stage 1 starts.

---

## 4. Stage 1 — Gemini cloud spike and model bake-off (→ Gate 1)

1. Create the Firebase project; enable AI Logic; configure App Check (Play Integrity for release config, the documented debug provider for local dev only).
2. Add the Firebase AI Logic SDK to `app`. No raw Gemini API key anywhere in the APK (FR-06).
3. Implement the `provider/` boundary exactly as the typed contract in [Technical plan §3](TECHNICAL_PLAN.md#3-v1-logical-boundaries): `translate(text, sourceHint, target) → translated text | typed failure`, with **no** Firebase/Gemini types leaking outside `provider/gemini/`.
4. Implement `cloud/`: disclosure gate (blocks the first content-bearing request until acknowledgement), connectivity/quota/timeout state machine, bounded retry with jitter only where safe.
5. Wire the coordinator to call the provider only after disclosure + validation, per the [runtime state model](TECHNICAL_PLAN.md#5-runtime-state-model).
6. Map provider failures (offline, `RESOURCE_EXHAUSTED`, timeout, invalid response) to content-free typed errors surfaced by the result Activity.
7. Instrument the full timing chain from Stage 0B's harness against real network calls.
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

## 8. Immediate next actions

1. `git init` + Android `.gitignore`.
2. Pick the working package ID and create the `app` + `testhost` Gradle projects (Kotlin, single module each, min SDK 23 provisional).
3. Implement the Stage 0A stub adapter end-to-end (steps in §2.1–2.7 above) — this is the fastest path to a real, demoable artifact.
4. In parallel, start building the `testhost` fixtures and the compatibility matrix template.
5. Raise the privacy-posture decision (§7) with whoever owns product/legal sign-off, so it isn't discovered late.
