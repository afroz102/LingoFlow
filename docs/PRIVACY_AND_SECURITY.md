# Privacy and Security Model

| Field | Value |
|---|---|
| Status | V1 design requirement |
| Date | 2026-07-30 |
| Data principle | Selected content is ephemeral in the app and transmitted only for the requested Gemini operation |

## 1. Accurate privacy promise

Recommended V1 wording:

> V1 and V2 send the text you explicitly select to Google Gemini to perform the language operation. InstantTranslate does not keep translation history or use selected content in app analytics. Internet access is required.

The first-use disclosure must also say:

> The initial service uses Gemini's unpaid API quota. Under Google's current terms, unpaid-service inputs and outputs may be used to improve Google products and may be reviewed by humans. Do not use this version for sensitive, confidential, or personal text.

Do not say:

> Private by design; your text never leaves your device.

That is false for V1/V2. The current [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms) distinguish unpaid and paid services. For unpaid services, Google states that submitted content and generated responses may be used to provide, improve, and develop products, human reviewers may process inputs/outputs, and users must not submit sensitive, confidential, or personal information. Paid service terms state a different product-improvement posture. Legal/product review must verify the current terms and regional treatment at release.

This creates a material conflict with the original privacy-first positioning. Arbitrary selected text can contain personal or confidential data, and the app cannot reliably classify sensitivity locally. The unpaid tier is therefore approved for development and controlled validation, not automatically approved for a public privacy-first release.

## 2. Data inventory

| Data | Purpose | Storage | Network | Retention |
|---|---|---|---|---|
| Selected source text | Perform current language operation | Volatile app memory only | Sent over TLS through Firebase AI Logic to Gemini | App copy destroyed when request/result lifecycle ends; provider handling follows current terms |
| Generated output | Display/copy/replace | Volatile app memory only | Returned by Gemini | App copy destroyed when request/result lifecycle ends; provider handling follows current terms |
| Editability signal | Decide whether Replace is allowed | Volatile memory only | Never | Request lifetime |
| Operation and language/script controls | Constrain provider request | Volatile; preferences may persist | Sent with the Gemini request | Request lifetime; preference until changed |
| Direction/script preference | User configuration | Local preferences | Not sent until needed for a request | Until changed/uninstall |
| Disclosure version/acknowledgement | Prevent undisclosed transmission | Local non-content preference | Not required in prompt | Until notice changes/uninstall |
| App Check token and installation/device attestation | Protect cloud quota from unauthorized clients | SDK-managed | Firebase/App Check | Provider-defined |
| Provider/model ID | Compatibility and routing | Build/config metadata | Firebase/Gemini | Until release/config change |
| Performance timing | Local testing or content-free diagnostics | Development results; production undecided | None by default | Defined per benchmark/release policy |
| Error category | Debug reliability without content | Development only by default | None by default | Defined per benchmark/release policy |
| Clipboard output | User-requested reuse | Android clipboard | Governed by Android/other apps | Outside app control after Copy |

No source/result hash, embedding, token list, length-associated content sample, screenshot, notification, filename, or crash breadcrumb is permitted.

## 3. Data lifecycle

```text
Untrusted external Process Text intent
          │
          ▼
Validate action/type/extra/size
          │ reject safely
          ▼
Flatten to immutable plain string in memory
          │
          ├── Language/script policy
          ├── Disclosure gate
          └── Firebase AI Logic + App Check
                         │
                         ▼
                    Gemini API
                         │
                         ▼
                 Transient result UI
                    │
                    ├── Close → discard
                    ├── Copy → explicit Android clipboard boundary
                    └── Replace → return only translated plain text to host
```

Provider initialization is a separate non-content path:

```text
App initializes Firebase
→ obtains App Check token
→ resolves pinned model configuration
→ verifies readiness
```

## 4. V1 prohibited behavior

- Logging source or output at any level
- Persisting source/output through saved instance state, preferences, files, database, cache, backup, or history
- Analytics, crash reports, breadcrumbs, screen recordings, or session replay containing content
- Automatic clipboard writes or clipboard polling
- Notifications containing source/output
- Sharing source/output with another app except explicit Copy or Replace
- Sending selected text before disclosure acknowledgement
- Sending surrounding text, clipboard content, host-app identity, account history, or earlier requests
- Silent cloud-provider or model fallback
- Background translation or prefetch based on clipboard/selection activity
- Deriving caller identity or behavioral profiles from translation requests
- App-owned use of source/output for model training, evaluation, or prompt corpora
- Retaining content hashes as “anonymous” diagnostics
- Embedding a raw Gemini API key in the APK
- Enabling model tools, web browsing, grounding, URL fetching, or file access for V1/V2 language operations

## 5. Threat model

| Threat | Attack or failure | Required control |
|---|---|---|
| Crafted external invocation | Any app invokes the exported Activity with wrong action/type/large/malformed extras | Validate before any provider request, catch parcel failures, cap input, treat unknown editability as read-only |
| Content injection into diagnostics | Source appears in logs, exceptions, analytics, crash messages, trace names, or test screenshots | Content-free error/timing schema; review logs and generated reports with canary strings |
| Process/state persistence | Android saves Activity state or a Recents snapshot containing text | Do not save content state; exclude sensitive result from Recents; verify process-death and task snapshots |
| Clipboard exposure | User copies a sensitive translation and other system components can access it | Copy only after explicit action; disclose clipboard boundary; consider sensitive clipboard metadata on supported Android |
| SDK telemetry mismatch | Third-party SDK collects identifiers/configuration beyond product wording | Review official disclosure and actual traffic for every version; update Play/privacy text or reject SDK |
| Unpaid-provider data use | User selects personal/confidential text despite provider terms | Prominent warning, acknowledgement, no background sends, controlled-test limitation, and external-release gate requiring paid/local or changed positioning |
| API/quota theft | Extracted client configuration or cloned app consumes shared free quota | Firebase AI Logic proxy, enforced App Check with Play Integrity, per-user rate limits, replay protection assessment, quota monitoring |
| Prompt injection in selected text | Selection asks the model to ignore the operation or reveal system instructions | Quote selection as data, fixed typed task, no tools/history, strict output validation, preview before Replace |
| Provider response injection | Generated output contains markup, links, or unexpected instructions | Treat output as plain text, never execute/render HTML, cap output, preview before Replace |
| Quota/availability failure | Shared free tier is exhausted or provider/model is unavailable | Explicit rate-limit/unavailable state, bounded retry, no automatic provider switch, source unchanged |
| Package discoverability | An app-level `forceQueryable` workaround makes installation visible to a broader set of apps | Keep disabled by default; A/B test need; document and approve the privacy/discoverability trade-off |
| Model download tampering | Corrupt or substituted future model asset | HTTPS, fixed source/version, cryptographic checksum/signature, atomic install, compatibility manifest, rollback |
| Denial of service | Repeated or very large requests exhaust memory/CPU | Input cap, cancellation, single active inference policy, resource failures that return safely |
| Host replacement mismatch | Custom host modifies or rejects returned text | Preview, explicit Replace, return plain text only, retain Copy fallback |
| Model hallucination | Fluent output adds facts or changes meaning | Fixed Translate contract, low-variance settings, quality corpus, original preview, explicit replacement, bounded output |
| Sensitive keyboard context in V4 | IME observes passwords or sends composing text unexpectedly | Separate keyboard threat model, sensitive-field bypass, local default, explicit invocation, no every-keystroke AI |

## 6. Exported Activity security requirements

- Accept only the intended Process Text action and `text/plain`.
- Read the text defensively as a `CharSequence`, then flatten to plain text.
- Reject missing, empty, or excessive input before initializing the provider or sending a request.
- Treat missing/invalid read-only state as read-only.
- Do not trust `callingPackage`, referrer, or source package as authentication.
- Never open incoming URIs, execute links, render HTML, or relay nested intents/extras.
- Return only translated plain text on explicit Replace.
- Cancel and every failure return no replacement.
- Limit concurrent work and cancel obsolete requests.
- Keep provider/network work off the main thread; perform only rendering on the main thread.

## 7. Dependency, provider, and SDK acceptance review

For every production dependency and version, record:

- publisher, artifact, version, license, and transitive dependencies;
- permissions and components added to the merged manifest;
- app-start initialization;
- network hosts contacted on clean install, attestation, successful operation, failures, and idle;
- identifiers, configuration, sizes, events, errors, and content collected;
- opt-out or configuration controls;
- local files/cache written;
- provider/model source, version/deprecation policy, configuration change, and fallback behavior;
- attribution/branding obligations; and
- response if the dependency is abandoned or terms change.

For Firebase AI Logic and Gemini specifically, review the current:

- [Firebase AI Logic overview](https://firebase.google.com/docs/ai-logic);
- [App Check guidance](https://firebase.google.com/docs/ai-logic/app-check);
- [supported models](https://firebase.google.com/docs/ai-logic/models);
- [Gemini rate limits](https://ai.google.dev/gemini-api/docs/rate-limits);
- [Gemini pricing and tier data-use table](https://ai.google.dev/gemini-api/docs/pricing); and
- [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms).

This review must be repeated on version upgrades.

## 8. Verification procedure

Use a unique, non-secret canary source and output, then search for exact and normalized variants after each path.

### Network

- Capture clean install and first launch.
- Capture Firebase/App Check initialization.
- Capture successful English, Hindi, and Hinglish operations.
- Capture error/cancel paths and 30 minutes idle.
- Repeat in airplane mode.
- Verify selected content appears only in the intended encrypted request body, not URLs, headers, unrelated telemetry payloads, or DNS labels.
- Verify no surrounding text, caller identity, clipboard data, earlier request, or unrequested capability is transmitted.
- Document traffic that cannot be decrypted and the reason it is still accepted or rejected.

### Logs and diagnostics

- Inspect `logcat`, structured traces, crash reports, ANR traces, analytics debug views, and test artifacts.
- Trigger malformed input, provider errors, timeout, quota exhaustion, cancellation, process death, and replacement failure.
- Confirm no source/output, hash, substring, or tokenized form appears.

### Storage and state

- Search app-private files, cache, preferences, databases, external/shared storage, backups, task state, screenshots, and Recents.
- Test success, Copy, Replace, error, rotation, backgrounding, process death, force close, reboot, upgrade, and uninstall.
- Confirm only approved non-content preferences and documented SDK caches remain.

### Idle and background

- Inspect scheduled jobs, alarms, services, wake locks, background network, and CPU after setup and after translation.
- Confirm no app-owned recurring work.

### Manifest and build

- Audit merged manifest permissions, exported components, providers, receivers, and services.
- Generate a dependency/license inventory.
- Verify release build logging and debug tooling are disabled or content-safe.

Any selected-content finding outside the intended Gemini request/response is release-blocking.

## 9. User-facing controls

V1:

- show cloud processing before the first request;
- identify Gemini as the provider and state that internet is required;
- disclose the unpaid-tier data-use warning;
- require acknowledgement when the notice version changes;
- expose offline, timeout, quota, and provider failure honestly;
- state that no history is kept;
- expose Copy as a deliberate action; and
- link to the supported-host and privacy explanation.

There is no hidden local mode in V1/V2. Choosing Translate or another operation after disclosure is the per-request transmission action.

## 10. V1/V2 cloud boundary

Every cloud capability requires:

- explicit opt-in before the first transmitted request;
- a visible “cloud” indicator before execution;
- provider, purpose, data sent, retention, training use, region, and deletion disclosure;
- no silent fallback to another provider/model;
- an explicit operation invocation for each selected text;
- transport and authentication review;
- content-safe application logs; and
- clear unavailable behavior when offline.

The general provider disclosure may be remembered, but it does not authorize background use, surrounding context, keyboard context, or a future provider with different data terms.

Before external beta, choose and approve one:

1. paid Gemini processing with current data-processing/legal review;
2. on-device processing; or
3. clearly non-private positioning limited to non-sensitive text.

The unpaid free tier is not a reliability or privacy foundation for an unrestricted public cross-app tool.

## 11. Future open-model boundary

V3 local open-weight models can improve content privacy and offline availability but add supply-chain and device risks:

- multi-hundred-megabyte or multi-gigabyte download;
- model integrity and version compatibility;
- license/notice obligations;
- memory, thermal, and battery pressure;
- quantization-induced quality regressions; and
- potentially unsafe or fabricated outputs.

They must be optional downloadable assets, verified before use, removable, and benchmarked on the declared device tier. “Local” does not remove the need for safety and quality controls.

## 12. Future keyboard boundary

The IME requires its own privacy/security review before implementation:

- exactly what composing/surrounding text is visible;
- behavior in password, payment, OTP, private/incognito, and enterprise-managed fields;
- whether any inference runs without an explicit user action;
- crash recovery and basic typing when models fail;
- local/cloud indicator and consent;
- clipboard and personalization retention;
- learned dictionary storage/deletion;
- no raw keystroke analytics; and
- Play policy and security testing.

V1/V2 cloud approval does not approve sending every keystroke or surrounding editor text from the keyboard.
