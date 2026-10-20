# Project recap — Nearby Android

Living handoff so a cold agent can resume without replaying chat. Newest session first under Sessions.

## Project purpose

Nearby is LiveFeed’s Bluetooth radio: encrypted voice/video calls and live video or audio-only streams between nearby phones over a Bluetooth mesh, with no internet, account or server on the air. Relays forward ciphertext hop-local. Application id `com.livefeed.nearby`. The product is in **beta** (`0.3.0-beta`). GPL-3.0-or-later fork of MeshLive; an iPhone client lives upstream (`MeshLive/meshlive-ios`). This checkout is Android plus a Windows C# receiver.

## Architecture / layout map

| Path | Role |
| --- | --- |
| `app/src/main/java/com/example/bluetoothtest/` | Kotlin app (package name kept; application id `com.livefeed.nearby`) |
| `app/src/main/cpp/` | Native libaom + libopus JNI |
| `docs/PROTOCOL.md` | Wire and security |
| `docs/PLAN.md` | Unfinished work |
| `docs/DEVICES.md` | Phone results (no full pass yet) |
| `docs/SWITCHES.md` | Experimental / control-arm register |
| `docs/TESTING.md` | Gates and workflow |
| `receiver/`, `shared/` | Windows C# receiver |
| `tools/` | Benchmark harness and host tests |

Transports: BLE GATT (default), Classic RFCOMM, experimental LE L2CAP CoC. Discovery is always BLE advertising.

## Inception → now timeline

- Maintainer-directed, largely AI-written Android mesh caller/streamer (GPL-3.0).
- 0.2.0-beta: rotating discovery IDs and BMH3 on by default.
- 0.3.0-beta: current versionCode 3 in this zip extract (not a git checkout).
- Maintainer tests on seven Android phones (Android 10–16) plus a first iPhone; **no phone has met every acceptance gate**.
- 2026-10-20: JVM-proven join UI fix so a failed Watch join no longer flashes the playback screen.
- 2026-10-20: product rebrand to Nearby (`com.livefeed.nearby`); protocol UUIDs unchanged.

## Sessions

### 2026-10-20 — Rebrand to Nearby

**Session goal.** Ship this fork under LiveFeed’s Nearby identity, not the upstream product name.

**What changed.** Launcher name Nearby; application id `com.livefeed.nearby`; APK prefix `Nearby`; intended GitHub `Slaze/livefeed-nearby`; Fastlane, updater strings, F-Droid draft, user docs. Kotlin package stays `com.example.bluetoothtest`. BLE UUIDs, BMH2/BMH3 and hop-local protocol unchanged. GPL copyright of The MeshLive Authors kept. Historical `docs/DECISIONS.md` measurements left as written. Device-policy JSON re-exported from Kotlin. Host intent remains `com.livefeed.action.NEARBY`.

**Why.** LiveFeed opens this APK as a sidecar. A new application id is a different app to Android (cannot update an old MeshLive install).

**How verified.** `:app:assembleGithubDebug` SUCCESS, applicationId `com.livefeed.nearby`, APK SHA-256 `a755a03ad1e450cda0b84759f825387d71f11ebf0c442c632f7fe2a476171006`. `:app:testGithubDebugUnitTest` BUILD SUCCESSFUL after policy JSON export. `python3 -B -m unittest tools.tests.test_make_release tools.tests.test_fdroid_recipe` 33 OK. Host pytest module not installed on this Python.

**Next.** Create `Slaze/livefeed-nearby` before shipping updates. Cut a Nearby signing cert and replace `AppSharePolicy.PUBLISHED_RELEASE_CERT_SHA256`. Phone check of LiveFeed → Nearby.

**Blockers.** No GitHub release yet. Money MIT unchanged (Lokator + Nairacoin).

### 2026-10-20 — LiveFeed Nearby sidecar

**Session goal.** Let LiveFeed open this fork as extra nearby features, without merging internet Go Live into the Bluetooth radio.

**What changed.** `LiveFeedHostLaunch` + exported `com.livefeed.action.NEARBY`. Host extras do not skip onboarding. LiveFeed Flutter/web start this app by action, with no foreign package id. Identities stay separate. Library embed into LiveFeed is unfinished (PLAN).

**How verified.** `./gradlew :app:testGithubDebugUnitTest --tests com.example.bluetoothtest.LiveFeedHostLaunchTest`.

**Next.** Phone check: LiveFeed Nearby → this app’s Live tab. Do not compile this module into foap until that app’s Android Gradle is current. GPL if linked into one APK.

### 2026-10-20 — Audit, device/platform constraints, failed-join Watch flash

**Session goal.** Analyze bugs and device constraints; assess Android / iOS / PWA room; fix what JVM tests can prove.

**What changed**

- `ReceiverJoinUi` + `receiverJoinScreenArmed` in `ReceiverRecoveryNoticePolicy.kt`: with `receiverUiStability` on, a fresh join stays on the Live list until `onStreamActive`; Watch is not shown for a failed join. TextureView is kept at 1×1 px under the opaque browser so the decoder surface exists without punching through. Connecting copy names elapsed seconds after 2 s (15 s GATT accept is no longer a frozen spinner). Control arm `test_receiver_ui_stability=false` still opens Watch on tap.
- `MainActivity.kt`: wire join-pending, back-cancels-join, tab/Go live/Settings abandon the pending join.
- Tests in `ReceiverUiStabilityTest.kt`. Follow-up: empty Live list uses suppression deadlines (`receiverBrowserCoolingRetryMs`) so a failed join that cleared last-seen still says the stream is in range; Watch frame-age tick stops when returning to the list.
- Encoder capability log null-safe (`CameraVideoEncoder.kt`).
- Docs: `PLAN.md`, `DECISIONS.md`, `SWITCHES.md`; typo in `ARCHITECTURE.md`.
- `local.properties` (gitignored) pointed at this Mac’s Android SDK for the Gradle run.

**Why.** PLAN/DECISIONS named the 0.25–0.4 s Watch flash and the 15 s connecting overlay with no progress as remaining UI defects. Both are UI-state only (no protocol change). Cross-platform “ship iOS + PWA as equivalents” was rejected: Web Bluetooth cannot advertise, cannot do L2CAP CoC or Classic, and cannot run a background mesh; iOS cannot put manufacturer data in adverts and is a separate client.

**How verified**

- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL (JDK 17 on this Mac; project docs pin JDK 21).
- `./gradlew :app:testGithubDebugUnitTest` after the encoder log tweak — BUILD SUCCESSFUL.
- GitHub debug unit results: **3618 tests, 0 failures, 1 skipped**.
- Host pytest: 46 passed / 3 skipped on a numpy-free subset (`test_ios_interop_call`, `test_fetch_native_sources`, `test_dex_api_scan`, flavour/fdroid/policy). Full `pytest tools` needs numpy/cv2/cryptography, not installed on this Mac.
- `./gradlew :app:assembleGithubDebug` — BUILD SUCCESSFUL. APK `app/build/outputs/apk/github/debug/app-github-debug.apk` (30 MB), SHA-256 `23435260e86c7ca2611fa4a6cfa1cb07d2e946fa0d24100fc15f55be27ca552e`.
- No phones were connected. No radio, Camera2, encoder or screen gates were run.

**Current state**

- Android app compiles and its JVM suite is green. Behaviour is still **beta**: device table is partial/fail; three-phone relays, A15s CoC exhaustion, M31 VP9 bursts, reverse GATT stalls remain phone work.
- iOS: compact discovery is **on** (`ConnectivityFeatureDefaults.IPHONE_DISCOVERY = true`) after first two-way calls/streams; H.264 codec path is not coded; iOS app source is not in this tree.
- PWA: no web client in this repo. A PWA cannot be a functional substitute for Bluetooth mesh media.
- This folder is a zip extract (`nearby-android-main`), not a git checkout.

**Next steps**

1. Phone A/B of `test_receiver_ui_stability` (failed join never shows Watch; connecting line names seconds; recovery cover still held).
2. Frozen-APK retest of relay and call cases in `docs/PLAN.md` (same gates for every phone).
3. iOS work belongs in `MeshLive/meshlive-ios`, plus Android T8 scan-filter evidence.
4. Do not start a Web Bluetooth PWA for calls/streams.

**Blockers / risks**

- Production-ready across Android + iOS + PWA is **not** a reachable claim from this session: radio quality is phone-pair specific, iOS is another repo, PWA is the wrong substrate.
- Phone tests need exclusive device ownership per `AGENTS.md`. None were available here.
- Money MIT remains Lokator Play upload + Nairacoin seed host; this session was explicit Nearby work.

### Device and platform constraints (standing)

**Android (this product).** minSdk 29. GATT is the compatibility transport. CoC is experimental (A15s dies after ~15 CoC uses per Bluetooth session). Classic needs pairing unless the experimental bond-free switch is on. Dual-role relays collide on some Exynos stacks (M31). Hardware VP9 on M31 overshoots. 2.4 GHz Wi-Fi can halve Bluetooth goodput.

**iOS.** CoreBluetooth cannot send Android-style manufacturer data; this app already maps one 128-bit compact UUID. iOS is a poor GATT peripheral under load, weak at dual central/peripheral, and aggressive about background radio. Optional H.264 needs a PROTOCOL design (`test_h264_codec` uncoded). Interop tools: `tools/ios_interop_call.py`.

**PWA.** Web Bluetooth is central-only, GATT-only, no advertising, no Classic, no CoC, no reliable background. Camera + BLE together is hostile. A PWA could at most be a same-Wi-Fi viewer (a different product, design review required); it cannot join the Bluetooth mesh.

## Honest production-ready verdict

| Surface | Status |
| --- | --- |
| Android (this repo) | Functional beta. JVM-tested. Not gate-complete on any reference phone. |
| iOS | Sibling app + compact discovery on Android. Not in this checkout. |
| PWA | Do not build as a mesh peer. |

Shipping a “complete production app” here means: keep Android beta honest, measure phones, then grow iOS interop — not a three-OS rewrite.
