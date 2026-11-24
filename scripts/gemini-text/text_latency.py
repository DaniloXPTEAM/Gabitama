#!/usr/bin/env python3
"""Real text-chat check for the Gabi default chat model (free tier only).

Three short calls to gemini-3.5-flash-lite: availability, streamed first-token time, full completion time.
Output is JSON lines with status codes, timings and counts. It never prints the key, the prompt or the answer.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request

KEY = os.environ.get("GEMINI_API_KEY", "").strip()
MODEL = os.environ.get("GEMINI_TEXT_MODEL", "gemini-3.5-flash-lite").strip()
# Official model page (aistudio.google.com/docs/models/gemini-3.5-flash-lite): Free tier "Free of charge".
FREE_TIER_MODELS = {"gemini-3.5-flash-lite"}
BASE = "https://generativelanguage.googleapis.com/v1beta"
PROMPT = "Responda em português, em uma frase curta: qual é a capital do Ceará?"
RUNS = 3


def log(phase, **fields):
    print(json.dumps({"phase": phase, **fields}, ensure_ascii=False), flush=True)


def scrub(text):
    text = str(text)
    return text.replace(KEY, "***") if KEY else text


def body():
    return json.dumps({
        "contents": [{"role": "user", "parts": [{"text": PROMPT}]}],
        "generationConfig": {"maxOutputTokens": 120, "temperature": 0.3},
    }).encode("utf-8")


def request(url, data=None):
    headers = {"x-goog-api-key": KEY, "Content-Type": "application/json"}
    return urllib.request.Request(url, data=data, headers=headers)


def availability():
    try:
        with urllib.request.urlopen(request(f"{BASE}/models/{MODEL}"), timeout=30) as resp:
            info = json.loads(resp.read().decode("utf-8"))
        methods = info.get("supportedGenerationMethods", [])
        log("availability", ok="generateContent" in methods, http_status=resp.status,
            streamable="streamGenerateContent" in methods)
        return "generateContent" in methods
    except urllib.error.HTTPError as err:
        log("availability", ok=False, http_status=err.code)
    except Exception as err:  # noqa: BLE001
        log("availability", ok=False, error=scrub(type(err).__name__))
    return False


def stream_once(run):
    t0 = time.monotonic()
    first_text_ms = None
    chars = 0
    finish = None
    usage = {}
    try:
        with urllib.request.urlopen(request(f"{BASE}/models/{MODEL}:streamGenerateContent?alt=sse", body()), timeout=90) as resp:
            status = resp.status
            for raw in resp:
                line = raw.decode("utf-8", errors="replace").strip()
                if not line.startswith("data:"):
                    continue
                event = json.loads(line[5:].strip())
                for cand in event.get("candidates", []):
                    for part in cand.get("content", {}).get("parts", []):
                        text = part.get("text", "")
                        if text and first_text_ms is None:
                            first_text_ms = int((time.monotonic() - t0) * 1000)
                        chars += len(text)
                    finish = cand.get("finishReason", finish)
                usage = event.get("usageMetadata", usage) or usage
        total_ms = int((time.monotonic() - t0) * 1000)
        log("stream", run=run, http_status=status, first_text_ms=first_text_ms, total_ms=total_ms,
            answer_chars=chars, finish_reason=finish, prompt_tokens=usage.get("promptTokenCount"),
            output_tokens=usage.get("candidatesTokenCount"))
        return chars > 0 and finish == "STOP"
    except urllib.error.HTTPError as err:
        log("stream", run=run, ok=False, http_status=err.code, total_ms=int((time.monotonic() - t0) * 1000))
    except Exception as err:  # noqa: BLE001
        log("stream", run=run, ok=False, error=scrub(type(err).__name__), total_ms=int((time.monotonic() - t0) * 1000))
    return False


def main():
    if not KEY:
        log("config", ok=False, reason="GEMINI_API_KEY ausente")
        return 2
    if MODEL not in FREE_TIER_MODELS:
        log("config", ok=False, reason="modelo fora da lista gratuita", model=MODEL)
        return 2
    log("config", ok=True, model=MODEL, runs=RUNS)
    if not availability():
        log("result", ok=False, reason="modelo indisponível")
        return 1
    results = [stream_once(i + 1) for i in range(RUNS)]
    ok = all(results)
    log("result", ok=ok, passed_runs=sum(results), runs=RUNS)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
