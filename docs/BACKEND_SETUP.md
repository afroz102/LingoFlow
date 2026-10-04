# Custom backend setup and Android phone testing

Current architecture, replacing Firebase and Supabase for this test version:

```text
Android text selection → Translate → HTTPS POST /v1/translate
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

## Build and install on a real Android phone

The final APK will already contain the hosted endpoint. No backend configuration, Supabase
account, or Gemini key entry is needed on the phone.

1. Transfer `app/build/outputs/apk/debug/app-debug.apk` to your phone and install it. Allow
   installation from the particular browser/file app if Android asks.
2. Optionally install `testhost/build/outputs/apk/withQueries/debug/testhost-withQueries-debug.apk`
   for the controlled sample host. It has a launcher named InstantTranslate TestHost.
3. Open the test host or another app that supports third-party text-selection actions.
4. Long-press English text, select it, then choose **More → Translate** (or **अनुवाद करें**).
5. Accept the cloud-processing disclosure. Check that the Hindi result appears and Copy works.
6. Try Hindi text for English output. In an editable field, verify Replace changes only the
   selected text. In a read-only field, verify Copy is available without Replace.

The translator has no launcher icon; it is opened through the selection menu. If Translate
is missing in one host, try the controlled test host. Host apps choose whether they expose
Android Process Text actions. Internet is required; no user authentication is required.

For another development machine, copy `backend.properties.example` to `backend.properties`,
set `BACKEND_URL` to the deployed HTTPS origin, then build:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug :app:testDebugUnitTest
```

## Live verification

Hosted HTTP smoke (two harmless translations, content-free report):

```sh
node backend/smoke.mjs --live https://lingoflow-backend.lingoflow-backend.workers.dev benchmark/backend_smoke_result.json
```

Opt-in Android test (two harmless translations, no automatic retry):

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lingoflow.instanttranslate.provider.backend.BackendTranslationSmokeTest \
  -Pandroid.testInstrumentationRunnerArguments.liveCloud=true
```

For the local server, first `adb reverse tcp:8787 tcp:8787`, and add
`-Pandroid.testInstrumentationRunnerArguments.testBackendUrl=http://127.0.0.1:8787`.

Verified on 2026-10-04 (India time):

| Check | Result |
|---|---|
| Backend tests | 15 passed, including real SQLite persistence, four concurrent connections and stalled-database timeout |
| Android JVM tests | 20 passed |
| Debug app and Android test APKs | Built successfully with the deployed HTTPS origin |
| Hosted HTTP smoke | All 6 checks passed; health 200, invalid input 400, wrong method 405, no Auth route 404, both translation directions 200 |
| Real Android transport, API 34 emulator | Both English→Hindi and Hindi→English passed against the public Worker without credentials |
| Local portable backend | Both directions passed over real Gemini; Android localhost transport also passed |
| Hosted database | Schema and aggregate count row inspected; 4 real request reservations persisted, no content/identity columns |
| Physical phone | Not attached; use the installation steps above |

The [Android smoke record](../benchmark/backend_android_smoke_result.json) documents the live
transport test. The content-free [HTTP smoke report](../benchmark/backend_smoke_result.json) records two small
translation requests at approximately 1.1 and 1.0 seconds. This is a connectivity smoke test,
not a quality/latency benchmark or a proven performance comparison with Supabase. The new
hostname initially returned TLS handshake failures, then became reachable after activation;
no certificate checks were bypassed.

This remains a testing build:
physical-device compatibility, Hinglish controls/output preferences, and the full blinded
model-quality benchmark are still open.


## Free-tier references

Cloudflare documents 100,000 Worker requests/day and 10 ms CPU/request on its free plan;
network waiting does not count as CPU time. D1's free allocation includes 5 million rows
read/day, 100,000 rows written/day and 5 GB total storage. Our shared 200/day translation cap
is lower, but unsolicited public traffic can still exhaust infrastructure or Gemini quotas.
Free limits cause failures rather than automatic upgrades in this configured account.

- [Workers pricing](https://developers.cloudflare.com/workers/platform/pricing/)
- [D1 pricing and limits](https://developers.cloudflare.com/d1/platform/pricing/)
- [Google unpaid API terms](https://ai.google.dev/gemini-api/terms)
