# Validation, Benchmark, and Launch Plan

| Field | Value |
|---|---|
| Status | Protocol draft; results not yet collected |
| Date | 2026-07-30 |
| Rule | Freeze protocol before comparing provider/model candidates |

## 1. Purpose

This document prevents a successful demo on one phone from becoming an unsupported product claim. It defines:

- the Android Process Text compatibility spike;
- the common English/Hindi/Hinglish Gemini benchmark;
- resource, network, quota, and privacy tests;
- human quality evaluation; and
- evidence gates from proof of concept through release.

All performance tests use release builds and physical devices unless a row is explicitly a compatibility-only emulator test.

## 2. Gate 0 — Android workflow viability

### 2.1 Spike implementation scope

The first build contains no translation SDK. It uses a deterministic local test mapping or equally trivial transformation so that model initialization, quality, and networking cannot hide platform behavior.

It must demonstrate:

- action discovery from a separate controlled host package;
- exact selected-text receipt;
- safe plain-text conversion;
- correct editable/read-only handling;
- a usable read-only result surface;
- explicit editable replacement;
- Copy;
- cancellation with no modification;
- malformed/oversized-input handling; and
- cold and warm platform-only latency.

### 2.2 Controlled host fixtures

Use a separate test host app containing:

- selectable read-only `TextView`;
- editable single-line `EditText`;
- editable multiline `EditText`;
- password field as a negative case;
- Android Views and Compose text surfaces;
- WebView-hosted selectable text; and
- a deliberately custom selection implementation as a negative/control case.

Do not use a same-package host as the primary proof; AOSP treats same-package Process Text discovery differently.

On Android 11+, build controlled-host variants with and without the official Process Text package-visibility `<queries>` declaration. Separately A/B test the receiver application's app-level `android:forceQueryable` setting only in the spike. Record whether it changes discovery and weigh that against making installation more broadly visible to other apps before any production decision. See Android's [custom text-selection package-visibility guidance](https://developer.android.com/training/package-visibility/use-cases#show-custom-text-selection-actions).

### 2.3 Compatibility matrix

Record exact model, Android build, security patch, RAM, active keyboard, host app version, field type, action location, input received, read-only flag, Copy result, Replace result, and notes.

Minimum OS bands:

| Band | Purpose |
|---|---|
| API 23 | Contract minimum |
| API 26 and 27 | Known Android 8.x selection-menu risk |
| API 30 | Android 11 package-visibility behavior |
| API 31–33 | Modern OEM behavior |
| Current stable Android | Primary release behavior |

Minimum OEM/device families:

- Pixel/AOSP-like device;
- Samsung Galaxy;
- OnePlus;
- Xiaomi or Redmi; and
- one 4 GB low-memory device.

Representative external hosts:

- Chrome or another Chromium browser;
- Gmail;
- Google Messages;
- WhatsApp;
- Telegram;
- Google Docs;
- Samsung Notes on Samsung;
- one Compose-heavy application;
- one PDF viewer; and
- one application known to use custom/non-standard selection.

These are test subjects, not a promise that every row must support Process Text. Unsupported rows are a product finding.

Keyboard matrix in editable fixtures:

- Gboard;
- Samsung Keyboard;
- Microsoft SwiftKey; and
- one additional installed IME if available.

The expected conclusion is that the IME does not change the contract. Any observed difference must be recorded as host/device behavior.

### 2.4 Platform input cases

- English, Hindi, mixed script, Romanized Hindi, and Hinglish
- Leading/trailing whitespace and multiple newlines
- Emoji and surrogate pairs
- Combining characters and Hindi matras
- Bullets, tabs, punctuation, and bidirectional characters
- Numbers, dates, currencies, URLs, and email addresses
- Styled/spanned input
- Empty/whitespace-only input
- Near-limit and over-limit input
- Wrong action, wrong MIME type, missing extra, wrong extra type, and malformed parcel
- Multiple rapid launches
- Rotation, backgrounding, Back, process death, and source-field change while result is open

### 2.5 Gate 0 pass criteria

- Exact input is received in every matrix row that declares Process Text support.
- Read-only input never exposes Replace.
- Explicit Replace works in every matrix row declared replacement-compatible.
- Copy works from the result surface.
- Cancel, Back, and errors leave the host unchanged.
- No Accessibility, overlay, IME, clipboard-read, background-service, or dangerous permission is used.
- Unsupported hosts and overflow placement are documented.
- The product support statement is revised to match evidence.
- No selected text appears in network traffic, logs, persistent storage, notifications, or Recents snapshots during the stub flow.

If this gate fails for the target host segment, stop before cloud-provider work and reconsider the product—not the permission scope.

## 3. Gate 1 — Gemini cloud and model bake-off

### 3.1 Candidates

Required:

- Firebase AI Logic with Firebase App Check and Gemini Developer API unpaid quota;
- the lowest-latency stable Gemini Flash-Lite model eligible for the chosen tier;
- at least one stronger stable Gemini Flash candidate as a quality comparator if eligible; and
- the deterministic local stub for platform-overhead comparison.

Rules:

- pin every model ID and client SDK version;
- do not compare preview models as the default release candidate unless no stable model meets scope;
- do not enable model tools, browsing, grounding, files, chat history, or URL retrieval;
- enforce App Check in the production-like test build; and
- use the same typed prompt/output contract for model comparisons.

### 3.2 Reference devices

Select and freeze exact devices before results:

| Tier | Minimum intent | Role |
|---|---|---|
| Low | 4 GB RAM, older/mid CPU, still within min supported Android | Failure/resource boundary |
| Mid | 6–8 GB RAM, mainstream current Samsung/OnePlus class | Primary release gate |
| High | Recent Pixel or Snapdragon flagship | Upper-bound comparison |

Record thermal state, battery level, power mode, available storage, carrier/network, signal, VPN/private DNS, Firebase/App Check state, and whether the process has an existing connection.

### 3.3 Workloads, networks, and states

Text lengths:

- 1–3 words;
- normal selections up to 50 characters;
- normal selections up to 200 characters;
- 201–1,000 characters; and
- near the proposed hard limit.

Provider/client states:

1. process dead, Firebase client uninitialized, App Check token cold, connection cold;
2. process alive, client uninitialized;
3. client initialized, App Check token warm, connection cold;
4. process/client/connection warm;
5. airplane mode;
6. high-latency or lossy network;
7. App Check rejected or unavailable;
8. free-tier quota/rate limit exhausted;
9. provider/model unavailable or invalid response;
10. repeated alternating English/Hindi/Hinglish requests; and
11. low-memory/background eviction followed by a new request.

Named network profiles:

- stable Wi-Fi with measured bandwidth/RTT;
- stable 5G/4G with measured bandwidth/RTT and signal;
- degraded mobile profile with injected latency/loss;
- captive/offline failure; and
- VPN/private-DNS profile if it is inside the supported user segment.

### 3.4 Timing definitions

Measure:

- `T_action`: automated tap on the selection action;
- `T_receive`: Process Text Activity receives and validates input;
- `T_direction`: direction available;
- `T_client_ready`: Firebase client ready;
- `T_attested`: App Check token ready;
- `T_request_sent`: request body sent;
- `T_first_output`: first provider output observed when exposed by the SDK;
- `T_response_end`: complete validated response returned;
- `T_render`: usable result visible.

Derived:

- platform entry = `T_receive - T_action`;
- direction = `T_direction - T_receive`;
- client readiness = `T_client_ready - T_direction`;
- attestation = `T_attested - T_client_ready`;
- request setup/upload = `T_request_sent - T_attested`;
- provider/network first output = `T_first_output - T_request_sent`;
- completion = `T_response_end - T_first_output`;
- render = `T_render - T_response_end`;
- end-to-end = `T_render - T_action`.

Use a controlled host, automated interaction, Perfetto/trace markers, and a visual/high-speed sanity check. App timing must use monotonic clocks. Do not log selected content beside timings.

For each measured workload/state:

- at least 10 untimed stabilization runs where appropriate;
- at least 100 measured warm runs per device/network/operation slice where quota permits;
- at least 30 measured cold-process/connection runs per primary device/direction; and
- report P50, P95, P99, minimum, maximum, and failures.

Do not average English → Hindi, Hindi → English, Hinglish → English, and English → Hinglish into one number without retaining each slice. Record provider failures and quota consumption, not only successful latency.

### 3.5 Resource measurements

For each candidate record:

- AAB and universal APK size;
- Play-style per-device download estimate;
- installed app bytes;
- Java heap, native heap, PSS, RSS, and peak incremental PSS;
- CPU time and thread count;
- energy/charge delta for a fixed repeated workload;
- time and memory recovery after the result closes;
- request and response bytes per operation/text length;
- radio/network energy for fixed Wi-Fi and mobile workloads;
- Firebase/App Check initialization traffic;
- network activity during successful, failed, cancelled, and idle states; and
- app-owned scheduled work/CPU/network while idle.

Measure on clean installs and upgrades. Separate app code, Firebase AI Logic, App Check/Play Integrity, and other transitive footprint.

### 3.6 Provisional V1 budgets

These are planning gates, not measured claims. Freeze or deliberately revise them before viewing comparative results.

| Metric | Target | Provisional ceiling / rule |
|---|---:|---:|
| Result/loading shell after Activity creation | P95 ≤150 ms | No network wait before first state |
| Full response, ≤200 chars, stable reference Wi-Fi | P50 ≤1,000 ms | P95 ≤2,000 ms; revise only by explicit sign-off |
| Full response, stable mobile | Report P50/P95/P99 | Product sign-off required |
| Direction/script policy, clear English/Devanagari corpus | P95 ≤50 ms | ≥98% correct clear-script routing |
| Cold client/App Check/connection | Report separately | No persistent service workaround |
| Per-device app download excluding models | ≤25 MB | ≤35 MB |
| Incremental peak PSS on mid device | ≤100 MB | ≤175 MB and no low-device OOM |
| Intended request data | Selected text + bounded controls only | No surrounding/clipboard/host/history content |
| Offline failure safety | 100% | Clear unavailable state; source never modified |
| Quota/provider failure safety | 100% | Honest state; bounded retry; source never modified |
| App-owned idle work | None | No scheduled work; CPU/network at measurement noise floor |
| Reliability | 0 crash/ANR | 1,000 repeated workflow runs |

If no candidate satisfies the gates, change the product trade-off explicitly. Do not quietly weaken measurements after choosing a favorite model.

### 3.7 Weighted decision scorecard

Every hard response-validity, disclosure, security, privacy-positioning, and reliability gate is pass/fail. Only model candidates that pass are weighted:

| Dimension | Weight |
|---|---:|
| Human English/Hindi/Hinglish quality and fidelity | 35 |
| Warm full-response latency | 20 |
| Cold client/connection latency | 5 |
| Provider/schema reliability and safe failure | 10 |
| App size, memory, and radio energy | 5 |
| Quota/capacity behavior | 10 |
| Terms, disclosure, and privacy fit | 10 |
| Implementation/maintenance simplicity | 5 |

Record raw measures beside normalized scores. The decision record must list candidate versions, rejection reasons, uncertainty, and re-evaluation triggers.

## 4. Translation quality protocol

### 4.1 Corpus

Freeze a versioned 450–700 item corpus with balanced English → Devanagari Hindi, Hindi → English, Hinglish → English, and English → natural Hinglish examples:

- one-word and short conversational phrases;
- ordinary messages and 50–200 character sentences;
- formal and informal Hindi;
- names, brands, acronyms, numbers, dates, times, money, and units;
- negation, comparison, tense, gender, politeness, and modality;
- commands, questions, requests, and safety-relevant wording;
- idioms and colloquial text;
- Romanized Hindi spelling variation and Hindi-English code-mixing as first-class slices;
- informal constructions such as “kal mujhe office aane me late ho jayegi”;
- Devanagari ↔ Romanized Hindi normalization cases;
- mixed punctuation, emoji, URLs, emails, and newlines; and
- longer paragraphs reported outside the primary latency slice.

Include adversarial meaning cases such as “do not,” dosage/quantity, before/after, less/more, can/cannot, and positive/negative sentiment reversals.

### 4.2 Review method

Use two independent fluent bilingual reviewers, blinded to model identity. Adjudicate disagreements of two or more points.

Score each result:

| Score | Meaning |
|---|---|
| 5 | Accurate, complete, natural, and preserves protected tokens |
| 4 | Meaning fully preserved with minor fluency/word-choice issue |
| 3 | Understandable but contains noticeable omission, awkwardness, or mild drift |
| 2 | Major meaning error, omission/addition, or unusable phrasing |
| 1 | Wrong, contradictory, fabricated, or potentially harmful |

Also tag:

- critical meaning reversal;
- unsupported addition/hallucination;
- omission;
- named-entity/number corruption;
- grammar/fluency issue;
- script/transliteration issue; and
- direction-routing failure;
- unnecessary translation of an English loanword;
- prompt-injection compliance; and
- response-schema violation.

### 4.3 Quality gate

- mean human score ≥4.0/5 overall and per direction;
- at least 95% of release-scope items score ≥3;
- zero unresolved score-1 results;
- zero critical meaning reversals in the protected critical subset;
- original input always previewed before Replace; and
- each Hinglish slice independently meets the release threshold.

Automatic BLEU/ChrF/COMET-style metrics may support regression testing but cannot replace human release review.

## 5. Cloud lifecycle, quota, and failure tests

- Clean install before cloud disclosure
- Disclosure accepted, declined, and notice-version change
- Firebase initialization and App Check debug/Play Integrity separation
- Unattested, replayed, expired, and rejected App Check states where reproducible
- Wi-Fi/mobile success and network switch during a request
- Airplane mode, DNS failure, TLS failure, timeout, and cancellation
- `429 RESOURCE_EXHAUSTED`, transient 5xx, invalid model, and malformed/oversized response
- Process death and rotation during the request/result
- Pinned model unavailable/deprecated and reviewed model migration
- Per-user and project quota behavior under controlled load
- Bounded retry behavior with no retry storm
- App uninstall removes app-owned preferences and cached content
- No selected text reaches Firebase/provider before acknowledgement
- No selected text is placed in Remote Config, analytics, crash reports, or App Check diagnostics

Record provider-controlled model behavior, quota configuration, default per-user limits, and every reviewed model/config change.

## 6. Privacy and security validation

Run the exact procedures in [Privacy and security](PRIVACY_AND_SECURITY.md), including:

- proxy/packet inspection during setup, attestation, cloud operations, failures, and idle;
- `logcat` and crash-record review with distinctive canary text;
- app-private/shared storage search after success, error, rotation, process death, and reboot;
- saved-state, Recents, notification, and clipboard checks;
- malformed intent and oversized-input tests;
- dependency/merged-manifest inspection; and
- app idle CPU/network observation.

A hash of selected text also counts as selected-content persistence because short phrases can be recovered by guessing.

## 7. Gate 2 — V1 alpha

Pass when:

- disclosure and every cloud failure state work;
- language/script policy passes the clear-language gate and exposes ambiguity;
- English/Hindi/Hinglish quality gates pass for the pinned model;
- App Check is enforced in the production-like build and no raw Gemini key exists in the APK;
- lifecycle/cancellation/process-death tests pass;
- 1,000 repeated workflows have no crash or ANR;
- all declared privacy assertions pass;
- no app-owned idle work is found; and
- the compatibility matrix is current.

## 8. Gate 3 — Release

Pass when:

- every P0 requirement maps to a passing test;
- selected provider, SDK, prompt/schema version, and model ID are locked;
- English/Hindi/Hinglish review signs off the release corpus;
- Play Data safety, privacy notice, licensing, attribution, internet, and provider disclosures are accurate;
- capacity testing supports the expected audience;
- the external-release data posture has explicitly resolved the unpaid-tier privacy conflict;
- the supported-host statement matches observed results;
- UI accessibility checks pass in English and Hindi;
- there are no unresolved severity-1 or severity-2 correctness, privacy, data-loss, crash, or ANR defects; and
- benchmark results are preserved with build/device identifiers.

## 9. Result templates

### Process Text compatibility row

| Build | Device/OS | Host/version | Field | Keyboard | Action visible/location | Input exact | Read-only correct | Copy | Replace | Notes |
|---|---|---|---|---|---|---|---|---|---|---|

### Cloud performance row

| Build | SDK/model | Device | Network | Operation/direction | Chars | State | Runs | P50 | P95 | P99 | Failures |
|---|---|---|---|---|---:|---|---:|---:|---:|---:|---:|

### Provider/model decision row

| Candidate/version | Hard gates | Quality | Latency | Reliability | Quota | Resources | Terms/privacy | Complexity | Weighted score | Decision |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
