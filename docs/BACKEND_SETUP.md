# Custom backend setup and Android phone testing

Current architecture, replacing Firebase and Supabase for this test version:

```text
Android selection / confirmed clipboard text → LingoBoard Translate → HTTPS POST /v1/translate
→ our JavaScript Worker → atomic SQLite quota reservation → Gemini → result
```

No account, sign-in, device identifier, session, or authentication token is required.
The API is public; anyone who knows its URL can call it. Aggregate limits protect the
shared testing allowance: **10 requests per minute and 200 per UTC day**, across all callers.
Failed model requests still consume quota, and retries require an explicit user action.
A new minute/day resets the relevant allowance. Google may impose a lower model limit.

## Deployed test resources

- Backend: https://lingoflow-backend.lingoflow-backend.workers.dev
- Health check: https://lingoflow-backend.lingoflow-backend.workers.dev/healthz
- Worker: `lingoflow-backend`, account `a596a6673bf6947a19dfd4c38deaa93b`
- Database: `lingoflow-usage`, ID `2730147f-8af9-4c4f-8cee-076c1e894f99`, APAC/Singapore
- Model: `gemini-3.5-flash-lite`
- Public preview URLs disabled; Worker application logs and traces disabled.

## Hosting and portability

The free hosted target is Cloudflare Workers with a D1 SQLite database, in the Cloudflare
account signed in as s78.meghnad@gmail.com. Gemini uses that same Google account's existing
LingoFlow Testing project (`gen-lang-client-0112174397`) and unpaid API key. No paid plan or
billing is needed for the current testing limits.

The application code and SQL live in `backend/`. The managed hosting service is a dependency,
not a claim that every hosted component is open source. `local-server.mjs` uses Node 24+ and
SQLite to run the same translation logic outside Cloudflare. This makes later self-hosting
possible without rebuilding Android's provider contract.

## Data and credentials

- Android sends only `text`, `direction`, `sourceLanguage` and `targetLanguage` in the JSON request.
- Our database stores only UTC day/minute buckets and aggregate request counts. It prunes
  buckets older than seven days. It has no text, translation, IP, or identity columns.
- Selected text passes through the hosting provider to Google. Application request logging,
  Worker observability, and content caching are disabled; infrastructure providers may still
  process connection metadata under their own policies.
- Google unpaid API terms remain applicable. Use harmless sample text for these tests;
  changing the backend does not change Google's treatment of free-tier inputs/outputs.
- `GEMINI_API_KEY` stays in Worker Secrets. Local development uses ignored `backend/.dev.vars`
  with file permissions 600. Never put this key in Android, source control, or screenshots.
- `backend.properties` contains only the public HTTPS origin, which is embedded in the APK.

## Deploy or recreate

From `backend/`, using an authorized Cloudflare session:

```sh
npm ci
npx wrangler login --scopes account:read user:read workers_scripts:write d1:write
npx wrangler d1 create lingoflow-usage
# Set the returned database_id and account_id in wrangler.toml.
npx wrangler d1 execute lingoflow-usage --remote --file schema.sql
npx wrangler deploy
npx wrangler secret put GEMINI_API_KEY
```

The final command reads the key privately from standard input. `GEMINI_MODEL` is pinned to
`gemini-3.5-flash-lite` in `wrangler.toml`. Never enable Workers paid billing to bypass a free
limit automatically. `GET /healthz` verifies configuration and database access, not Gemini
quota or translation quality.

## Local development

```sh
cd backend
cp .dev.vars.example .dev.vars
# Fill the key locally, then chmod 600 .dev.vars.
npm start
npm test
```

This listens on `127.0.0.1:8787` and creates `backend/data/usage.sqlite`. The local database is
separate from D1. Production builds require HTTPS. Debug builds allow HTTP only to localhost,
127.0.0.1, or the emulator host alias 10.0.2.2; they do not bypass TLS certificate validation.

## Test on a real Android phone

The `1.0.2` APK includes the public backend URL. No account, Gemini key
entry or server configuration is needed on the phone. Internet is required for translation.

1. Transfer `app/build/outputs/apk/lingoboard/LingoBoard-1.0.2.apk` to your phone and install/update it.
   Allow installation from that file/browser app if Android asks.
2. Open **LingoBoard**, tap **1. Enable LingoBoard**, and enable **LingoBoard**
   in system settings. Return and tap **2. Choose LingoBoard** to select it.
3. Open your game/chat and tap its chat input. LingoBoard appears. Display-over-apps
   permission and Start reading session are unnecessary for this keyboard flow.
4. **Write:** tap the **Translate** icon in the top row. The selectors show **Auto → English**.
   Type `main kal nahi aa sakta` in the separate translation box. It should not appear in the chat yet.
   Tap the round **Translate & insert** arrow. On first use, read the disclosure and tap **Continue**.
   English enters the chat input. Review it and send using your chat app when ready.
5. Tap the target selector and choose **Hindi (Roman)** to test `How are you?`.
   Choose **Hindi** for Devanagari output or any other supported target. The source selector
   offers **Detect language** or a specific language; your choices are remembered. **⇄** swaps
   explicit languages; from Auto → English it offers English → Hindi (Roman).
   Translation failures keep the source; tap Translate & insert again for an explicit retry.
6. **Read a copied message:** copy a received message, open the chat input and tap the **message icon**.
   Read stays highlighted in the header and offers no insertion action. The copied source appears
   in the keyboard panel. Tap the round **Translate** arrow; the English
   result appears above the keys. Translation leaves your existing chat draft unchanged; you can then type a reply below the result. **Copy**, **New**
   and **×** are available; reading never inserts its result into the chat.
7. **Read selected editor text:** select text in the active typing field. The translation icon’s accessible label changes
   to **Translate selection**. Tap it to read English above the keys. The selection stays unchanged.
   A keyboard cannot generally detect selections in received-message UI outside that editor.
8. If the host offers **LingoBoard Translate** on received-message selection, choose it, then open
   the chat input within 60 seconds for the keyboard result. Use copy + Read if the action is absent.
9. **Keyboard controls:** tap Shift for one capital, double-tap or hold for Caps Lock; tap again
   for lowercase. **?123** opens numbers/signs; the page-number key cycles through three symbol pages.
   The number row gives direct digit access. **☺** opens 50 smiley emoji. Hold Backspace to delete
   repeatedly. Tap the **globe icon** or hold Space to switch keyboards. Translation-panel Enter creates a newline.
10. Try your actual target apps, part-selection, long input, offline failure and landscape.
    Closing/hiding the keyboard clears the translation draft/result and cancels pending work.
    If the chat cursor moves during a writing request, review the held result and use **Insert here** or **Copy**.

There are 45 translation languages, including all 22 scheduled Indian languages with separate
native-script and Roman choices, and 67 total language/script options. English is the default
target; you can choose another target for both writing and reading. The keyboard layout remains
Latin QWERTY. Language selectors follow the reading icon in the same header only while a
translation panel is open. Paste is inside the draft field; the round arrow alongside it submits
the request. Comma is left of emoji. There are three symbol pages with 111 distinct characters
and a dedicated number row; letters have no long-press alternates. All key pages keep the same
height. Reading requests allow continued chat typing. Tap the highlighted Read icon or × to leave reading mode.
Screenshots are enabled in LingoBoard. Context comes only
from the requested passage. Normal typing stays local; only explicit translation requests go
to Gemini. No word prediction, autocorrect, swipe or voice typing yet.

**Optional floating mode:** if your app permits overlays, tap **Optional: floating reading session**
to expand its tools, allow **floating translations**, then tap **Start reading session**. With LingoBoard selected, copy prompts can appear above the app;
with another keyboard, tap the Lingo bubble after copying. Confirmed English floats over the app.
Drag, Copy, Minimize, Close and Stop remain available. An explicitly running floating session
keeps the older received-selection route. This mode is separate from keyboard translation.

Actual Game of Khans, Discord, WhatsApp, Telegram and Instagram compatibility still needs
real-phone testing. Apps must expose an Android editor for the keyboard route. The provisional
minimum is Android 6/API 23; support for every phone/version is not established.

For controlled testing, optionally install
`testhost/build/outputs/apk/withQueries/debug/testhost-withQueries-debug.apk`.
It exposes standard selection fields. The dev-only `FlowFixtureActivity` also provides cross-UID
copy and result-contract controls for instrumentation; it is not part of the shipping app.

## Build on another machine

Copy `backend.properties.example` to ignored `backend.properties`, set `BACKEND_URL` to the
hosted HTTPS origin, and use JDK 17/Android SDK 34:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :testhost:assembleWithQueriesDebug
npm --prefix backend test
```

## Live verification

Current multilingual transport smoke uses ten synthetic requests (one full shared minute):

```sh
node backend/multilingual-smoke.mjs https://lingoflow-backend.lingoflow-backend.workers.dev benchmark/lingoboard_multilingual_http_smoke.json
```

Run UI live checks in a different UTC minute to stay within the shared quota.


Hosted smoke makes five harmless model requests and saves only status/timing/validation flags:

```sh
node backend/smoke.mjs --live https://lingoflow-backend.lingoflow-backend.workers.dev benchmark/reading_writing_http_smoke.json
```

Android cross-app smoke makes five translations; configure only on an owned emulator. The test
restores its default IME/overlay access after each case. Install both APKs first:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lingoflow.instanttranslate.ui.ReadingWritingSmokeTest \
  -Pandroid.testInstrumentationRunnerArguments.liveCloud=true
```

The keyboard suite makes seven live model calls and tests with overlays denied:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lingoflow.instanttranslate.ui.KeyboardTranslationSmokeTest \
  -Pandroid.testInstrumentationRunnerArguments.liveCloud=true
```

The separate `BackendTranslationSmokeTest` makes four requests. Run each suite in a separate
UTC minute from other live checks to stay within the shared 10/minute limit. The historical
latency harness is explicitly skipped pending adaptation to the new floating surface.
For local Android transport, use `adb reverse tcp:8787 tcp:8787` and instrumentation argument
`testBackendUrl=http://127.0.0.1:8787` in the transport test. TLS checks are never disabled.

Current checks and remaining coverage are in [VALIDATION_PLAN.md](VALIDATION_PLAN.md).
This keyboard revision does not change the deployed backend.
Older backend/Hinglish smoke records remain historical; their Devanagari routes and old UI
no longer describe the active API or APK.

## Free-tier references

Cloudflare documents 100,000 Worker requests/day and 10 ms CPU/request on its free plan;
network waiting does not count as CPU time. D1's free allocation includes 5 million rows
read/day, 100,000 rows written/day and 5 GB total storage. Our shared 200/day translation cap
is lower, but unsolicited public traffic can still exhaust infrastructure or Gemini quotas.
Free limits cause failures rather than automatic upgrades in this configured account.

- [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/)
- [D1 pricing and limits](https://developers.cloudflare.com/d1/platform/pricing/)
- [Google unpaid API terms](https://ai.google.dev/gemini-api/terms)
