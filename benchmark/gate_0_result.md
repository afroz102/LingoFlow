> Historical platform-spike evidence using the former stub provider. That provider has been
> removed; these findings are not current backend/network or physical-device release results.

# Gate 0 result — in progress, not a pass/fail verdict yet

Status as of this entry: **partial evidence gathered on two emulator OS bands, one device
profile**. This is not a Gate 0 pass — see the historical scorecard below for the formal criterion-by-
criterion walkthrough. [VALIDATION_PLAN.md §2.3](../docs/VALIDATION_PLAN.md#23-compatibility-matrix)
requires physical devices across five OEM/device families and five OS bands, real IMEs, and ten
representative host apps. Nothing here substitutes for that. What follows is what has actually
been exercised, so it doesn't have to be re-derived from scratch.

## Environment

- Emulators: Pixel 6 AVD profile, `google_apis` arm64-v8a system images, on two OS bands —
  Android 14 / API 34 (`google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.050/12077443:userdebug/dev-keys`)
  and Android 11 / API 30 (the exact version that introduced package-visibility/`<queries>`).
- No physical device, no real IME engaged (input driven via `adb shell input`), no OEM skin.
- Builds: `app-debug` 0.1.0-stage0a, `testhost-withQueries`/`testhost-withoutQueries`
  0.1.0-{with,without}-queries.

## What's confirmed (see `compatibility_matrix.csv` for the row-level detail)

1. **Real cross-package discovery works.** `testhost` (a separate installed package, not a
   same-package harness) surfaces our `Translate` action from a genuine long-press text
   selection on a read-only `TextView`, in the overflow menu specifically — not the top-level
   toolbar. Action location in the overflow, not top-level, is itself a finding for §2.3's
   "action visible/location" column, not a bug.
2. **The Android 11+ `<queries>` hypothesis is directly confirmed, on two separate OS bands.**
   Identical app, identical field, identical selected text; the only variable is whether the host
   declares the `<queries>` package-visibility element. With it: `Translate` appears. Without it:
   it does not, and only system actions (`Read aloud` on API 34; nothing beyond
   Copy/Share/Select all on API 30) show. Re-run on Android 11 / API 30 — the exact version that
   introduced this behavior — with the same result as API 34, so this isn't an API-34-specific
   artifact. This is exactly the variable docs/TECHNICAL_PLAN.md and VALIDATION_PLAN.md
   §2.2 call out. minSdk=23 install and launch were also confirmed working on the API 30 image.
3. **Read-only vs. editable is handled correctly.** Read-only selection → Copy only, no Replace
   button rendered. Editable selection → both Copy and Replace, and Replace closes the whole
   task cleanly (confirmed via `adb logcat`: no `FATAL EXCEPTION`, clean activity teardown back
   to the host).
4. **Copy actually writes the system clipboard**, confirmed via the OS's own clipboard-preview
   toast plus this app's own "Copied" confirmation.
5. **Password field negative case holds**, though this is the platform's own behavior
   (`inputType="textPassword"` fields never offer Process Text actions to any app), not
   something our code does.
6. **Malformed-input handling has no crash path**, exercised directly via `am start` against the
   exported activity (not through host discovery): wrong action, missing extra, whitespace-only
   text, wrong extra type (`int` instead of `CharSequence`), and oversized input (4500 chars,
   over the 4000-char provisional limit) all resolve to a silent return to the previous screen —
   no crash, no `ResultActivity` shown, no host modification.
7. **Direction detection and the stub provider behave as designed**: `hello` → English→Hindi →
   dictionary hit `नमस्ते`; `नमस्ते` → Hindi→English → dictionary hit `hello`; `test` (no
   dictionary entry) → deterministic fallback `[stub EN→HI] tset`.
8. **The custom-canvas negative case holds.** Long-pressing `CustomSelectionTextView` — a view
   that deliberately never calls `startActionMode`, only paints a local highlight — produces
   exactly that local highlight and nothing else: no system selection toolbar, no Copy, no
   `Translate`, confirming zero accidental framework text-selection integration.
9. **Compose's selection toolbar doesn't surface `Translate` at all**, on this Compose BOM.
   Long-pressing text inside a `SelectionContainer`/Material3 `Text` shows only a bare `Copy` —
   no `Share`, `Select all`, or overflow menu, tested with both a single-word and a much longer
   multi-word selection. This isn't a bug in our code; it means the Compose text-selection
   surface itself doesn't expose a path to third-party `PROCESS_TEXT` actions in this
   environment, which is a real product-relevant finding for whatever fraction of target hosts
   are Compose-built.
10. **A bare embedded `WebView` doesn't surface `Translate` either**, but for a different reason
    than Compose: its toolbar does show `Copy`/`Share`/`Select all`/`Read aloud`, just no overflow
    and no `Translate`. This is genuinely surprising next to finding 11 below, and is exactly the
    kind of host-specific divergence VALIDATION_PLAN.md §2.3 keeps "WebView-hosted" and
    "Chrome/Chromium browser" as separate matrix rows for.
11. **Real Chrome does surface `Translate`, and the full loop works from it.** Tested against an
    actual (unmodified, AOSP-preinstalled) Chrome on a real rendered page
    (`https://example.com`), not just the synthetic `testhost` fixture: long-press → `Translate`
    in the overflow (next to Chrome's own `Read aloud`) → our app opens → correct translation of
    the selected word `Domain` → correct read-only gating (no Replace). This is the first
    evidence against a genuine external representative host from VALIDATION_PLAN.md §2.3's list,
    not a controlled fixture.

## Two real bugs this testing caught and fixed

### 1. `windowNoDisplay` crash (found during the initial smoke test)

`ProcessTextActivity` originally used a `windowNoDisplay="true"` theme on the assumption that
starting a child activity for result would satisfy Android's "activity without a UI" contract.
It doesn't: `windowNoDisplay` specifically requires `finish()` before `onResume()` completes,
which is incompatible with staying alive (invisible, in the back stack) while awaiting
`ResultActivity`'s asynchronous outcome. This crashed on first launch
(`did not call finish() prior to onResume() completing`). Fixed by dropping
`windowNoDisplay` and keeping only `windowIsTranslucent` + no animation — see
`app/src/main/res/values/themes.xml` (`Theme.InstantTranslate.Trampoline`).

### 2. Selected/translated content leaked into the host app's Recents snapshot (found during the
   Privacy and Security §8 storage audit)

`android:excludeFromRecents="true"` is set on both `ProcessTextActivity` and `ResultActivity`,
and correctly keeps our own task out of the Overview task list. It does **not**, however, stop
the *calling host's* task snapshot from compositing whatever was visually drawn on top of it —
our translucent `ResultActivity` dialog — at the moment the user leaves via the Overview gesture.

Reproduced directly: ran the flow from `testhost`, waited for the dialog to render, pressed
Overview, and the resulting Recents card for `testhost`'s task showed our dialog's original and
translated canary text in full, readable detail. This is a direct violation of Gate 0's own pass
criterion ("No selected text appears in ... Recents snapshots") and of Privacy and Security §8's
"any selected-content finding outside the intended Gemini request/response is release-blocking."

Fixed by setting `WindowManager.LayoutParams.FLAG_SECURE` on `ResultActivity`'s window
(`app/src/main/java/com/lingoflow/instanttranslate/ui/ResultActivity.kt`) — the standard Android
mechanism for excluding a window from both screenshots and Recents thumbnail capture. Verified
three ways after the fix, all using the exact same reproduction steps that showed the leak:

- `adb shell screencap` returns an empty capture whenever `ResultActivity` is the foreground
  activity, or whenever its content would be part of an Overview composite — confirming the
  surface itself is now marked non-capturable, not just visually redacted.
- A `uiautomator dump` of the Overview screen after the same repro steps shows no text nodes
  from our content at all (only the system's own `Screenshot`/`Select` labels) — the redaction
  holds at the accessibility-tree layer too, not only pixels.
- Screenshots taken immediately after leaving `ResultActivity` (Back button) work normally again
  (verified non-empty), confirming the flag is scoped to that window only and doesn't leak into
  the rest of the system.

Trade-off accepted: `FLAG_SECURE` also blocks the user from taking their own screenshot of the
translation. Not flagged as a problem, since Copy already covers the "save the output" use case
and the flag is only on the transient result dialog, not the whole app.

Both bugs are recorded here because they're exactly the kind of platform-behavior facts Gate 0
exists to surface, and neither would have been caught by compiling alone.

## Network / log / storage / idle audit (Privacy and Security §8)

Run with a unique canary string (`ZQCANARY7f3a9c1e` and others) embedded in the selected text
across editable, read-only, Replace-clicked, and malformed/whitespace-boundary variants, per the
verification procedure in [privacy audit](../docs/TECHNICAL_PLAN.md#9-audit-and-release-review).

- **Network**: `dumpsys netstats` shows zero recorded traffic for the app's UID before and after
  running multiple full flows — consistent with no `INTERNET` permission being declared and the
  Stage 0A stub doing no networking. Nothing to intercept yet; this check gets materially harder
  and more important once Stage 1 adds real network calls.
- **Logs**: the canary string, and its reversed stub-transform variant, never appear anywhere in
  the full `logcat` buffer across all tested flows, including the two stale crash reports left
  over from the `windowNoDisplay` bug (confirming that even that crash never leaked selected
  content into its stack trace).
- **Storage**: `/data/data/com.lingoflow.instanttranslate` contains exactly one file
  (`files/profileInstalled`, an AndroidX Profile Installer marker, no user content) — no
  `shared_prefs`, no databases, no cache content, and a recursive `grep` for the canary across
  the whole app data directory finds nothing.
- **Idle/background**: no alarms, no wake locks, and no services registered under our package
  (the only running "service" attributable anywhere near our apps is WebView's own sandboxed
  renderer process, owned by `testhost`, not us). The `TopAppTimer`/`ShrinkableDebits` entries
  under our package in `dumpsys jobscheduler` are the OS's own per-app foreground-usage
  bookkeeping applied uniformly to every installed app, not anything we scheduled.
- **Manifest/build**: merged manifest audited via `aapt2 dump xmltree`. Exactly one exported
  activity (`ProcessTextActivity`, as intended) and one exported receiver
  (`androidx.profileinstaller.ProfileInstallReceiver`, standard AndroidX boilerplate, gated
  behind the system-only `android.permission.DUMP` signature permission). The only declared
  permission is a self-defined signature permission
  (`...DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) auto-generated by AndroidX tooling, not
  something declared by this app's own code. No `INTERNET`, no dangerous permission, no
  Accessibility/overlay/IME.

## Lifecycle edge cases (Validation Plan §2.4)

All exercised via `adb`/`uiautomator` (no crash in any case, confirmed via `logcat` after each):

- **Rotation mid-flow**: state survives correctly — `uiautomator dump` after rotating shows the
  same original/translated text and direction as before rotation, confirming the `ViewModel`
  isn't re-querying the provider or losing state on a config change.
- **Backgrounding + Overview**: no crash; confirmed (see FLAG_SECURE finding above) that no
  content leaks through this path either.
- **Process death**: killed the app process (`adb shell am kill`) while `ResultActivity` was
  backgrounded, confirmed the process was actually gone (`pidof` empty), then attempted to bring
  the task back — no crash, no zombie state. Consistent with the intended design: this is a
  transient, `excludeFromRecents`/`noHistory` flow with nothing meant to survive for the user to
  resume into.
- **Rapid repeated launches**: fired 5 `PROCESS_TEXT` intents at the exported activity
  concurrently with no waits between them. Exactly one instance rendered (the others were logged
  by the system as "delivered to currently running top-most instance" and silently absorbed,
  since `ProcessTextActivity` doesn't override `onNewIntent`); no crash, no mixed/corrupted state
  across the two competing texts. Worth a product decision later (should a second concurrent
  Process Text request replace the first, or be ignored as it is now?) but not a Gate 0 blocker.
- **Back/cancel**: a single Back press from `ResultActivity` correctly propagates
  `RESULT_CANCELED` back through `ProcessTextActivity` to the host and closes both activities.
- **Source-field change while result is open**: not independently re-tested this pass — it's
  satisfied by construction, not by a runtime check. `ResultActivity` only ever holds the
  immutable `String` snapshot `ProcessTextActivity` flattened at request time; it has no live
  reference to the host's field, so a host-side edit while the dialog is open cannot affect or
  crash it. A synthetic repro would need to inject input past our modal dialog, which doesn't
  reflect anything a real host interaction could do.

## What's still open before this gate can actually be scored

- Everything in VALIDATION_PLAN.md §2.3's device/OS/host/keyboard matrix beyond two emulator
  OS bands on one device profile — no Samsung/OnePlus/Xiaomi/low-memory device, no API
  23/26/27/31-33 coverage (30 and 34 are now covered), no Gboard/SwiftKey/Samsung Keyboard, no
  Gmail/WhatsApp/Telegram/Docs/Samsung Notes/PDF viewer hosts (Chrome is now covered, see finding
  11). All of this needs physical hardware this environment doesn't have.
- The Compose and WebView findings (9, 10) need re-confirmation on a physical device and against
  a real IME/newer Compose BOM before treating them as more than this-environment observations —
  they're plausible platform behavior, not yet corroborated a second way.
- The FLAG_SECURE fix needs re-verification on a physical device/real OEM launcher — Recents
  implementations vary enough across skins (Samsung, OnePlus, MIUI) that this should not be
  assumed universal from one AOSP-like emulator.
- The network audit is necessarily thin right now (there's no network code yet to audit
  meaningfully) — it needs to be re-run in full once Stage 1 adds the Gemini provider, per
  TECHNICAL_PLAN.md's network section (airplane mode, App Check init, error/cancel
  paths, 30-minute idle).


# Historical Gate 0 scorecard

Formal walkthrough of [VALIDATION_PLAN.md §2.5](../docs/VALIDATION_PLAN.md#25-gate-0-pass-criteria)
against the evidence gathered so far (`compatibility_matrix.csv`, `gate_0_result.md`). This is
**docs/TECHNICAL_PLAN.md** — checking results against pass criteria — done honestly:
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
[benchmark protocol](../docs/VALIDATION_PLAN.md)'s
own instruction: stop and revisit product scope if the target host segment fails, rather than
compensating with a prohibited mechanism. Nothing found so far suggests that outcome — but nothing
found so far rules it out on hardware not yet tested, either.
