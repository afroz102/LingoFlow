# Product Roadmap and Open-Model Strategy

| Field | Value |
|---|---|
| Status | Directional; V1 scope remains authoritative |
| Model landscape snapshot | 2026-07-30 |
| Core rule | Gemini cloud validates V1/V2; V3 local models remain optional and capability-specific |

## 1. Product sequence

The long-term vision is logical if it is built as separate product surfaces sharing proven capability code:

```text
V1 Selection translator
   Gemini cloud
       ↓
V2 Selection language tools
   Gemini cloud
       ↓
V3 Personal local language assistant
   on-device model evaluation
       ↓
V4 Optional AI keyboard
```

This is not merely a feature order. Each version increases the content available to the product, the model weight, the quality burden, and the user's required trust.

## 2. Version roadmap

### V0 — Evidence spikes

Outcome:

- validate Android Process Text behavior with a local stub;
- validate Firebase AI Logic/App Check without embedding a Gemini key;
- freeze and run the V1 English/Hindi/Hinglish cloud benchmark; and
- decide whether the support/latency/quota/privacy trade-off is viable.

Exit gate:

- Gates 0 and 1 in [Validation plan](VALIDATION_PLAN.md).

### V1 — Instant English/Hindi/Hinglish cloud translation

Surface:

- one Process Text translation action;
- compact result Activity;
- Copy and conditional explicit Replace;
- cloud disclosure/status; and
- language plus Hindi-script preference.

Provider:

- Gemini Developer API unpaid quota through Firebase AI Logic with enforced App Check;
- the lowest-latency stable Flash-Lite model that passes the frozen quality/latency benchmark; and
- a pinned model ID and fixed Translate response contract.

Explicitly absent:

- Explain, Grammar, Rewrite, Simplify, offline mode, local model, history, account, and IME.

Release constraint:

- the free tier is appropriate for development/validation, but external beta requires an explicit paid/local/non-private-positioning decision because current unpaid-service terms conflict with sensitive cross-app text.

### V2 — Selection-based Language Tools

Surface:

- keep one selection-menu entry to avoid five overflow actions;
- open a compact chooser for Translate, Explain, Grammar, Rewrite, and Simplify;
- preserve a fast default path to Translate; and
- preview every transformative result before Apply/Replace.

Architecture:

- Translate retains the V1 typed translation contract and Gemini cloud adapter.
- Each new operation has a typed request/result and its own quality, latency, privacy, and replacement contract.
- Every operation is cloud-processed under the V1 disclosure; no action silently sends additional surrounding context.
- No action assumes surrounding host context beyond the selected text.
- Hinglish remains first-class across Translate, Explain, Grammar, Rewrite, and Simplify.

Entry gate:

- V1 workflow and retention evidence;
- one additional operation passes its task-specific corpus;
- the pinned Gemini model passes operation-specific English/Hindi/Hinglish gates;
- quota/capacity supports the intended audience;
- the external-release privacy posture is approved; and
- the new chooser does not materially slow the translation path.

### V3 — Personal local language assistant

Capabilities:

- opt-in downloadable local generative model;
- preferred target language;
- natural versus literal style where measured;
- British English output where measured;
- tone/formality;
- personal glossary if it can remain local and deterministic; and
- capability-aware provider selection.

Important product rule:

> A preference is shown only when the active provider has demonstrated that it follows it.

“Natural” and “British English” cannot be decorative settings layered over an engine that ignores them.

V3 is the appropriate point to benchmark open-weight general-purpose models because users now benefit from grammar, rewrite, simplification, explanation, style control, offline use, and a stronger local-content posture. A local model can replace Gemini per capability only after it passes the same typed contract and corpus; no silent fallback is allowed.

### V4 — Optional AI keyboard

The keyboard is a separate IME surface that reuses proven operation providers. It is not an extension inside Gboard, Samsung Keyboard, or SwiftKey; Android users switch to it as their active keyboard.

Typing architecture:

```text
Key event
→ reliable local typing/layout/autocorrect path
→ text appears without waiting for an LLM

Explicit AI action on composing/selected text
→ Grammar / Translate / Rewrite / Simplify
→ streamed preview
→ user applies or cancels
```

The LLM must not run on every keystroke. Explicit invocation, a sentence boundary, or a carefully tested idle suggestion are the only reasonable triggers. Sensitive/password fields bypass AI entirely.

Keyboard investment requires:

- evidence that users will switch IMEs;
- a decision between a transformation-focused keyboard and full typing replacement;
- layout, autocorrect, suggestions, multilingual input, crash recovery, and offline baseline;
- separate privacy/security and Play-policy review;
- typing that remains usable when models and networks fail; and
- measured memory, battery, and thermal behavior during long sessions.

## 3. Recommended capability routing

```text
User operation
      ↓
Typed operation router
      ├── V1/V2
      │      └── Gemini cloud adapter for each typed operation
      └── V3/V4 candidates
             ├── local open-weight provider
             ├── Android system model when reliably available
             └── explicitly disclosed Gemini provider
```

Why:

- one cloud model accelerates learning across translation and V2 operations;
- typed contracts keep translation fidelity and rewrite behavior separately testable;
- platform models can avoid app-owned model storage but are device/version/quota dependent;
- cloud can offer higher quality but changes privacy, latency, cost, and reliability; and
- provider failure must return an honest unavailable state, not an undisclosed fallback.

## 4. “Open source” versus “open weight”

Use precise product language.

The [Open Source AI Definition](https://opensource.org/ai/open-source-ai-definition) expects freedoms to use, study, modify, and share, plus the preferred form for modification, including sufficient training-data information and training/run code. Public model weights alone do not necessarily satisfy that standard.

Recommended phrase:

> Downloadable open-weight model running locally on your device.

Licensing snapshot:

| Family | Current weight terms | Product implication |
|---|---|---|
| Gemma 4 | Apache 2.0 | Permissive baseline; still preserve notices and verify the exact artifact/runtime terms. |
| Qwen 3.5 | Apache 2.0 for Qwen base weights | Permissive baseline; optimized third-party/vendor packages can add separate terms. |
| Llama 3.2 | Custom Llama Community License and Acceptable Use Policy | Not Apache/OSI; requires notices/attribution and includes additional commercial conditions. |
| Gemma 3/3n | Older custom Gemma terms | Do not assume Gemma 4's Apache terms apply to older artifacts. |
| IndicTrans2 checkpoints | MIT | Permissive specialist translation comparator, subject to exact artifact/data notice review. |

Licensing review must cover model, tokenizer, runtime, converted/quantized artifact, fine-tuning data, adapter, and distribution method—not just the model-card badge.

## 5. Current V3/V4 candidate shortlist

The model landscape will change before V3, so this is a benchmark shortlist, not an irrevocable selection.

### 5.1 Gemma 4 E2B — leading Android production candidate

Google's current [Gemma 4 model card](https://ai.google.dev/gemma/docs/core/model_card_4) describes E2B as a mobile/edge model with 2.3B effective parameters, 5.1B including embeddings, Apache 2.0 weights, and training across 140+ languages.

Google's [memory table](https://ai.google.dev/gemma/docs/core) estimates about 0.84 GB to load the text-only mobile configuration through LiteRT-LM. The official [LiteRT-LM performance post](https://developers.googleblog.com/blazing-fast-on-device-genai-with-litert-lm/) describes a roughly 2.58 GB E2B model and reports 52 decode tokens/second on a Samsung S26 Ultra GPU. That is a flagship reference, not a midrange guarantee.

Pros:

- designed for mobile/edge use;
- official Kotlin-oriented LiteRT-LM path;
- Apache 2.0 weights;
- broad multilingual training;
- stronger expected capability than sub-1B models;
- system prompts and configurable reasoning/non-reasoning behavior; and
- text-only memory optimizations.

Cons:

- multi-gigabyte download is large for a lightweight utility;
- recently released tooling/conversion paths need maturity testing;
- published performance is on premium hardware;
- broad multilingual training does not prove English/Hindi operation quality; and
- cold initialization, storage, battery, and thermal costs still need app-specific measurement.

Recommendation: **first standard-tier local generative candidate for V3**, benchmarked in non-thinking mode for short language tasks.

### 5.2 Qwen 3.5 2B — primary size/portability challenger

The official [Qwen 3.5 2B model card](https://huggingface.co/Qwen/Qwen3.5-2B) uses Apache 2.0. Qualcomm's [Android-oriented package and measurements](https://huggingface.co/qualcomm/Qwen3.5-2B) describe 100+ languages/dialects and Q4 execution through a llama.cpp-based mobile runtime. Q4 assets are roughly in the 1.2 GB class; freeze the exact chosen artifact and checksum during evaluation.

Pros:

- materially smaller quantized asset than the Gemma E2B artifact;
- permissive base-weight license;
- broad multilingual capability;
- capable enough to be a credible grammar/rewrite/explain model; and
- GGUF/llama.cpp ecosystem flexibility.

Cons:

- no published Hindi-specific product score for these tasks;
- cross-vendor Android acceleration is less turnkey than the Gemma/LiteRT-LM path;
- JNI/runtime/model conversion and lifecycle become application maintenance;
- vendor-optimized artifacts can carry terms beyond the Apache base model; and
- time-to-first-token increases sharply with longer prompts.

Recommendation: **direct bake-off against Gemma 4 E2B**.

### 5.3 Qwen 3.5 0.8B — reduced-capability tier

The official [Qwen 3.5 0.8B model](https://huggingface.co/Qwen/Qwen3.5-0.8B) is Apache 2.0; Qualcomm also provides an [Android-oriented Q4 path](https://huggingface.co/qualcomm/Qwen3.5-0.8B). Its Q4 asset is roughly in the 0.5 GB class.

Pros:

- smallest credible multilingual candidate on the shortlist;
- faster load/generation and lower device burden;
- potential base for task-specific English/Hindi fine-tuning; and
- suitable for a constrained “lite” tier if quality passes.

Cons:

- limited capacity can miss nuance, instructions, tone, or meaning;
- explanation factuality and complex rewriting are weaker risks;
- quantization can worsen the exact tasks for which the size is attractive; and
- a half-gigabyte download is still not “tiny.”

Recommendation: test for Grammar, Rewrite, and Simplify; do not assume it is good enough for Explain or natural contextual translation.

### 5.4 Llama 3.2 1B — explicit-Hindi mobile baseline

Meta's [Llama 3.2 1B model card](https://huggingface.co/meta-llama/Llama-3.2-1B-Instruct) explicitly lists Hindi and mobile writing assistance. Its published OnePlus 12/ExecuTorch SpinQuant result uses a 1,083 MB model, about 1,921 MB RSS, 0.3-second time-to-first-token for a 64-token prompt, and 50.2 decode tokens/second.

Pros:

- Hindi is explicitly in the supported-language list;
- official mobile quantization and ExecuTorch measurements;
- mature ecosystem; and
- useful comparison against newer multilingual candidates.

Cons:

- custom license, attribution, use policy, and commercial condition rather than Apache 2.0;
- older/lower-capability model than the current leaders;
- published RSS is substantial for a 1B model;
- favorable short-prompt flagship measurement is not a general Android result; and
- small-model rewriting can be fluent while changing meaning.

Recommendation: **measured baseline, not default winner**.

### 5.5 Other models

**SmolLM2:** [SmolLM2](https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct) offers Apache 2.0 models at 135M, 360M, and 1.7B and is designed for on-device use, but its own model card says it primarily understands and generates English. It may be useful for English-only rewriting research, not as the default English/Hindi assistant.

**IndicTrans2:** [AI4Bharat IndicTrans2](https://github.com/AI4Bharat/IndicTrans2) is a valuable permissively licensed translation-quality comparator for Indian languages. It is a specialist NMT system, not a general Explain/Rewrite model, and its bidirectional checkpoints/mobile port are too heavy and complex to assume for V1.

**Android AICore / Gemini Nano:** this is not an open model, but it is an important comparator because a shared system model can avoid an app-managed multi-gigabyte asset. Current [ML Kit GenAI APIs](https://developers.google.com/ml-kit/genai) remain device-limited, quota-sensitive, version-variable, and foreground-only. It should be opportunistic, not the only provider.

## 6. Open-weight model advantages

### Product and privacy

- Selected content can remain on-device.
- Core features remain usable without connectivity.
- No per-request inference bill or server outage in the local path.
- Model/version can be pinned and evaluated rather than changing silently.
- Users can see, delete, and opt out of the model pack.

### Capability control

- Fine-tune for English/Hindi, Hinglish, Indian names, and product-specific operations.
- Train for meaning-preserving rewrites and protected tokens.
- Quantize and limit context/output to the actual mobile task.
- Enforce local provider routing and deterministic generation settings.
- Reduce dependence on a single hosted API vendor.

### Long-term economics

- Heavy users do not create linear inference API costs.
- A successful tuned model becomes a product asset.
- Local inference can be cheaper at scale when device support and download cost are acceptable.

## 7. Open-weight model drawbacks

### Size and device cost

- Even the lite tier is hundreds of megabytes; standard models are one to several gigabytes.
- Runtime memory is larger than model-file size because of caches, activations, tokenizer, and runtime overhead.
- Cold loading can take seconds.
- Generation consumes battery, produces heat, and may throttle.
- Android CPU/GPU/NPU behavior varies by SoC, OEM, driver, OS, and available RAM.

### Quality and safety

- Small models can hallucinate explanations, add facts, omit negation, or “improve” meaning away.
- Broad multilingual claims do not establish Hindi quality.
- 4-bit or lower quantization can materially damage particular language tasks.
- Prompt injection can arrive inside selected text.
- General safety tuning can cause unnecessary refusals or inconsistent behavior.

### Engineering and operations

- Model conversion, tokenizer, decoding, JNI, cancellation, memory mapping, acceleration, and lifecycle become product code.
- A model still needs secure distribution, resumable downloads, integrity verification, versioning, rollback, and deletion.
- Model CDN/asset bandwidth replaces some cloud-inference cost.
- Every model/runtime/device combination multiplies QA.
- Fine-tuning needs licensed data, training infrastructure, regression evaluation, and reproducibility.

### Licensing

- “Free to download” is not automatically permissive commercial use.
- Base, quantized conversion, runtime, tokenizer, dataset, adapter, and vendor-optimized package may have different terms.
- Llama is open-weight under a custom license, not Apache.
- Product attribution and notices consume UI/legal work.

## 8. Recommended V3 model architecture

### 8.1 Optional asset packs

Do not bundle a generative model in the base APK/AAB.

Each model pack should have:

- user-initiated resumable Wi-Fi-default download;
- exact download and installed size;
- minimum RAM/SoC/backend compatibility;
- model/runtime/tokenizer version manifest;
- cryptographic checksum/signature;
- atomic install and rollback;
- deletion control;
- no automatic multi-gigabyte update; and
- an offline self-test before “Ready.”

### 8.2 Provisional device tiers

Freeze actual tiers from benchmark data, but begin with this conservative hypothesis:

| Device | Local generative policy |
|---|---|
| Under 6 GB RAM | Dedicated translation and deterministic utilities only |
| 6–8 GB | Evaluate Qwen 3.5 0.8B lite tier; no promise until stability passes |
| 8–12 GB recent device | Evaluate Qwen 3.5 2B or Gemma 4 E2B |
| 12 GB+ recent flagship | Standard tier; larger model only if user value clearly justifies it |

Do not use model size alone. Check actual free memory, backend support, thermal behavior, and free storage.

### 8.3 Generation policy

- Use non-thinking mode for grammar, rewrite, and simplify unless quality testing proves otherwise.
- Keep prompt/context and maximum output short.
- Prefer deterministic or low-variance decoding for meaning-preserving tasks.
- Treat selected text as untrusted quoted data.
- Give the model no tools, file access, network access, or automatic external action.
- Stream visible output when it improves perceived latency.
- Always preview before Replace/Apply.
- Cancel immediately when the result surface closes or a new request supersedes it.

Do not apply a sub-500 ms promise to cloud or local generative operations. Measure cold initialization/connection, warm time-to-first-token, full completion, and user-perceived readiness separately.

## 9. Fine-tuning strategy

Do not fine-tune first.

1. Evaluate unmodified candidates on the product corpus.
2. Identify systematic failures by capability and language.
3. Improve task format, output constraints, and inference settings.
4. Fine-tune only if remaining failures justify ownership cost.
5. Quantize the tuned model and re-run the full corpus; never infer that the full-precision score survived.

Training/evaluation data should include:

- English, Devanagari Hindi, Romanized Hindi, and Hinglish;
- meaning-preserving grammar corrections;
- natural and literal translations labeled separately;
- British versus American English;
- names, numbers, dates, units, URLs, code-switching, and formatting;
- no-change examples so the model learns not to rewrite correct text; and
- adversarial selected text attempting to override the task.

Keep a human-written holdout set that neither training nor teacher-model generation sees. Record provenance and redistribution rights for every dataset.

For a lite tier, task-specific distillation or fine-tuning of Qwen 3.5 0.8B may outperform a generic larger model on narrow operations. It will not automatically become a trustworthy explainer.

## 10. V3 generative decision gate

Benchmark Gemma 4 E2B, Qwen 3.5 2B, Qwen 3.5 0.8B, and Llama 3.2 1B on identical physical devices and tasks.

Measure:

- model download and installed storage;
- cold load and warm time-to-first-token;
- full completion for fixed output lengths;
- P50/P95 latency;
- PSS/RSS and low-memory failure;
- battery/thermal effect;
- cancellation and repeated-run stability;
- English/Hindi/Hinglish task quality;
- meaning preservation;
- British English and style adherence;
- prompt-injection resistance;
- license/runtime/distribution terms; and
- implementation/maintenance complexity.

Suggested task gate:

- Grammar and Rewrite preserve meaning and protected tokens in all critical cases.
- Explain is factually acceptable for the declared scope and clearly assistive.
- Warm output begins fast enough for the chosen device tier; no universal sub-500 ms promise.
- Cold load has a clear user-visible state and does not block typing/UI.
- The model is removable and never silently downloads.
- No content leaves the device in local mode.

## 11. V4 keyboard rules

An open model is useful in a keyboard only if it remains off the keystroke-critical path.

Good uses:

- explicit rewrite of composing/selected text;
- sentence-level grammar review;
- translation preview;
- simplification;
- tone conversion; and
- opt-in local suggestions after a stable pause.

Bad uses:

- regenerating on every key;
- blocking key echo on model readiness;
- reading password/payment/OTP/private fields;
- silently uploading when local inference is unavailable;
- automatically replacing text;
- preserving cross-app context/history by default; and
- loading a multi-gigabyte model merely to type normally.

The keyboard must have a lightweight deterministic baseline for key handling, layout, autocorrect, and recovery. AI is an enhancement that can fail closed while typing continues.

## 12. Recommended decision today

- Use Gemini cloud for the narrow V1 Translate contract and all typed V2 operations; do not expose an open-ended chat surface.
- Use Firebase AI Logic with enforced App Check, not a raw key in the APK.
- Treat English, Devanagari Hindi, and Hinglish/Romanized Hindi as separate benchmark slices and support both Devanagari and natural Romanized Hindi output.
- Treat the free tier as development/validation infrastructure until the external-release privacy and capacity gates are explicitly resolved.
- At V3 start, refresh the landscape and directly benchmark **Gemma 4 E2B** versus **Qwen 3.5 2B**, with **Qwen 3.5 0.8B** as a lite tier and **Llama 3.2 1B** as an explicit-Hindi mobile baseline.
- Prefer Apache-2.0 base artifacts when quality is comparable, but audit optimized package terms separately.
- Offer generative models as optional downloadable packs on measured device tiers.
- In V4, invoke the model explicitly on a bounded text span; never put it on every keystroke.

This uses cloud inference to validate demand and Hinglish quality early, while preserving a credible migration path to the broader local AI language assistant. The product must not claim the original offline/privacy strengths until V3 local processing—or a reviewed paid-cloud posture—actually provides them.
