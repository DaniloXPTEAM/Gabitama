#!/usr/bin/env bash
# Gate de distribuição (fail-closed). Não publica nada; apenas decide se uma
# distribuição pode prosseguir. Saída 0 = liberado; 1 = bloqueado (com motivos).
#
# Regras (decisão do proprietário, 27/11/2026):
#  - GabiTama é MIT. A presença de LICENSE com texto MIT é obrigatória.
#  - LICENSE_PENDING_OWNER.txt presente = licença não decidida/regularizada: bloqueia.
#  - Componentes GPL/LGPL herdados são listados (informativo). Não são relicenciados
#    aqui; a regularização segue docs/LICENSE_PROVENANCE.md e docs/LICENSE_MATRIX_COMPUTER_RUNTIME.md.
#  - Este script não é chamado pelo CI de validação técnica; os workflows de
#    distribuição permanecem desativados.
set -u

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT" || exit 1

blockers=0
block() { echo "BLOQUEIO: $1"; blockers=$((blockers + 1)); }

if [ ! -f LICENSE ]; then
  block "arquivo LICENSE ausente na raiz."
elif ! grep -q "MIT License" LICENSE; then
  block "LICENSE existe, mas não contém o texto 'MIT License'."
fi

if [ -e LICENSE_PENDING_OWNER.txt ]; then
  block "LICENSE_PENDING_OWNER.txt presente: licença definitiva pendente de decisão do proprietário."
fi

echo "Componentes copyleft herdados (informativo, exigem regularização antes de distribuir):"
for p in \
  android/app/src/main/cpp/pty_bridge.c \
  android/app/src/main/kotlin/com/openminis/app/sandbox/PtyBridge.kt \
  third_party/tama-runtime/android/deps/proot \
  third_party/tama-runtime/android/deps/talloc \
  android/app/src/main/assets/licenses/tama-runtime; do
  if [ -e "$p" ]; then echo "  - $p"; else echo "  - $p (AUSENTE)"; fi
done

if [ "$blockers" -gt 0 ]; then
  echo "RESULTADO: DISTRIBUIÇÃO BLOQUEADA ($blockers motivo(s))."
  exit 1
fi
echo "RESULTADO: liberado pelo gate de licença."
exit 0
