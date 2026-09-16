# OpenManet Field Performance Tester

An Android app that runs on an End User Device (EUD) to test performance across an
[OpenManet](https://openmanet.github.io/docs/) mesh network: connect to a node, discover the
mesh, ping targets, log GPS (device + Cursor-on-Target multicast), run iperf3 throughput tests,
store everything locally as time series, and export/upload the results as CSV.

The app does **not** manage Wi-Fi. You join the mesh SSID yourself via Android's system Wi-Fi
settings before opening the app; the app only talks to the node once you're already on its
network.

## Screenshot

<img src="docs/screenshots/dashboard.png" alt="Dashboard showing mesh peers, link quality, and a per-node card with live stats and ping result" width="360">

The dashboard's mesh-peers section: an aggregate summary card, then one card per discovered node
with its live API stats (hops, signal, throughput, interface) and its most recent ping result. A
switch on each card includes/excludes that node from the current test session.

## Requirements

- Android Studio (or the command-line tools below) with:
  - Android SDK platform 37+, build-tools 35/37
  - NDK 29 (side-by-side), only needed if rebuilding the vendored iperf3 binaries
- [`buf`](https://buf.build/docs/installation/) and `protoc`, only needed if regenerating the
  Connect-RPC client from the vendored `.proto` files
- A physical Android device (minSdk 29) or emulator to actually exercise mesh connectivity,
  GPS, ping, and iperf3 - none of that is meaningfully testable in a JVM unit test

## Building

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Unit tests (parsers, Room DAOs, the connection state machine) run without a device:

```sh
./gradlew testDebugUnitTest
```

## Project layout

Single-module Compose app, packages organized by feature:

```
app/src/main/java/net/openmanet/perfapp/
  connectivity/   node-address entry, default-gateway detection (no Wi-Fi management)
  auth/           encrypted per-node credential storage (EncryptedSharedPreferences)
  rpc/            Connect-RPC client to openmanetd, one repository per service, real auth,
                  node-identity dedup/hostname cleanup (Hostnames.kt)
  ping/           ICMP ping via /system/bin/ping subprocess (no root required)
  cot/            Cursor-on-Target UDP multicast listener (239.2.3.1:6969)
  gps/            device GPS (FusedLocationProviderClient) + CoT fix merging
  iperf/          iperf3 subprocess bridge (vendored native binaries) + output parser
  session/        foreground service coordinating ping/GPS/CoT for a test run
  data/           Room entities/DAOs/database, CSV export, upload
  settings/       app settings persisted via DataStore (refresh interval, disabled nodes)
  ui/             one package per screen, plus nav/ for the NavHost and shared ViewModels
```

`app/src/main/proto/` holds the vendored `openmanetd` `.proto` files (pulled from
`buf.build/openmanet/protobufs`). `app/src/main/kotlin-gen/` (gitignored) holds the generated
Connect-RPC Kotlin/Java stubs; regenerate with:

```sh
./scripts/generate_proto.sh
```

`app/src/main/jniLibs/<abi>/libiperf3exec.so` are real `iperf3` binaries cross-compiled for
Android (see `scripts/build_iperf3.sh` - only needs to be re-run when bumping the iperf3 version
or adding an ABI, not on every build).

See `CLAUDE.md` for the non-obvious decisions and gotchas behind this setup.
