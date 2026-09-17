#!/bin/sh
# Cross-compiles iperf2 (the classic, separately-maintained SourceForge project - NOT the same
# codebase as iperf3, and not wire-compatible with it) for Android and vendors the stripped
# executables into app/src/main/jniLibs/<abi>/libiperf2exec.so.
#
# Needed because real OpenManet nodes run `iperf` (2.x), not `iperf3` - confirmed against a real
# node ("iperf version 2.1.9"). iperf3 and iperf2 speak different, incompatible control
# protocols, so the app vendors both clients and picks one per test (see IperfConfig.engine).
#
# Naming/packaging notes mirror build_iperf3.sh exactly - see that script's header for why
# "lib*.so", why jniLibs, and why packaging.jniLibs.useLegacyPackaging=true is required in
# app/build.gradle.kts for either binary to actually be extracted to disk at install time.
#
# Requires: the Android NDK (side-by-side) installed via sdkmanager, e.g.:
#   sdkmanager "ndk;29.0.14206865"
set -eu
cd "$(dirname "$0")/.."

IPERF_VERSION="2.2.1"
IPERF_TARBALL_URL="https://downloads.sourceforge.net/project/iperf2/iperf-${IPERF_VERSION}.tar.gz"
# Verified once against the tarball above (also matches Homebrew's iperf formula, which vendors
# the same SourceForge release) - SourceForge doesn't publish a .sha256 sidecar the way GitHub
# releases do, so this is pinned directly rather than fetched.
IPERF_SHA256="754ab0a7e28033dbea81308ef424bc7df4d6e2fe31b60cc536b61b51fefbd8fb"

NDK="${ANDROID_NDK_HOME:-/opt/homebrew/share/android-commandlinetools/ndk/29.0.14206865}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/darwin-x86_64"
API=29 # matches app/build.gradle.kts minSdk

BUILD_ROOT=".native-build"
SRC_DIR="$BUILD_ROOT/iperf-${IPERF_VERSION}"
JNI_LIBS_DIR="app/src/main/jniLibs"

mkdir -p "$BUILD_ROOT"
if [ ! -d "$SRC_DIR" ]; then
  curl -fsSL -o "$BUILD_ROOT/iperf2-${IPERF_VERSION}.tar.gz" "$IPERF_TARBALL_URL"
  echo "${IPERF_SHA256}  $BUILD_ROOT/iperf2-${IPERF_VERSION}.tar.gz" | shasum -a 256 -c -
  tar xzf "$BUILD_ROOT/iperf2-${IPERF_VERSION}.tar.gz" -C "$BUILD_ROOT"
fi

build_abi() {
  abi="$1"
  target="$2"       # NDK clang target triple prefix, e.g. aarch64-linux-android
  jni_abi="$3"       # Android ABI directory name, e.g. arm64-v8a

  work_dir="$BUILD_ROOT/build2-$abi"
  rm -rf "$work_dir"
  cp -R "$SRC_DIR" "$work_dir"

  (
    cd "$work_dir"
    export CC="$TOOLCHAIN/bin/${target}${API}-clang"
    export CXX="$TOOLCHAIN/bin/${target}${API}-clang++"
    export AR="$TOOLCHAIN/bin/llvm-ar"
    export RANLIB="$TOOLCHAIN/bin/llvm-ranlib"
    export STRIP="$TOOLCHAIN/bin/llvm-strip"
    export PATH="$TOOLCHAIN/bin:$PATH"

    ./configure --host="$target" --disable-dependency-tracking --enable-static --disable-shared
    # compat/Makefile.am hardcodes "AR = ar" (AM_PROG_AR was never invoked), so the exported AR
    # env var above is silently ignored there - macOS's system ar can't index the resulting ELF
    # .o files (ranlib: "not a mach-o file"), producing an empty libcompat.a and undefined-symbol
    # link errors for every thread/compat function. Passing AR/RANLIB as make *arguments* (not
    # just env) overrides the Makefile's hardcoded default, since Make variable-assignment
    # precedence beats inherited environment.
    make -j"$(sysctl -n hw.ncpu 2>/dev/null || nproc)" AR="$AR" RANLIB="$RANLIB"
    "$STRIP" src/iperf
  )

  mkdir -p "$JNI_LIBS_DIR/$jni_abi"
  cp "$work_dir/src/iperf" "$JNI_LIBS_DIR/$jni_abi/libiperf2exec.so"
  echo "Built $jni_abi -> $JNI_LIBS_DIR/$jni_abi/libiperf2exec.so ($(du -h "$JNI_LIBS_DIR/$jni_abi/libiperf2exec.so" | cut -f1))"
}

build_abi "arm64-v8a" "aarch64-linux-android" "arm64-v8a"
build_abi "armeabi-v7a" "armv7a-linux-androideabi" "armeabi-v7a"
build_abi "x86_64" "x86_64-linux-android" "x86_64"

echo "iperf2 ${IPERF_VERSION} vendored for all ABIs."
