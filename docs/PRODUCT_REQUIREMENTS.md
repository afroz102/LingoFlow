# Product Requirements — InstantTranslate V1

| Field | Value |
|---|---|
| Status | Draft for implementation planning |
| Version | 0.2 |
| Date | 2026-07-30 |
| Product | InstantTranslate, working name |
| Repository | LingoFlow |
| Platform | Native Android, Kotlin |
| V1 languages | English, Hindi, and Romanized Hindi/Hinglish |
| Source of truth | This document |

## 1. Product summary

InstantTranslate V1 lets a user select English, Devanagari Hindi, or Romanized Hindi/Hinglish text in a supported Android text surface, invoke a translation action from Android's native selection menu, and receive a Gemini-generated translation in a compact transient result surface.

The intended experience is:

```text
Select text in a supported app
→ choose Translate, possibly from the overflow menu
→ send the selected text to Gemini after clear cloud disclosure
→ receive a translation
→ Copy, or explicitly Replace when the source is editable
```

The user does not need to replace their keyboard, grant Accessibility permission, monitor the clipboard, create an account, or use an app-owned backend. V1/V2 do require internet connectivity and transmit selected text to Google through Firebase AI Logic.

## 2. Problem

Cross-app translation commonly interrupts the user's task: copy text, open a translator, paste, translate, copy again, and return. Keyboard replacement and Accessibility-based approaches increase setup, trust, privacy, and reliability costs.

V1 tests whether Android's standard text-processing contract can remove most of that friction while keeping the app architecture and the data sent to the cloud narrowly bounded.

## 3. Product outcome

V1 succeeds when a user can:

- complete a one-time cloud-processing disclosure;
- select English, Devanagari Hindi, or Hinglish text in a declared supported host;
- invoke the translation action without opening the app first during normal use;
- receive a usable translation quickly on a working connection;
- copy the result or explicitly replace editable source text;
- correct a mistaken English/Hindi/Hinglish direction or output-script choice; and
- find no translation history or persisted selected text.

V1 does not claim universal cross-app coverage, offline translation, or guaranteed sub-500 ms cloud responses.

## 4. Product principles

1. **Fast in the measured path.** Performance claims use release builds, physical devices, named text lengths, and percentiles.
2. **Minimal disclosed cloud data.** Send only selected text, the requested operation, language/script choices, and the minimum provider-required metadata. Do not send surrounding text, clipboard contents, host-app context, or app-owned user identity.
3. **Native entry point, minimal app surface.** Android owns text selection; InstantTranslate owns a small result Activity and setup/status surface.
4. **Explicit destructive action.** Translation never replaces source text until the user chooses Replace.
5. **Honest capability boundaries.** Unsupported hosts, ambiguous language, connectivity, cloud processing, unpaid-tier data terms, quotas, and provider limitations are disclosed.
6. **Typed operations over generic chat.** Gemini is invoked through narrow Translate/Explain/Grammar/Rewrite/Simplify contracts; selected text is treated as data, not instructions.
7. **No speculative architecture.** V1 uses one app module and only the boundaries required to isolate Android integration and translation.

## 5. Target users and jobs

### Primary user

An English/Hindi bilingual or learner who frequently encounters text in the other language while reading or writing on Android.

### Core jobs

- Understand selected English text in Hindi.
- Understand selected Hindi text in English.
- Translate text being composed and deliberately replace it.
- Understand Romanized Hindi such as “kal mujhe office aane me late ho jayegi.”
- Produce Hindi in either Devanagari or a natural Romanized/Hinglish form when requested.

### Later jobs, outside V1

- Explain, correct, rewrite, or simplify selected text in V2.
- Apply preferred tone, naturalness, or British English.
- Invoke language tools from a dedicated keyboard.

## 6. Supported platform contract

V1 targets Android 6.0/API 23 and later provisionally, because `ACTION_PROCESS_TEXT` was added in API 23. The final minimum SDK is frozen only after the Firebase SDK compatibility review and platform spike are complete.

Support means:

- the host app or text component exposes third-party Android Process Text actions;
- the selected content is non-password text;
- the Process Text handler is discoverable on that Android/OEM build; and
- the host accepts the normal Android result contract if replacement is requested.

Support does **not** mean:

- every Android app, custom editor, PDF viewer, WebView, Compose text surface, or selection implementation;
- guaranteed placement in the first row of the selection toolbar;
- inline rendering inside the host application's toolbar; or
- access to surrounding paragraphs, cursor context, or reliable host-app metadata.

Android documents the input/output contract in [`Intent.ACTION_PROCESS_TEXT`](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT). Current AOSP places discovered Process Text handlers in the selection menu overflow, so the practical path may be **Select → More → Translate**.

The active IME is not involved. Compatibility with Gboard, Samsung Keyboard, and SwiftKey must be tested, but keyboard compatibility must not be presented as universal host-app compatibility.

**Stage 0A emulator evidence (partial — not a substitute for the physical-device matrix in [VALIDATION_PLAN.md §2.3](VALIDATION_PLAN.md#23-compatibility-matrix); full trail in [../benchmark/gate_0_result.md](../benchmark/gate_0_result.md)):**

- Confirmed working end to end: a genuine separate host app (both a controlled test host and an unmodified Chrome browser), on a read-only `TextView`/rendered webpage. The action appeared in the selection-toolbar overflow in both cases, exactly as this section already anticipated.
- Confirmed **not** working, on the one environment tested: a Jetpack Compose `SelectionContainer` text surface (no overflow menu is offered at all — only a bare Copy), and a bare `android.webkit.WebView` embedded directly in another app (Copy/Share/Select all/Read aloud are offered, but no overflow, so no third-party action). Both results held even with a much longer, multi-word selection. Chrome itself does not carry this WebView limitation, which is why "WebView-hosted" and "Chromium browser" are kept as distinct cases above rather than treated as the same thing.
- Confirmed the Android 11+ package-visibility mechanism matters for host-side discoverability: an identical build, identical field, identical text, differed only in whether the *host* declared the `<queries>` element for `PROCESS_TEXT` — present, the action appeared; absent, it did not. This app's own manifest carries no `<queries>` obligation (it's the *discovered* party, not the discovering one), but it does mean some hosts may simply never be able to find this app regardless of anything this app does, which is a real, host-dependent boundary on top of the "declared supported host" framing above.

None of this is yet corroborated on a physical device, a real IME, or an OEM launcher/skin, and it reflects one Android version at a time — treat it as a first, partial data point against this section's claims, not a revision of them.

## 7. User flows

### 7.1 First-run setup

1. User opens InstantTranslate once.
2. App explains that V1/V2 send selected text to Google Gemini, require internet, do not keep app history, and are subject to the provider's data terms and quotas.
3. App states that the unpaid Gemini service must not be used for sensitive, confidential, or personal information.
4. User acknowledges the disclosure before the first cloud request.
5. App verifies provider configuration and connectivity without transmitting selected content.
6. App explains how to find the translation action and that some apps do not expose it.

No selected text is required, transmitted, or retained during setup.

### 7.2 Read-only translation

1. User selects text in a supported reading surface.
2. User chooses the translation action.
3. InstantTranslate validates the request and resolves English, Devanagari Hindi, or Hinglish source plus the requested output language/script.
4. After disclosure has been acknowledged, the app sends the bounded translation request through Firebase AI Logic to Gemini.
5. A compact result Activity shows original text, translated text, detected direction/script, Copy, and a direction/script switch.
6. Back or Close dismisses the Activity without modifying the host.

### 7.3 Editable translation

The flow is the same as read-only translation, with one additional **Replace** action. Replace returns plain translated text through the Process Text result contract. Copy remains available because custom hosts may reject or alter replacement.

Cancel, Back, disclosure, loading, or error states must never modify the selected source.

### 7.4 Cloud unavailable

If Gemini cannot be reached:

- offline: explain that V1 requires connectivity and provide Retry and a non-destructive exit;
- rate-limited: explain temporary unavailability and avoid aggressive automatic retries;
- provider/configuration failure: show a content-free error and provide a non-destructive exit; and
- never silently switch providers, send more context, or modify the source.

## 8. V1 functional requirements

| ID | Priority | Requirement |
|---|---|---|
| FR-01 | P0 | Register one externally launchable `text/plain` Process Text Activity with a short localized label whose final wording passes provider attribution review. |
| FR-02 | P0 | Accept only the expected Process Text action, MIME type, text extra, and supported input size. Treat malformed input and a missing read-only flag safely. |
| FR-03 | P0 | Convert accepted input to an immutable plain string for processing. Do not rely on incoming styling or relay arbitrary extras. |
| FR-04 | P0 | Support English, Devanagari Hindi, and Romanized Hindi/Hinglish as source modes; support English and Hindi in Devanagari or natural Romanized form as output modes. |
| FR-05 | P0 | Resolve language/script automatically where practical and provide a visible one-tap language/script correction for ambiguous, mixed, short, or incorrectly detected text. |
| FR-06 | P0 | Translate through the Gemini Developer API using Firebase AI Logic with App Check; never embed a raw Gemini API key in the APK. |
| FR-07 | P0 | Show original text, translation, direction, loading, and failure states in a compact transient Activity. |
| FR-08 | P0 | Offer Copy only as an explicit user action. Never poll or automatically write the clipboard. |
| FR-09 | P0 | Offer Replace only when the host marks the selection editable. Replace must be previewed and explicitly selected. |
| FR-10 | P0 | Provide one-time cloud-processing disclosure plus connectivity, quota, timeout, provider-failure, cancellation, and retry states. |
| FR-11 | P0 | Fail gracefully for empty, unsupported, excessive, malformed, or resource-constrained requests without persisting selected text. |
| FR-12 | P0 | Provide direction preference: Automatic, English → Hindi, and Hindi → English. |
| FR-13 | P1 | Provide a concise help screen describing supported hosts, overflow-menu placement, internet requirement, Gemini processing, unpaid-tier warning, and privacy behavior. |
| FR-14 | P1 | Let the user choose Hindi output script: Devanagari or Romanized/Hinglish. |
| FR-15 | P0 | Send only selected text and the minimum operation/language controls; never send surrounding text, clipboard content, host-app text, or caller identity as prompt context. |
| FR-16 | P0 | Treat selected text as untrusted quoted data and constrain Gemini to the requested operation without tools, browsing, or conversation memory. |

## 9. Language and text behavior

| Input | V1 behavior |
|---|---|
| Clear Devanagari Hindi | Prefer Hindi → English. |
| Clear English in Latin script | Prefer English → the configured Hindi script. |
| Romanized Hindi or Hinglish | First-class input; prefer Hinglish → English when Hindi semantics are detected, show the detected source, and keep correction visible. |
| Mixed English and Devanagari | First-class mixed input; use the sentence's dominant meaning and preserve necessary loanwords. |
| Very short text | Use configured direction or target preference rather than claiming reliable detection. |
| Whitespace-only | Reject without initializing or calling the provider. |
| Number-, punctuation-, emoji-, URL-, or email-only | Report that no translatable text was found; do not invent a translation. |
| Names, dates, units, URLs, emojis, and newlines inside normal text | Preserve where practical and include them in quality testing. |
| Requested Hindi output | Produce Devanagari by default or natural Romanized Hindi when that output preference is selected; do not merely transliterate an English sentence word-for-word. |
| Unsupported scripts | Explain that V1 supports English, Hindi, and Hinglish only. |
| Styled text | Process as plain text; style preservation is not a V1 promise. |
| Excessive input | Reject before cloud transmission using the measured safety limit; do not truncate silently. |

The final normal-selection and hard-safety limits are frozen during the benchmark. The target performance corpus must include selections up to 200 Unicode characters, while longer paragraphs are evaluated separately.

## 10. Non-functional requirements

### 10.1 Performance

- Render a responsive loading/result shell at P95 at or below 150 ms after Activity creation on the agreed mid-tier reference device.
- Provisional full cloud target: P50 at or below 1 second and P95 at or below 2 seconds for normal selections up to 200 Unicode characters on the reference Wi-Fi profile. Measure mobile and degraded networks separately.
- Measure application overhead, App Check, connection establishment, provider time-to-first-token, and full response separately.
- Do not market a cloud latency claim until it is measured; the original universal sub-500 ms goal is not a V1 commitment.
- If a response cannot complete promptly, render a lightweight progress state without blocking the main thread.
- Do not add a persistent service solely to keep the client or connection warm.

### 10.2 Connectivity and provider behavior

- V1/V2 require internet access; airplane mode returns a clear unavailable state without changing source text.
- Quota is finite, project-scoped, model-dependent, and not guaranteed. Rate-limit failures must be explicit and must not trigger retry storms.
- The model ID is pinned per release and can change only through a reviewed configuration/release process.
- No undisclosed provider fallback is allowed.

### 10.3 Footprint and resources

- Record app download size, installed app size, peak PSS, Java/native heap, CPU time, energy, request bytes, response bytes, and network latency.
- Resource ceilings in [Validation plan](VALIDATION_PLAN.md) are provisional until the first cloud benchmark.
- The app performs no scheduled work, polling, or app-owned network activity while idle.

### 10.4 Privacy and security

- Source and translated content remain ephemeral in the app and are not logged, persisted, or included in app analytics/crash breadcrumbs; source text is transmitted to Google for the requested operation.
- The user must see and acknowledge the cloud/unpaid-service disclosure before the first request.
- The app must not claim that content stays on-device or that V1/V2 are offline.
- A public privacy-first release cannot pass while it relies on unpaid-tier terms that permit product-improvement use/human review and warn against sensitive data; this requires an explicit product/legal decision before external beta.
- The exported Activity validates untrusted requests and caps work before provider initialization or transmission.
- No Accessibility, overlay, notification-listener, contacts, microphone, storage, or IME permission is permitted in V1.
- Copy is an explicit disclosure boundary because copied text enters the Android clipboard.

### 10.5 Reliability

- No crash or ANR on malformed, repeated, cancelled, rotated, backgrounded, process-death, low-memory, offline, timeout, quota, or provider-failure paths.
- Errors do not modify host text.
- Network, App Check, quota, provider, and response-validation failures recover through explicit bounded retry or exit paths.

### 10.6 Accessibility and localization

- Result and setup surfaces support screen readers, scalable text, logical focus, sufficient contrast, and touch targets.
- V1 UI strings are available in English and Hindi.
- Accessibility of the UI is required; an Android Accessibility Service remains prohibited.

## 11. V1 user stories and acceptance

### Reader

As a reader, I can select English text in a declared supported host and see a Hindi translation without copying the source or changing keyboards.

Acceptance:

- exact source text reaches the receiver;
- direction and result are visible;
- Copy is explicit; and
- closing the result changes nothing in the host.

### Writer

As a writer, I can preview a translation and deliberately replace editable selected text.

Acceptance:

- Replace is absent for read-only input;
- Replace returns only the translated plain text;
- Cancel and failure leave the source unchanged; and
- unsupported custom hosts retain a usable Copy fallback.

### Cloud-aware user

As a user, I understand before use that the selected text will be sent to Gemini and can safely exit when connectivity or quota is unavailable.

Acceptance:

- the first cloud request cannot occur before disclosure acknowledgement;
- airplane mode produces an honest unavailable state and never modifies host text;
- selected text appears only in the intended encrypted provider request and never in app logs, analytics, or persistent storage; and
- no history is shown or recoverable from app storage.

### Ambiguous-language user

As a user with short, mixed, or Hinglish input, I can obtain a useful result and correct the detected language/script instead of receiving an unexplained wrong result.

Acceptance:

- direction is visible;
- it can be switched with one action; and
- the app does not present low-confidence detection as certain.

### Hinglish user

As a Hinglish user, I can translate natural Romanized Hindi and request Hindi output in Roman script without manually converting scripts first.

Acceptance:

- Hinglish → English and English → Hinglish pass their independent quality gates;
- mixed English loanwords are preserved or translated according to natural usage;
- output is natural Romanized Hindi rather than mechanical word substitution; and
- the original text and chosen script remain visible before Replace.

## 12. Success metrics

V1 launch is based on measured product quality rather than download or engagement analytics:

- workflow pass rate across the declared compatibility matrix;
- warm P50/P95/P99 and cold-path latency;
- translation quality on fixed English, Devanagari Hindi, and Hinglish corpus slices;
- critical meaning-reversal count;
- crash/ANR-free repeated runs;
- installed size and peak memory;
- cloud success, timeout, rate-limit, and failure-recovery rates by network profile;
- selected content present only in the intended encrypted Gemini request and absent from app logs, analytics, and storage; and
- zero app-owned idle CPU/network work.

Production analytics are intentionally undecided. V1 must remain viable with no analytics SDK.

## 13. Explicit V1 non-goals

- Guaranteeing support in hosts that suppress or replace Android selection actions
- Inline UI inside another application's selection toolbar
- Accessibility Service, overlay, floating bubble, or clipboard monitoring
- Custom keyboard/IME
- Multiple selection-menu actions
- Automatic replacement
- Context outside selected text
- OCR, image, screen, PDF, voice, or long-document translation
- Additional languages
- Natural/literal style, British English, tone, glossary, or learning preferences
- Explain, Grammar, Rewrite, Simplify, chat, or general assistant
- App-owned backend, accounts, sync, history, or database
- Offline translation in V1/V2
- Persistent prewarming, scheduled workers, or background service
- Monetization and social features

## 14. Release criteria

V1 may release only when:

- the platform spike passes and the public support statement matches observed behavior;
- the pinned Gemini model passes the frozen benchmark and provider/security review;
- all P0 requirements and critical user stories have mapped passing tests;
- bilingual reviewers accept the release corpus with zero unresolved critical meaning reversals;
- offline-failure, quota, timeout, process-death, and privacy audits pass;
- no severity-1 or severity-2 correctness, privacy, data-loss, crash, or ANR defect remains; and
- Play Store disclosures, provider attribution, app download size, and host limitations are accurate.

## 15. Open decisions

| Decision | Current position | Must be resolved by |
|---|---|---|
| Public product name | InstantTranslate is a working name; repository is LingoFlow. | Before store assets |
| Minimum SDK | API 23 is provisional. | Platform/Firebase compatibility spike |
| Cloud provider | Gemini Developer API free tier through Firebase AI Logic and App Check for V1/V2. | Decided |
| Gemini model ID | Choose the lowest-latency stable Flash-Lite model that passes the benchmark; pin it per release. | Cloud benchmark |
| Process Text action label | Concept is “Translate”; attribution may require different wording. | Provider/legal review |
| Normal and maximum input length | Measure; never silently truncate. | Benchmark freeze |
| App/RAM/network ceilings | Provisional values are in the validation plan. | Before cloud benchmark |
| Cold-start threshold | Measure client, attestation, and connection states separately; do not hide with a service. | Cloud benchmark |
| Hinglish source/output UX | Treat Hinglish as first-class; finalize labels and correction controls through usability tests. | Alpha |
| Public-release data posture | Unpaid-tier terms conflict with sensitive cross-app text and a strong privacy-first claim; choose non-private positioning, paid processing, or on-device processing. | Before external beta |
| Screenshot and Recents policy | Exclude selected text from Recents; screenshot blocking needs a UX/privacy decision. | Alpha |
| Production analytics | Default is none. | Before beta |
| Provider/model change policy | Pin and review changes; no silent cross-provider fallback. | Alpha |
| Distribution markets and legal review | Undecided. | Before external beta |

## 16. Future direction

After V1 evidence, one selection entry may evolve in V2 into **Language Tools** with Translate, Explain, Grammar, Rewrite, and Simplify. V1/V2 use Gemini through explicit typed capabilities and the same cloud disclosure. V3 evaluates local open-weight providers without changing the selection integration or operation contracts.

Personal preferences are shown only when an active provider can honor them. A future AI keyboard remains an optional separate surface and must pass its own trust, typing-reliability, performance, battery, and sensitive-field gates.

See [Roadmap and model strategy](ROADMAP_AND_MODEL_STRATEGY.md).
