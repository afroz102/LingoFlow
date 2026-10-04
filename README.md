# LingoFlow / InstantTranslate

Native Android text-selection translation: **Select → More → Translate**, then preview,
Copy, or explicitly Replace an editable selection. Hosts must expose Android Process Text.
The app has no launcher and does not replace your keyboard.

The testing backend is live on Cloudflare's Free plan: our JavaScript API, SQLite/D1 aggregate
quotas and server-side Gemini. No authentication or user sessions are required. Selected text
passes through Cloudflare to Google; the app/database keep no translation history. Gemini's
unpaid service is for non-sensitive test samples.

## Status

English↔Devanagari Hindi works through the deployed backend. Backend tests and Android JVM tests
pass; hosted HTTP and real Android transport smoke checks passed on 2026-10-04. Historical
Process Text evidence exists on API 30 and 34. Physical-phone/OEM compatibility, full quality
scoring, Hinglish direction correction and Romanized output preferences remain open.

Shared testing allowance: **10 translations/minute, 200/UTC day** across all callers. The API is
public, so other callers can exhaust that allowance. No public-release reliability/privacy
claim has been established.

## Build and run

Requirements: JDK 17, Android SDK 34 (minimum API 23 provisional), Node 24+ for local backend work.
Copy `backend.properties.example` to ignored `backend.properties` and set the deployed public
HTTPS origin from [backend setup](docs/BACKEND_SETUP.md). Never add a Gemini key to Android config.

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:assembleDebug :app:testDebugUnitTest :testhost:assembleWithQueriesDebug
npm --prefix backend test
python3 benchmark/validate_corpus.py
```

Install `app/build/outputs/apk/debug/app-debug.apk` and optionally the controlled host APK at
`testhost/build/outputs/apk/withQueries/debug/testhost-withQueries-debug.apk`.
Open the host, select text, choose More → Translate and acknowledge the disclosure.
Full installation, hosted deployment and local Node/SQLite instructions are in the setup guide.

## Repository

| Directory | Purpose |
|---|---|
| `app/` | Kotlin Process Text adapter, result UI and HTTP provider |
| `backend/` | Worker/portable Node server, SQLite schema/quotas, tests and HTTP smoke tool |
| `testhost/` | Development-only host with package-visibility A/B and editor fixtures |
| `benchmark/` | Corpus, validation/timing tools and dated evidence |
| `docs/` | Current product, architecture, validation and operational guides |

## Documentation

- [Product requirements](docs/PRODUCT_REQUIREMENTS.md): current capabilities, remaining V1 work and roadmap.
- [Architecture and privacy](docs/TECHNICAL_PLAN.md): active request path, data handling, limits and audit requirements.
- [Validation plan](docs/VALIDATION_PLAN.md): compatibility, model-quality/resource budgets and release gates.
- [Backend setup and phone testing](docs/BACKEND_SETUP.md): deployment, credentials, installation and live evidence.
- [Benchmark index](benchmark/README.md): corpus, tools and historical findings.

Superseded integrations and duplicated planning guides were removed; previous versions remain
in Git history. `CLAUDE.md` contains the repository's coding conventions.
