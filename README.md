# nfc-device-check

Two Android apps that verify a device's identity over an NFC tap.

- **Sender** (`sender/`) — installed on Phone B. Scrapes every stable
  system/hardware fact the phone exposes without special permissions
  (manufacturer, model, build, kernel, CPUs, RAM, GPU, display, storage,
  sensors, cameras, feature list…), hashes each field with SHA-256, and serves
  the hashes to the other phone over NFC (Host Card Emulation). Raw values
  never leave Phone B.
- **Checker** (`checker/`) — installed on Phone A. Reads the hashes on tap
  (NFC reader mode), enrolls known devices, and on later taps accepts or
  denies the device based on a per-field comparison. Results are logged
  locally with sound/haptic feedback.
- **shared** (`shared/`) — common wire protocol, chunking, and hashing code
  used by both apps so they can never disagree on the format.

## How it works

1. Sender (Phone B) collects ~28 stable system/hardware fields and hashes each
   one: model, build number, Android version, GPU renderer, RAM, sensor list,
   and so on. Volatile values (battery, uptime, MACs) and the user-settable
   device name are excluded.
2. Sender serves those per-field hashes over NFC HCE as a chunked payload.
3. Checker (Phone A) taps Sender and reads the hashes (read-only — nothing on
   the Checker is deleted).
4. Checker compares the hashes against an enrolled device profile and shows
   **ACCEPT** or **DENY**, indicating which fields mismatched.

Both apps must run the same release (wire protocol v2); mismatched versions
fail fast with an "app version mismatch" message.

## Project status

- [x] Phase 1 — toolchain bootstrap + project skeleton (both APKs build)
- [x] Phase 2 — shared protocol/chunking/hashing + unit tests
- [x] Phase 3 — Sender: HCE service + system-info hashing
- [x] Phase 4 — Checker: reader mode, enroll/verify, Room log, feedback
- [ ] Phase 5 — on-device testing (Pixel 8 + Pixel 7a)

## Building

Requires JDK 17+ and an Android SDK (compileSdk 37).

```bash
./gradlew assembleDebug          # both APKs
./gradlew test                   # protocol/hashing/verifier unit tests
```

APKs land in `sender/build/outputs/apk/debug/` and
`checker/build/outputs/apk/debug/`. Signed release-ready APKs are attached to
[GitHub Releases](https://github.com/TanBread/nfc-device-check/releases).

## Notes

- No permissions beyond `NFC` in either app, no `INTERNET` permission: nothing
  is transmitted off-device.
- Field set: Manufacturer, Model, Brand, Device, Product, Board, Hardware,
  Bootloader, Build ID, Build number, Build fingerprint, Build type, Build
  timestamp, Baseband version, Android version, API level, Kernel version,
  CPU ABIs, CPU cores, Total RAM, Display resolution, Display density, Total
  storage, GPU renderer, GPU version, Sensors, Cameras, System features.
- No IMEI/EID/serial: they need privileged permissions a normal app can't get.
- Upgrading from the v0.1.0 Sender? If you provisioned device owner for it,
  remove it first while the old APK is still installed:
  `adb shell dpm remove-active-admin com.tanbread.nfcdevicecheck.sender/.admin.SenderDeviceAdmin`
- v0.1.x enrolled devices are incompatible with v0.2.x (field set changed):
  re-enroll on the Checker after updating both apps.
