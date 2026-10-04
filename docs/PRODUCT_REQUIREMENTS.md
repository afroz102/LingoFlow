# Product requirements — LingoTranslate V1

Updated 2026-10-04 for `0.7.0-keyboard-translate`. This replaces the earlier preview/Replace and
Devanagari scope. Historical evidence remains dated in `benchmark/` and Git history.

## Language and purpose

English and natural Roman Hindi/Hinglish only. No Devanagari input, output or conversion modes.
Hinglish includes informal Roman Hindi mixed with English. Translate meaning, intent, tone,
negation and idioms; preserve names, numbers, game terms, URLs, emojis and message formatting.
Use context inside the supplied passage, without inventing missing chat history.

## User flows

| Use case | Behavior |
|---|---|
| Keyboard writing | Translate icon → separate local draft → choose direction → Translate & insert into chat |
| Keyboard reading | Copy received message → open composer → Read → confirm → English card above keys |
| Selected editor reading | Select text in active editor → Translate selection → English card above keys |
| Read-only selection action | Select message → Lingo-Translate → open composer within 60 seconds → keyboard English card |
| Selection writing | Type → select draft → Lingo-Translate → translate and automatically replace selection |
| Optional floating reading | Start reading session → copy → prompt → confirm → English floats over host |
| Other keyboard floating fallback | Copy message → tap Lingo bubble → English floats over host |

Keyboard writing never sends a chat message automatically. It uses explicit directions, with
Roman Hindi → English as the default, and swap for English → Roman Hindi. Source typing stays
inside its local, cursor-editable draft, not the host chat. Translation errors retain that source.
The toolbar has a translation icon, reading action and keyboard switcher. Local typing offers
one-shot Shift, double-tap/hold Caps Lock, two symbol pages, long-press top-row numbers,
selection-aware/hold-repeat backspace and a scrolling picker with 50 smiley emoji. Key views
stay mounted during normal typing. No promise of Gboard feature or performance parity.

The selected action itself confirms a selection request. An automatic copy prompt must never
send the message before confirmation. Reading never changes a host selection or clipboard
unless the user presses Copy. Cancel/error never changes a draft. No global paste commands.

## Platform boundary

Writing uses `ACTION_PROCESS_TEXT` with an editable flag and returns a replacement to the host.
Read-only selections hand off to the selected Lingo keyboard by default; an explicitly running
floating session remains a separate route. Host support is required; the app cannot insert a
selection action into custom message renderers.

Keyboard translation needs no overlay permission, foreground session or Accessibility Service.
Android only shares selected text from the active input connection. A received-message selection
outside an editable field cannot generally be observed by a keyboard; copy it and open the chat
composer. Apps must expose a working editor/IME for this route. Input hiding, editor changes and
process death discard drafts/results and cancel requests. Changed host cursor/selection prevents
automatic insertion of a late response; the result offers explicit Insert here or Copy.

Optional automatic floating copy prompts require the selected Lingo keyboard plus a user-started
reading session. Overlay permission alone does not enable clipboard access. Other keyboards use
the manual bubble route. No background clipboard polling or Accessibility Service is used.

Minimum API 23 remains provisional; compile/target API 34. Android/OEMs and host apps may restrict
overlays. Universal support has not been established. Game of Khans, Discord, WhatsApp,
Telegram and Instagram messaging are targets, awaiting real-device testing.

## Implemented requirements

- Launcher setup prioritizing keyboard translation; floating mode remains optional.
- Write/read translation panels, local draft, direction swap and inline English result.
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
- Local/server input bounds, unsupported-script rejection, validated output and shared quota.

## Quality and completion boundary

Writing AUTO maps English → Roman Hindi and Roman Hindi/Hinglish → English. Short ambiguous
words default to English → Roman Hindi; no detector can establish absent context. Reading always
targets English; already-English messages are returned unchanged by the requested model contract.

The backend has no local/system translation fallback. Human evaluation of real Hinglish, game
slang, negation and ambiguity remains required. No claim of superiority to another translator
or guaranteed model behavior follows from a smoke test.

Full target-app/OEM compatibility, physical performance/battery, process-death and permission
revocation stress testing, TalkBack/font scaling, and public distribution review remain open.
The keyboard has no prediction, autocorrect, swipe or voice input. Physical typing latency,
font scaling, numeric layouts and target-app behavior still need broader device testing.
