#!/usr/bin/env python3
"""Opt-in live smoke test. Uses public project config; never prints auth tokens.

Runs two harmless translations, plus authentication and input rejection checks.
This is integration evidence, not the bilingual quality benchmark.
"""
import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true", help="Allow two real Gemini requests")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    if not args.live:
        parser.error("Pass --live to explicitly allow real provider requests")
    root = Path(__file__).resolve().parents[1]
    config = {}
    for line in (root / "supabase.properties").read_text().splitlines():
        if line.strip() and not line.lstrip().startswith("#") and "=" in line:
            name, value = line.split("=", 1)
            config[name.strip()] = value.strip()
    url = config["SUPABASE_URL"].rstrip("/")
    key = config["SUPABASE_PUBLISHABLE_KEY"]

    def post(path, payload, token=None):
        headers = {"Content-Type": "application/json", "apikey": key}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        request = urllib.request.Request(url + path, json.dumps(payload).encode(), headers=headers)
        started = time.monotonic()
        try:
            with urllib.request.urlopen(request, timeout=35) as response:
                return response.status, json.load(response), round((time.monotonic() - started) * 1000)
        except urllib.error.HTTPError as failure:
            # Capture typed error categories only. Auth bodies/tokens never enter the report.
            body = json.loads(failure.read())
            return failure.code, {"error": body.get("error", body.get("code", "HTTP_ERROR"))}, round((time.monotonic() - started) * 1000)

    result = {"project_url": url, "checks": []}
    payload = {"text": "Hello", "direction": "ENGLISH_TO_HINDI"}
    status, _, _ = post("/functions/v1/translate", payload)
    assert status == 401, f"Unauthenticated request must be rejected, got {status}"
    result["checks"].append({"check": "unauthenticated", "status": status})
    status, _, _ = post("/functions/v1/translate", payload, "invalid.token.value")
    assert status == 401, f"Invalid user token must be rejected, got {status}"
    result["checks"].append({"check": "invalid_token", "status": status})

    status, session, _ = post("/auth/v1/signup", {})
    assert status == 200 and session.get("access_token"), f"Anonymous auth failed with status {status}"
    token = session["access_token"]
    status, _, _ = post("/functions/v1/translate", {"text": "", "direction": "ENGLISH_TO_HINDI"}, token)
    assert status == 400, f"Blank text must be rejected, got {status}"
    result["checks"].append({"check": "blank_input", "status": status})

    samples = [("How are you?", "ENGLISH_TO_HINDI"), ("आप कैसे हैं?", "HINDI_TO_ENGLISH")]
    for text, direction in samples:
        status, body, elapsed = post("/functions/v1/translate", {"text": text, "direction": direction}, token)
        assert status == 200 and isinstance(body.get("translation"), str) and body["translation"].strip(), \
            f"{direction} failed: status={status}, category={body.get('error', 'INVALID_RESPONSE')}"
        # Only these fixed, harmless test results are recorded, never arbitrary user selections.
        result["checks"].append({"check": direction, "status": status, "elapsed_ms": elapsed, "translation": body["translation"]})

    status, refreshed, _ = post("/auth/v1/token?grant_type=refresh_token", {"refresh_token": session["refresh_token"]})
    assert status == 200 and refreshed.get("access_token"), f"Session refresh failed: {status}"
    result["checks"].append({"check": "session_refresh", "status": status})
    output = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.report:
        args.report.write_text(output)
    print(output, end="")


if __name__ == "__main__":
    main()
