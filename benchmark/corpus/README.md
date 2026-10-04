# corpus/

The frozen translation-quality corpus for the Gate 1 model bake-off
([VALIDATION_PLAN.md §4.1](../../docs/VALIDATION_PLAN.md#41-corpus)), built as
[IMPLEMENTATION_PLAN.md §3](../../docs/IMPLEMENTATION_PLAN.md#3-stage-0b--freeze-the-benchmark-protocol)
item 3.

| File | What it is |
|---|---|
| `corpus_v1.jsonl` | The corpus. One JSON object per line, 458 items. |
| `coverage_report.md` | Generated counts by direction, length bucket, critical kind and slice. Regenerate, don't hand-edit. |

Validate and regenerate the report with:

```
python3 benchmark/validate_corpus.py --report benchmark/corpus/coverage_report.md
```

The validator is a gate, not a formatter: it fails loudly on unbalanced directions, a
shrunken critical subset, a missing slice, a mis-stated length bucket, a protected token
that isn't actually in the source, or a source longer than the app's own 4,000-character
input cap. Run it after any edit.

## Why JSONL

One item per line keeps diffs readable when items are added or corrected — a reviewer
disagreement about a single sentence shows up as a one-line change rather than a
reformatting of the whole file. It also means a malformed edit fails on one line instead
of invalidating the entire corpus.

## Item schema

| Field | Meaning |
|---|---|
| `id` | Stable identifier. The prefix encodes the direction (`en_hi`, `hi_en`, `hg_en`, `en_hg`) and the validator enforces the match. |
| `direction` | `en_to_hi`, `hi_to_en`, `hinglish_to_en`, or `en_to_hinglish`. |
| `source` | The exact text a user would have selected. This is what gets sent to the model — nothing else. |
| `register` | `formal`, `informal`, or `neutral`. Register inversion is a scoreable quality failure, so it has to be recorded per item. |
| `length_bucket` | Derived from the source length, and re-derived by the validator. It slices the latency results per [§3.3](../../docs/VALIDATION_PLAN.md#33-workloads-networks-and-states), so a wrong bucket corrupts the latency report, not just the metadata. |
| `slices` | The §4.1 phenomena this item covers (`negation`, `dosage`, `code_mixing`, …). Drives the coverage report. |
| `critical` / `critical_kind` | Whether this item is in the protected critical subset, and which failure mode it guards against. |
| `protected_tokens` | Substrings that must survive verbatim — names, digits, URLs, emails, codes. The validator checks each one actually occurs in the source. |
| `assertion` | What a reviewer should check, in one or two sentences. Deliberately **not** a reference translation — see below. |
| `latency_slice` | `primary` or `extended`. §4.1 requires longer paragraphs to be reported outside the primary latency slice. |
| `release_scope` | Whether the item counts toward the §4.3 "95% of release-scope items score ≥3" gate. Degenerate and exploratory items are excluded. |

## Why there are no reference translations

[§4.2](../../docs/VALIDATION_PLAN.md#42-review-method) scores model output directly on a
1–5 scale with two blinded reviewers. It does not compare output against a reference. Adding
reference translations would therefore not feed the gate — and would actively harm it, because
a reviewer who has seen a reference anchors on it and scores divergence rather than quality.
The `assertion` field carries what the reviewer actually needs (what must survive) without
supplying a target to match.

The consequence: automatic BLEU/ChrF-style regression metrics, which §4.3 permits as a
*supporting* signal, are not available from this corpus as it stands. If those are wanted later,
add a separate `reference` field populated by the bilingual reviewers **after** the blinded
scoring round, never before it.

## Composition

458 items, balanced across the four directions (117 / 116 / 113 / 112) with 244 in the
protected critical subset. Full breakdown in `coverage_report.md`.

The critical subset is built mostly from **minimal pairs** — two items identical except for the
one thing that must not flip (`en_hi_018`/`en_hi_019` for a prohibition, `hi_en_020`/`hi_en_021`
for a dosage, `hg_en_060`/`hg_en_061` for left-versus-right). A model that scores well on one
half of a pair and badly on the other has a specific, nameable defect; a model that scores well
on both has actually preserved the distinction rather than guessed the common phrasing.

Four `near_limit` items are **composites**, assembled from this corpus's own sentences to sit
just under the 4,000-character input cap. They exist to measure latency, truncation and memory
at the input boundary, and are excluded from the quality gate because they were assembled rather
than naturally written. Every other item is authored.

## Status: NOT yet frozen — one blocking dependency

This corpus is complete and structurally validated, but it is **not** frozen, for one honest
reason: the Hindi and Hinglish source text in it has not been checked by a native bilingual
speaker. It was authored as part of the Stage 0B backfill, and
[IMPLEMENTATION_PLAN.md §3](../../docs/IMPLEMENTATION_PLAN.md#3-stage-0b--freeze-the-benchmark-protocol)
item 4 — recruiting two independent bilingual reviewers — has not happened yet.

That matters concretely: if a Hindi source sentence is subtly unnatural, every model in the
bake-off is scored on text no real user would have selected, and the resulting quality numbers
are measuring the wrong thing. Naturalness of the *source* is a precondition for the review
protocol, not an output of it.

**Before this corpus is frozen and used for Gate 1**, the reviewers recruited under §3 item 4
must pass over every `hi_to_en`, `hinglish_to_en` and `en_to_hinglish` item and confirm the
source reads as something a real speaker would write. Corrections at that stage are expected and
are not a defect in the corpus. Freeze it — tag the version, record the reviewers — only after
that pass. Until then, treat `corpus_v1.jsonl` as a complete draft.
