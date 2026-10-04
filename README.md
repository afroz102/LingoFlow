# LingoFlow / LingoTranslate

Android translation for **English ↔ Roman Hindi/Hinglish**, with meaning and conversational
context preserved by Gemini. Devanagari is outside this V1 scope.

- **Keyboard writing:** tap the translation icon → choose Roman Hindi → English or reverse →
  type in the separate draft → **Translate & insert**. The result enters the chat; it is never sent automatically.
- **Keyboard reading:** copy a received message → open the chat input → **Read** →
  **Translate to English**. The English card appears above the keys, leaving your chat draft untouched.
- **Selected editor text:** selecting text in the active typing field changes the toolbar to
  **Translate selection**. Tap it to read the selection in English above the keyboard.

The keyboard has rounded keys, one-shot Shift, double-tap/hold Caps Lock, two symbol pages,
50 smiley emoji, hold-to-delete and a keyboard switcher. Translation in the keyboard needs
**no overlay permission or reading session**. Typing is local; there is no word prediction,
autocorrect, swipe typing or voice input yet.

The existing **Lingo-Translate** selection action still replaces editable selections. For
read-only selections, with Lingo keyboard selected, it hands off to the next opened chat input
for 60 seconds. A keyboard cannot inspect arbitrary message selections outside its active editor;
copying and opening the composer is the reliable fallback.

[Keyboard layout](docs/images/keyboard-typing.png) · [Writing panel](docs/images/keyboard-writing.png) ·
[Reading card](docs/images/keyboard-reading.png) · [Landscape](docs/images/keyboard-landscape.png).
These show the actual view rendered on an emulator with synthetic samples.

An optional floating reading session remains for apps that allow overlays, with Copy, Minimize,
Close and Stop. It is explicitly started and never restarts on boot.

The public testing backend runs on Cloudflare Workers Free with D1/SQLite and server-side Gemini.
No account or authentication is needed. Every translation uses Gemini; there is no local
translation engine or cached translation history. Only the requested text and direction leave
the phone. Gemini's unpaid service is for non-sensitive test samples. Shared allowance:
**10 translations/minute, 200/UTC day**, across all callers.

## Test on your phone

Install `app/build/outputs/apk/debug/app-debug.apk`, then open **LingoTranslate**.
Tap **1. Enable Lingo keyboard**, enable it in system settings, then tap **2. Choose Lingo keyboard**.
Open a chat input in your game/messaging app. Use **Translate** to write or **Read** to load a
copied message, and acknowledge the disclosure before your first translation.

Try `main kal nahi aa sakta` in the translation draft. Tap **Translate & insert** and check
that English appears in the chat without sending it. Swap the direction to test English → Roman Hindi.
For reading, copy a Hinglish message, open the composer and tap **Read**, then **Translate to English**.
The [phone guide](docs/BACKEND_SETUP.md#test-on-a-real-android-phone) includes all routes.

## Build and validate

JDK 17, Android SDK 34, provisional minimum Android 6/API 23; Node 24+ for the portable backend.
Copy `backend.properties.example` to ignored `backend.properties` and set the public HTTPS origin.
Never put a Gemini key in Android configuration.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :testhost:assembleWithQueriesDebug
npm --prefix backend test
```

The backend is deployed at `https://lingoflow-backend.lingoflow-backend.workers.dev`.
The APK version is **0.7.0-keyboard-translate**, version code 4. No phone-side server/key setup.

## Support and evidence

Game of Khans, Discord, WhatsApp, Telegram and Instagram messages are target apps. Their actual
selection, copy, overlay and keyboard behavior still needs physical-phone testing. Support for
every Android phone/version is not established. Apps can omit selection actions or block overlays;
Android/OEM restrictions also apply. Controlled keyboard tests run against a different-UID host with overlay permission denied.
See the [validation plan](docs/VALIDATION_PLAN.md) for current keyboard results and earlier floating-flow evidence.

## Repository

| Directory | Purpose |
|---|---|
| `app/` | Kotlin selection flows, setup, translation keyboard, optional floating reading session and HTTP provider |
| `backend/` | Worker/Node API, SQLite aggregate quotas, tests and live smoke tool |
| `testhost/` | Development-only host and cross-UID reading/writing fixtures |
| `benchmark/` | Dated evidence and historical corpus/timing tools |
| `docs/` | [Product](docs/PRODUCT_REQUIREMENTS.md), [architecture](docs/TECHNICAL_PLAN.md), [validation](docs/VALIDATION_PLAN.md), [setup](docs/BACKEND_SETUP.md) |
