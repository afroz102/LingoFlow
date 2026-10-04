# Product requirements — LingoBoard

Updated 2026-10-05 for the tabs and local suggestions release (APK version `1.1.0`). This expands the earlier Roman Hindi/English-only keyboard. Historical evidence remains dated in `benchmark/` and Git history.

## Language and purpose

45 languages and 67 native/Roman translation options. All 22 scheduled Indian languages have
both script options; Russian, French, Spanish, German, Korean, Indonesian, Chinese, Japanese
and other common global languages are included. Auto source and English target are the defaults.
Hinglish includes informal Roman Hindi mixed with English. Translate meaning, intent, tone,
negation and idioms; preserve names, numbers, game terms, URLs, emojis and message formatting.
Use context inside the supplied passage, without inventing missing chat history.

## User flows

| Use case | Behavior |
|---|---|
| Keyboard writing | Translate icon → separate local draft → choose source/target languages → Translate & insert into chat |
| Keyboard reading | Copy received message → open composer → Read → confirm → result card above keys |
| Selected editor reading | Select text in active editor → Translate icon → result card above keys |
| Read-only selection action | Select message → LingoBoard Translate → open composer within 60 seconds → keyboard English card |
| Selection writing | Type → select draft → LingoBoard Translate → translate and automatically replace selection |
| Optional floating reading | Start reading session → copy → prompt → confirm → English floats over host |
| Other keyboard floating fallback | Copy message → tap Lingo bubble → English floats over host |

Keyboard writing never sends a chat message automatically. It defaults to Auto → English; both selectors offer explicit languages and remember the user’s
choices. Indian Roman targets are distinct options. Swap exchanges explicit languages; Auto →
English swaps to English → Hindi (Roman), since Auto cannot be a target. Source typing stays
inside its local, cursor-editable draft, not the host chat. Translation errors retain that source.
The compact toolbar has Translate, Read and keyboard-switch icons. Source/swap/target controls
appear in the header only while a translation panel is open. The logo remains on the setup
screen. Paste sits inside the draft field; a round translation arrow sits beside it. Read remains
highlighted and its arrow translates without inserting.

Local typing offers a number row, one-shot Shift, double-tap/hold Caps Lock, three symbol pages
with 111 distinct characters, selection-aware/hold-repeat backspace and 50 smiley emoji in a
fixed grid. Comma sits left of emoji. All key pages retain the same total height. Keys use one
canvas, immediate press feedback and release-based character entry with sliding and multi-finger
rollover. Letters have no long-press alternates; hold Space to switch keyboards. The palette is
neutral graphite with an indigo accent and system light/dark mode. No promise of Gboard feature
or performance parity.

The selected action itself confirms a selection request. An automatic copy prompt must never
send the message before confirmation. Reading never changes a host selection or clipboard
unless the user presses Copy. Cancel/error never changes a draft. No global paste commands.

## Platform boundary

Writing uses `ACTION_PROCESS_TEXT` with an editable flag and returns a replacement to the host.
Read-only selections hand off to the selected LingoBoard by default; an explicitly running
floating session remains a separate route. Host support is required; the app cannot insert a
selection action into custom message renderers.

Keyboard translation needs no overlay permission, foreground session or Accessibility Service.
Android only shares selected text from the active input connection. A received-message selection
outside an editable field cannot generally be observed by a keyboard; copy it and open the chat
composer. Apps must expose a working editor/IME for this route. Input hiding, editor changes and
process death discard drafts/results and cancel requests. Changed host cursor/selection prevents
automatic insertion of a late response; the result offers explicit Insert here or Copy.

Optional automatic floating copy prompts require the selected LingoBoard plus a user-started
reading session. Overlay permission alone does not enable clipboard access. Other keyboards use
the manual bubble route. No background clipboard polling or Accessibility Service is used.

Minimum API 23 remains provisional; compile/target API 34. Android/OEMs and host apps may restrict
overlays. Universal support has not been established. Game of Khans, Discord, WhatsApp,
Telegram and Instagram messaging are targets, awaiting real-device testing.

## Implemented requirements

- Launcher setup prioritizing keyboard translation; floating mode remains optional.
- Write/read translation panels, local draft, language selectors, swap and inline result.
- Password fields disable translation actions; sensitive and own-output clips are skipped.
- Automatic replacement on validated writing success; no preview/Replace step.
- Floating English reading results, draggable card/bubble, scrollable long output, Copy,
  Minimize, Close, Stop, loading, explicit Retry and error states.
- Notification with Stop; user-started foreground session, no boot/process restart.
- Default-IME clipboard listener, local confirmation, duplicate/own-result suppression,
  password-field and sensitive-clipboard suppression; prompt expires after 60 seconds.
- Manual bubble route briefly focuses only after a user tap to read the clipboard.
- One active request/card; newer copies cancel the previous in-memory request/prompt.
- Versioned cloud disclosure before content requests. No sign-in or content history.
- One server-side Gemini call per accepted request, including writing language detection.
- Local/server input bounds, allowlisted language options and Roman-output script validation, validated output and shared quota.

## Quality and completion boundary

Source detection and translation share one Gemini request. Short ambiguous words may require
the user to choose a source explicitly. Reading uses the selected target, defaulting to English.
Already-target-language input should retain its meaning and requested writing system. Native
Hindi/Devanagari support is restored as an explicit translation option. The typing layout
remains Latin QWERTY. The app and IME allow screenshots. Light/dark themes, icon toolbar,
larger keys and in-place draft editing improve appearance and input responsiveness.

The backend has no local/system translation fallback. Human evaluation of real Hinglish, game
slang, negation and ambiguity remains required. No claim of superiority to another translator
or guaranteed model behavior follows from a smoke test.

Full target-app/OEM compatibility, physical performance/battery, process-death and permission
revocation stress testing, TalkBack/font scaling, and public distribution review remain open.
The keyboard has local English/Hinglish word suggestions, but no automatic correction on Space,
swipe or voice input. Physical typing latency,
font scaling, numeric layouts and target-app behavior still need broader device testing.

### 1.1.0 tabs and suggestions

Write and Read behave as tabs: switching must feel instant (no panel rebuild or flash), keep each
tab's draft/result, and never move received (Read) text into the Write draft. Add Gboard-style
suggestions: a three-slot strip while typing that completes, corrects and predicts words (English
and Hinglish), inserting the tapped word at the cursor. Translate remains reachable while the
strip is shown; Read returns when suggestions clear. Ordinary typing stays local. Finished words
and word-pair counts are stored in a bounded private-app dictionary, skipped in password, email,
URL and app-marked incognito fields, and erasable from setup. Suggestions are unavailable in
translation drafts; the translation backend receives only explicit requests. Closing the panel
or hiding the keyboard discards both tab states. Only one translation request runs at a time;
starting one in the other tab cancels the previous request. A Write request may still insert while
Read is open if its original editor/cursor target remains valid.

### Current usability requirements

Keep the header and draft composer compact, preserve the key grid and draft cursor during
panel updates, and allow continued chat typing during reading requests. Setup uses a dim slate
palette with collapsible optional floating tools. Screenshot capture remains enabled. Validate
touch accuracy, multi-finger typing, accessibility and actual responsiveness on physical phones
before claiming parity with another keyboard.
