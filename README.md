# Streamer Remote — reviewed source (v0.3)

**Status: GitHub Actions builds a debug APK; no hardware test has been performed.** The original v0.1 ZIP was inspected; this version fixes key handling, input validation, and makes the streamer address and incoming keycodes configurable. The Kadb library compiles; its authentication behavior on the actual streamer remains unverified. Treat the APK as an experimental test build.

## Intended behavior

- On Xiaomi Mini LED S55 in Basic TV mode, short Home (3) should retain the Xiaomi action; long Home should send `input keyevent 3` to Google TV Streamer.
- Netflix (193), Prime Video (194), YouTube (190), All Apps (284) each map to a manually entered package name on the streamer. Blank leaves the original key action.
- Enter the streamer IPv4 address in the app; the field starts empty. ADB port defaults to 5555. Commands go directly over authenticated ADB on the LAN. No Google login, HTTP server, cloud, or continuously running Mac is intended. Voice assistant is out of scope.

## Download the test APK\n\nOpen the latest successful [Build debug APK run](https://github.com/MaWeXi/streamer-remote/actions/workflows/build-apk.yml), download the `streamer-remote-debug-apk` artifact, and unzip it to obtain `app-debug.apk`. No Android Studio installation on your Mac is needed. Each cloud build uses a fresh debug signing key, so a later APK may require uninstalling the old test version first (which clears its settings). This APK was compiled but has not been installed or tested on the Xiaomi or streamer.\n\n## Initial ADB setup on the streamer

1. On Google TV Streamer, enable Developer options and ADB debugging. Keep streamer and Xiaomi on the same trusted LAN. Reserve its chosen IP address in the router.
2. Confirm which ADB mode the streamer offers. For classic TCP ADB at port 5555, authorize the *Xiaomi app's own ADB key* on the streamer's screen when the app first connects. A Mac's previously authorized key does **not** authorize the app. The current source has no proven persistent key setup, so this is a required on-device validation.
3. If the streamer offers Android's newer Wireless debugging pairing-code flow instead, its pairing and connection ports can differ from 5555. This source does not implement pairing-code setup; do not assume changing the port is sufficient.
4. Do not forward ADB port 5555 from the router to the internet. Debugging may need to be re-enabled after a streamer restart.

## Xiaomi installation and first test

1. Enable Developer options and USB/network debugging on the Xiaomi if available. Install the built APK using `adb -s XIAOMI_IP:5555 install -r app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to the TV and open it with a file manager that permits app installs from that source.
2. Launch Streamer Remote on the Xiaomi. Enter the streamer address and set mappings and incoming button codes, then save. Mappings are **package names**, not display names; find them with an already authorized ADB client using `adb shell pm list packages` on the streamer.
3. Disable Button Mapper's accessibility service, then enable Streamer Remote under Xiaomi Settings > Accessibility. Select **Home auf Streamer testen**. Confirm any ADB authorization prompt on the streamer.
4. Verify short Home still opens Xiaomi Home, long Home opens streamer Home, and each of the four app keys opens its chosen app. Test repeated presses and a restart of both devices. If Home never reaches the service, Xiaomi intercepts it before this app can act; this cannot be repaired by the timer alone.

## Findings from source review

- v0.1 used only `KeyEvent.isLongPress`, which some remotes never set. v0.3 also measures press duration using Android's long-press timeout.
- v0.1 accepted invalid package names and silently ignored them later. v0.3 validates on save.
- Short Home may be processed by Xiaomi immediately on key-down, before long press is known. Thus the device may switch to Xiaomi Home even when long Home eventually sends a streamer command. Only on-device testing can establish the actual behavior.
- The selected Kadb library (`com.flyfishxu:kadb:2.1.4`) compiles in the cloud build. Persistent authenticated ADB identity has not been demonstrated on the devices.
- Only one accessibility key-filter service should be active during testing.

## Configurable incoming keycodes

The five incoming button codes default to Home 3, Netflix 193, Prime Video 194, YouTube 190, and All Apps 284. Each can be changed in the app. Codes must be valid and distinct. The outgoing ADB Home command remains Android keycode 3.
