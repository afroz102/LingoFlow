<p align="center">
  <img src="docs/images/lingoboard-logo.svg" alt="LingoBoard logo" width="80">
</p>

<h1 align="center">LingoBoard</h1>

<p align="center">Translate what you write and read, right above your Android keyboard.</p>

LingoBoard supports **45 languages and 67 language/script options**, including all 22 scheduled
Indian languages in native script and Roman characters such as Hinglish. Source detection is
automatic by default; the target defaults to English.

[Phone setup](docs/BACKEND_SETUP.md#test-on-a-real-android-phone) ·
[Build](#build) · [Technical docs](docs/TECHNICAL_PLAN.md)

## Features

| Mode | How it works |
|---|---|
| **Write** | Open Translate → type a separate draft → translate and insert into the chat. You decide when to send. |
| **Read** | Copy a message → open the chat input → Read → confirm translation. The result appears above the keys without changing your reply. |

Write and Read keep separate drafts while you switch tabs. Keyboard translation requires no
overlay permission. English/Hinglish word completions, typo suggestions and next-word predictions
work offline. The keyboard includes a number row, three symbol pages, 50 emoji and light/dark themes.

<p align="center">
  <img src="docs/images/keyboard-writing.png" alt="Write: translate and insert a draft" width="32%">
  <img src="docs/images/keyboard-reading.png" alt="Read: translation above the keys" width="32%">
  <img src="docs/images/keyboard-suggestions.png" alt="Offline word suggestions" width="32%">
</p>

Screenshots use synthetic text on an emulator.

## Try it

1. Build and install `LingoBoard-1.1.0.apk` using the steps below.
2. Open **LingoBoard**, tap **Enable LingoBoard**, then **Choose LingoBoard**.
3. Open a chat input and tap Translate. Try `main kal nahi aa sakta`, then tap the round
   **Translate & insert** arrow. Accept the disclosure on first use; English enters the chat without being sent.

For received messages, copy the text and use Read. If suggestions hide Read, open Translate
first, then switch tabs. See the [phone guide](docs/BACKEND_SETUP.md#test-on-a-real-android-phone)
for selection actions and optional floating translations.

## Build

Requires **JDK 17** and **Android SDK 34**.

```sh
cp backend.properties.example backend.properties
```

Set `BACKEND_URL` in `backend.properties` to your public HTTPS backend origin, then run:

```sh
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/lingoboard/LingoBoard-1.1.0.apk`.

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

[Backend setup](docs/BACKEND_SETUP.md) covers hosted deployment and local development with Node.js 24+.
Gemini keys belong on the server, never in the APK.

## Translation and privacy

Every translation uses Gemini through a Cloudflare Worker; internet is required. Only explicitly
requested text and language choices are sent. No sign-in or stored translation history.
Typing and suggestions stay local; **Clear learned words** in setup erases the personalization dictionary.

The shared test backend allows **10 translations/minute and 200/UTC day** across all callers.
Use non-sensitive samples with the unpaid Gemini service.

## Status

This is a testing build. Android 6/API 23 is the provisional minimum; physical-phone and target-app
compatibility still need validation. Typing uses Latin QWERTY, with no automatic correction on Space,
swipe or voice input. Multilingual translation quality still needs fluent-speaker review.

[Product requirements](docs/PRODUCT_REQUIREMENTS.md) ·
[Architecture](docs/TECHNICAL_PLAN.md) ·
[Test results and remaining checks](docs/VALIDATION_PLAN.md)
