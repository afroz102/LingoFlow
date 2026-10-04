# LingoBoard

Android translation keyboard with **45 languages and 67 language/script choices**, including
22 Indian languages in native script or Roman characters. Source defaults to **Detect language**;
target defaults to **English**. Gemini translates the meaning and context of the supplied passage.

- **Keyboard writing:** tap the translation icon → choose source/target languages →
  type in the separate draft → **Translate & insert**. The result enters the chat; it is never sent automatically.
- **Keyboard reading:** copy a received message → open the chat input → message icon →
  **Translate**. The result card appears above the keys, leaving your chat draft untouched.
- **Selected editor text:** selecting text in the active typing field changes the translation icon’s
  accessible label to **Translate selection**. Tap it to read the selection above the keyboard.

The keyboard follows system light/dark mode, with a compact icon toolbar, larger 56dp portrait
key targets, and in-place draft editing. The logo, mode icons and language selectors share one header;
Read stays highlighted and offers translation without insertion. Comma sits left of emoji.
Letter keys show long-press digit/symbol shortcuts. Screenshots are allowed. The keyboard has rounded keys, one-shot Shift, double-tap/hold Caps Lock, three symbol pages (81 symbols),
50 smiley emoji, hold-to-delete and a keyboard switcher. Translation in the keyboard needs
**no overlay permission or reading session**. Typing is local; there is no word prediction,
autocorrect, swipe typing or voice input yet. Key releases commit immediately, the key grid stays
mounted across panel updates, and reading requests leave chat typing available. Physical-device
smoothness still needs testing. Setup uses a dim slate theme and collapses optional floating tools.

The existing **LingoBoard Translate** selection action still replaces editable selections. For
read-only selections, with LingoBoard selected, it hands off to the next opened chat input
for 60 seconds. A keyboard cannot inspect arbitrary message selections outside its active editor;
copying and opening the composer is the reliable fallback.

[Keyboard layout](docs/images/keyboard-typing.png) · [Writing panel](docs/images/keyboard-writing.png) ·
[Reading card](docs/images/keyboard-reading.png) · [Landscape](docs/images/keyboard-landscape.png) ·
[Dark keyboard](docs/images/keyboard-typing-dark.png) · [Dark reading](docs/images/keyboard-reading-dark.png).
These show the actual view rendered on an emulator with synthetic samples.
[Dim setup screen](docs/images/lingoboard-setup.png) · [LingoBoard logo](docs/images/lingoboard-logo.svg).

An optional floating reading session remains for apps that allow overlays, with Copy, Minimize,
Close and Stop. It is explicitly started and never restarts on boot.

The public testing backend runs on Cloudflare Workers Free with D1/SQLite and server-side Gemini.
No account or authentication is needed. Every translation uses Gemini; there is no local
translation engine or cached translation history. Only the requested text and language options leave
the phone. Gemini's unpaid service is for non-sensitive test samples. Shared allowance:
**10 translations/minute, 200/UTC day**, across all callers.

## Test on your phone

Install `app/build/outputs/apk/lingoboard/LingoBoard-1.0.2.apk`, then open **LingoBoard**.
Tap **1. Enable LingoBoard**, enable it in system settings, then tap **2. Choose LingoBoard**.
Open a chat input in your game/messaging app. Use the **translation icon** to write or the **message icon** to load a
copied message, and acknowledge the disclosure before your first translation.

Try `main kal nahi aa sakta` in the translation draft. Tap **Translate & insert** and check
that English appears in the chat without sending it. Choose **Hindi (Roman)** as the target to test English → Roman Hindi.
For reading, copy a Hinglish message, open the composer and tap the message icon, then **Translate**.
The [phone guide](docs/BACKEND_SETUP.md#test-on-a-real-android-phone) includes all routes.

## Languages and versions

Indian languages: Assamese, Bengali, Bodo, Dogri, Gujarati, Hindi, Kannada, Kashmiri, Konkani,
Maithili, Malayalam, Manipuri, Marathi, Nepali, Odia, Punjabi, Sanskrit, Santali, Sindhi, Tamil,
Telugu and Urdu. Each has native-script and Roman translation options.

Other languages: English, Korean, Indonesian, Simplified Chinese, Japanese, Russian, French,
Spanish, German, Arabic, Portuguese, Italian, Turkish, Vietnamese, Thai, Dutch, Polish,
Ukrainian, Malay, Filipino, Persian, Hebrew and Swedish. The typing layout is Latin QWERTY;
these are translation options, not separate native-script keyboard layouts.

The language selectors remember your choices. Auto-detection is part of the same Gemini request,
not an extra network call. Ambiguous short text can require explicitly choosing its source.
Translation quality across every language still needs fluent-speaker review.

APK naming is `LingoBoard-<version>.apk`: patches `1.0.1` → `1.0.2`, feature upgrades `1.1.0`,
and complete upgrades `2.0.0`. Increment Android’s `versionCode` for every update. The application
ID stays unchanged so the APK updates the existing installation.

## Build and validate

JDK 17, Android SDK 34, provisional minimum Android 6/API 23; Node 24+ for the portable backend.
Copy `backend.properties.example` to ignored `backend.properties` and set the public HTTPS origin.
Never put a Gemini key in Android configuration.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :testhost:assembleWithQueriesDebug
npm --prefix backend test
```

The backend is deployed at `https://lingoflow-backend.lingoflow-backend.workers.dev`.
The APK version is **1.0.2**, version code 6. No phone-side server/key setup.

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
| `shared/` | Translation language catalog, checked against Android IDs by backend tests |
| `testhost/` | Development-only host and cross-UID reading/writing fixtures |
| `benchmark/` | Dated evidence and historical corpus/timing tools |
| `docs/` | [Product](docs/PRODUCT_REQUIREMENTS.md), [architecture](docs/TECHNICAL_PLAN.md), [validation](docs/VALIDATION_PLAN.md), [setup](docs/BACKEND_SETUP.md) |
