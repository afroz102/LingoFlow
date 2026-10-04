# LingoFlow / LingoTranslate

Android translation for **English ↔ Roman Hindi/Hinglish**, with meaning and conversational
context preserved by Gemini. Devanagari is outside this V1 scope.

- **Write:** select a draft → **Lingo-Translate** → replace the selected text automatically.
- **Read selected text:** select a received message → **Lingo-Translate** → floating English result.
- **Read copied text:** during a reading session, copy a message → confirmation prompt → floating
  English result. On modern Android, automatic copy prompts require the optional **Lingo keyboard**
  to be the selected keyboard. With another keyboard, copy → tap the Lingo bubble translates it.

The movable reading card has Copy, Minimize, Close and Stop controls. Reading sessions are
explicitly started, show an ongoing notification and never restart on boot. Writing does not
require an overlay, a session or switching keyboards. Host apps must expose Android Process Text.

The public testing backend runs on Cloudflare Workers Free with D1/SQLite and server-side Gemini.
No account or authentication is needed. Every translation uses Gemini; there is no local
translation engine or cached translation history. Only the requested text and direction leave
the phone. Gemini's unpaid service is for non-sensitive test samples. Shared allowance:
**10 translations/minute, 200/UTC day**, across all callers.

## Test on your phone

Install `app/build/outputs/apk/debug/app-debug.apk`, then open **LingoTranslate**.
Allow floating translations. For automatic copy prompts, enable and select Lingo keyboard.
Tap **Start reading session**, acknowledge the disclosure and return to your chat/game.
The optional keyboard is a basic Roman QWERTY keyboard without suggestions or swipe typing.
Use **Stop reading session** to end monitoring and remove the overlay.

For writing, select English or Hinglish in an editable field, choose **Lingo-Translate** from
its selection menu and check that only your selection changes. Cancel/failure leaves the draft.
The [phone guide](docs/BACKEND_SETUP.md#test-on-a-real-android-phone) includes both reading routes.

## Build and validate

JDK 17, Android SDK 34, provisional minimum Android 6/API 23; Node 24+ for the portable backend.
Copy `backend.properties.example` to ignored `backend.properties` and set the public HTTPS origin.
Never put a Gemini key in Android configuration.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :testhost:assembleWithQueriesDebug
npm --prefix backend test
```

The backend is deployed at `https://lingoflow-backend.lingoflow-backend.workers.dev`.
The APK version is **0.6.0-reading-writing**, version code 3. No phone-side server/key setup.

## Support and evidence

Game of Khans, Discord, WhatsApp, Telegram and Instagram messages are target apps. Their actual
selection, copy, overlay and keyboard behavior still needs physical-phone testing. Support for
every Android phone/version is not established. Apps can omit selection actions or block overlays;
Android/OEM restrictions also apply. Current checks passed: 19 backend and 22 Android JVM tests, app/host lint, APK builds,
and controlled Android 11/14 reading/writing tests. See the [validation plan](docs/VALIDATION_PLAN.md).

## Repository

| Directory | Purpose |
|---|---|
| `app/` | Kotlin selection flows, setup, floating reading session, optional keyboard and HTTP provider |
| `backend/` | Worker/Node API, SQLite aggregate quotas, tests and live smoke tool |
| `testhost/` | Development-only host and cross-UID reading/writing fixtures |
| `benchmark/` | Dated evidence and historical corpus/timing tools |
| `docs/` | [Product](docs/PRODUCT_REQUIREMENTS.md), [architecture](docs/TECHNICAL_PLAN.md), [validation](docs/VALIDATION_PLAN.md), [setup](docs/BACKEND_SETUP.md) |
