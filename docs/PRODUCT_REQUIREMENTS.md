# Product requirements — LingoTranslate V1

Updated 2026-10-04 for `0.6.0-reading-writing`. This replaces the earlier preview/Replace and
Devanagari scope. Historical evidence remains dated in `benchmark/` and Git history.

## Language and purpose

English and natural Roman Hindi/Hinglish only. No Devanagari input, output or conversion modes.
Hinglish includes informal Roman Hindi mixed with English. Translate meaning, intent, tone,
negation and idioms; preserve names, numbers, game terms, URLs, emojis and message formatting.
Use context inside the supplied passage, without inventing missing chat history.

## User flows

| Use case | Behavior |
|---|---|
| Writing | Type → select draft → Lingo-Translate → translate and automatically replace selection |
| Selectable reading | Select received text → Lingo-Translate → English result floats over host |
| Copy-only reading | Copy message → local prompt → confirm → English result floats over host |
| Other keyboard fallback | Copy message → tap Lingo bubble → English result floats over host |

The selected action itself confirms a selection request. An automatic copy prompt must never
send the message before confirmation. Reading never changes a host selection or clipboard
unless the user presses Copy. Cancel/error never changes a draft. No global paste commands.

## Platform boundary

Writing uses `ACTION_PROCESS_TEXT` with an editable flag and returns a replacement to the host.
Read-only selections use the same action and a separate overlay result. Host support is required;
the app cannot insert a menu item into a custom game that does not expose selection actions.

On modern Android, an ordinary unfocused app cannot read/listen to all clipboard changes.
The implemented automatic route requires the optional Lingo keyboard to be the selected IME
and a user-started reading session. A minimal Roman keyboard makes this route usable without
reading or transmitting keystrokes. The keyboard is optional; other keyboards use the manual
bubble route. Overlay permission alone does not enable automatic monitoring. No Accessibility
Service, background polling or automatic focus-stealing is used.

Minimum API 23 remains provisional; compile/target API 34. Android/OEMs and host apps may restrict
overlays. Universal support has not been established. Game of Khans, Discord, WhatsApp,
Telegram and Instagram messaging are targets, awaiting real-device testing.

## Implemented requirements

- Launcher setup, clear keyboard/overlay instructions and Start/Stop reading controls.
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
The minimal keyboard has no prediction, autocorrect, swipe, voice or emoji picker. Keyboard
polish is separate from verifying automatic clipboard access.
