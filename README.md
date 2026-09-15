# MistFox Mini Apps Platform (Android / Kotlin / JS SDK)

MistFox is a complete, secure Android mini-application platform running self-contained applications packaged as `.pkg` container archives.

## Platform Architecture

```
Android OS (Kotlin / Material 3 / AndroidX)
  ↓
MistFox Host Application & Launcher Grid
  ↓
MiniAppRuntime (Hardened AndroidX WebKit WebView)
  ↓
HTML5 / CSS / JavaScript / WebAssembly (Origin: https://app.mistfox.local/)
  ↓
MistFox JavaScript SDK (@mistfox/sdk - Mist.call)
  ↓
MistFox Secure Native Bridge (MistFoxNativeBridge)
  ↓
APIRegistry & Dual-Layer PermissionManager
  ↓
Controlled Android Native APIs
```

## Core Security Model

1. **Executable Code Integrity**: ALL executable code (HTML, JS, WASM) MUST reside inside the `.pkg` package file. External network downloads are strictly restricted to **DATA** retrieval (REST API, JSON).
2. **Immutable Identity Context**: `appId` is strictly assigned by the native runtime upon package launch. JavaScript calls cannot impersonate or override mini-app identity.
3. **Storage & Data Isolation**: `Mist.storage` and `Mist.files` are isolated per mini-app package ID (`mistfox_storage_<appId>`).
4. **WebView Hardening**: `file://` access is disabled, mixed content is blocked, and bridge access is immediately detached if the WebView navigates to an external origin.

## Project Structure

```
mistfox/
├── android/            # Main Android application in Kotlin (Gradle DSL)
│   ├── app/            # Source code, UI activities, runtime, APIs, and tests
│   └── build.gradle.kts
├── sdk/                # @mistfox/sdk TypeScript / JavaScript SDK
│   ├── dist/mistfox-sdk.js
│   └── dist/index.d.ts
├── tools/
│   └── packager.py     # CLI tool for building and validating .pkg archives
├── examples/           # 8 sample mini-apps (Calculator, Notes, Camera, etc.)
│   └── dist/           # Compiled .pkg sample binaries
└── docs/               # Platform architecture and developer guides
```

## How to Build

### 1. Build Android APK
```bash
cd android
gradle assembleDebug
# APK location: android/app/build/outputs/apk/debug/app-debug.apk
```

### 2. Run Tests
```bash
cd android
gradle testDebugUnitTest
```

### 3. Package a Mini-App (.pkg)
```bash
python3 tools/packager.py examples/calculator -o examples/dist/calculator.pkg
```

## Implemented Native APIs (V1)
- `app.info`, `app.id`, `app.version`, `app.close`, `app.getLaunchInfo`
- `ui.toast`, `ui.vibrate`
- `storage.get`, `storage.set`, `storage.remove`, `storage.clear`, `storage.has`, `storage.keys`
- `files.read`, `files.write`, `files.delete`, `files.exists`, `files.info`
- `camera.isAvailable`, `camera.takePhoto`, `camera.pickPhoto`, `camera.getCameras`
- `microphone.isAvailable`, `microphone.requestPermission`
- `device.info`
- `network.fetch`
- `clipboard.read`, `clipboard.write`
- `share.text`
- `browser.open`
- `phone.openDialer`
- `sms.openComposer`
- `notifications.send`
- `sensors.available`
- `location.getCurrent`
- `biometric.isAvailable`, `biometric.authenticate`
- `bluetooth.isAvailable`
- `nfc.isAvailable`
