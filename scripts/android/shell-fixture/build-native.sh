#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_HOME:?}"
root="$PWD"
ndk="$ANDROID_HOME/ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin"
src="$root/third_party/tama-runtime/android/deps/proot/src"
talloc="$root/third_party/tama-runtime/android/deps/talloc"
build="$RUNNER_TEMP/tama-x86-native"
mkdir -p "$build" android/app/src/main/jniLibs/x86_64
cc="$ndk/x86_64-linux-android26-clang"
"$cc" -c "$talloc/talloc.c" -o "$build/talloc.o" -I"$talloc" -fPIC -O2 -Wall -std=gnu99 -DHAVE_STDARG_H=1 -DHAVE_VA_COPY=1 -DHAVE_UNISTD_H=1 -DHAVE_INTPTR_T=1
"$ndk/llvm-ar" rcs "$build/libtalloc.a" "$build/talloc.o"
(
  cd "$src"
  export GIT_CEILING_DIRECTORIES="$root/third_party/tama-runtime"
  make CC="$cc" STRIP="$ndk/llvm-strip" OBJCOPY="$ndk/llvm-objcopy" OBJDUMP="$ndk/llvm-objdump" \
    CPPFLAGS="-D_FILE_OFFSET_BITS=64 -D_GNU_SOURCE -I. -DARG_MAX=131072 -I$talloc" \
    CFLAGS='-O2 -Wall -Wextra -fPIE' LDFLAGS="-Wl,-z,noexecstack -pie -L$build -ltalloc" -j2
  install -m 0755 proot "$root/android/app/src/main/jniLibs/x86_64/libproot.so"
  install -m 0755 loader/loader "$root/android/app/src/main/jniLibs/x86_64/libproot-loader.so"
  install -m 0755 loader/loader-m32 "$root/android/app/src/main/jniLibs/x86_64/libproot-loader32.so"
)
"$ndk/llvm-readelf" -h android/app/src/main/jniLibs/x86_64/libproot.so | grep 'Advanced Micro Devices X86-64'
