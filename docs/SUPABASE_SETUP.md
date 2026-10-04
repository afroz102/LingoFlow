# Supabase cloud setup and phone testing

> Historical setup, replaced by [our custom backend](BACKEND_SETUP.md) on 2026-10-04.
> The active Android app no longer calls Supabase. Resources remain available for reference.

Updated 2026-10-04. This user-directed migration supersedes the earlier Firebase-specific cloud
plan. The app is a test prototype; the formal physical-device, language-quality, and release
gates remain open.

## Active architecture

```text
Android Process Text selection
→ versioned cloud disclosure and connectivity check
→ anonymous Supabase Auth session
→ POST /functions/v1/translate with the user access token
→ verify the token with Supabase Auth
→ atomically reserve quota in Postgres
→ Gemini Developer API, structured translation response
→ validated result → explicit Copy / Replace
```

Firebase dependencies, initialization, and App Check are removed. The Android app contains only
the public Supabase URL and publishable key. Gemini and service-role credentials remain on the
server. Anonymous Auth does create a persistent opaque identity in Supabase, although there is
no email/password or user-facing login flow in the app.

Only auth/session metadata and rate-limit counts persist. The function never writes source text
or translations to Postgres, never logs request/response bodies or exceptions, and returns
content-free error codes. Google processes the selected text under its own API terms; changing
the backend to Supabase does not make Google's free tier private or offline.

## Configured test project

- Supabase organization: **Jack**, Free plan.
- Project: **LingoFlow Testing**.
- Project reference: `ykpdceyrukuayhzrrlxn`.
- Region: Singapore (`ap-southeast-1`).
- Project URL: `https://ykpdceyrukuayhzrrlxn.supabase.co`.
- Function: `translate`.
- Gemini spike model: `gemini-3.5-flash-lite`, explicitly pinned in function secrets.
- Google account: `s78.meghnad@gmail.com`.
- Google project: **LingoFlow Testing**, `gen-lang-client-0112174397`, Free tier; billing not enabled.

The model is a working testing choice, not the result of the formal bilingual model bake-off.

## Verification recorded on 2026-10-04

| Check | Result |
| --- | --- |
| Android debug app, test APK, and test-host build | Passed |
| JVM tests | 25 passed |
| Edge Function unit tests | 12 passed |
| Deno type check | Passed |
| Corpus validator | 458 items passed |
| Gemini direct live request using the s78 test key | Passed; Hindi `नमस्ते`, finish reason `STOP` |
| Deployed function rejects missing user authorization | Passed; HTTP 401 |
| Complete Supabase HTTP smoke test | Blocked; Auth timed out and invalid-token verification returned HTTP 504 |
| Actual Android live adapter test after correcting DNS | Blocked; `/auth/v1/signup` returned HTTP 522 |
| Physical phone | Not connected; not tested |

Anonymous sign-in, the quota SQL migration, server-side Gemini secrets, and the `translate`
function were configured through Chrome. A project restart was attempted to recover Auth, but
the dashboard continued to show **Restarting** while the final checks were recorded. Live
translation is **not yet verified or ready for phone testing**. The remaining setup step is
Supabase service recovery, followed by both live smoke commands below. If the project remains
stuck, use Supabase's project support; changing the Android code or disabling TLS will not fix
an origin timeout. There is no successful full-cloud report yet.

## Recreate or deploy the backend

1. Create a dedicated free Supabase project. Enable Data API, disable automatic table exposure,
   and enable automatic RLS.
2. Enable **Authentication → Sign In / Providers → Allow anonymous sign-ins**. Keep the platform
   signup rate limits. Production distribution needs a separate abuse-prevention review;
   this configuration is for controlled device testing.
3. Run `supabase/migrations/202610040001_translation_limits.sql` in SQL Editor. The table has
   RLS enabled and no `anon`/`authenticated` grants. Only `service_role` can execute the quota RPC.
4. Create a Gemini API key in a dedicated Google AI Studio free-tier project, without enabling
   billing. In **Supabase → Edge Functions → Secrets**, set `GEMINI_API_KEY` and `GEMINI_MODEL`.
5. Deploy `supabase/functions/translate/`. Platform-provided Supabase credentials need no manual
   copy. Modern publishable/secret key dictionaries are preferred; legacy keys are a fallback.
6. For this function, turn off **Verify JWT with legacy secret**. The handler independently calls
   `/auth/v1/user` and rejects unauthenticated, expired, and invalid tokens before spending quota.
   An anon/publishable project key alone never authorizes translation.

CLI deployment, if you already have an authorized Supabase CLI session:

```bash
supabase link --project-ref ykpdceyrukuayhzrrlxn
supabase db push
supabase functions deploy translate
```

If the migration was applied through SQL Editor, record that migration in your CLI migration
history before `db push`; otherwise the CLI will attempt to replay it. The checked-in
`supabase/config.toml` sets the same function-side verification policy as the dashboard.

## Test quotas

The quota RPC serializes reservations across function instances using a transaction advisory
lock. It permits 5 requests per anonymous user per minute, 10 total per minute, and 200 total per
UTC day. A failed model call still consumes the reserved request; automatic content-bearing
retries are deliberately disabled. Old quota buckets are cleaned on later reservations.
Provider free-tier limits can be lower and also surface as the normal rate-limit state.

## Android build and install

`supabase.properties` is local and gitignored. Its public configuration has been populated on
the development machine; on another machine, copy `supabase.properties.example` and fill it in.
Never put Gemini or Supabase server keys in that file: its values enter the APK.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="/Users/afroz/Library/Android/sdk/platform-tools:$PATH"
adb devices
./gradlew :app:installDebug :testhost:installWithQueriesDebug
```

Connect an Android 6/API 23+ phone with USB debugging enabled and accept its debugging prompt.
There is no launcher entry for the translator. Open the test-host app, select clear English or
Devanagari Hindi text, and choose **More → Translate**. A Hindi system locale uses the localized
action label **अनुवाद करें**. Accept the updated Supabase/Gemini disclosure once.

Read-only selections offer Copy; editable fields additionally offer explicit Replace. Back
leaves the source unchanged. With no internet, the app shows the offline message. No Firebase
configuration or App Check debug token is needed.

Hinglish ambiguity correction and Romanized output are still unfinished. Some host apps do not
offer Process Text actions; begin with the controlled test host, then check Chrome and your
actual target apps.

### Network issue found on this development network

The emulator's default DNS resolved this project's Supabase hostname to an ACT Broadband
address (`202.83.21.15`) and HTTPS failed during the TLS handshake. With Android Private DNS
set to `dns.google`, it resolved to Cloudflare (`104.18.38.10`) instead. Merely selecting public
UDP DNS servers at emulator startup did not fix the observed DNS response.

If your phone has the same problem on that Wi-Fi network, use mobile data or set
**Settings → Network & internet → Private DNS → Private DNS provider hostname → `dns.google`**.
Menu names vary by phone. Google documents this setting in its
[Public DNS setup guide](https://developers.google.com/speed/public-dns/docs/using).
Keep HTTPS certificate verification enabled. The app does not override system DNS or TLS.

## Verification commands

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest
npx --yes deno check supabase/functions/translate/index.ts
node --test supabase/functions/translate/handler.test.mjs
python3 benchmark/smoke_supabase.py --live --report benchmark/supabase_smoke_result.json
```

The HTTP smoke test performs two fixed harmless translations, checks rejection of missing and
invalid user tokens and blank input, and verifies session refresh. It never prints auth tokens.

Opt-in live Android adapter test on a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.lingoflow.instanttranslate.provider.supabase.SupabaseTranslationSmokeTest \
  -Pandroid.testInstrumentationRunnerArguments.liveCloud=true
```

This test uses the actual Android HTTP/auth adapter for two harmless translations. It does not
replace physical-phone Process Text compatibility testing or the quality benchmark.
