#!/usr/bin/env python3
"""Real Gemini Live smoke test for GabiTama (free-tier models only).

Runs in CI with the key in the environment (GEMINI_API_KEY). Output is JSON lines with
phases, status codes, timings and byte counts only. It never prints the key, audio
payloads, the prompt text, or the model's answer text.
"""
import base64
import json
import math
import os
import struct
import sys
import time
import urllib.error
import urllib.request

from websockets.sync.client import connect

KEY = os.environ.get("GEMINI_API_KEY", "").strip()
MODEL = os.environ.get("GEMINI_LIVE_MODEL", "gemini-3.8-live").strip()
# Pricing page (aistudio.google.com/docs/pricing): Free tier "Free of charge" for these.
FREE_TIER_MODELS = {"gemini-3.8-live", "gemini-3.1-flash-live-preview"}
REST_MODELS = "https://generativelanguage.googleapis.com/v1beta/models?pageSize=1000"
WS_URL = (
    "wss://generativelanguage.googleapis.com/ws/"
    "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
)
PROMPT = "Responda em português, em uma frase curta: qual é a capital do Ceará?"


def log(phase, **fields):
    print(json.dumps({"phase": phase, **fields}, ensure_ascii=False), flush=True)


def scrub(text):
    text = str(text)
    return text.replace(KEY, "***") if KEY else text


def discover():
    req = urllib.request.Request(REST_MODELS, headers={"x-goog-api-key": KEY})
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            data = json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as err:
        log("discovery", ok=False, http_status=err.code)
        return False
    except Exception as err:  # noqa: BLE001 - report class only
        log("discovery", ok=False, error=type(err).__name__)
        return False
    models = data.get("models", [])
    bidi = {
        m.get("name", "").removeprefix("models/")
        for m in models
        if "bidiGenerateContent" in m.get("supportedGenerationMethods", [])
    }
    log(
        "discovery",
        ok=True,
        models_listed=len(models),
        bidi_models=len(bidi),
        selected_model_is_bidi=MODEL in bidi,
        selected_model_listed=any(m.get("name") == f"models/{MODEL}" for m in models),
    )
    return MODEL in bidi


def setup_message():
    return json.dumps({
        "setup": {
            "model": f"models/{MODEL}",
            "generationConfig": {
                "responseModalities": ["AUDIO"],
                "speechConfig": {"voiceConfig": {"prebuiltVoiceConfig": {"voiceName": "Kore"}}},
            },
            "systemInstruction": {"parts": [{"text": "Você é uma assistente em português do Brasil. Seja breve."}]},
            "inputAudioTranscription": {},
            "outputAudioTranscription": {},
        }
    })


def decode(message):
    if isinstance(message, bytes):
        return message.decode("utf-8", errors="replace")
    return message


def open_session(api_key):
    return connect(WS_URL, additional_headers={"x-goog-api-key": api_key}, open_timeout=20, max_size=None)


def negative_control():
    """Fake key: must be refused without any model usage."""
    t0 = time.monotonic()
    try:
        with open_session("invalid-key-for-negative-control") as ws:
            ws.send(setup_message())
            reply = decode(ws.recv(timeout=15))
            log("negative_control", refused=("error" in reply.lower()), ms=int((time.monotonic() - t0) * 1000))
    except Exception as err:  # noqa: BLE001
        log("negative_control", refused=True, error=scrub(type(err).__name__), ms=int((time.monotonic() - t0) * 1000))


def silence_chunks(seconds=2.0, rate=16000):
    samples = int(seconds * rate)
    pcm = struct.pack("<%dh" % samples, *([0] * samples))
    step = 1280
    return [pcm[i:i + step] for i in range(0, len(pcm), step)]


def live_session():
    ok = False
    t_open = time.monotonic()
    try:
        ws = open_session(KEY)
    except Exception as err:  # noqa: BLE001
        log("handshake", ok=False, error=scrub(type(err).__name__))
        return False
    log("socket_open", ms=int((time.monotonic() - t_open) * 1000))
    try:
        ws.send(setup_message())
        t_setup = time.monotonic()
        ready = False
        deadline = t_setup + 20
        while time.monotonic() < deadline and not ready:
            try:
                msg = json.loads(decode(ws.recv(timeout=max(0.1, deadline - time.monotonic()))))
            except TimeoutError:
                break
            if "error" in msg:
                log("handshake", ok=False, server_error_class=msg["error"].get("status", "UNKNOWN"),
                    code=msg["error"].get("code"))
                return False
            ready = "setupComplete" in msg
        log("handshake", ok=ready, setup_complete_ms=int((time.monotonic() - t_setup) * 1000))
        if not ready:
            return False

        # (e) text turn, the same frame shape the app sends (realtimeInput.text)
        ws.send(json.dumps({"realtimeInput": {"text": PROMPT}}))
        t_turn = time.monotonic()
        audio_bytes = 0
        first_audio_ms = None
        out_chars = 0
        turn_done = False
        deadline = t_turn + 60
        while time.monotonic() < deadline and not turn_done:
            try:
                msg = json.loads(decode(ws.recv(timeout=max(0.1, deadline - time.monotonic()))))
            except TimeoutError:
                break
            if "error" in msg:
                log("text_turn", ok=False, server_error_class=msg["error"].get("status", "UNKNOWN"))
                return False
            sc = msg.get("serverContent", {})
            for part in sc.get("modelTurn", {}).get("parts", []):
                inline = part.get("inlineData")
                if inline and inline.get("mimeType", "").startswith("audio/pcm"):
                    raw = base64.b64decode(inline.get("data", ""))
                    if first_audio_ms is None:
                        first_audio_ms = int((time.monotonic() - t_turn) * 1000)
                    audio_bytes += len(raw)
            out_chars += len(sc.get("outputTranscription", {}).get("text", ""))
            turn_done = bool(sc.get("turnComplete"))
        log("text_turn", ok=turn_done and audio_bytes > 0, turn_complete=turn_done,
            audio_bytes=audio_bytes, first_audio_ms=first_audio_ms,
            output_transcript_chars=out_chars,
            duration_ms=int((time.monotonic() - t_turn) * 1000))

        # (c) microphone-style streaming: 2 s of PCM16 16 kHz silence in 1280-byte chunks
        sent = 0
        for chunk in silence_chunks():
            ws.send(json.dumps({"realtimeInput": {"audio": {
                "data": base64.b64encode(chunk).decode("ascii"),
                "mimeType": "audio/pcm;rate=16000",
            }}}))
            sent += 1
            time.sleep(0.04)  # each 1280-byte PCM16 chunk at 16 kHz is 40 ms of audio: real-time pacing
        log("audio_input", ok=True, chunks_sent=sent, paced_realtime=True)
        # Check the socket is still alive after streaming input.
        try:
            msg = json.loads(decode(ws.recv(timeout=3)))
            log("post_audio_input", error_frame="error" in msg)
        except TimeoutError:
            log("post_audio_input", error_frame=False, idle=True)
        ok = turn_done and audio_bytes > 0
    finally:
        # (h) Stop: client closes the socket
        t_close = time.monotonic()
        closed_ok = False
        try:
            ws.close(1000, "smoke_done")
            closed_ok = True
        except Exception as err:  # noqa: BLE001
            log("stop_error", error=scrub(type(err).__name__))
        log("stop", socket_closed=closed_ok, ms=int((time.monotonic() - t_close) * 1000))
    # Success needs the turn AND a clean socket close (a failed close is a failed smoke).
    return ok and closed_ok


def main():
    if not KEY:
        log("config", ok=False, reason="GEMINI_API_KEY ausente")
        return 2
    if MODEL not in FREE_TIER_MODELS:
        log("config", ok=False, reason="modelo fora da lista gratuita", model=MODEL)
        return 2
    log("config", ok=True, model=MODEL, free_tier_listed=True)
    if not discover():
        log("result", ok=False, reason="modelo não disponível como bidi na conta")
        return 1
    negative_control()
    ok = live_session()
    log("result", ok=ok)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
