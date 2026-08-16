# LingoFlow / InstantTranslate

A lightweight Android language utility: select English, Hindi, or Hinglish text in any app that
exposes Android's `ACTION_PROCESS_TEXT` action, and translate it in place via **Select → More →
Translate**.

Status: **Stage 0A in progress** — the Android platform spike (`docs/IMPLEMENTATION_PLAN.md` §2),
built against a deterministic local stub translator with zero cloud/ML code, per the staged plan
in `docs/TECHNICAL_PLAN.md` §10. Real cross-app Process Text discovery, Copy/Replace, and a
privacy/lifecycle audit are verified on two Android emulator OS bands (API 30, API 34); the full
physical-device compatibility matrix required to formally close Gate 0 has not been run yet. See
[`benchmark/gate_0_result.md`](benchmark/gate_0_result.md) and
[`benchmark/gate_0_scorecard.md`](benchmark/gate_0_scorecard.md) for the evidence trail. No cloud
provider (Stage 1) is implemented yet.

The working product name in the supplied specification is **InstantTranslate**; the repository name is **LingoFlow**. Final naming remains an open product decision.

## Repository structure

```
app/         The shipping InstantTranslate Android module (Stage 0A: platform + stub only)
testhost/     Dev-only controlled host app for Process Text compatibility testing — never published
benchmark/    Tracked validation evidence: compatibility matrix, Gate 0 results/scorecard
docs/         Product/engineering planning documents (see Document set below)
```

See [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) §1 for the full module/package
boundary rationale.

## Building and running

Requirements: JDK 17, Android SDK (`compileSdk`/`targetSdk` 34, `minSdk` 23 provisional).

```
./gradlew :app:assembleDebug                                    # build the app
./gradlew :app:testDebugUnitTest                                 # run unit tests
./gradlew :testhost:assembleWithQueriesDebug                     # build the compatibility test host
```

`app` has no exported launcher — it's reached only through the Process Text selection menu in
another app. Install both `app` and a `testhost` flavor on a device/emulator, long-press text in
`testhost`, and select **Translate** from the overflow menu to try it.

No secrets are required to build or run Stage 0A. See [`.env.example`](.env.example) for what
Stage 1 (Gemini/Firebase) will eventually need for local dev tooling — nothing there is embedded
in the shipped app; copy it to `.env` (gitignored) if and when you need it.

## Current decision snapshot

- V1 is a native Kotlin Android utility for English ↔ Hindi translation through Android's `ACTION_PROCESS_TEXT` text-selection mechanism.
- The honest support boundary is **host apps and text fields that expose third-party Process Text actions**, not every Android app.
- V1 and V2 use the Gemini Developer API free tier through Firebase AI Logic. They require connectivity and send only the selected text plus the requested operation to Google.
- Hinglish—Hindi written in Latin script, often mixed with English—is a first-class V1/V2 input and output mode, not an unsupported edge case.
- Read-only selections show a compact result with Copy. Editable selections additionally offer explicit Replace.
- The active keyboard is independent of the V1 flow. V1 does not implement an IME, Accessibility Service, overlay, app-owned backend, account, or history.
- The initial cloud model is the lowest-latency stable Gemini Flash-Lite model that passes the frozen English/Hindi/Hinglish benchmark; the exact model ID is pinned per release.
- V2 adds Explain, Grammar, Rewrite, and Simplify through typed operation contracts using the same cloud provider.
- On-device/offline models move to V3 evaluation so the Process Text and UI layers do not have to change.
- A custom AI keyboard is a separately gated product surface, not a V1 extension.
- The Gemini unpaid tier is acceptable for development and product validation, but it is not compatible with a strong privacy-first production claim: Google's current terms permit product-improvement use and human review of unpaid API inputs/outputs and say not to submit sensitive, confidential, or personal information. This is a release decision, not a footnote.

## Document set

- [Product requirements](docs/PRODUCT_REQUIREMENTS.md) — V1 source of truth: users, scope, flows, requirements, acceptance criteria, metrics, and open decisions.
- [Feasibility review](docs/FEASIBILITY_REVIEW.md) — assessment of the supplied specification, constraints, drawbacks, recommended changes, and risk register.
- [Technical plan](docs/TECHNICAL_PLAN.md) — platform contract, proposed architecture, lifecycle, state model, and staged implementation plan.
- [Validation plan](docs/VALIDATION_PLAN.md) — Process Text compatibility spike, Gemini/model benchmark protocol, quality evaluation, and launch gates.
- [Privacy and security](docs/PRIVACY_AND_SECURITY.md) — data lifecycle, privacy claims, threat model, SDK review, and verification checklist.
- [Roadmap and model strategy](docs/ROADMAP_AND_MODEL_STRATEGY.md) — V0–V4 product sequencing and the role of Qwen, Llama, Gemma, specialist models, and platform GenAI.
- [Cloud and Hinglish decision](docs/CLOUD_AND_HINGLISH_DECISION.md) — the V1/V2 Gemini decision, Hinglish contract, privacy conflict, and migration boundary.
- [Implementation plan](docs/IMPLEMENTATION_PLAN.md) — concrete, ordered engineering steps (repo/module layout, stage-by-stage tasks) to build V1 against the gates defined above.

## Evidence set

- [Compatibility matrix](benchmark/compatibility_matrix.csv) — per-field/host Process Text discovery results.
- [Gate 0 result](benchmark/gate_0_result.md) — narrative findings: bugs found and fixed, privacy/lifecycle audit, what's still open.
- [Gate 0 scorecard](benchmark/gate_0_scorecard.md) — formal walkthrough of every Validation Plan §2.5 pass criterion.

## Recommended build order

1. Freeze the open decisions required for the platform spike. **Done for Stage 0A's needs** — see `docs/IMPLEMENTATION_PLAN.md` §0.
2. Prove cross-app `ACTION_PROCESS_TEXT` input, read-only display, and editable replacement using a deterministic local stub. **In progress** — confirmed on two emulator OS bands; physical-device matrix still open.
3. Prove Firebase AI Logic, App Check, quota handling, and content-safe cloud calls in a development project. **Not started.**
4. Benchmark eligible stable Gemini Flash-Lite models for English, Hindi, and Hinglish on physical devices and realistic Indian networks. **Not started.**
5. Build and harden only the narrow V1 flow. **Not started.**
6. Before any public release, resolve the unpaid-tier privacy gate by accepting a clearly non-private positioning, enabling a paid data-processing posture, or moving content processing on-device. **Open decision — blocks release, not engineering.**
7. Add V2 selection operations only after V1 evidence.
8. Treat an IME as a new product decision with a separate threat model.

## Document provenance

These documents were prepared on 2026-07-30 after reviewing:

- `/Users/afroz/Downloads/InstantTranslate_Android_Development_Specification.docx`
- The initial V1 and long-term product vision supplied in the project conversation
- Current official Android, Google ML Kit, model-provider, and mobile-runtime documentation linked throughout this document set

The original specification remains useful background. [Product requirements](docs/PRODUCT_REQUIREMENTS.md) supersedes it where the two differ.
