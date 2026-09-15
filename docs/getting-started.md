# MistFox Mini Apps Platform Documentation

## Architecture Overview

MistFox is an Android mini-app platform designed to run self-contained mini-applications packaged as `.pkg` container archives.

### Stack Flow
```
Android (Kotlin / Jetpack / Material 3)
  ↓
MistFox Host Platform Application
  ↓
MiniAppRuntime (Hardened AndroidX WebKit WebView)
  ↓
HTML5 + CSS + JavaScript + WebAssembly (Local https://app.mistfox.local/ origin)
  ↓
MistFox JavaScript SDK (Mist.call)
  ↓
MistFox Secure Native Bridge (MistFoxNativeBridge)
  ↓
MistFox APIRegistry & PermissionManager
  ↓
Controlled Android OS APIs
```

## Security & Executable Code Policy

1. **Package Isolation**: ALL mini-app executable code (HTML, JS, WASM) MUST reside inside the `.pkg` package file.
2. **Data vs Executable Code**: Network access (`fetch`, `Mist.network.fetch`) is strictly permitted for **DATA** retrieval only. Dynamic code download and remote execution are prohibited.
3. **Origin Restriction**: The IPC bridge is exclusively accessible when rendering content loaded from `https://app.mistfox.local/`. Navigating to external URLs automatically detaches native bridge access.
4. **Identity Binding**: Mini-app identity (`appId`) is immutably set by the native runtime upon package launch and cannot be overridden by JavaScript arguments.
5. **Storage Isolation**: Storage (`Mist.storage`) and isolated files (`Mist.files`) are strictly partitioned per mini-app package ID (`mistfox_storage_<appId>`).

## Manifest Specification (`manifest.json`)

```json
{
  "id": "com.example.calculator",
  "name": "Calculator",
  "version": "1.0.0",
  "versionCode": 1,
  "description": "Calculator mini-app",
  "entry": "index.html",
  "icon": "icon.png",
  "permissions": ["storage"],
  "minMistFoxVersion": "1.0.0"
}
```

## Permissions Hierarchy

- **SAFE**: `storage`, `vibrate`, `audio`, `share`, `device.info`, `network`, `sensors`, `orientation`, `clipboard.write`.
- **SENSITIVE**: `files.read`, `files.write`, `location`, `notifications`, `clipboard.read`, `calendar.read`, `calendar.write`, `phone`, `sms`, `alarms`, `bluetooth`.
- **DANGEROUS**: `camera`, `microphone`, `location.precise`, `contacts.read`, `contacts.write`, `bluetooth.scan`, `bluetooth.connect`, `biometric`, `nearby_devices`.
- **RESTRICTED**: `background`, `foreground_service`.

## JavaScript / TypeScript SDK Usage (@mistfox/sdk)

```javascript
// Generic IPC Primitive
const res = await Mist.call("storage.get", { key: "username" });

// Convenience Namespace API Examples
await Mist.storage.set("username", "MistFoxUser");
const userInfo = await Mist.storage.get("username");

await Mist.ui.toast("Hello MistFox!");
await Mist.ui.vibrate(200);

const devInfo = await Mist.device.info();
console.log(devInfo.model, devInfo.osVersion);
```

## Building & Packaging Mini-Apps

Use `tools/packager.py`:

```bash
python3 tools/packager.py examples/calculator -o examples/dist/calculator.pkg
```

## Game Development Support

MistFox supports standard Web technologies natively inside the WebView:
- **Canvas 2D** (`canvas.getContext('2d')`)
- **WebGL 3D** (`canvas.getContext('webgl')`)
- **WebAssembly** (`.wasm` asset loading)

## Future Store Architecture (V2 Readiness)

The package manager pipeline (`PackageInstaller.installPackage(inputStream)`) is designed for direct integration with online store downloads:
```
Online Store API -> Download .pkg -> PackageVerifier.verifyPackageSignature() -> PackageInstaller.installPackage() -> Launcher Grid
```
