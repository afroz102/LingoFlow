# Stage 0B — frozen benchmark protocol

The exit artifact for
[IMPLEMENTATION_PLAN.md §3](../docs/IMPLEMENTATION_PLAN.md#3-stage-0b--freeze-the-benchmark-protocol):
the corpus, the device/host/keyboard matrix, the budgets, and the timing harness, frozen
**before** the Gate 1 model bake-off runs so that no measurement decision can be made after
seeing which model is winning.

This document records what is frozen, what is drafted but not yet frozen, and what is blocked
on the project owner. It does not claim Stage 0B is complete — §6 below says exactly what
remains.

---

## 1. Reference devices (§3 item 1) — BLOCKED on the project owner

[VALIDATION_PLAN.md §3.2](../docs/VALIDATION_PLAN.md#32-reference-devices) requires three
physical tiers, selected and frozen *before* any results are recorded. No physical devices are
available in the current development environment, and no devices have been reserved.

Fill this table in and commit it before running any timed benchmark. Leaving it empty and
benchmarking on whatever hardware is at hand is exactly the failure this section exists to
prevent — it makes latency numbers incomparable between runs.

| Tier | Minimum intent | Exact device (fill in) | Android build | RAM | Reserved by |
|---|---|---|---|---|---|
| Low | 4 GB RAM, older/mid CPU, within min supported Android | _not yet reserved_ | | | |
| Mid | 6–8 GB RAM, mainstream current Samsung/OnePlus class | _not yet reserved_ | | | |
| High | Recent Pixel or Snapdragon flagship | _not yet reserved_ | | | |

Per-run state to record alongside every result, per §3.2: thermal state, battery level, power
mode, available storage, carrier/network, signal, VPN/private DNS, Firebase/App Check state, and
whether the process already has a live connection.

---

## 2. Frozen host, field and keyboard matrix (§3 item 2) — FROZEN

Stage 0A's matrix is adopted unchanged as the frozen set for cloud benchmarking, so that Gate 0
platform findings and Gate 1 latency/quality findings describe the same surface. The full
specification is [VALIDATION_PLAN.md §2.3](../docs/VALIDATION_PLAN.md#23-compatibility-matrix);
it is restated here as the frozen list rather than referenced loosely.

**OS bands** (5): API 23 · API 26–27 · API 30 · API 31–33 · current stable Android.

**OEM/device families** (5): Pixel/AOSP-like · Samsung Galaxy · OnePlus · Xiaomi or Redmi · one
4 GB low-memory device.

**Representative hosts** (10): Chrome or another Chromium browser · Gmail · Google Messages ·
WhatsApp · Telegram · Google Docs · Samsung Notes (on Samsung) · one Compose-heavy application ·
one PDF viewer · one application with custom/non-standard selection.

**Keyboards** (3 required, 1 optional): Gboard · Samsung Keyboard · Microsoft SwiftKey · one
additional installed IME if available.

**Field types**: read-only text · editable text · password field (negative case) · Compose
selection surface · WebView-hosted text.

Two things carry forward from Stage 0A as **findings, not omissions**, and stay in the frozen
matrix so the next pass re-checks them rather than assuming them:

- the Compose `SelectionContainer` toolbar did not surface third-party `PROCESS_TEXT` actions at
  all on the tested BOM; and
- a bare embedded `WebView` did not surface the action, while full Chrome did.

Coverage actually achieved so far is 2 of 5 OS bands, 1 of 5 device families, 2 of 10 hosts and
0 of 4 keyboards — see [`gate_0_scorecard.md`](gate_0_scorecard.md). Freezing the matrix does not
close that gap; it fixes what the gap is measured against.

---

## 3. Corpus (§3 item 3) — DRAFTED, not frozen

[`corpus/corpus_v1.jsonl`](corpus/corpus_v1.jsonl): 458 items across the four directions, 244 in
the protected critical subset, structurally validated by
[`validate_corpus.py`](validate_corpus.py).

**Not frozen**: the Hindi and Hinglish source text has not been checked by a native bilingual
speaker. If a source sentence is subtly unnatural, every model in the bake-off is scored on text
no real user would have selected. See [`corpus/README.md`](corpus/README.md) "Status" for the
specific pass that has to happen first — it depends on §4 below.

---

## 4. Blinded reviewers (§3 item 4) — BLOCKED on the project owner

[VALIDATION_PLAN.md §4.2](../docs/VALIDATION_PLAN.md#42-review-method) requires two independent
fluent bilingual reviewers, blinded to model identity, with adjudication of disagreements of two
or more points. None are recruited.

| Role | Name/contact (fill in) | Blinding confirmed | Adjudicator for disagreements ≥2 |
|---|---|---|---|
| Reviewer A | _not recruited_ | | |
| Reviewer B | _not recruited_ | | |
| Adjudicator | _not assigned_ | | |

These reviewers have two jobs, in order: first validate the corpus source text (§3 above), then
score model outputs. Recruiting them is on the critical path for Gate 1 — the bake-off cannot
produce a quality verdict without them, no matter how much engineering is finished.

---

## 5. Budgets (§3 item 5) — PROPOSED as-is, awaiting sign-off

[IMPLEMENTATION_PLAN.md §3](../docs/IMPLEMENTATION_PLAN.md#3-stage-0b--freeze-the-benchmark-protocol)
item 5 allows exactly two options: adopt
[VALIDATION_PLAN.md §3.6](../docs/VALIDATION_PLAN.md#36-provisional-v1-budgets)'s provisional
budgets as-is, or revise them with explicit sign-off. Never silently.

**Recommendation: adopt all of §3.6 as-is, unrevised.** They were written before any model was
measured, which is precisely what makes them usable as gates. There is no measured evidence yet
that would justify moving any of them, and moving a budget without evidence is the "quietly
weaken measurements after choosing a favorite model" failure §3.6 warns against.

One budget deserves attention at sign-off rather than revision now: **full response ≤200 chars on
stable reference Wi-Fi, P50 ≤1,000 ms / P95 ≤2,000 ms**. That is an end-to-end budget covering a
cold-ish client, App Check attestation, a network round trip to a cloud model, and render. If any
candidate misses it, the honest options are to revise the budget with sign-off *before* seeing
the per-model comparison, or to change the product trade-off — not to re-slice the measurement
afterwards.

Sign-off is a product decision and cannot be made from engineering. Record it here:

| Field | Value |
|---|---|
| Decision | ☐ adopt §3.6 as-is ☐ revise (attach revised table + rationale) |
| Signed off by | _pending_ |
| Date | _pending_ |

Until this is signed, treat §3.6 as the operative budget set — that is the conservative reading,
since adopting as-is is also the recommendation.

---

## 6. Timing harness (§3 item 6) — see `TIMING_HARNESS.md`

Implementation and current verification status are documented in
[`TIMING_HARNESS.md`](TIMING_HARNESS.md).

---

## What still blocks Stage 0B from being complete

| § | Item | Status | Blocked on |
|---|---|---|---|
| 1 | Reference devices | Not reserved | Project owner — physical hardware |
| 2 | Host/field/keyboard matrix | **Frozen** | — |
| 3 | Corpus | Drafted, 458 items, validated | Native bilingual source-text pass (needs §4) |
| 4 | Blinded reviewers | Not recruited | Project owner |
| 5 | Budgets | Proposed as-is | Product sign-off |
| 6 | Timing harness | See `TIMING_HARNESS.md` | — |

Stage 1 items 7–11 (timing instrumentation against real calls, the model bake-off, blinded
review, the decision scorecard, the Gate 1 check) stay blocked until §1, §3 and §4 are closed.
