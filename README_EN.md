# HyperEars

[简体中文](README.md) · [Installation](docs/installation.md) · [Compatibility](docs/compatibility.md) · [Troubleshooting](docs/troubleshooting.md)

[![CI](https://github.com/silverpoetry/HyperEars/actions/workflows/ci.yml/badge.svg)](https://github.com/silverpoetry/HyperEars/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/silverpoetry/HyperEars?display_name=tag)](https://github.com/silverpoetry/HyperEars/releases)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0--only-blue.svg)](LICENSE)

HyperEars integrates selected third-party Bluetooth headsets with Xiaomi HyperOS and the MiLink
device center. It complements Android's existing audio stack with device identity, battery,
noise-control state and handoff metadata for supported vivo/iQOO, OPPO Enco, Bose and StarRing
devices.

> [!WARNING]
> HyperEars requires root, LSPosed and private HyperOS APIs. Be prepared to recover your system
> before installing it. ROM updates may temporarily break compatibility. This project is not
> affiliated with Xiaomi, vivo, iQOO, OPPO, Bose or any other device vendor.

## Scope

- Publishes eligible third-party headsets to MiLink while retaining Android's A2DP/HFP routing.
- Reads model-appropriate battery telemetry and maps verified private noise-control protocols.
- Opens the real Android Bluetooth-device details page from a HyperEars MiLink card.
- Falls back to handoff, volume and Android's aggregate battery for standard Bluetooth headsets.
- Exposes a per-device lifecycle dashboard for recognition, channel, protocol and publication.

HyperEars does not proxy audio, continuously scan for Bluetooth devices, inject the HyperOS
Settings UI or poll MiLink views. A private RFCOMM channel is created only for adapters that need
vendor telemetry, and its lifetime is bound to the physical device session.

## Requirements

- Xiaomi HyperOS on Android 15 or newer;
- LSPosed API 101 or newer;
- static scopes `com.android.bluetooth` and `com.milink.service`;
- a headset already paired through Android Bluetooth settings.

See the Chinese [compatibility matrix](docs/compatibility.md) for evidence levels and known
limitations. Broad family profiles and protocol-derived OPPO support are experimental unless a
model is explicitly marked as hardware-verified.

## Install

1. Download the APK and matching `.sha256` file from
   [Releases](https://github.com/silverpoetry/HyperEars/releases).
2. Verify the SHA-256 digest.
3. Install the APK, enable HyperEars in LSPosed and confirm both static scopes.
4. Reboot the device, pair/connect the headset and inspect the HyperEars dashboard.

Early development builds used a different certificate. Android cannot update such a build in
place; disable it in LSPosed, uninstall it, install the public release and enable it again. The
complete upgrade and removal procedure is documented in [installation.md](docs/installation.md).

The public signing-certificate fingerprint and verification procedure are documented in
[release-signing.md](docs/release-signing.md).

## Repository layout

- `protocol`: stateless wire codecs and incremental decoders.
- `integration`: adapter hierarchy, capabilities and per-session protocol state machines.
- `system-module`: production LSPosed module, Bluetooth lifecycle, MiLink bridge and dashboard.
- `protocol-test`: developer protocol laboratory; not shipped as a production artifact.

Architecture, process boundaries, state revisioning and extension rules are described in
[system-module-architecture.md](docs/system-module-architecture.md).

## Privacy and security

The production module declares no Internet permission and includes no analytics, advertising or
remote crash reporting. Bluetooth addresses remain local and are masked in normal module logs.
The protocol laboratory intentionally displays raw frames and device addresses, so redact them
before sharing diagnostics. See [PRIVACY.md](PRIVACY.md) and [SECURITY.md](SECURITY.md).

## Build

JDK 17 and Android SDK 36 are required:

```powershell
.\gradlew.bat --no-daemon clean testDebugUnitTest `
  :protocol-test:assembleDebug `
  :system-module:lintRelease `
  :system-module:assembleRelease
```

Without the four `HYPEREARS_KEY*`/`HYPEREARS_KEYSTORE*` environment variables, the Release APK is
left unsigned. Tagged GitHub builds retrieve the durable release key from repository Secrets,
verify the resulting APK and publish a matching SHA-256 file.

## Contributing and licensing

Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting a model or protocol change. Never publish
a complete personal Bluetooth MAC, account data, credentials or proprietary vendor assets.

HyperEars is licensed under [GNU GPL-3.0-only](LICENSE). Protocol research references and
attributions are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Product and trademark
names are used only to describe compatibility and remain the property of their respective owners.
