# Architecture — reading and writing V1

Updated 2026-10-04. Current Kotlin app uses the existing hosted JavaScript/SQLite/Gemini backend.

## Selection entry

`ProcessTextActivity` validates action, `text/plain`, CharSequence input and a 4,000 UTF-16-unit
cap; flattens spans and defaults uncertain editable flags to read-only. `ResultActivity` handles
first-use disclosure and loading/errors.

- Editable: `ResultViewModel`/`TranslateCoordinator` use AUTO writing. Success returns
  `RESULT_OK` and only `EXTRA_PROCESS_TEXT`; host replaces its selected range. Cancel/error
  returns no text. Activity ViewModel prevents duplicate calls on ordinary rotation.
- Read-only: acquire overlay permission from visible UI if needed, start `ReadingOverlayService`
  with the selected text, then finish with cancellation/no replacement. Selecting Lingo-Translate
  already authorized the translation. No read-only ViewModel network request is started.

No `noHistory` trampoline flag: it must remain alive for the result callback, including a trip
to overlay settings. Content is never written into saved state or translation history.

## Reading session and clipboard

`MainActivity` explicitly starts the foreground overlay service after disclosure/permission.
The service declares the Android 14 `specialUse` type and subtype, displays a notification with
Stop and returns `START_NOT_STICKY`. It neither starts on boot nor restarts after process death.
Android 13+ notification permission is requested once at session start; denial still permits
the session, with Stop available in the overlay/launcher. The system hides its notification
from the drawer when permission is denied. Public store approval of the service/IME combination has not been evaluated.

The normal overlay is `TYPE_APPLICATION_OVERLAY` on API 26+ (legacy `TYPE_PHONE` below),
`FLAG_NOT_FOCUSABLE` and `FLAG_SECURE`. It does not move focus from the game/chat. A draggable
header/bubble, bounded scrolling and viewport clamping support portrait/landscape.

Automatic copy route:

1. Android notifies the session's clipboard listener when the UID has access.
2. Verify that Lingo keyboard is the current default IME, phone is unlocked/interactive,
   password input is inactive, and the clip is a single text item, supported/bounded and not
   marked sensitive or labeled as Lingo's own result. Never coerce URI clips.
3. Suppress consecutive duplicate texts with a memory-only SHA-256 fingerprint.
4. Replace the current prompt/request locally; show Translate confirmation. No cloud call yet.
5. Confirmation uses READ_TO_ENGLISH, then shows validated English output. Unconfirmed prompts
   expire after 60 seconds. Close clears content; Stop removes windows/listeners/jobs/content.

With another default keyboard there is no background-read claim. Tapping the bubble temporarily
creates a focusable overlay, waits for actual window focus, reads the eligible clipboard and
restores a non-focusable card. The tap is explicit translation confirmation. No periodic polling
or unsolicited focus acquisition. This focused route can briefly pause a game; test target apps.

One coroutine job/generation guard prevents canceled/older requests from overwriting newer cards.
Minimize retains the current result/request in memory; Close discards it. New copy events cancel
an existing request; an already-sent request can still consume server quota.

## Optional keyboard

`LingoKeyboardService` is permission-protected by `BIND_INPUT_METHOD`. The user must enable and
select it through system UI. It provides local Roman QWERTY, case toggle, digits/punctuation,
space, selection-aware backspace, Enter/editor action and a keyboard picker. No network provider
is called by keyboard events; no surrounding message history or keystrokes are collected.
Password editor variations disable clipboard prompts. No accessibility permission is requested.

## Translation contract

Only `{text, direction}` is sent over HTTPS to `/v1/translate`:

| Direction | Behavior |
|---|---|
| AUTO | Writing detection and translation in one call; resolves to one of the next two |
| ENGLISH_TO_HINGLISH | Natural Hindi in Roman letters |
| HINGLISH_TO_ENGLISH | Informal Roman Hindi/mixed Hindi-English → natural English |
| READ_TO_ENGLISH | Received text → English; already English unchanged |

Old Devanagari directions are rejected. Both input and output reject Devanagari blocks, including
extended characters. Gemini's fixed system prompt treats input as literal data, preserves semantic
meaning and passage context, and forbids invented surrounding conversations or instructions in
selected text. Temperature 0.2 and JSON schema remain; no tools, chat memory or separate detection call.

Input ≤4,000 UTF-16 units/32 KiB request body; output ≤16,000 chars/128 KiB upstream body.
Backend deadline 18 seconds, Android 30 seconds. Explicit Retry only. No automatic retries or cache.

## Backend and data

Cloudflare Workers Free, D1 SQLite and `gemini-3.5-flash-lite`; portable Node 24+ server/SQLite
shares business logic. Key only in server secrets/ignored local environment. No authentication,
user/device identity or phone-side Gemini credentials. Health probes do not spend model quota.

Atomic quota: 10 requests/minute and 200/UTC day across all callers. Failure after reservation
still consumes allowance. Database failure blocks Gemini. D1 stores only day/minute buckets and
aggregate counts; old buckets pruned after seven days. No text, translations or identity columns.

Text/results are memory-only, except explicit output Copy to Android's clipboard. App windows use
FLAG_SECURE. No content logging/analytics; Worker observability disabled. Cloudflare/Google still
process requests; unpaid Gemini terms and the app disclosure apply. A service/IME killed by Android
loses its state. Universal clipboard/overlay reliability and public release are not assumed.

## Official platform references

- [Process Text](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT)
- [Clipboard focus/default-IME restriction](https://developer.android.com/reference/android/content/ClipboardManager#getPrimaryClip())
- [Input method implementation](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [Overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [Foreground service types](https://developer.android.com/about/versions/14/changes/fgs-types-required)
