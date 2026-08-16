# Feasibility Review

| Field | Value |
|---|---|
| Reviewed source | `InstantTranslate_Android_Development_Specification.docx` |
| Review date | 2026-07-30 |
| Verdict | Technically feasible; V1/V2 cloud decision introduces a production privacy and reliability gate |

## 1. Executive assessment

The specification is reasonable and unusually disciplined for an initial product brief. It prioritizes one clear workflow, explicitly rejects risky scope expansion, separates the Android entry point from the translation engine, and treats performance as something to measure.

The revised V1 is technically feasible:

```text
Supported host selection
→ Android Process Text action
→ small result Activity
→ English/Hindi/Hinglish language and script policy
→ Firebase AI Logic + App Check
→ Gemini Developer API
→ Copy or explicit Replace
```

The original specification should not be implemented literally without revision. Its central weaknesses were promises that the platform cannot guarantee and performance targets without percentile/device definitions. The subsequent decision to use Gemini's unpaid cloud API through V2 simplifies initial model work and improves Hinglish flexibility, but deliberately removes offline behavior and creates a serious conflict with the original privacy-first promise.

### Feasibility by area

| Area | Assessment | Why |
|---|---|---|
| Native text-selection entry | Feasible with limits | `ACTION_PROCESS_TEXT` is the correct Android API from API 23, but host support and menu placement vary. |
| English/Hindi/Hinglish cloud operations | Feasible | Gemini can handle translation and V2 generation, but quality must be measured on a dedicated code-mixed corpus. |
| Offline translation through V2 | Not in the revised scope | Gemini cloud requires connectivity; local/offline processing moves to V3 evaluation. |
| Full response below 500 ms | Unreliable as a cloud promise | App Check, radio/network, provider queueing, and generation are outside app control. |
| “Any Android app” | Not feasible through Process Text alone | Custom editors, PDFs, password fields, and apps that suppress third-party actions can omit it. |
| Inline result in another app | Not available to a normal third-party handler | Android launches the handler's Activity; the app cannot inject arbitrary UI into the host toolbar. |
| Keyboard independence | Feasible | The active IME is not part of the flow. |
| Hinglish/Romanized Hindi | Feasible as a tested first-class mode | Long natural phrases are practical; very short Latin text remains ambiguous and needs correction controls. |
| Strong privacy-first claim on Gemini unpaid tier | Not reasonable for public cross-app text | Current terms permit improvement use/human review and say not to submit sensitive, confidential, or personal information. |
| Lightweight APK/RAM | More achievable in V1/V2 | Cloud inference avoids a bundled model, though Firebase/App Check add dependencies and network/battery cost. |
| Future selection AI tools | Feasible with selection-only context | The Process Text contract does not provide reliable surrounding context. |
| Future full AI keyboard | Feasible as a separate product | It carries substantially greater typing, security, trust, battery, and policy complexity. |

## 2. What the original specification gets right

- It protects V1 from an Accessibility, overlay, keyboard, app-owned backend, account, and history expansion.
- It makes the Android selection integration independent of the translation engine.
- It calls for a platform proof before ML integration, which isolates the highest product risk first.
- It originally treated offline setup separately from normal translation.
- It requires cold and warm measurements rather than assuming one latency number.
- It correctly avoids a database, DI framework, persistent service, and large architecture for a tiny app.
- It includes text edge cases and device/app compatibility rather than treating one Pixel demo as proof.
- It preserves a future route to richer operations without forcing those operations into the V1 Translate UI.

These are strong foundations and should be retained.

## 3. Required corrections

### 3.1 Replace “any app” with a declared support contract

Android defines `ACTION_PROCESS_TEXT` as an Activity contract carrying selected text and a read-only signal. It does not force every editor to expose third-party handlers. See the official [`ACTION_PROCESS_TEXT` reference](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT) and current [AOSP editor implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/widget/Editor.java).

Recommended wording:

> Works in host apps and text components that expose Android's third-party Process Text actions. Universal Android-app coverage is not claimed.

This is the single most important correction because it changes marketing, testing, and the definition of done.

### 3.2 Describe the real result surface

The selection action starts an exported InstantTranslate Activity. That Activity can be styled like a small dialog or sheet, but it remains app-owned UI. It cannot render arbitrary inline results inside the caller's selection toolbar.

Read-only and editable sources also differ:

- Read-only: show result and explicit Copy.
- Editable: show Copy and explicit Replace.
- Back, Cancel, and errors: return no replacement.

Treat a missing read-only flag as read-only. Never auto-replace.

### 3.3 Separate performance states

“Under 500 ms” needs a workload, percentile, device, and readiness state. The primary release target should be:

> Result/loading UI visible at P95 ≤150 ms, with provisional full-response targets of P50 ≤1 second and P95 ≤2 seconds for selections up to 200 Unicode characters on the named Wi-Fi profile.

Report separately:

- process and Firebase client cold;
- App Check token cold/warm;
- connection cold/warm;
- named Wi-Fi and mobile profiles;
- quota/provider unavailable;
- airplane-mode failure; and
- longer selections.

Do not introduce a persistent service merely to manufacture a warm connection. The original sub-500 ms aspiration may be retained as an observed best case, not a release promise.

### 3.4 Make first-run disclosure a first-class flow

Cloud translation and a normal no-app-opening workflow are compatible only after a clear first-use disclosure. At least one setup/status screen is necessary to:

- identify Gemini and the internet requirement;
- explain the unpaid-tier data-use warning;
- record the notice version acknowledged;
- show connectivity, timeout, quota, provider, and retry states; and
- teach the user where the selection action appears.

This is not “complex UI”; it is a prerequisite for honest cloud behavior.

### 3.5 Tighten privacy language

“Privacy-first” is not supportable without qualification while V1/V2 use the Gemini unpaid tier. The product contract should say:

> The selected text is sent to Google Gemini only after the user invokes an operation. The app does not keep history or put content into its logs or analytics.

Google's current [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms) state that unpaid-service content and outputs may be used to improve Google products, may be processed by human reviewers, and must not include sensitive, confidential, or personal information. Cross-app selected text can contain exactly that kind of material.

The logical position is to use the free tier for prototypes and controlled validation. Before external beta, enable a paid processing posture, move inference on-device, or explicitly abandon the privacy-first positioning and restrict the intended content. A disclaimer alone cannot make arbitrary sensitive text compatible with the unpaid-service warning.

### 3.6 Narrow automatic language detection

Devanagari provides a cheap strong Hindi signal. Latin text is not a reliable English signal because Hindi is frequently Romanized. One or two words, names, URLs, mixed scripts, and Hinglish are inherently ambiguous.

Recommended policy:

1. Route clear Devanagari toward Hindi → English.
2. Ask the provider to classify longer Latin-script input as English, Romanized Hindi, or mixed.
3. Use the user's configured target/output script as fallback.
4. Keep detected source and output script visible and switchable.
5. Evaluate short ambiguous inputs separately and never present uncertain classification as fact.

Hinglish is now a first-class quality claim, but not a promise that every one-word Latin selection can be detected correctly.

### 3.7 Define lightweight with budgets

Cloud inference reduces local model size and RAM, but it adds Firebase/App Check libraries, radio use, request latency, quota dependence, and provider availability. Measure AAB/APK size, PSS, request/response bytes, radio energy, cold/warm connection time, and success rate per network profile.

Freeze provisional budgets before benchmarking and allow changes only through an explicit product decision. The current proposed values are in [Validation plan](VALIDATION_PLAN.md).

### 3.8 Separate the future IME

A future custom keyboard does not “work alongside” Gboard or SwiftKey in the same active typing session; Android normally has one active IME. Users must enable and switch to the new keyboard.

An IME also changes the privacy and reliability problem:

- it can observe composing text;
- basic typing must work if every model and network is unavailable;
- password and sensitive fields need strict bypass behavior;
- latency, crashes, memory, and battery are continuously visible; and
- Play policy, trust, layouts, autocorrect, suggestions, and multilingual typing become core product work.

The selection utility should remain independently usable if a keyboard is built later.

### 3.9 Prove preferences rather than assuming prompt compliance

Gemini can be prompted for “Natural” translation and “British English,” but promptability is not proof of consistent behavior.

Add them only when the pinned model passes blinded tests demonstrating that it follows each preference without degrading meaning.

## 4. Provider feasibility

### Gemini Developer API through Firebase AI Logic

Strengths:

- one provider for V1 Translate and V2 Explain/Grammar/Rewrite/Simplify;
- strong natural-language handling that is promising for Hinglish and code-mixing;
- no local model download or large inference RAM requirement;
- official Kotlin/Android access through Firebase AI Logic; and
- Firebase proxy/App Check path avoids embedding a raw Gemini API key.

Drawbacks:

- no offline operation;
- network latency and availability prevent a universal instant guarantee;
- free-tier quotas are finite, mutable, project-scoped, and not guaranteed;
- unpaid-service data terms conflict with sensitive cross-app text and privacy-first positioning;
- generative translation can add, omit, or rewrite meaning; and
- model behavior/version can change, requiring a pinned ID and regression suite.

Verdict: **reasonable for prototype/V1 validation and V2 capability discovery; conditional for public production until the privacy and capacity gates are resolved**.

### Why Firebase AI Logic instead of a raw API call

Firebase's [AI Logic documentation](https://firebase.google.com/docs/ai-logic) describes an Android client SDK and proxy that keeps the Gemini API key off the client. Its [App Check guidance](https://firebase.google.com/docs/ai-logic/app-check) supports Play Integrity and quota-abuse controls.

Verdict: use this client path for V1/V2. Do not ship a raw Gemini key in the APK.

### Local providers

On-device NMT, Android platform models, and open-weight LLMs remain V3 candidates. They restore offline operation and can improve content privacy, but add downloads, RAM, device fragmentation, model distribution, licensing, and thermal/battery work.

Verdict: preserve the provider boundary now; run the detailed local-model bake-off in V3.

## 5. Principal drawbacks

1. **Coverage ceiling:** the product cannot repair hosts that do not expose Process Text without adopting a prohibited mechanism.
2. **Discoverability:** the action may be under More/overflow, OEMs may reorder it, and built-in Translate actions may cause label duplication.
3. **Network latency:** radio state, App Check, connection setup, provider queueing, and generation are outside app control.
4. **No offline value:** V1/V2 fail cleanly but cannot operate without connectivity.
5. **Ambiguous routing:** short Latin text can still be misclassified despite first-class Hinglish support.
6. **Translation quality risk:** fluent output can still reverse negation, numbers, tense, gender, or intent.
7. **Provider privacy/capacity:** unpaid data use, human review possibility, mutable quota, and shared-project exhaustion are material product risks.
8. **Replacement variability:** standard editable hosts can accept a returned result, while custom hosts may ignore or transform it.
9. **Context ceiling:** selected-text actions do not receive reliable surrounding context for future explanations or rewrites.
10. **Future model weight:** a local general-purpose LLM can erase the “lightweight” identity unless it is an optional downloadable capability.

## 6. Risk register

| Risk | Likelihood | Impact | Mitigation / decision gate |
|---|---|---|---|
| Action absent in important apps | High | High | Run the cross-app spike first; publish a supported-host statement; do not add Accessibility as a hidden workaround. |
| Full cloud response misses 500 ms | High | Medium | Treat sub-500 ms as an aspiration; gate on named P50/P95 network profiles and show immediate UI. |
| Cold connection feels slow | High | Medium | Show immediate lightweight state; reuse process client/connections when available; publish cold results; no persistent warming service. |
| Hindi output contains critical errors | Medium | High | Fixed bilingual corpus, two reviewers, critical negation/number/safety subset, original preview, explicit Replace. |
| Offline request cannot run | Certain | Medium | State internet requirement before use; clear unavailable state; no source modification. |
| Hinglish routed incorrectly | Medium | High | Dedicated Hinglish corpus, target/script preference, visible source/script correction. |
| Unpaid Gemini receives sensitive text | High | High | Controlled-use limitation, explicit warning, no background requests, and paid/local/positioning gate before external beta. |
| Shared free quota is exhausted | High at scale | High | App Check, per-user limits, bounded retry, capacity test, and production funding/provider decision. |
| Firebase dependencies exceed brand promise | Low | Medium | Freeze app/RAM/network budgets and audit transitive components. |
| Exported Activity abused by another app | Medium | Medium | Validate action/type/input/size, flatten text, avoid extra relay, cap work, and test malformed parcels. |
| Host ignores replacement | Medium | Medium | Always keep Copy; compatibility matrix records replacement separately. |
| Future keyboard overwhelms roadmap | High | High | Independent investment gate, separate threat model, no V1 architecture burden. |

## 7. Recommended product decision

Proceed, but treat the next work as validation rather than full implementation:

1. Prove cross-package Process Text discovery and return behavior with a deterministic local stub.
2. Freeze English/Hindi/Hinglish corpora, reference devices, network profiles, and resource/privacy gates.
3. Integrate Firebase AI Logic with enforced App Check in a development project and benchmark eligible stable Gemini Flash-Lite models.
4. Pin a model only if quality, latency, response-validation, quota, and failure gates pass.
5. Ship one excellent Translate action before adding general AI or a keyboard.
6. Resolve paid/local/non-private positioning before external beta.

With those changes, the product remains logical and buildable. The largest revised risk is no longer mobile model engineering; it is presenting an unpaid cloud service as privacy-first or production-reliable when its terms and quotas do not support that promise.
