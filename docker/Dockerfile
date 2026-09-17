# Build environment for the OpenManet Field Performance Tester Android app - everything needed
# to run `./gradlew assembleDebug`/`testDebugUnitTest` against the repo without installing the
# Android SDK, buf, or protoc on the host. Does NOT bake the repo in: mount it as a volume (see
# scripts/docker_build.sh) so rebuilding the image is only needed when this file changes, not on
# every source edit.
#
# Deliberately does not install the NDK - that's only needed for scripts/build_iperf2.sh and
# build_iperf3.sh (rebuilding the vendored native binaries), not for a normal app build, and it's
# a multi-gigabyte download not worth carrying in this image by default.
FROM eclipse-temurin:21-jdk

SHELL ["/bin/bash", "-o", "pipefail", "-c"]

RUN apt-get update && apt-get install -y --no-install-recommends \
        unzip \
        curl \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# --- Android SDK command-line tools ---
# Pinned to the exact build downloaded and sha256-verified from
# https://developer.android.com/studio#command-line-tools-only at the time this was written.
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=${ANDROID_SDK_ROOT}
ARG CMDLINE_TOOLS_VERSION=15859902
ARG CMDLINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583

RUN curl -fsSL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
    && echo "${CMDLINE_TOOLS_SHA256}  /tmp/cmdline-tools.zip" | sha256sum -c - \
    && mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && unzip -q /tmp/cmdline-tools.zip -d "${ANDROID_SDK_ROOT}/cmdline-tools" \
    && mv "${ANDROID_SDK_ROOT}/cmdline-tools/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

ENV PATH="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${ANDROID_SDK_ROOT}/platform-tools:${PATH}"

# Package list mirrors exactly what this project builds against (see README's Requirements
# section) - keep in sync with app/build.gradle.kts's compileSdk/minSdk/targetSdk if those change.
# (yes || true): under `set -o pipefail`, sdkmanager closing the pipe early after its last
# license prompt sends `yes` a SIGPIPE, which pipefail otherwise treats as this whole command
# failing (exit 141) even though license acceptance itself succeeded - confirmed by hitting
# exactly that spurious failure while building this image.
RUN (yes || true) | sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" --licenses >/dev/null \
    && sdkmanager --sdk_root="${ANDROID_SDK_ROOT}" \
        "platform-tools" \
        "platforms;android-35" \
        "platforms;android-37.0" \
        "platforms;android-37.2" \
        "build-tools;35.0.0" \
        "build-tools;36.0.0" \
        "build-tools;37.0.0" \
    && chmod -R a+rX "${ANDROID_SDK_ROOT}"

# --- protoc + buf ---
# Only needed the first time app/src/main/kotlin-gen doesn't exist yet (see app/build.gradle.kts's
# generateProto task) - which is always true on a fresh clone/mount, since that directory is
# gitignored. Pinned/sha256-verified the same way as the SDK tools above.
ARG PROTOC_VERSION=36.2
ARG PROTOC_SHA256=121f6c7afe1d4d0e3ea6aab9432038599250134cbf4474cb1167d2c7decd4278
ARG BUF_VERSION=1.73.0
ARG BUF_SHA256=8f2986298ad08f0cc1bf999b9797b7c383adf32d7edf0f73d6f1e1a701baeac1

RUN curl -fsSL -o /tmp/protoc.zip \
        "https://github.com/protocolbuffers/protobuf/releases/download/v${PROTOC_VERSION}/protoc-${PROTOC_VERSION}-linux-x86_64.zip" \
    && echo "${PROTOC_SHA256}  /tmp/protoc.zip" | sha256sum -c - \
    && unzip -q /tmp/protoc.zip -d /opt/protoc \
    && ln -s /opt/protoc/bin/protoc /usr/local/bin/protoc \
    && rm /tmp/protoc.zip \
    && curl -fsSL -o /usr/local/bin/buf \
        "https://github.com/bufbuild/buf/releases/download/v${BUF_VERSION}/buf-Linux-x86_64" \
    && echo "${BUF_SHA256}  /usr/local/bin/buf" | sha256sum -c - \
    && chmod +x /usr/local/bin/buf

# Gradle's own wrapper (gradle-wrapper.jar, already committed) downloads the pinned Gradle
# distribution on first run - not baked in here, since that happens automatically and caches
# under GRADLE_USER_HOME (see scripts/docker_build.sh, which points it at a directory inside the
# mounted repo so the download survives across container runs).
WORKDIR /workspace
ENTRYPOINT ["./gradlew"]
CMD ["assembleDebug"]
