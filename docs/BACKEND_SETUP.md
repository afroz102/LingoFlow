# Custom backend setup and Android phone testing

Current architecture, replacing Firebase and Supabase for this test version:

```text
Android selection / confirmed clipboard text → Lingo-Translate → HTTPS POST /v1/translate
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

- Android sends only `text` and `direction` in the JSON request.
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

The `0.6.0-reading-writing` APK includes the public backend URL. No Supabase account,
Gemini key entry or server configuration is needed on the phone. Internet is required.

1. Transfer `app/build/outputs/apk/debug/app-debug.apk` to the phone and install it, allowing
   installs from that particular file/browser app if Android asks. Update any older LingoFlow APK.
2. Open **LingoTranslate** from the launcher and tap **Allow floating translations**. Enable
   display over other apps in Android settings, then return. This permission is needed for reading.
3. For automatic copy prompts, tap **1. Enable Lingo keyboard** and enable it in system settings.
   Return, tap **2. Choose Lingo keyboard**, and select it. Android will show its keyboard warning.
   The keyboard is a basic local Roman keyboard; it has no suggestions or swipe typing. To keep
   your current keyboard, skip this step and use the manual bubble route below.
4. Tap **Start reading session** and acknowledge the cloud-processing disclosure. The Lingo bubble
   appears. On Android 13+, allow notifications when asked for the notification Stop control;
   declining still allows the overlay session. The session notification appears when permitted.
   Return to your game/chat; do not stop the session yet.
5. **Automatic reading:** with Lingo keyboard selected, copy a received Hinglish message in the
   host. The prompt asks whether to translate. Tap **Translate**; English floats over the host.
6. **Manual reading:** with another keyboard, copy the message, then tap **Lingo**. That tap requests
   translation. The card briefly takes focus to read the clipboard, then floats the English result.
7. **Selectable reading:** select a read-only Hinglish message and choose **Lingo-Translate** from
   the selection menu (possibly under More). The result floats without another confirmation tap.
8. Drag the card by its title. Use **Minimize** to keep a result as a bubble, **Close** to discard
   it, **Copy** to put English in the clipboard, and **Stop** to end the entire reading session.
   Stop is also available in the session notification or launcher.
9. **Writing:** type `main kal nahi aa sakta`, select that draft, and choose **Lingo-Translate**.
   It should automatically replace the selection with English. Try `How are you?` for Roman Hindi.
   Writing works with any keyboard and does not require a running reading session or overlays.
10. Try cancellation/offline input, selection of only part of a draft, rotation, landscape game,
    repeated copies and session Stop. Errors/cancel must leave the original draft untouched.

Only English and Roman Hindi/Hinglish are supported; Devanagari is rejected. Reading an already
English message should return it unchanged. Results use context in the copied/selected passage,
not surrounding chat. Sensitive clips, password contexts and Lingo's own copied output are skipped.

The minimum is provisionally Android 6/API 23, not a guarantee for every phone/version. If a host
omits the selection action, use its copy behavior. If the host blocks overlays, the floating route
cannot be promised. Automatic copying requires the selected Lingo keyboard on modern Android;
overlay permission alone does not provide clipboard access. Actual Game of Khans, Discord,
WhatsApp, Telegram and Instagram compatibility still needs real-phone testing.

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

The separate `BackendTranslationSmokeTest` makes four requests. Run each suite in a separate
UTC minute from other live checks to stay within the shared 10/minute limit. The historical
latency harness is explicitly skipped pending adaptation to the new floating surface.
For local Android transport, use `adb reverse tcp:8787 tcp:8787` and instrumentation argument
`testBackendUrl=http://127.0.0.1:8787` in the transport test. TLS checks are never disabled.

Current checks and remaining coverage are in [VALIDATION_PLAN.md](VALIDATION_PLAN.md).
The deployed V1 Worker version is `933559fa-feea-4a9f-ba0a-1baa81a4444c`.
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
