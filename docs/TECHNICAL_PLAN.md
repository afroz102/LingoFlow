# Technical Plan — InstantTranslate V1

> **Backend update — 2026-10-04:** The user requested our own backend without authentication.
> [BACKEND_SETUP.md](BACKEND_SETUP.md) describes the current Workers/SQLite test implementation.
> Firebase, Supabase, App Check, and “no app-owned backend/database” statements below are historical;
> the selection workflow, language requirements, and unpassed quality/release gates remain applicable.

| Field | Value |
|---|---|
| Status | Proposed; no code implemented |
| Date | 2026-07-30 |
| Architecture style | One Android app module with small logical boundaries |
| Provider decision | Gemini Developer API free tier through Firebase AI Logic for V1/V2; exact model pending benchmark |

## 1. Technical objective

Validate and then implement the smallest safe system that converts selected English, Hindi, or Hinglish text from a supported external Android host into a translation and exposes Copy or explicit Replace.

The platform integration and cloud provider must be independently replaceable, but V1 must not introduce a chat framework, multiple Gradle modules, DI framework, database, app-owned backend, service, or worker.

## 2. System context

```text
External host app
    │
    │ ACTION_PROCESS_TEXT + selected CharSequence + read-only signal
    ▼
Process Text adapter Activity
    │ validated plain string + editability
    ▼
Translate-selection coordinator
    ├── Direction policy
    ├── Cloud readiness and disclosure
    ├── Translation provider
    └── Content-safe timing/error signals
    │
    │ bounded request through Firebase AI Logic + App Check
    ▼
Gemini Developer API
    ▼
Compact result presenter
    ├── Copy
    └── Replace result, editable source only
```

The external host and selected text are untrusted and may be non-standard. Firebase/Gemini is a replaceable provider. The result Activity is the only app surface that temporarily holds selected content.

## 3. V1 logical boundaries

These are package/responsibility boundaries inside one application module, not separate services or modules.

### Text-action adapter

Responsibilities:

- expose the Process Text intent filter;
- validate action, MIME type, expected extra, read-only signal, and size;
- catch malformed input;
- flatten an accepted `CharSequence` to a plain immutable string;
- start the translation flow; and
- return only an explicit replacement result.

It knows Android intent semantics but nothing about a concrete model API.

### Translate-selection coordinator

Responsibilities:

- reject non-translatable input before provider work;
- resolve or request source/target direction;
- verify disclosure, connectivity, and provider availability;
- coordinate cancellation and lifecycle;
- invoke the translation provider;
- expose typed UI states; and
- emit timing/error categories with no source or result content.

### Direction policy

Responsibilities:

- honor an explicit English → Hindi or Hindi → English preference;
- use a cheap script signal for clear Devanagari;
- classify short Latin, mixed, and Romanized text as potentially ambiguous before the request;
- allow the provider to interpret Hindi semantics expressed in Latin script;
- distinguish Hindi output script as Devanagari or Roman/Hinglish; and
- retain user-correctable language and script controls.

The policy must never represent a low-confidence result as certainty.

### Translation provider boundary

Conceptual contract:

```text
translate(plain text, source language/script hint, target language/script)
→ translated plain text or typed failure
```

The boundary supports English, Devanagari Hindi, and Romanized Hindi/Hinglish in V1. Its public contract does not expose prompts, chat history, Firebase types, or provider-specific response objects.

The Gemini adapter owns the fixed operation prompt and response validation. It sends no tools, browsing, grounding, files, surrounding context, host identity, or previous-request history. Selected text is quoted and treated as data even when it contains instructions.

### Cloud readiness and disclosure

Responsibilities:

- report disclosure-required, offline, connecting, rate-limited, timed-out, provider-failed, and ready states;
- prevent the first selected-content request until disclosure acknowledgement;
- initialize Firebase AI Logic and App Check without embedding a raw Gemini API key;
- apply bounded retries with jitter only where safe;
- map provider failures to content-free typed errors; and
- never persist selected source text.

### Result presenter

Responsibilities:

- render disclosure-required, loading, success, ambiguity, and cloud error states;
- display original and translated text;
- show direction and a switch;
- write the translation to the clipboard only after Copy;
- return translated plain text only after Replace and only for editable input; and
- avoid saving source/result through Activity state, Recents, logs, or notifications.

### Local preferences

Persist only non-content configuration:

- direction: Automatic / English → Hindi / Hindi → English;
- Hindi output script: Devanagari / Roman-Hinglish;
- cloud-processing disclosure acknowledgement version; and
- completed setup/help acknowledgement if useful.

Use the simplest platform storage that satisfies these values. Do not add a repository abstraction or database solely for a few settings.

## 4. Android Process Text contract

The Activity must be externally launchable with:

- action `android.intent.action.PROCESS_TEXT`;
- category `android.intent.category.DEFAULT`;
- MIME type `text/plain`; and
- an explicit exported declaration on Android versions that require it.

The public contract is documented in [`Intent.ACTION_PROCESS_TEXT`](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT).

Implementation requirements:

- assume any app can invoke the exported Activity with crafted data;
- require the expected action and type;
- read the text defensively;
- treat absent/invalid read-only data as read-only;
- reject empty and excessive input before provider initialization or transmission;
- do not trust caller identity as authentication;
- do not forward incoming nested intents, URIs, or arbitrary extras; and
- finish with no replacement on cancellation or failure.

### Result semantics

Read-only source:

- show result and Copy;
- never offer Replace;
- finish without a replacement result.

Editable source:

- show result, Copy, and Replace;
- only Replace returns success with processed plain text;
- Cancel/Back/error returns no modification.

The host may still reject a returned value. Copy is the fallback, not proof that every editable host supports replacement.

### Discoverability limitations

Current AOSP adds Process Text handlers to the overflow menu. Android 8.0/8.1 have known handler-menu behavior, Android 11+ package visibility can affect host queries, and OEM/custom editors vary.

Do not add a discoverability workaround until the controlled-host A/B tests in [Validation plan](VALIDATION_PLAN.md) establish the real problem and privacy trade-off.

## 5. Runtime state model

```text
Request received
    ├── Invalid → safe error → finish
    └── Valid
         ├── No translatable content → explanation → finish
         └── Direction resolved/ambiguous
              ├── Disclosure missing → disclose/acknowledge or finish
              ├── Offline → unavailable/retry or finish
              └── Cloud request
                   ├── cancelled/timeout/quota/provider error → no replacement
                   └── validated success
                        ├── Copy → remain/dismiss
                        ├── Replace if editable → return result
                        └── Close → no replacement
```

Only non-content timing labels and error categories may leave this state machine.

## 6. Provider lifecycle and latency

- Construct the Firebase/Gemini client lazily after a valid request and disclosure acknowledgement.
- Reuse a process-scoped client when the SDK permits to reduce repeated initialization overhead.
- Never create an always-running or foreground service for prewarming in V1.
- Pin the stable Gemini model ID per app release; never silently route to another provider.
- Use explicit deadlines and cancellation; avoid automatic retries for validation, quota, and safety failures.
- If the process is killed, the next request is a legitimate cold client/network path and must remain correct.

The app must measure:

- Process Text Activity creation to request parsing;
- direction resolution;
- Firebase client and App Check readiness;
- connection establishment and request upload;
- provider time to first output and full response;
- first usable render; and
- full automated tap-to-result separately.

Timing records must contain no text, hash, token sequence, language-detection sample, or caller content.

## 7. Provider strategy

### V1/V2 primary provider

Use the Gemini Developer API through [Firebase AI Logic](https://firebase.google.com/docs/ai-logic). This client path keeps the Gemini API key behind Firebase's proxy and supports Android App Check. Production App Check uses Play Integrity; development uses only the documented debug provider.

Benchmark the lowest-latency stable Flash-Lite model that is eligible for the chosen tier. As of 2026-07-30, Firebase lists `gemini-3.5-flash-lite` without a billing requirement, but the implementation must recheck availability, terms, and quotas and pin the selected model version.

The provider passes only when:

- English/Hindi/Hinglish quality passes human review;
- Translate responses preserve meaning without additions or omissions;
- V2 operations follow their typed contracts;
- warm/cold network latency and failure recovery pass frozen gates;
- App Check, quota, timeout, and cancellation paths pass;
- data handling and network behavior match the privacy document; and
- the external-release data posture is explicitly approved.

Free-tier capacity is not a production service-level agreement. The app must treat `RESOURCE_EXHAUSTED` and temporary unavailability as normal recoverable product states.

### V3 local-provider trigger

V3 evaluates local open-weight and platform providers behind the same typed operation contracts. That decision must account for tokenizer/runtime size, model distribution, updates, integrity, RAM, thermal behavior, licensing, and English/Hindi/Hinglish quality.

No V1/V2 request silently falls back between cloud and local providers. The result surface must identify the processing mode once multiple providers exist.

## 8. Proposed V1 application surfaces

### Setup/status Activity

- cloud-processing and unpaid-tier disclosure;
- provider/connectivity status;
- direction and Hindi-script preferences;
- concise usage instructions;
- privacy summary; and
- supported-host limitation.

### Process Text result Activity

- dialog-like, transient, quick to render;
- excluded from Recents if this prevents content snapshots on target versions;
- original and translated text;
- direction indicator/switch;
- Copy and conditional Replace; and
- accessible error/setup states.

No dashboard, navigation framework, history, or chat screen is required.

## 9. Component and permission budget

Expected V1 components:

- one launcher/setup Activity;
- one exported Process Text Activity; and
- Firebase AI Logic and App Check components brought by the selected dependencies.

Expected permission posture:

- internet/network-state access required for cloud operation;
- no dangerous runtime permission;
- no Accessibility Service;
- no overlay;
- no IME;
- no clipboard read/polling;
- no notification listener;
- no microphone/camera/contacts/location/storage permission; and
- no app-owned background service or scheduled worker.

The final merged manifest must be audited because dependencies can add components and permissions.

## 10. Staged implementation plan

### Stage 0A — Platform stub spike

Build only:

- the exported Process Text adapter;
- a deterministic local test translation;
- read-only result display and Copy;
- editable explicit Replace; and
- safe invalid/cancel behavior.

Use a separate controlled host package plus representative real hosts. The same-package path is not sufficient proof.

Exit only when Gate 0 in [Validation plan](VALIDATION_PLAN.md) passes.

### Stage 0B — Freeze benchmark

Before integrating the cloud provider:

- select physical reference devices;
- freeze app/editor/keyboard matrix;
- freeze corpus and human scoring;
- freeze provisional size/RAM/network/latency gates;
- define cold client, cold connection, and warm connection procedures; and
- establish content-network/log/storage audit methods.

### Stage 1 — Gemini cloud spike and benchmark

Implement only the narrow provider boundary using Firebase AI Logic and App Check. Benchmark eligible stable Gemini Flash-Lite candidates in release builds. Append results and record the selected model ID, response contract, terms, quotas, rejected alternatives, and version.

Exit only when Gate 1 passes.

### Stage 2 — V1 alpha

Add:

- cloud disclosure/status;
- language/script policy and correction;
- all result/error states;
- lifecycle, cancellation, process-death, and low-resource handling;
- bilingual UI; and
- privacy hardening.

No future language tool is allowed into this stage.

### Stage 3 — Release hardening

- complete compatibility and repeated-run matrices;
- remove or justify every dependency/component/permission;
- run packet, log, storage, Recents, idle, offline-failure, quota, and timeout audits;
- finish English/Hindi/Hinglish quality review;
- settle unpaid/paid provider posture, attribution, privacy notice, and Play disclosures; and
- publish the support boundary.

## 11. Evolution boundary

When a second real capability is built, evolve toward:

```text
Selection surface ─┐
Future IME surface ├─→ Operation coordinator → typed capability providers
Settings surface ──┘

Translate → typed translation provider
Explain   → typed explanation provider
Grammar   → typed proofreading provider
Rewrite   → typed rewriting provider
Simplify  → typed simplification provider

V1/V2 provider: Gemini cloud adapter
V3 candidate: local open-weight/platform adapter
```

Do not expose one untyped `AIEngine` or chat function. These operations differ in output contract, acceptable latency, replacement safety, context, model, connectivity, and privacy even when Gemini serves all of them.

Extract a shared Gradle module only after the second product surface creates proven reuse.

## 12. Required implementation records

Implementation is not complete without:

- completed Process Text compatibility matrix;
- cloud/model benchmark scorecard with raw device/network/version details;
- selected provider/model decision and rejected alternatives;
- permission/component/dependency inventory;
- privacy/network/log/storage audit;
- fixed quality corpus version and reviewer sign-off; and
- launch-gate checklist.
