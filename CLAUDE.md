# CLAUDE.md

Guidance for working on this repo in Claude Code. Read this before making build-system or
connectivity/auth changes - several things here are non-obvious and re-discovering them costs
real time.

## Build system gotchas

- **AGP 9's built-in Kotlin compilation is incompatible with KSP** (used by Room and Hilt) as of
  this AGP/KSP release pair. `gradle.properties` sets `android.builtInKotlin=false` and
  `android.newDsl=false` to fall back to the classic Kotlin Gradle plugin + KSP path. Both flags
  are required together - either alone still routes through the new (KSP-incompatible) path, and
  AGP/Kotlin both flag the combination as deprecated ("removed in AGP 10.0"). Revisit this when
  KSP adds AGP 9 built-in-Kotlin support.
- **Room schema changes require a version bump**, not just changing the entity/DAO. This project
  uses `fallbackToDestructiveMigration(true)` (see `di/DatabaseModule.kt`) instead of hand-written
  `Migration`s since the schema is still pre-1.0, but that fallback only triggers on a *version*
  change. Edit an entity without bumping `version` in `data/ManetDatabase.kt` and the app crashes
  on first launch after update with `IllegalStateException: Room cannot verify the data
  integrity... identity hash mismatch` - confirmed the hard way on a real device mid-session.
- **Connect-RPC codegen uses local plugins, not `buf.build`'s remote codegen** -
  `buf.build`'s remote plugin service rate-limits aggressively on repeated `buf generate` calls
  (hit this during initial setup). `scripts/generate_proto.sh` uses `protoc`'s built-in
  java/kotlin generators plus a vendored `.tools/protoc-gen-connect-kotlin.jar`, entirely local.
  Regenerate with that script, not a hand-rolled `buf generate` invocation.
- **`buf/validate/validate.proto`'s `java_package` must be excluded from managed mode** - it
  declares its own `option java_package = "build.buf.validate"`; buf's managed-mode "com." prefix
  rewrite clobbers that unless explicitly disabled in `buf.gen.yaml`'s `managed.disable` list.
  Without this, every generated file that references a `buf.validate` field option
  (`openmanet/service/v1/node.proto`, `station.proto`, `interface.proto`, ...) fails to compile
  with `package com.buf.validate does not exist`.
- **Only vendor the `.proto` files/services actually used.** `scripts/generate_proto.sh`'s
  `--path` list is deliberately narrow (NodeService, MeshNeighborService, StatusService,
  InterfaceService, StationService, GNSSService + their message dependencies), not the full
  `openmanet` API surface (comms, blos, setup, sysupgrade, wifi_config, etc.) - buf's `--path`
  does not automatically pull in a referenced message's own file, so a new field added from
  another package needs its file added to that list too (see the script's comments for the exact
  failure mode this avoids).

## Auth - do not assume it's absent

The OpenMANET docs site (as fetched during initial research) describes `openmanetd`'s protobuf
API as having no documented authentication. **This is wrong for real deployed nodes.** Confirmed
against real hardware: the API returns `401 {"error":"unauthorized"}` without a valid session.

The real mechanism (confirmed via the public source at `github.com/OpenMANET/openmanetd`,
`internal/auth/`):

- `POST http://<node-ip>:8087/auth/login` with `{"username","password"}` JSON body, PAM-backed
  (i.e. the same credentials as SSH/LuCI admin login on the device, not a separate app credential).
- Response: `{"username","token"}` (64-char hex) plus a `session` cookie.
- Every other API call needs `Authorization: Bearer <token>`, **except**
  `DashboardService.GetDashboardStatus` and `SetupService`'s two wizard endpoints, which are
  explicitly exempted (see `middleware.go`'s `isAPISkipPath`).

This app implements it as: `rpc/AuthRepository` (login/logout, plain REST - not Connect-RPC),
`rpc/SessionTokenHolder` (in-memory token for the current connection), `rpc/AuthHeaderInterceptor`
(OkHttp interceptor that attaches the header automatically, wired into
`rpc/OpenManetClientFactory`). If you add a new RPC call site, you don't need to do anything extra
for auth - the interceptor covers it. If you add a call to `/auth/*` or a `SetupService` wizard
endpoint, remember those are unauthenticated by design on the server side.

## The app does not manage Wi-Fi

Earlier iterations of this app used `WifiNetworkSpecifier` to join the mesh SSID programmatically.
That was removed - the user joins the mesh Wi-Fi themselves via Android system settings before
opening the app. Do not reintroduce Wi-Fi-join code without an explicit product decision to do so;
it was a real source of crashes (`ConnectivityManager.unregisterNetworkCallback` throwing
`IllegalArgumentException` when the OS had already dropped a pending request out from under the
app - confirmed on a MIUI device).

Consequence: "the mesh network" throughout the codebase (`rpc/OpenManetClientFactory`,
`ping/PingRunner`, `cot/CotMulticastListener`, `iperf/IperfProcessRunner`) means
`ConnectivityManager.activeNetwork` - whatever network is currently active - not a
self-managed `Network` handle. `connectivity/DefaultGatewayResolver` reads the active network's
default route to prefill the node-address field, since on OpenManet the gateway is normally the
node itself.

## Data model conventions

- Every time-series entity carries `sessionId` + `timestampMs`, indexed together, and belongs to
  a `TestSession` row. `session/TestSessionService` (foreground service) is the only thing that
  should start continuous collection (ping/GPS/CoT); iperf3 runs are discrete, user-triggered, but
  still get tagged with whatever session is currently active.
- Room entities that wrap a `.proto`-derived RPC message (e.g. `NeighborSnapshot`) keep a
  `rawJson`/raw-field fallback column alongside typed fields, as a hedge against the vendored
  proto schema changing without every mapping site being updated in lockstep.
- Real credentials never go in plain Room/DataStore. `NodeProfile` (the "previously used node
  addresses" history, plain Room) stores the last-used *username* for convenience prefill, but
  the password lives in `auth/NodeCredentialStore` - `EncryptedSharedPreferences`, AES-256 keys
  held in the Android Keystore - keyed by node IP. This matters because the manifest has
  `android:allowBackup="true"`; an unencrypted store would put a real device login password on
  Android's auto-backup. Keystore keys don't survive a restore to a different device, so a
  backed-up copy of the encrypted file is just ciphertext there, not a leak.

## Native/vendored artifacts

- `app/src/main/jniLibs/<abi>/libiperf3exec.so` are real, statically-linked `iperf3` binaries
  (from `github.com/esnet/iperf`, OpenSSL-less build), named like a shared library on purpose:
  Android 10+'s W^X restrictions exempt `applicationInfo.nativeLibraryDir` from the
  can't-execute-app-writable-files rule, and that's the one location the APK installer extracts
  files matching `lib*.so` into automatically. `iperf/IperfProcessRunner` invokes it via
  `ProcessBuilder` from that path - don't try to run it any other way.
- `.tools/protoc-gen-connect-kotlin.jar` and `gradle/wrapper/gradle-wrapper.jar` are committed
  binaries the build depends on; don't gitignore them.

## Verification

Most of this app's correctness (real mesh connectivity, GPS fixes, ping routing, iperf3 against a
real server, CoT multicast) genuinely requires a physical device on real OpenManet hardware - unit
tests cover the pure logic (output parsers, CSV formatting, the connection state machine) but
can't substitute for on-device testing. When diagnosing a device-only bug, `adb logcat` plus
`adb shell curl ...` (curl and nc are both present in `/system/bin` on stock Android/AOSP images)
are the fastest way to distinguish "the app is wrong" from "the node is doing something the docs
didn't mention" - see the auth section above for an example where that distinction mattered.
