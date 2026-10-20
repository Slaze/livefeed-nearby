# Nearby

**LiveFeed’s Bluetooth radio.** Encrypted voice and video calls, live video and live audio between phones over a Bluetooth mesh. No internet needed.

This is a GPL-3.0-or-later fork of [MeshLive Android](https://github.com/MeshLive/meshlive-android). The launcher name is **Nearby**, the application id is `com.livefeed.nearby`, and LiveFeed opens it with `com.livefeed.action.NEARBY`. The Kotlin package stays `com.example.bluetoothtest`. Wire formats, BLE UUIDs and hop-local security are unchanged, so phones on this radio still speak the same mesh protocol.

<p>
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/02-live.png" width="19%" alt="Live nearby: a radio and a video stream">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/03-golive.png" width="19%" alt="Go live: video and audio, or audio only">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/04-calls.png" width="19%" alt="Calls: people nearby">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/05-settings.png" width="19%" alt="Settings">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/01-welcome.png" width="19%" alt="Welcome">
</p>

## What you can do

- **Call people nearby.** Voice or video, end-to-end encrypted, phone to phone.
- **Go live.** Share your camera with everyone around you, or go **audio only**, like a local radio that keeps playing with the screen off.
- **Reach further.** Phones in between pass calls and streams along, so you can reach people out of your own Bluetooth range.
- **Stay private.** No internet, SIM card, account or server on the radio. Strangers see a random name; your contacts recognise you.

LiveFeed accounts stay off the Bluetooth wire. Binding a LiveFeed user to a radio node ID is a separate, off-air step. Android 10 or newer. Free and open source (GPL-3.0-or-later).

## How it works

Your phone talks to nearby phones over Bluetooth, about 10 to 30 metres, never through the internet. A phone that receives a stream can pass it on, and the next phone can pass it on again: that chain is the mesh. Bluetooth is slow, so video is small and gets a little more delayed with each hop; audio-only streams reach further.

```
 Source phone ))) Viewer phone ))) Viewer phone ))) ...
     (camera)     (also relays)    (also relays)
```

## Privacy

- The ID your phone broadcasts is random and changes every 15 minutes, so a listener cannot follow you.
- Calls hide both phones' identities from anyone listening in. A stranger sees a throwaway identity until you answer.
- Your name, if you set one, is never broadcast. A stream title is public, so everyone nearby can read it.

Limits, plainly: a live stream shows its source to everyone who watches; on Android 13 and older a phone that connects to yours can read its Bluetooth name; anyone whose call you accept learns who you are; there has been no independent security audit. Details: [docs/PRIVACY.md](docs/PRIVACY.md).

## Install

No public Nearby release is published yet. Build the GitHub-flavour debug APK and sideload it:

```bash
python tools/fetch_native_sources.py
./gradlew :app:assembleGithubDebug
```

The APK is `app/build/outputs/apk/github/debug/app-github-debug.apk`. Open Nearby and allow **Nearby devices**, **Camera** and **Microphone**. Both people need this app. With no internet, a friend can send you the app: in Nearby, Settings, then **Share this app**.

Intended GitHub home (create it before shipping updates): [Slaze/livefeed-nearby](https://github.com/Slaze/livefeed-nearby). Release files will be `Nearby.apk` and `Nearby-<version>-<abi>.apk`. How to verify a future release: [docs/USER_GUIDE.md](docs/USER_GUIDE.md#verifying-a-release).

## Status

Nearby is in **beta**, tested on seven phones from Samsung, Xiaomi, ASUS and OPPO (Android 10 to 16) and with a first iPhone (upstream MeshLive runs). Results vary by phone pair: see [docs/DEVICES.md](docs/DEVICES.md). Do not rely on it where your safety depends on it. An iPhone app is in development upstream (`MeshLive/meshlive-ios`).

## Build from source

You need JDK 21, the Android SDK with NDK `28.2.13676358` and CMake `3.22.1`, Perl and Python 3.11 or newer.

```bash
python tools/fetch_native_sources.py   # downloads the pinned libaom and libopus
./gradlew :app:testDebugUnitTest :app:assembleGithubDebug
```

Developers can read [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (how it is built, build notes), [docs/SWITCHES.md](docs/SWITCHES.md) (the list of experimental switches) and [docs/PROTOCOL.md](docs/PROTOCOL.md) (wire formats and security rules). Upstream phone results live in [MeshLive/meshlive-benchmarks](https://github.com/MeshLive/meshlive-benchmarks).

## Documentation

| I want to... | Read |
| --- | --- |
| Install, update, verify, use the app, get answers | [docs/USER_GUIDE.md](docs/USER_GUIDE.md) |
| Know what is exposed and what is hidden | [docs/PRIVACY.md](docs/PRIVACY.md) |
| See which phones work | [docs/DEVICES.md](docs/DEVICES.md) |
| Understand how it is built | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Look up an experimental or optional switch | [docs/SWITCHES.md](docs/SWITCHES.md) |
| Implement or review the wire format and security rules | [docs/PROTOCOL.md](docs/PROTOCOL.md) |
| See what is unfinished | [docs/PLAN.md](docs/PLAN.md) |
| Test on phones, or read why a measured choice was made | [docs/TESTING.md](docs/TESTING.md), [docs/DECISIONS.md](docs/DECISIONS.md) |
| Contribute, or follow the rules for coding agents | [CONTRIBUTING.md](CONTRIBUTING.md), [AGENTS.md](AGENTS.md) |

## Contributing

Bug reports, device results, documentation and code are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md). Please follow the [code of conduct](CODE_OF_CONDUCT.md).

## Security

Report vulnerabilities privately through [GitHub private vulnerability reporting](https://github.com/Slaze/livefeed-nearby/security/advisories/new) once that repository exists. See [SECURITY.md](SECURITY.md). Until then, open an issue that says only “security report” with no details.

## How this was built

Most of the radio code was written with AI assistance (Anthropic's Claude), directed by the upstream MeshLive maintainer, who tested it on real phones. This fork’s product identity (Nearby / LiveFeed sidecar) is a later change. It has not had an independent security audit.

## License

GPL-3.0-or-later. Original work copyright The MeshLive Authors. See [LICENSE](LICENSE), [AUTHORS](AUTHORS) and [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
