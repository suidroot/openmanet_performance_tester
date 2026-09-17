#!/bin/sh
# Builds the app inside Docker, so nobody needs the Android SDK/buf/protoc installed on the
# host - see docker/Dockerfile for what's in the image. Builds (or reuses a cached) image, then
# runs the repo's own ./gradlew inside a container with the repo mounted at /workspace.
#
# Usage: scripts/docker_build.sh [gradle args...]
#   scripts/docker_build.sh                    # assembleDebug (the image's default CMD)
#   scripts/docker_build.sh testDebugUnitTest
#   scripts/docker_build.sh assembleDebug testDebugUnitTest
#
# Gradle/SDK downloads land in .gradle-docker/ inside the repo (gitignored) so they survive
# across runs without a separate named Docker volume; the container runs as the host's own
# uid:gid so those files - and app/build/'s output - stay owned by you, not root.
set -eu
cd "$(dirname "$0")/.."

image_tag="manet-perf-app-build"
# Forced to amd64 - Android's aapt2 native binary (pulled in transitively by AGP) isn't reliably
# published for linux/arm64, and letting the base image resolve to arm64 on Apple Silicon while
# aapt2 resolves to amd64 produces a mixed-arch container where aapt2 fails outright ("rosetta
# error: failed to open elf at /lib64/ld-linux-x86-64.so.2", confirmed hitting this directly).
# Forcing the whole image to amd64 makes Docker Desktop emulate it consistently instead - slower
# on Apple Silicon, but correct there and native-speed on an actual x86_64 host/CI runner.
platform="linux/amd64"

echo "Building Docker image ($image_tag)..."
docker build --platform "$platform" -t "$image_tag" -f docker/Dockerfile docker/

mkdir -p .gradle-docker

if [ "$#" -eq 0 ]; then
  set -- assembleDebug
fi

echo "Running: ./gradlew $*"
docker run --rm \
  --platform "$platform" \
  -v "$(pwd)":/workspace \
  -w /workspace \
  -u "$(id -u):$(id -g)" \
  -e GRADLE_USER_HOME=/workspace/.gradle-docker \
  -e HOME=/workspace/.gradle-docker \
  "$image_tag" "$@"
