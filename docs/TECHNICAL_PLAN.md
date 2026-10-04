# Architecture and privacy — LingoFlow

Updated 2026-10-04. Describes the implemented test version. Operational commands and deployed
resource IDs live in [BACKEND_SETUP.md](BACKEND_SETUP.md); requirements in
[PRODUCT_REQUIREMENTS.md](PRODUCT_REQUIREMENTS.md).

## 1. Repository boundaries

`app/` is the native Android product; `testhost/` is a separate development-only host with
package-visibility A/B flavors, read-only/editable, Compose and WebView fixtures.
`backend/` contains the translation API and SQLite quota code; `benchmark/` contains validation
scripts, corpus and evidence. No shared Android library or DI framework is needed.

## 2. Request path

```text
Host ACTION_PROCESS_TEXT → ProcessTextActivity validates input
→ ResultViewModel / TranslateCoordinator → disclosure and connectivity gates
→ BackendTranslationProvider → HTTPS /v1/translate
→ Worker → atomic D1 quota reservation → Gemini → validated JSON
→ transient ResultActivity → explicit Copy / editable Replace / Back
```

The Android provider sends only `text` and `direction`, with no API key, cookie, Auth session or
caller/device identity. Host text is untrusted. The backend validates it independently and puts
it inside a literal selected-text field, with a fixed translation instruction and JSON schema.
It enables no tools, grounding, URL fetching or conversation history.

## 3. Android logical boundaries

- **Text-action adapter:** validates action/text/size, flattens CharSequence, defaults uncertain
  editability to read-only, launches the result and relays only its explicit result.
- **Coordinator:** resolves direction, checks disclosure before connectivity, invokes the provider
  and exposes typed outcomes. No Android SDK/model-specific response types cross this boundary.
- **Direction policy:** current script heuristic detects any Devanagari as Hindi→English. Latin
  Hinglish and mixed/short text still require future correction controls; no full detector exists.
- **Cloud readiness and disclosure:** no selected-content request until disclosure acknowledgement.
  SharedPreferences stores only the acknowledged notice version; a changed notice prompts again.
- **Provider:** one bounded HTTPS request, response validation, typed content-free failures and
  cancellation propagation. Explicit user Retry is the only retry; no model/provider fallback.
- **Presenter:** loading/disclosure/success/error state; original/result/direction; deliberate Copy
  and conditional Replace. ViewModel survives rotation but content is not saved for process death.

## 4. Process Text contract

`ProcessTextActivity` is exported for `android.intent.action.PROCESS_TEXT`, DEFAULT category and
`text/plain`. It has no launcher. `ResultActivity` is not exported. The app does not use
`forceQueryable` or add an Accessibility/overlay workaround for hosts that do not expose actions.

Input validation requires the expected action, a readable CharSequence, nonblank text and at most
4,000 UTF-16 code units. The manifest filters MIME type for implicit discovery; the current
validator does not re-check MIME on a direct explicit invocation. That boundary needs release
review rather than a claim that every crafted-intent case is covered.

Malformed extras fail safely. Missing/wrong-type read-only information defaults to read-only.
Only Replace returns `RESULT_OK` with translated plain text in `EXTRA_PROCESS_TEXT`.
Cancel, Back, invalid input and failures return no modification. Some custom hosts can reject
replacement; Copy remains the fallback. Hosts control discovery and toolbar placement.

## 5. Backend and database

`translation.mjs` is shared by `worker.mjs` and the portable Node HTTP server. Workers binds
`DB` to D1; the local server uses Node 24+ built-in SQLite and a separate WAL database.
Both use the same conditional SQL upsert. The quota reservation and old-bucket cleanup occur
inside one transaction, so parallel callers share a 10/minute and 200/UTC-day cap.
Clock skew cannot reset a newer minute to an older bucket. Failed model calls still consume
quota; unavailable quota storage blocks Gemini calls. Only day/minute/counts are stored, with
buckets older than seven days pruned during requests.

Routes: GET `/healthz` checks configuration/database; POST `/v1/translate` translates. Unknown
routes are 404, incorrect methods 405. Health does not verify model availability or quality.
The API intentionally has no authentication; anyone knowing the URL can consume shared quota.
There is no guaranteed app-only access or production availability claim.

## 6. Deadlines, payloads and failures

- Android: 8-second connect, 22-second socket read, 30-second provider deadline; no redirects.
- Backend: 18-second quota/model deadline, 5-second health database deadline.
- Input: JSON only, streamed body at most 32 KiB, nonblank text ≤4,000 code units, two directions.
- Upstream/Android response: at most 128 KiB; translated string ≤16,000 code units and nonblank.
- Gemini must complete with STOP; malformed/truncated/non-string output fails validation.
- Model ID pinned to `gemini-3.5-flash-lite`; model changes require quality/terms/quota rechecking.
- Typed errors distinguish input, rate limit, timeout, provider and invalid-response failures.
  Exception messages/upstream bodies never reach UI or application logs.

Cancellation can still leave a server request/quota reservation already in progress. Do not
retry automatically or claim cancelling recovers quota. Connection waiting does not justify
blocking Android's main thread or adding an idle background service.

## 7. Configuration and transport

Android reads a public HTTPS origin from ignored `backend.properties` or `LINGOFLOW_BACKEND_URL`.
The build rejects credentials, paths, query/fragment and invalid ports. The Gemini key lives
only in Worker Secrets or ignored local `.dev.vars` with permissions 600.
Production cleartext is disabled; debug allows HTTP only to localhost/127.0.0.1/10.0.2.2.
TLS certificate checks remain enabled. Public preview URLs, Worker logs/traces and application
request/content caching are disabled. Managed infrastructure may process connection metadata.
The code/schema are portable; Cloudflare remains a managed hosting dependency.

## 8. Privacy and data lifecycle

No selected text or hash ever enters a trace label. Source/result content must not be written to
preferences, databases, files, saved instance state, diagnostics, analytics or notifications.
The result sets FLAG_SECURE to prevent screenshot/Recents capture; backup is disabled.
Only timing enums/monotonic readings and typed outcomes are emitted in debug. Release timing is off.

| Data | Handling |
|---|---|
| Selection/result | Volatile app/request memory; transmitted through Cloudflare to Google |
| Disclosure version | Local non-content SharedPreferences |
| Usage buckets/counts | Backend SQLite; no text, translation, IP or identity columns |
| Clipboard result | Written only on Copy; then governed by Android/other apps |
| Replacement result | Returned only on explicit Replace to the invoking host |
| Model/public origin | Configuration; no Gemini credential in APK |

There is no surrounding text, clipboard read, host context, cross-request history, background
translation or identity profiling. Closing the UI does not promise that Google deletes its copy.
Google unpaid API terms permit product improvement/human review: this build is for non-sensitive
samples, not an unrestricted privacy-first public release.

## 9. Audit and release review

Use harmless canaries to verify intended traffic, logs, app files/preferences, backups/state,
Recents, clipboard boundaries and idle behavior. Exercise failed/cancelled requests as well as
success. Inspect the merged manifest, dependency/license inventory and APK for keys, debug tools,
unexpected exported components/services and dangerous permissions. Review process death, rotated
results, repeated requests, low memory and host result propagation on physical devices.
A canary outside intended request/result/explicit Copy/Replace is release-blocking.
Privacy acceptance requires current Google/hosting terms, truthful disclosure, distribution-market
review and an explicit public-release data posture. See [validation](VALIDATION_PLAN.md#6-privacy-and-security-validation).
