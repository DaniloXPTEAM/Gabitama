#!/usr/bin/env bash
set -euo pipefail
[ "${CI:-}" = true ]
: "${ANDROID_HOME:?}" "${RUNNER_TEMP:?}"
root="$PWD"
work="$RUNNER_TEMP/tama-arm64-original"
cp -a third_party/tama-runtime/android "$work"
(
 cd "$work"
 export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
 export GIT_CEILING_DIRECTORIES="$RUNNER_TEMP"
 bash deps/build_proot.sh
)
mkdir -p android/app/src/main/jniLibs/arm64-v8a android/app/src/main/assets
cp "$work"/src/android/app/src/main/jniLibs/arm64-v8a/libproot*.so android/app/src/main/jniLibs/arm64-v8a/
name=alpine-minirootfs-3.21.3-aarch64.tar.gz
curl -fsSL --retry 3 "https://dl-cdn.alpinelinux.org/alpine/v3.21/releases/aarch64/$name" -o "$RUNNER_TEMP/$name"
echo "ead8a4b37867bd19e7417dd078748e2312c0aea364403d96758d63ea8ff261ea  $RUNNER_TEMP/$name" | sha256sum -c -
cp "$RUNNER_TEMP/$name" android/app/src/main/assets/alpine-minirootfs.tar.gz
cp scripts/android/arm64-shell-fixture/Arm64OriginalShellAndroidTest.kt android/app/src/androidTest/java/com/novoagentehb/app/
sed -i 's/<application$/<application android:extractNativeLibs="true"/' android/app/src/main/AndroidManifest.xml
"$ANDROID_HOME/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf" -h android/app/src/main/jniLibs/arm64-v8a/libproot.so | grep 'AArch64'
echo '::notice title=ARM64 fixture::Original PRoot and pinned Alpine prepared for compilation only; no device execution or distribution.'
