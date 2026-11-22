#!/usr/bin/env python3
"""
Teste executável local de protocolo Streamable HTTP MCP (JSON-RPC 2.0 + SSE + Mcp-Session-Id)
contra servidor HTTP efêmero em 127.0.0.1 e validação das regras de segurança/skills em Kotlin.
Escopo honesto: valida o protocolo HTTP/JSON-RPC/SSE e os contratos estáticos do cliente Android,
sem substituir `gradlew testDebugUnitTest` (bloqueado neste sandbox por ausência de JDK 17).
"""
from __future__ import annotations

import json
import re
import threading
import unittest
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MCP_KT = ROOT / "android/app/src/main/kotlin/com/novoagentehb/app/agent/skills/McpJsonRpcClient.kt"


class _MockMcpHandler(BaseHTTPRequestHandler):
    methods_seen: list[tuple[str, str | None]] = []

    def log_message(self, format: str, *args) -> None:  # noqa: A002
        return

    def do_POST(self) -> None:  # noqa: N802
        length = int(self.headers.get("Content-Length", "0"))
        body = self.rfile.read(length).decode("utf-8")
        payload = json.loads(body)
        method = payload.get("method", "")
        sid = self.headers.get("Mcp-Session-Id")
        _MockMcpHandler.methods_seen.append((method, sid))

        if method == "initialize":
            resp = json.dumps({
                "jsonrpc": "2.0",
                "id": payload.get("id"),
                "result": {
                    "protocolVersion": "2024-11-05",
                    "serverInfo": {"name": "mock-mcp-python", "version": "1.0.0"},
                },
            }).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Mcp-Session-Id", "mcp-sess-r11")
            self.send_header("Content-Length", str(len(resp)))
            self.end_headers()
            self.wfile.write(resp)
        elif method == "notifications/initialized":
            self.send_response(202)
            self.end_headers()
        elif method == "tools/list":
            resp = json.dumps({
                "jsonrpc": "2.0",
                "id": payload.get("id"),
                "result": {
                    "tools": [
                        {"name": "echo_safe", "description": "Eco controlado"},
                        {"name": "sse_echo", "description": "Resposta em SSE"},
                    ]
                },
            }).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(resp)))
            self.end_headers()
            self.wfile.write(resp)
        elif method == "tools/call":
            tool_name = payload.get("params", {}).get("name", "")
            if tool_name == "sse_echo":
                msg = json.dumps({
                    "jsonrpc": "2.0",
                    "id": payload.get("id"),
                    "result": {"content": [{"type": "text", "text": "sse:ok"}]},
                })
                sse = f"event: message\ndata: {msg}\n\n".encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Content-Length", str(len(sse)))
                self.end_headers()
                self.wfile.write(sse)
            elif tool_name == "fail_tool":
                self.send_response(500)
                self.end_headers()
            else:
                resp = json.dumps({
                    "jsonrpc": "2.0",
                    "id": payload.get("id"),
                    "result": {"content": [{"type": "text", "text": "json:ok"}]},
                }).encode("utf-8")
                self.send_response(200)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(resp)))
                self.end_headers()
                self.wfile.write(resp)
        else:
            self.send_response(400)
            self.end_headers()


def extract_sse_data(body: str) -> str:
    events: list[str] = []
    cur: list[str] = []
    for raw in body.splitlines():
        line = raw.strip()
        if not line:
            if cur:
                events.append("\n".join(cur))
                cur.clear()
            continue
        if line.startswith("data:"):
            part = line[len("data:"):].strip()
            if part and part != "[DONE]":
                cur.append(part)
    if cur:
        events.append("\n".join(cur))
    return events[-1] if events else body.strip()


class McpProtocolAndClientContractTest(unittest.TestCase):
    def test_kotlin_client_implements_streamable_http_and_ssrf_controls(self) -> None:
        src = MCP_KT.read_text(encoding="utf-8")
        for token in (
            "notifications/initialized",
            "Mcp-Session-Id",
            "MCP-Protocol-Version",
            "text/event-stream",
            "followRedirects(false)",
            "allowLocalLan",
            "169.254",
            "negotiationLog",
        ):
            self.assertIn(token, src)

    def test_mock_streamable_http_handshake_sse_and_error_sequence(self) -> None:
        _MockMcpHandler.methods_seen = []
        server = HTTPServer(("127.0.0.1", 0), _MockMcpHandler)
        port = server.server_address[1]
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f"http://127.0.0.1:{port}/mcp"

            def post_rpc(payload: dict, session_id: str | None = None) -> tuple[int, str, str, str | None]:
                headers = {
                    "Content-Type": "application/json",
                    "Accept": "application/json, text/event-stream",
                }
                if session_id:
                    headers["Mcp-Session-Id"] = session_id
                req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST")
                try:
                    with urllib.request.urlopen(req, timeout=5) as resp:
                        return (
                            resp.status,
                            resp.headers.get("Content-Type", ""),
                            resp.read().decode("utf-8"),
                            resp.headers.get("Mcp-Session-Id"),
                        )
                except urllib.error.HTTPError as e:
                    return e.code, "", "", None

            # 1. initialize
            st, ct, body, sid = post_rpc({
                "jsonrpc": "2.0",
                "id": 1,
                "method": "initialize",
                "params": {"protocolVersion": "2024-11-05", "capabilities": {}, "clientInfo": {"name": "NOVO_AGENTE_HB"}},
            })
            self.assertEqual(200, st)
            self.assertEqual("mcp-sess-r11", sid)
            init_json = json.loads(body)
            self.assertEqual("mock-mcp-python", init_json["result"]["serverInfo"]["name"])

            # 2. notifications/initialized
            st_n, _, _, _ = post_rpc({"jsonrpc": "2.0", "method": "notifications/initialized"}, session_id=sid)
            self.assertEqual(202, st_n)

            # 3. tools/list
            st_l, _, body_l, _ = post_rpc({"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": {}}, session_id=sid)
            self.assertEqual(200, st_l)
            tools = json.loads(body_l)["result"]["tools"]
            self.assertEqual(["echo_safe", "sse_echo"], [t["name"] for t in tools])

            # 4. tools/call SSE
            st_s, ct_s, body_s, _ = post_rpc(
                {"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": {"name": "sse_echo", "arguments": {}}},
                session_id=sid,
            )
            self.assertEqual(200, st_s)
            self.assertIn("text/event-stream", ct_s)
            parsed_sse = json.loads(extract_sse_data(body_s))
            self.assertEqual("sse:ok", parsed_sse["result"]["content"][0]["text"])

            # 5. tools/call error 500
            st_err, _, _, _ = post_rpc(
                {"jsonrpc": "2.0", "id": 4, "method": "tools/call", "params": {"name": "fail_tool", "arguments": {}}},
                session_id=sid,
            )
            self.assertEqual(500, st_err)

            self.assertEqual(
                [
                    ("initialize", None),
                    ("notifications/initialized", "mcp-sess-r11"),
                    ("tools/list", "mcp-sess-r11"),
                    ("tools/call", "mcp-sess-r11"),
                    ("tools/call", "mcp-sess-r11"),
                ],
                _MockMcpHandler.methods_seen,
            )
        finally:
            server.shutdown()
            server.server_close()


if __name__ == "__main__":
    unittest.main(verbosity=2)
