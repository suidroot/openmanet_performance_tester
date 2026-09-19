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

## Other RPC field quirks confirmed on real hardware

Like the auth situation above, a couple of proto-documented fields don't match what real nodes
actually send - found by pulling the on-device Room DB (`adb exec-out run-as
net.openmanet.perfapp cat databases/manet_perf.db`) and inspecting raw values/output rather than
guessing from the docs:

- **`NodeService.ListNodes`' `Node.hostname` carries a `"_<iface>"` suffix and one entry per
  active interface on multi-homed nodes** (e.g. `man-gate_wlan0` and `man-gate_vxlan0` for one
  physical node) - unlike `MeshTopologyService.GetMeshTopology`'s `MeshNode.hostname`, which the
  proto explicitly documents as already deduplicated to one entry per physical node with the
  suffix stripped. `rpc/NodeRepository.listNodes()` uses the topology call as ground truth to
  collapse `ListNodes`' raw entries back to one per node and clean up the hostname; `rpc/
  Hostnames.kt`'s `String.baseHostname()` is the fallback stripper (interface-name regex) used
  wherever the topology call isn't available or as a second pass on other hostname-shaped fields
  (`MeshNeighbor.neighbor`, "hostname.iface"). Without this, both the mesh-peer count and every
  displayed node name were wrong.
- **`MeshNeighborService`'s `MeshNeighbor.throughput` is documented as already-scaled bit/s
  ("kbit/s scaled up") but is actually still kbit/s on real hardware** - a 400+ Mbps link was
  displaying as "400 Kbps". `rpc/NeighborRepository` scales it ×1,000 when mapping to
  `MeshNeighbor.throughputBps`, so it holds real bit/s and every consumer (dashboard, ping's
  expected-throughput hint, CSV export) can trust the field name.

## Ping must bind by local IP, not interface name

`ping/PingRunner` shells out to `/system/bin/ping`. Its first implementation passed `-I <iface>`
(e.g. `-I wlan0`) to force egress onto the mesh network even if cellular is also active - this
looked reasonable and compiled fine, but **every ping silently failed** with `ping:
SO_BINDTODEVICE: Operation not permitted`, recorded as an indistinguishable "timeout" in
`ping_result.rawOutputLine`. `SO_BINDTODEVICE` needs a privileged capability a normal app UID
doesn't have; `/system/bin/ping` invoked via `ProcessBuilder` runs as the app's own UID, not root.
The fix: pass the network's own local IPv4 address instead (`-I 10.41.0.123`, from
`LinkProperties.linkAddresses`) - ping's `-I` accepts either an interface name *or* a source
address, and a source-address `bind()` is unprivileged. Confirmed directly against the app's own
UID with `adb shell run-as net.openmanet.perfapp /system/bin/ping ...` before and after. If ping
ever silently "times out" against a host another tool can reach, check
`ping_result.rawOutputLine` (pull the DB the same way) before assuming it's a routing problem.

## Session target selection

`ui/session/SessionViewModel` builds each session's ping-target list from
`NodeRepository.listNodes()` (deduplicated, clean hostnames - see above), filtered against
`settings/DisabledNodesRepository` (DataStore, keyed by base hostname) - the dashboard's per-node
"Include in test session" switch writes there. If a session is already running when a node's
switch flips, `SessionViewModel.setNodeDisabled` stops and immediately restarts the
`TestSessionService` with the recomputed target list (a new `sessionId`) rather than trying to
cancel one target's ping job in place - simpler, and consistent with `TestSessionService` already
only supporting one session at a time.

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
  starts *persisted* continuous collection (ping/GPS/CoT rows written to Room); iperf3 runs are
  discrete, user-triggered, but still get tagged with whatever session is currently active.
  GPS is the one exception to "collection only happens during a session": position must be
  visible on the dashboard at all times, not just while logging is on (confirmed as a real bug -
  the dashboard's GPS card went blank the moment logging stopped, even though ATAK on the same
  device kept receiving the mesh's position broadcasts the whole time). `gps/GpsRepository` has a
  second, non-persisting set of collectors (`collectLiveDeviceFixes`/`collectLiveMeshFixes`)
  that `ui/dashboard/DashboardViewModel` starts in its own `viewModelScope` whenever the dashboard
  is visible, writing only to `gps/LiveGpsHolder` (never Room) - independent of whether
  `TestSessionService` is also running its session-persisting collectors. Both sets can run
  concurrently (dashboard open + logging on) without double-counting Room rows, at the cost of
  briefly joining the mesh multicast group twice - a real but minor duplication, preferred over
  gating live display on logging being on.
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
can't substitute for on-device testing. When diagnosing a device-only bug:

- `adb logcat` plus `adb shell curl ...` (curl and nc are both present in `/system/bin` on stock
  Android/AOSP images) are the fastest way to distinguish "the app is wrong" from "the node is
  doing something the docs didn't mention" - see the auth section above for an example.
- For a bug in stored data (e.g. "ping shows timeout but it should work"), pull the app's Room DB
  and read the raw values/output directly rather than guessing:
  `adb exec-out run-as net.openmanet.perfapp cat databases/manet_perf.db > local.db`, then query
  it with a local `sqlite3` (the on-device shell doesn't have one). This is what found the ping
  `SO_BINDTODEVICE` bug above - `rawOutputLine` had the real error, and "timeout" alone wouldn't
  have.

## Session viewer (`viewer/`)

A standalone Python tool, unrelated to the Gradle build: `viewer/app.py` (Flask + sqlite3) ingests
the app's exported session CSVs and `viewer/static/index.html` (a single file, Leaflet from a CDN,
no build step) draws them on a map. Run it with the venv described in `viewer/README.md`.

- **CSV columns are matched by header name** (case-insensitive) via the `COLUMNS` map in
  `app.py`. Note the export's longitude header is literally `Log`, not `Lon` - both are accepted.
  If the Android export's columns change, update that map rather than positional parsing.
- **Empty `Ping delay` means a timeout**, stored as NULL and rendered as "timeout"/grey - don't
  coerce it to 0.
- **Dedup is by SHA-256 of the file bytes**, not filename: re-uploading identical content is a
  no-op, same name with different content gets a `(2)` suffix.
- **Color ranges are hard-coded** in the `METRICS` table in `index.html` (tuned to a single
  sample session); adjust there if real data falls outside them.
- **Schema changes**: the tables are created with `CREATE TABLE IF NOT EXISTS`, so editing a
  column on an existing `sessions.db` is not picked up - delete the DB (it's just a cache of the
  CSVs) or add a migration.
- **Also the upload receiver for the app's Export screen** (replaced `scripts/upload_test_server.py`).
  `data/export/UploadService.upload()` sends a raw HTTP `PUT` with a `text/csv` body - not
  multipart, no auth header - so `app.py`'s catch-all `PUT`/`POST /<path:name>` route reads the raw
  body and feeds it through the same `ingest()` as the browser upload; the last URL segment becomes
  the session name. Keep that route method-tolerant (some presigned-URL-style endpoints want POST)
  and don't require multipart or auth on it, or the phone's upload will break.
- Binds to `127.0.0.1` by default; `--lan` (0.0.0.0) is needed for the phone to reach it and exposes
  the unauthenticated delete endpoint too, so it's opt-in and prints a warning.
