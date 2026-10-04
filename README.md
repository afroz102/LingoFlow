# LingoFlow / InstantTranslate

A lightweight Android language utility: select English, Hindi, or Hinglish text in any app that
exposes Android's `ACTION_PROCESS_TEXT` action, and translate it in place via **Select → More →
Translate**.

Status: **Custom backend migration for phone testing (2026-10-04).** The Android app sends
one HTTPS request to our JavaScript backend, hosted on Cloudflare Workers. SQLite in D1 reserves
aggregate rate-limit quota before the backend calls Gemini. There are no accounts, anonymous
sessions, or authentication calls. Gemini credentials remain on the server.

The free hosted backend is live. All 15 backend tests, 20 Android JVM tests, six hosted HTTP
checks, and real English/Hindi translations from an API 34 Android emulator pass. The configured
APK is ready for physical-phone testing; see the setup guide for installation and evidence.

The Android Process Text workflow has recorded stub-provider evidence on API 30 and 34 emulators.
That historical evidence is not a live-cloud or physical-device release pass. Clear English and
Devanagari Hindi routing is implemented; Hinglish ambiguity correction and Romanized Hindi output
remain incomplete. The 458-item corpus and timing harness exist; formal model quality scoring and
the physical-device compatibility matrix remain open.

See [Backend setup and phone testing](docs/BACKEND_SETUP.md) for the current architecture,
configuration, deployment, and verification status. Earlier Firebase-specific planning documents
are historical where they conflict with this user-directed migration.

The working product name in the supplied specification is **InstantTranslate**; the repository name is **LingoFlow**. Final naming remains an open product decision.

## Repository structure

```
app/          The shipping InstantTranslate Android module (platform adapter + HTTP provider)
backend/      Our translation API, SQLite quota schema, Worker and portable Node server
testhost/     Dev-only controlled host app for Process Text compatibility testing — never published
benchmark/    Frozen validation protocol and evidence: compatibility matrix, Gate 0 results,
              the Stage 0B corpus, and the latency harness/aggregation scripts
docs/         Product/engineering planning documents (see Document set below)
```

See [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) §1 for the full module/package
boundary rationale.

## Building and running

Requirements: JDK 17, Android SDK (`compileSdk`/`targetSdk` 34, `minSdk` 23 provisional).

```
./gradlew :app:assembleDebug                                     # build the app
./gradlew :app:testDebugUnitTest                                 # run unit tests
./gradlew :testhost:assembleWithQueriesDebug                     # build the compatibility test host
python3 benchmark/validate_corpus.py                             # gate the benchmark corpus
```

If Gradle reports "Unable to locate a Java Runtime", point it at a JDK 17 explicitly, e.g.
`export JAVA_HOME=/opt/homebrew/opt/openjdk@17` — a Homebrew-installed JDK is not linked onto the
default `PATH`.

`app` has no exported launcher — it's reached only through the Process Text selection menu in
another app. Install both `app` and a `testhost` flavor on a device/emulator, long-press text in
`testhost`, and select **Translate** from the overflow menu to try it.

The app builds without cloud configuration, but translation then returns a provider error.
Copy `backend.properties.example` to `backend.properties` and set the public HTTPS origin
before building. Never put a Gemini API key in this file: its values are embedded in the APK.

### Setting up the backend for phone testing

Follow [docs/BACKEND_SETUP.md](docs/BACKEND_SETUP.md). The free hosted setup uses our Worker,
D1 SQLite database, and the existing Gemini free-tier project on s78.meghnad's Google account.
Neither Firebase nor Supabase is required. A local Node/SQLite server runs the same application
logic for development or later self-hosting.

## Current decision snapshot

- V1 is a native Kotlin Android utility for English ↔ Hindi translation through Android's `ACTION_PROCESS_TEXT` text-selection mechanism.
- The honest support boundary is **host apps and text fields that expose third-party Process Text actions**, not every Android app.
- The current test build uses the Gemini Developer API through our unauthenticated backend. It requires connectivity; selected text passes through Cloudflare to Google. Our database stores aggregate quota counts only, with no text, translations, or user identity.
- Hinglish—Hindi written in Latin script, often mixed with English—is a first-class V1/V2 input and output mode, not an unsupported edge case.
- Read-only selections show a compact result with Copy. Editable selections additionally offer explicit Replace.
- The active keyboard is independent of the V1 flow. V1 does not implement an IME, Accessibility Service, overlay, user-facing account flow, or translation history. Our backend does not create or require user identities.
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
- [Stage 0B frozen protocol](benchmark/stage_0b_frozen_protocol.md) — what is frozen for the Gate 1 bake-off, what is still drafted, and what is blocked on the project owner.
- [Benchmark corpus](benchmark/corpus/) — the 458-item English/Hindi/Hinglish quality corpus and its generated coverage report.
- [Timing harness](benchmark/TIMING_HARNESS.md) — how the Gate 1 latency chain is measured, and the three §3.4 intervals that cannot be measured with the current provider.

## Next milestones

1. Verify the configured backend/Gemini path on a phone with the controlled test host.
2. Finish user-correctable direction controls and Hinglish/output-script preferences.
3. Run the physical-device compatibility matrix and the blinded model-quality benchmark.
4. Harden lifecycle, accessibility, quota handling, and release privacy/security checks.
5. Resolve the external-release privacy posture before publishing; this is still a testing build.

## Document provenance

These documents were prepared on 2026-07-30 after reviewing:

- `/Users/afroz/Downloads/InstantTranslate_Android_Development_Specification.docx`
- The initial V1 and long-term product vision supplied in the project conversation
- Current official Android, Google ML Kit, model-provider, and mobile-runtime documentation linked throughout this document set

The original specification remains useful background. [Product requirements](docs/PRODUCT_REQUIREMENTS.md) supersedes it where the two differ.
