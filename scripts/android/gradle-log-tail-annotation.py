#!/usr/bin/env python3
"""Publica a cauda do log do Gradle como UMA anotação de erro (legível sem o log bruto do job).

Uso: python3 scripts/android/gradle-log-tail-annotation.py <arquivo.log>
Não altera nada; apenas imprime ::error para o runner do GitHub Actions.
"""
import sys

path = sys.argv[1] if len(sys.argv) > 1 else "/tmp/gabitama-gradle.log"
try:
    lines = open(path, encoding="utf-8", errors="replace").read().splitlines()
except OSError as exc:
    print(f"::error title=Gradle log tail::log indisponível: {exc.__class__.__name__}")
    sys.exit(0)

message = "\n".join(lines[-80:])[-4000:]
encoded = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
print("::error title=Gradle log tail::" + encoded)
