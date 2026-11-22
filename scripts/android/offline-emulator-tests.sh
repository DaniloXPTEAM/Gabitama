#!/usr/bin/env bash
set -euo pipefail
# Only CI's disposable emulator: do not run this on a user device. Restrict the
# runner UID while QEMU is running, leaving local adb and local mock servers
# available. Remove the rules even on test failure.
[ "${CI:-}" = true ]
[[ "$(adb get-serialno)" =~ ^emulator-[0-9]+$ ]]
uid=$(id -u)
v4=0
v6=0
cleanup() {
  [ "$v6" = 0 ] || sudo ip6tables -D OUTPUT -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT
  [ "$v4" = 0 ] || sudo iptables -D OUTPUT -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT
}
trap cleanup EXIT
sudo iptables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d 127.0.0.0/8 -j REJECT
v4=1
sudo ip6tables -I OUTPUT 1 -m owner --uid-owner "$uid" ! -d ::1/128 -j REJECT
v6=1
echo '::notice title=Network isolation::External IPv4 and IPv6 blocked for emulator runner UID; loopback mocks remain available.'
scripts/android/emulator-smoke.sh
scripts/android/connected-tests.sh
