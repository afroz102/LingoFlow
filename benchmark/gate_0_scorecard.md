# Gate 0 scorecard

Formal walkthrough of [VALIDATION_PLAN.md §2.5](../docs/VALIDATION_PLAN.md#25-gate-0-pass-criteria)
against the evidence gathered so far (`compatibility_matrix.csv`, `gate_0_result.md`). This is
**IMPLEMENTATION_PLAN.md §2 item 13** — checking results against pass criteria — done honestly:
every criterion below is either met on every row actually tested, or explicitly marked as not yet
verifiable. None of this is a claim that Gate 0 has fully passed; see the verdict at the bottom.

| # | Criterion | Status | Evidence |
|---|---|---|---|
| 1 | Exact input is received in every matrix row that declares Process Text support | **Met, on tested rows** | Every successful discovery row (testhost read-only TextView, real Chrome) delivered the selected text byte-for-byte, including Devanagari and Hinglish samples. |
| 2 | Read-only input never exposes Replace | **Met** | Confirmed on every read-only test, both via direct intent and real host discovery — Replace is never rendered when `EXTRA_PROCESS_TEXT_READONLY` is true. |
| 3 | Explicit Replace works in every matrix row declared replacement-compatible | **Met, on tested rows** | Verified via testhost and direct-intent editable cases: `RESULT_OK` with `EXTRA_PROCESS_TEXT` set, clean task teardown, no crash. |
| 4 | Copy works from the result surface | **Met** | System clipboard preview toast + in-app "Copied" confirmation, verified repeatedly. |
| 5 | Cancel, Back, and errors leave the host unchanged | **Met** | Back propagates `RESULT_CANCELED` with no extras through the full relay chain; every malformed-input case (wrong action, missing/wrong-type extra, whitespace-only, oversized, malformed parcel) resolves to a silent no-op before `ResultActivity` is ever shown. |
| 6 | No Accessibility, overlay, IME, clipboard-read, background-service, or dangerous permission is used | **Met** | Manifest audit (`aapt2 dump xmltree`): one exported activity (`ProcessTextActivity`, intended), one exported receiver (AndroidX `ProfileInstallReceiver`, gated behind the system-only `DUMP` signature permission), one self-signed AndroidX boilerplate permission. No `INTERNET`, no dangerous permission, no services. |
| 7 | Unsupported hosts and overflow placement are documented | **Met** | `compatibility_matrix.csv` documents the Compose and embedded-WebView non-discovery cases as findings, not omissions. Overflow (not top-level toolbar) placement is documented for every successful discovery row. |
| 8 | The product support statement is revised to match evidence | **Met, this pass** | `PRODUCT_REQUIREMENTS.md` §6 now cites the specific Stage 0A findings (Compose/WebView absence, Chrome presence, the `<queries>` host-side dependency) directly, with a pointer to the full evidence trail. |
| 9 | No selected text appears in network traffic, logs, persistent storage, notifications, or Recents snapshots during the stub flow | **Met, after a fix** | Network/logs/storage audited clean with a canary string (Privacy and Security §8 procedure). Recents snapshot leakage was found (a real bug — the calling host's own task snapshot captured our dialog's content despite `excludeFromRecents`) and fixed with `FLAG_SECURE`, then re-verified three ways. Notifications were not runtime-tested; the app has no notification-posting code path at all, so this is satisfied by absence of the capability rather than by a runtime negative test. |

## Overall verdict: not a Gate 0 pass — conditionally clean on everything actually tested

Every criterion holds on every row this environment could exercise. That is a meaningfully
different claim from "Gate 0 passes." The gate is scoped to
[VALIDATION_PLAN.md §2.3](../docs/VALIDATION_PLAN.md#23-compatibility-matrix)'s full matrix — five
OEM/device families (Pixel/AOSP, Samsung, OnePlus, Xiaomi, a low-memory device), five OS bands,
real IMEs, and ten representative hosts. This pass has covered one AOSP-like emulator profile
across two of the five OS bands (API 30 and API 34 — Android 11 and Android 14), no real IME, and
two of the ten representative hosts (a controlled test host and Chrome). The remaining coverage
needs physical hardware this environment does not have — that is the actual blocker to a scored
pass, not any known failing criterion.

**Recommendation**: do not read this scorecard as clearance to start Stage 1. The physical-device
matrix should still happen before treating Gate 0 as passed, per
[IMPLEMENTATION_PLAN.md §2 item 13](../docs/IMPLEMENTATION_PLAN.md#2-stage-0a--process-text-platform-spike--gate-0)'s
own instruction: stop and revisit product scope if the target host segment fails, rather than
compensating with a prohibited mechanism. Nothing found so far suggests that outcome — but nothing
found so far rules it out on hardware not yet tested, either.
