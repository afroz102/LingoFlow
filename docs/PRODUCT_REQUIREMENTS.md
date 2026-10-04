# Product requirements — LingoFlow / InstantTranslate

Updated 2026-10-04. This document distinguishes the working test build from remaining V1
requirements. Backend deployment and phone installation are in [BACKEND_SETUP.md](BACKEND_SETUP.md).

## 1. Product and support boundary

An Android user selects text, chooses **More → Translate**, previews a translation, then copies
it or explicitly replaces an editable selection. The user keeps their existing keyboard.
Android hosts must expose `ACTION_PROCESS_TEXT`; support does not extend to every app/editor.
The translator currently has no launcher screen and is reached through the selection menu.
Minimum Android is provisionally API 23; compile/target SDK is 34.

Historical API 30/34 emulator evidence covers the controlled host and Chrome. Some Compose and
embedded WebView selections did not expose the action. Android 11+ host package visibility also
changed discovery. These are host limitations, not reasons to add an Accessibility Service or
overlay. See [compatibility evidence](../benchmark/gate_0_result.md) and the
[validation matrix](VALIDATION_PLAN.md#23-compatibility-matrix).

## 2. Current implementation

- Native Kotlin Process Text entry point, transient result screen, original text and direction.
- Explicit Copy; Replace only for editable input; Back/cancel/error leaves the host unchanged.
- First-use versioned cloud disclosure, offline detection, loading, explicit Retry and typed errors.
- English/Hindi UI strings; Devanagari-containing input routes to English. Latin text uses
  model-based English/Hinglish detection in the same request as translation.
- Per-selection language/script correction, including Romanized Hindi output and Devanagari conversion.
- One HTTPS request to our backend, SQLite/D1 aggregate quota, and server-side Gemini access.
- No user authentication, device sessions, account setup, translation history or background work.
- Input cap: 4,000 UTF-16 code units; no silent truncation. The backend independently validates it.

The server accepts automatic mode plus English→Hindi, Hindi→English, Hinglish→English,
English→Hinglish, Hindi→Hinglish and Hinglish→Hindi. Hinglish means conversational Hindi in
Roman characters, including Hindi-English code-mixing. Automatic Latin detection defaults
ambiguous short words/names to English→Hindi; users can correct this with **Change language /
script**. Romanized output uses everyday spellings, without scholarly diacritics. These controls
apply to the current selection; no language preference or content is saved. Model-quality
release thresholds still require blinded human review.

## 3. User flows

1. Select text in a supported host and choose Translate from the selection menu.
2. On first use or a changed notice, acknowledge cloud processing before any content request.
3. See loading, then a validated translation or a recoverable error.
4. If needed, choose **Change language / script** to correct the direction or select Romanized output.
5. Choose Copy, explicit Replace for editable input, or Back without modifying the source.

Internet is required. Text passes through Cloudflare to Google Gemini. The unpaid Gemini API
can use inputs/outputs for product improvement and human review; this testing version is for
non-sensitive samples. The APK contains only the public backend origin, never a Gemini key.

## 4. V1 requirements and remaining work

| ID | Requirement | State |
|---|---|---|
| FR-01 | Localized `text/plain` Process Text entry point | Implemented; final attribution/label review open |
| FR-02 | Reject malformed, blank and oversized inputs safely | Implemented; broader device audit open |
| FR-03 | Flatten accepted input to plain text, relay no arbitrary extras | Implemented |
| FR-04 | English, Hindi and natural Hinglish input/output modes | Implemented; full human quality scoring open |
| FR-05 | Visible direction/script correction for ambiguous input | Implemented per selection |
| FR-06 | Gemini through our backend, key only on server | Implemented; no authentication in this version |
| FR-07 | Compact original/result/direction, loading and error presentation | Implemented |
| FR-08 | Copy only on explicit user action, no clipboard polling | Implemented |
| FR-09 | Preview and explicit Replace for editable input only | Implemented; full host matrix open |
| FR-10 | Disclosure, offline/quota/timeout/errors, explicit Retry | Implemented; physical-device validation open |
| FR-11 | Safe malformed/resource-constrained failure without saving text | Input checks implemented; low-memory/process-death audit open |
| FR-12 | Automatic and explicit English/Hindi direction preferences | Script routing plus single-request model detection; explicit per-selection controls |
| FR-13 | Help/status surface explaining host/cloud/privacy limits | Setup guide exists; in-app help open |
| FR-14 | Devanagari or natural Romanized Hindi output preference | Both available per selection; persistent preference not implemented |
| FR-15 | Send selected text and bounded controls only | Implemented; no surrounding text, caller identity or history |
| FR-16 | Treat selection as literal data, no tools/browsing/chat memory | Implemented; adversarial quality evaluation open |

Negation, meaning, names, numbers, dates, units, URLs, emojis and newlines must survive translation.
Very short, mixed-script and Romanized input needs user correction rather than claimed certainty.
Final normal-selection limits and latency claims depend on benchmark evidence.

## 5. Reliability, privacy and release acceptance

- Exact selected input reaches the adapter; no result changes the host without explicit Replace.
- No crash/ANR or source modification on malformed input, timeout, quota, cancellation, rotation,
  backgrounding, process death, low memory or provider failure.
- Source/result content stays out of app storage, state restoration, logs, analytics and Recents.
- Copy intentionally places output in Android's clipboard; provider data terms still apply.
- No Accessibility Service, overlay, IME, clipboard monitoring, contacts/microphone/storage access.
- UI must support TalkBack, text scaling, focus order, contrast and adequate touch targets.
- Public release needs the physical-host matrix, quality/resource gates and privacy review in
  [VALIDATION_PLAN.md](VALIDATION_PLAN.md). Test APK readiness is not release clearance.

Latency targets are provisional: loading shell P95 ≤150 ms, normal ≤200-character Wi-Fi response
P50 ≤1 second/P95 ≤2 seconds. Measure physical release builds, cold and warm states separately.
Do not claim universal sub-500 ms translation or retain a service just to hide cold starts.

## 6. Roadmap

V1 completes physical compatibility, quality scoring, persistent language preferences and
release hardening. V2 may add Explain, Grammar, Rewrite and Simplify behind separate typed contracts and
one selection entry, with task-specific quality gates and previews before applying changes.
V3 evaluates optional local models: assess Hindi/Hinglish quality, downloads, integrity, licenses,
RAM, thermal/battery costs and removability before choosing a model. Local processing and any
cloud fallback must be visible and explicitly disclosed.
A later optional keyboard is a separate product surface with typing-reliability, sensitive-field,
privacy and battery gates; current selection consent does not authorize sending keystrokes.

## 7. Open decisions

Final product name, action attribution, minimum SDK, input/resource budgets, model choice after
blinded review, and distribution markets remain open. Before external beta, choose paid processing,
on-device processing, or clearly non-private/non-sensitive positioning with current terms review.
The current public API has shared limits of 10/minute and 200/UTC day; anyone can consume that
allowance. Authentication and per-user quotas are intentionally outside this testing version.
