#!/bin/sh
# Cross-compiles iperf3 for Android and vendors the stripped executables into
# app/src/main/jniLibs/<abi>/libiperf3exec.so.
#
# Named "lib*.so" (even though it's a plain executable, not a shared library) because that's
# the naming Android's APK packaging extracts into the app's native library directory - the one
# app-private location exempt from Android 10+'s W^X restrictions on executing app-writable
# files. IperfProcessRunner invokes it from applicationInfo.nativeLibraryDir via ProcessBuilder.
#
# Built once and the binaries are committed to the repo (see plan Phase 3) - this script only
# needs to be re-run when bumping the iperf3 version or adding an ABI, not on every build.
#
# Requires: the Android NDK (side-by-side) installed via sdkmanager, e.g.:
#   sdkmanager "ndk;29.0.14206865"
set -eu
cd "$(dirname "$0")/.."

IPERF_VERSION="3.21"
IPERF_SHA256_URL="https://github.com/esnet/iperf/releases/download/${IPERF_VERSION}/iperf-${IPERF_VERSION}.tar.gz.sha256"
IPERF_TARBALL_URL="https://github.com/esnet/iperf/releases/download/${IPERF_VERSION}/iperf-${IPERF_VERSION}.tar.gz"

NDK="${ANDROID_NDK_HOME:-/opt/homebrew/share/android-commandlinetools/ndk/29.0.14206865}"
TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/darwin-x86_64"
API=29 # matches app/build.gradle.kts minSdk

BUILD_ROOT=".native-build"
SRC_DIR="$BUILD_ROOT/iperf-${IPERF_VERSION}"
JNI_LIBS_DIR="app/src/main/jniLibs"

mkdir -p "$BUILD_ROOT"
if [ ! -d "$SRC_DIR" ]; then
  curl -fsSL -o "$BUILD_ROOT/iperf-${IPERF_VERSION}.tar.gz" "$IPERF_TARBALL_URL"
  curl -fsSL -o "$BUILD_ROOT/iperf-${IPERF_VERSION}.tar.gz.sha256" "$IPERF_SHA256_URL"
  (cd "$BUILD_ROOT" && shasum -a 256 -c "iperf-${IPERF_VERSION}.tar.gz.sha256")
  tar xzf "$BUILD_ROOT/iperf-${IPERF_VERSION}.tar.gz" -C "$BUILD_ROOT"
fi

build_abi() {
  abi="$1"
  target="$2"       # NDK clang target triple prefix, e.g. aarch64-linux-android
  jni_abi="$3"       # Android ABI directory name, e.g. arm64-v8a

  work_dir="$BUILD_ROOT/build-$abi"
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

    ./configure --host="$target" --without-openssl --enable-static --disable-shared
    make -j"$(sysctl -n hw.ncpu 2>/dev/null || nproc)"
    "$STRIP" src/iperf3
  )

  mkdir -p "$JNI_LIBS_DIR/$jni_abi"
  cp "$work_dir/src/iperf3" "$JNI_LIBS_DIR/$jni_abi/libiperf3exec.so"
  echo "Built $jni_abi -> $JNI_LIBS_DIR/$jni_abi/libiperf3exec.so ($(du -h "$JNI_LIBS_DIR/$jni_abi/libiperf3exec.so" | cut -f1))"
}

build_abi "arm64-v8a" "aarch64-linux-android" "arm64-v8a"
build_abi "armeabi-v7a" "armv7a-linux-androideabi" "armeabi-v7a"
build_abi "x86_64" "x86_64-linux-android" "x86_64"

echo "iperf3 ${IPERF_VERSION} vendored for all ABIs."
