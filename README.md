# nfc-device-check

Two Android apps that verify a device's identity over an NFC tap.

- **Sender** (`sender/`) — installed on Phone B. Hashes its own identity fields
  (EID, IMEI 1 & 2, Android version, build number) with SHA-256 and serves the
  hashes to the other phone over NFC (Host Card Emulation). Raw values never
  leave Phone B.
- **Checker** (`checker/`) — installed on Phone A. Reads the hashes on tap
  (NFC reader mode), enrolls known devices, and on later taps accepts or
  denies the device based on a per-field comparison. Results are logged
  locally with sound/haptic feedback.
- **shared** (`shared/`) — common wire protocol, chunking, and hashing code
  used by both apps so they can never disagree on the format.

## How it works

1. Sender (Phone B) hashes each identity field: `EID`, `IMEI 1`, `IMEI 2`,
   `Android version`, `Build number`.
2. Sender serves those hashes over NFC HCE as a chunked payload.
3. Checker (Phone A) taps Sender and reads the hashes (read-only — nothing on
   the Checker is deleted).
4. Checker compares the hashes against an enrolled device profile and shows
   **ACCEPT** or **DENY**, indicating which fields mismatched.

## Project status

- [x] Phase 1 — toolchain bootstrap + project skeleton (both APKs build)
- [ ] Phase 2 — shared protocol/chunking/hashing + unit tests
- [ ] Phase 3 — Sender: HCE service, device-owner provisioning, EID fallback
- [ ] Phase 4 — Checker: reader mode, enroll/verify, Room log, feedback
- [ ] Phase 5 — on-device testing (Pixel 8 + Pixel 7a)

## Building

Requires JDK 17+ and an Android SDK (compileSdk 37).

```bash
./gradlew assembleDebug          # both APKs
./gradlew :shared:test           # protocol/hashing unit tests
```

APKs land in `sender/build/outputs/apk/debug/` and
`checker/build/outputs/apk/debug/`.

## Notes

- Phone B (Sender) needs **device owner** provisioning via adb so it can read
  IMEI: `adb shell dpm set-device-owner ...` (device must have no accounts).
- The eSIM EID requires carrier privileges or `READ_PRIVILEGED_PHONE_STATE`,
  which a device-owner app does not get — the Sender falls back to one-time
  manual EID entry (Settings > About phone > SIM status, or dial `*#06#`).
- No `INTERNET` permission in either app: nothing is transmitted off-device.
