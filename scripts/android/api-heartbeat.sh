#!/usr/bin/env bash
# Sanitized runner heartbeat for the Android API jobs. Records only: UTC time, the current phase marker,
# host and device MemAvailable (kB), host 1-minute load, adb reachability, device boot flag and whether the
# instrumentation process exists. Never records serials, package data, test output, keys or user text.
# Output: /tmp/gabitama-api-heartbeat.log (CI private artifact, short retention).
set -uo pipefail
LOG=/tmp/gabitama-api-heartbeat.log
PID_FILE=/tmp/gabitama-api-heartbeat.pid
PHASE_FILE=/tmp/gabitama-api-phase.txt
INSTR_PACKAGE=com.novoagentehb.app.debug.test

case "${1:-}" in
  start)
    : > "$LOG"
    echo "start" > "$PHASE_FILE"
    printf '%s event=heartbeat-start\n' "$(date -u +%H:%M:%S)" >> "$LOG"
    setsid bash "$0" loop >/dev/null 2>&1 < /dev/null &
    echo $! > "$PID_FILE"
    ;;
  phase)
    echo "${2:-unknown}" > "$PHASE_FILE"
    printf '%s event=phase phase=%s\n' "$(date -u +%H:%M:%S)" "${2:-unknown}" >> "$LOG"
    ;;
  stop)
    if [ -f "$PID_FILE" ]; then kill "$(cat "$PID_FILE")" 2>/dev/null || true; fi
    printf '%s event=heartbeat-stop\n' "$(date -u +%H:%M:%S)" >> "$LOG"
    ;;
  loop)
    while :; do
      host_mem=$(awk '/^MemAvailable:/ {print $2}' /proc/meminfo 2>/dev/null)
      load=$(cut -d' ' -f1 /proc/loadavg 2>/dev/null)
      phase=$(cat "$PHASE_FILE" 2>/dev/null || echo none)
      adb_state=absent-binary
      dev_boot=-
      dev_mem=-
      instr=-
      if command -v adb >/dev/null 2>&1; then
        adb_state=$(timeout 10 adb get-state 2>/dev/null || echo unreachable)
        if [ "$adb_state" = device ]; then
          dev_boot=$(timeout 10 adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || echo unknown)
          dev_mem=$(timeout 10 adb shell 'grep MemAvailable /proc/meminfo' 2>/dev/null | tr -cd '0-9' || true)
          pids=$(timeout 10 adb shell pidof "$INSTR_PACKAGE" 2>/dev/null | tr -cd '0-9' || true)
          if [ -n "$pids" ]; then instr=running; else instr=absent; fi
        fi
      fi
      printf '%s phase=%s host_memavail_kb=%s load1=%s adb=%s dev_boot=%s dev_memavail_kb=%s instrumentation=%s\n' \
        "$(date -u +%H:%M:%S)" "$phase" "${host_mem:-?}" "${load:-?}" "$adb_state" "${dev_boot:-?}" "${dev_mem:--}" "$instr" >> "$LOG"
      sleep 60
    done
    ;;
  *)
    echo "usage: api-heartbeat.sh start|phase <name>|stop|loop" >&2
    exit 2
    ;;
esac
exit 0
