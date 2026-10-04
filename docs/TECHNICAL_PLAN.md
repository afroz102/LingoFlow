# Architecture — reading and writing V1

Updated 2026-10-05. Current Kotlin app uses the existing hosted JavaScript/SQLite/Gemini backend.

## Selection entry

`ProcessTextActivity` validates action, `text/plain`, CharSequence input and a 4,000 UTF-16-unit
cap; flattens spans and defaults uncertain editable flags to read-only. `ResultActivity` handles
first-use disclosure and loading/errors.

- Editable: `ResultViewModel`/`TranslateCoordinator` use the saved source/target languages (Auto → English by default). Success returns
  `RESULT_OK` and only `EXTRA_PROCESS_TEXT`; host replaces its selected range. Cancel/error
  returns no text. Activity ViewModel prevents duplicate calls on ordinary rotation.
- Read-only: hand off to the selected LingoBoard, or use an explicitly running floating
  session/other-keyboard overlay route. Finish with cancellation/no replacement. Selecting LingoBoard Translate
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
`FLAG_NOT_FOCUSABLE`. It does not move focus from the game/chat. A draggable
header/bubble, bounded scrolling and viewport clamping support portrait/landscape.

Automatic copy route:

1. Android notifies the session's clipboard listener when the UID has access.
2. Verify that LingoBoard is the current default IME, phone is unlocked/interactive,
   password input is inactive, and the clip is a single text item, supported/bounded and not
   marked sensitive or labeled as Lingo's own result. Never coerce URI clips.
3. Suppress consecutive duplicate texts with a memory-only SHA-256 fingerprint.
4. Replace the current prompt/request locally; show Translate confirmation. No cloud call yet.
5. Confirmation uses MULTILINGUAL with Auto → English, then shows validated English output. Unconfirmed prompts
   expire after 60 seconds. Close clears content; Stop removes windows/listeners/jobs/content.

With another default keyboard there is no background-read claim. Tapping the bubble temporarily
creates a focusable overlay, waits for actual window focus, reads the eligible clipboard and
restores a non-focusable card. The tap is explicit translation confirmation. No periodic polling
or unsolicited focus acquisition. This focused route can briefly pause a game; test target apps.

One coroutine job/generation guard prevents canceled/older requests from overwriting newer cards.
Minimize retains the current result/request in memory; Close discards it. New copy events cancel
an existing request; an already-sent request can still consume server quota.

## Translation keyboard

`LingoKeyboardService` is permission-protected by `BIND_INPUT_METHOD`. The user enables/selects
it through system UI. `LingoKeyboardView` holds a stable toolbar, canvas key grid and local
cursor-editable translation draft. `KeyboardLayout.rows()` supplies deterministic `KeySpec`
data; `KeyGridView` draws the keys and exposes virtual accessible buttons. Ordinary typing
never rebuilds the grid or requests translations. Draft edits use Editable.replace/delete in
place, with code-point-aware backspace and a 4,000-unit cap; they never recreate the Editable
on each key. Full touch cells include the visual gutters. The base row height is 56dp in
portrait and 40dp in landscape, with a weighted number row; every page has the same total
height. `KeyboardPalette` follows system light/dark mode. Toolbar actions are icons.

- Write: explicit source/target IDs, Auto → English by default, local draft, bounded request on button
  press. `commitText` inserts/replaces the host selection on success, never performs Send. A
  session/selection revision guard prevents automatic insertion after editor/cursor changes;
  otherwise the result offers Copy/explicit Insert here.
- Read: load eligible clipboard text only after Read/Paste copy is tapped, then show the source
  for confirmation. Active editor selection can be translated directly via the toolbar.
  MULTILINGUAL output remains inside the keyboard; no host replacement. While the request is
  pending and once its result appears, typing/backspace/editor actions target the chat while the
  result stays above the keys.
- Read-only Process Text: when this IME is selected and no floating session is running,
  `KeyboardReadingInbox` holds one validated source in process memory for at most 60 seconds.
  The next non-password composer consumes it. The selection action confirmed the request;
  opening the keyboard starts it. This does not force a keyboard onto non-editable message UI.

The IME never requests surrounding chat history. Password variations disable cloud controls,
reading handoffs and clipboard actions; no translation on keystrokes. Sensitive/own-output
clipboard filters are shared with the overlay. Hide/finish/editor changes cancel and clear the
panel; generation guards reject old responses. No drafts/results in saved state/disk/autofill;
Screenshots and Recents previews are allowed at the user’s request. A process kill loses the draft. Landscape uses compact keys/panels,
without fullscreen extraction. Word predictions/autocorrect/swipe/voice remain outside this build.

## Translation contract

Current UI requests send `{text, direction: "MULTILINGUAL", sourceLanguage, targetLanguage}`
over HTTPS to `/v1/translate`. Source is `auto` or a catalog ID; target must be a catalog ID.
Defaults are `auto` and `en`. Source/target selections are saved in local preferences, never
source text. The response echoes both requested IDs and direction, which Android validates.
`shared/translation-languages.json` contains 45 languages/67 native/Roman choices; a backend
test checks that Android’s language IDs match it. Native scripts are accepted for multilingual
requests. Roman targets reject non-Latin-script letters on both the server and client.

The backend retains old AUTO/HINGLISH_TO_ENGLISH/ENGLISH_TO_HINGLISH/READ_TO_ENGLISH routes
for earlier APKs. Current UI uses MULTILINGUAL. Legacy Roman routes retain their script bounds.

Gemini detects and translates in one call, using allowlisted language descriptions rather than
arbitrary user-supplied instructions. The fixed prompt treats text as literal data, preserves
meaning, tone, negation, names and passage context, and forbids invented surrounding conversation.
Temperature 0.2 and JSON schema remain; no tools, chat memory or separate detection call.

Input ≤4,000 UTF-16 units/32 KiB request body; output ≤16,000 chars/128 KiB upstream body.
Backend deadline 18 seconds, Android 30 seconds. Explicit Retry only. No automatic retries or cache.

## Backend and data

Cloudflare Workers Free, D1 SQLite and `gemini-3.5-flash-lite`; portable Node 24+ server/SQLite
shares business logic. Key only in server secrets/ignored local environment. No authentication,
user/device identity or phone-side Gemini credentials. Health probes do not spend model quota.

Atomic quota: 10 requests/minute and 200/UTC day across all callers. Failure after reservation
still consumes allowance. Database failure blocks Gemini. D1 stores only day/minute buckets and
aggregate counts; old buckets pruned after seven days. No text, translations or identity columns.

Text/results are memory-only, except explicit output Copy to Android's clipboard. The app allows screenshots; no window uses FLAG_SECURE. No content logging/analytics; Worker observability disabled. Cloudflare/Google still
process requests; unpaid Gemini terms and the app disclosure apply. A service/IME killed by Android
loses its state. Universal clipboard/overlay reliability and public release are not assumed.

## Official platform references

- [Process Text](https://developer.android.com/reference/android/content/Intent#ACTION_PROCESS_TEXT)
- [Clipboard focus/default-IME restriction](https://developer.android.com/reference/android/content/ClipboardManager#getPrimaryClip())
- [Input method implementation](https://developer.android.com/develop/ui/views/touch-and-input/creating-input-method)
- [Overlay windows](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#TYPE_APPLICATION_OVERLAY)
- [Foreground service types](https://developer.android.com/about/versions/14/changes/fgs-types-required)

### Compact translation controls

Write/Read icons, source/swap/target controls and close/switch action occupy one header
(46dp portrait, 40dp landscape).
Language controls are invisible until a panel opens; no logo occupies the keyboard header.
Paste sits inside the draft field, with an icon-only round translation arrow alongside it.
Read is selected while its panel is active; its translation action never opens a write/insert
flow. Pressing Read again closes it. Reading results offer Copy/New only. Setup retains the
logo, a dim slate theme and collapsible optional floating-session tools.

The key grid, local draft Editable and cursor persist across language/status updates. Language
changes replace only the two header chip nodes because IME accessibility can retain the old
target label; their descriptions include the full selected language. Same-editor IME restarts
preserve the panel while invalidating write insertion targets. Backspace uses the reported host
selection instead of querying the remote editor on every press. During a reading request,
key/delete/enter actions continue targeting the chat; the submitted reading source stays
unchanged. Write drafts remain disabled during their request.

### Drawn key grid

`KeyGridView` draws all keys on one canvas from the `KeyboardLayout.rows()` model, replacing
roughly 40 Button views and their ripple animation. Touch handling tracks each
pointer: characters/Space/Enter/page keys fire on release and follow a sliding finger; Delete and
Shift fire on touch-down; a new finger commits earlier unfired characters (rollover). Delete
repeats after 400ms at 50ms intervals; Shift and Space use the system long-press timeout (Caps
Lock, keyboard picker). The key preview is a drawable in the keyboard root's `ViewOverlay`, so it
can rise over the toolbar without a popup window. `ExploreByTouchHelper` exposes each key as a
virtual Button with the same text/descriptions as before for TalkBack and UiAutomator.
Every page has a fixed total height: the bottom row keeps one pitch and other rows share the rest
by weight (the number row is 0.82×). Colours come from `KeyboardPalette`. Pages hold 111 symbols
over three four-row pages and 50 smiley in a 10×5 grid; letters have no long-press alternates.
