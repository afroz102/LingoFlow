# Decision Record — Gemini Cloud Through V2 and First-Class Hinglish

> **Backend update — 2026-10-04:** The user requested our own backend without authentication.
> [BACKEND_SETUP.md](BACKEND_SETUP.md) describes the current Workers/SQLite test implementation.
> Firebase, Supabase, App Check, and “no app-owned backend/database” statements below are historical;
> the selection workflow, language requirements, and unpassed quality/release gates remain applicable.

| Field | Value |
|---|---|
| Status | Accepted product direction with a production privacy gate |
| Date | 2026-07-30 |
| Applies to | V1 and V2 |

## Decision

V1 and V2 will use the Gemini Developer API free tier as their primary inference provider. Android access will use Firebase AI Logic with Firebase App Check; the APK will not contain a raw Gemini API key.

V1 supports Translate. V2 adds Explain, Grammar, Rewrite, and Simplify. Each operation has a narrow typed request/result contract even though the same provider serves them.

Hinglish—Hindi expressed in Latin script, commonly mixed with English—is a first-class source and output mode. Example:

```text
kal mujhe office aane me late ho jayegi.
```

A suitable English translation is:

```text
I will be late getting to the office tomorrow.
```

The app must handle natural code-mixing rather than expecting every token to belong to one language.

## Why this direction is reasonable

- It validates the Android selection workflow without first shipping a large model or mobile inference runtime.
- One capable model can serve Translate plus the V2 generative operations.
- Gemini is likely to handle ambiguous Romanized Hindi and code-mixing better than simple script heuristics or a small specialist translator.
- Firebase AI Logic provides an Android client path and a proxy so the Gemini API key is not embedded in the app.
- The provider boundary remains replaceable for V3 local-model evaluation.

## Consequences

- V1/V2 require internet and are not offline products.
- End-to-end latency includes connection setup, App Check, network conditions, provider queueing, and generation. A universal sub-500 ms promise is not credible without measurements.
- Free-tier quotas are finite, project-scoped, model-dependent, and not guaranteed. Public usage may exhaust them.
- The model can change or be retired. The selected stable model ID must be pinned and reviewed per release.
- The app gains Firebase AI Logic and App Check dependencies and must test Play Integrity behavior.
- Translation quality is generative: the app must guard against additions, omissions, prompt injection, unsafe transformations, and non-deterministic output.

## Privacy conflict

Google's current [Gemini API Additional Terms](https://ai.google.dev/gemini-api/terms) state that content submitted to unpaid services and generated responses may be used to improve Google products, may be processed by human reviewers, and must not contain sensitive, confidential, or personal information.

That conflicts with a strong privacy-first claim for a cross-app text utility because selected content may come from messages, email, documents, or work applications. The app cannot reliably determine locally whether arbitrary selected text is sensitive.

Therefore:

- the unpaid tier is accepted for development, controlled testing, and product validation;
- V1/V2 documentation and UI must never say that selected content stays on-device;
- a clear disclosure and acknowledgement is required before the first request;
- app-owned logging, history, analytics content, and persistence remain prohibited; and
- before external beta, the product must explicitly choose one of these positions:
  1. use a paid Gemini project/data-processing posture;
  2. move content processing on-device; or
  3. abandon the privacy-first positioning and clearly limit the product to non-sensitive text.

This is a release gate. User consent alone does not make an inaccurate privacy claim or incompatible provider usage safe.

## Hinglish product contract

Treat these as distinct display modes even though Hindi and Hinglish share language semantics:

- English
- Hindi — Devanagari
- Hindi — Roman/Hinglish
- Mixed/ambiguous

Required behavior:

- detect obvious Devanagari locally;
- ask Gemini to interpret Latin-script Hindi and English code-mixing;
- show the detected source and requested output mode;
- let the user correct them with one action;
- default Hindi output to Devanagari, with a remembered Roman/Hinglish output preference;
- preserve names, URLs, numbers, emojis, and necessary English loanwords;
- produce natural Hindi/Hinglish, not mechanical word-for-word transliteration; and
- evaluate Hinglish separately from English and Devanagari Hindi.

The quality set must cover spelling variation, omitted diacritics, code-mixing, informal grammar, gender/number ambiguity, politeness, slang, regional vocabulary, and short ambiguous phrases.

## Provider and request boundary

Only transmit:

- the selected plain text;
- operation name;
- source language/script hint when available;
- desired output language/script;
- approved style controls; and
- provider-required technical metadata.

Do not transmit:

- surrounding paragraph or cursor text;
- clipboard content;
- host application identity;
- contacts, account identity, history, or device content;
- previous requests as chat history; or
- tools, browsing context, files, images, or URLs fetched from the selection.

Selected text is quoted as untrusted data. V1/V2 requests use no model tools, browsing, grounding, or cross-request conversation memory.

## Model choice

Start the benchmark with the lowest-latency stable Gemini Flash-Lite model available through Firebase AI Logic and eligible for the chosen tier. As of this decision date, Firebase lists `gemini-3.5-flash-lite` as a billing-not-required option, but availability, terms, quotas, and model identifiers must be rechecked at implementation and release.

Do not select solely by benchmark headline. Compare:

- English ↔ Devanagari Hindi quality;
- English ↔ natural Hinglish quality;
- Hinglish → English meaning preservation;
- Translate additions/omissions;
- V2 instruction following;
- response schema adherence;
- warm and cold latency on Indian Wi-Fi/mobile profiles;
- quota and rate-limit behavior; and
- safety refusal rate for ordinary language tasks.

## V3 migration boundary

The Android Process Text adapter and UI do not depend on Gemini. They call typed providers. V3 can introduce an on-device provider for some or all operations while retaining Gemini only as an explicitly disclosed fallback—or removing it entirely.

The migration is complete only when provider selection, privacy labels, latency targets, offline state, and quality gates are defined per operation.
