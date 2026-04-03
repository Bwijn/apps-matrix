# AppsMatrix

An LSPosed/Xposed module that provides **per-app environment spoofing** on rooted Android devices. Each target app sees its own self-consistent identity (SIM operator, locale, timezone), while all other apps remain unaffected.

## Problem

Global environment spoofing (e.g. using `resetprop` in a boot script) pollutes all processes at once. A Chinese phone pretending to be a US T-Mobile device globally will cause:

- Chinese apps (JD, Taobao) to see contradictory signals and trigger security verification
- Some apps to refuse registration when locale/timezone/SIM don't match
- Identity leaks across apps that compare different API sources

**AppsMatrix** solves this by hooking Java APIs **inside each app's own process**, giving every app a tailored, self-consistent environment.

## How It Works

### Architecture

```
KernelSU
  └── Zygisk (libzygisk.so injected into Zygote)
        └── LSPosed (arm64-v8a.so + lspd daemon)
              └── AppsMatrix (this module)
                    ├── TikTok  → sees US T-Mobile, en_US, America/New_York
                    ├── JD      → sees CMCC China, zh_CN, Asia/Shanghai
                    ├── Claude  → sees US T-Mobile, en_US, America/Los_Angeles
                    └── Others  → untouched, see real device values
```

### Dependency Chain

1. **KernelSU** provides root and mounts Zygisk into the boot process
2. **Zygisk** injects `libzygisk.so` into the Zygote process. Every app forked from Zygote inherits this library
3. **LSPosed** registers as a Zygisk module (`arm64-v8a.so`). In each forked app process, it checks its **scope** database — if the app is a target, LSPosed keeps itself loaded and enables Xposed hooking; otherwise it unmaps itself
4. **AppsMatrix** is loaded by LSPosed into scoped app processes. It reads `matrix.json` from its own APK assets, looks up the current `packageName`, and hooks the relevant Java APIs with spoofed return values

### What Gets Hooked

| API | Class | Purpose |
|-----|-------|---------|
| `getSimOperator()` | `TelephonyManager` | MCC+MNC code (e.g. `310260`) |
| `getSimOperatorName()` | `TelephonyManager` | Carrier name (e.g. `T-Mobile`) |
| `getSimCountryIso()` | `TelephonyManager` | SIM country (e.g. `us`) |
| `getNetworkOperator()` | `TelephonyManager` | Network MCC+MNC |
| `getNetworkOperatorName()` | `TelephonyManager` | Network carrier name |
| `getNetworkCountryIso()` | `TelephonyManager` | Network country |
| `getDefault()` | `Locale` | Language + country (e.g. `en_US`) |
| `getDefault()` | `TimeZone` | Timezone (e.g. `America/New_York`) |

Both the no-arg and `int subscriptionId` overloads of TelephonyManager methods are hooked.

**Key insight**: These APIs execute inside the app's own process (not in `system_server`), which is why per-process hooking works and why global `resetprop` is the wrong tool for this job.

## Project Structure

```
apps-matrix/
├── app/src/main/
│   ├── AndroidManifest.xml          # Xposed module metadata (no Activity/icon)
│   ├── assets/
│   │   ├── matrix.json              # Per-app spoofing config
│   │   └── xposed_init              # LSPosed entry point declaration
│   ├── java/com/matrix/env/
│   │   └── MatrixModule.java        # Core module — all hooks
│   └── res/values/
│       └── arrays.xml               # Xposed scope resource
├── stubs/                           # Xposed API stubs for compilation
│   └── de/robv/android/xposed/
│       ├── IXposedHookLoadPackage.java
│       ├── XC_MethodHook.java
│       ├── XposedBridge.java
│       ├── XposedHelpers.java
│       └── callbacks/
│           ├── XCallback.java
│           └── XC_LoadPackage.java
├── build/                           # Build artifacts (gitignored)
├── libs/                            # JAR dependencies (gitignored)
└── README.md
```

## Configuration

Edit `app/src/main/assets/matrix.json`:

```json
{
  "com.zhiliaoapp.musically": {
    "label": "TikTok",
    "sim_operator": "310260",
    "sim_operator_name": "T-Mobile",
    "sim_country": "us",
    "network_operator": "310260",
    "network_operator_name": "T-Mobile",
    "network_country": "us",
    "locale_language": "en",
    "locale_country": "US",
    "timezone": "America/New_York"
  },
  "com.jingdong.app.mall": {
    "label": "JD",
    "sim_operator": "46000",
    "sim_operator_name": "CMCC",
    "sim_country": "cn",
    "network_operator": "46000",
    "network_operator_name": "中国移动",
    "network_country": "cn",
    "locale_language": "zh",
    "locale_country": "CN",
    "timezone": "Asia/Shanghai"
  }
}
```

Each key is an Android package name. The `label` field is for human readability only. All other fields map directly to the hooked API return values.

### Adding a New App

1. Add a new entry to `matrix.json` with the app's package name as key
2. Fill in all fields with a **self-consistent** identity (matching country, operator, locale, timezone)
3. Rebuild and reinstall the APK
4. In LSPosed Manager, enable AppsMatrix for the new target app (add it to scope)
5. Force-stop the target app (or reboot) so hooks take effect on next launch

### Common MCC+MNC Codes

| Country | Operator | Code |
|---------|----------|------|
| US | T-Mobile | `310260` |
| US | AT&T | `310410` |
| US | Verizon | `311480` |
| CN | China Mobile (CMCC) | `46000` |
| CN | China Unicom | `46001` |
| CN | China Telecom | `46003` |
| UK | EE | `23430` |
| UK | Vodafone | `23415` |
| JP | NTT Docomo | `44010` |

## Build

This module is built **without Gradle**, using Android SDK command-line tools directly.

### Prerequisites

- Android SDK with build-tools (d8, aapt2, apksigner) — e.g. at `/opt/android-sdk/`
- Java Development Kit (javac)
- `android.jar` from an SDK platform (API 26+)

### Build Steps

```bash
# Set paths
SDK=/opt/android-sdk
BT=$SDK/build-tools/35.0.0
PLATFORM=$SDK/platforms/android-35/android.jar
OUT=build

# 1. Compile Xposed API stubs
javac -source 8 -target 8 \
  -d $OUT/stubs \
  stubs/de/robv/android/xposed/callbacks/XCallback.java \
  stubs/de/robv/android/xposed/callbacks/XC_LoadPackage.java \
  stubs/de/robv/android/xposed/IXposedHookLoadPackage.java \
  stubs/de/robv/android/xposed/XC_MethodHook.java \
  stubs/de/robv/android/xposed/XposedBridge.java \
  stubs/de/robv/android/xposed/XposedHelpers.java

# 2. Compile module source
javac -source 8 -target 8 \
  -cp "$PLATFORM:$OUT/stubs" \
  -d $OUT/classes \
  app/src/main/java/com/matrix/env/MatrixModule.java

# 3. Dex (include all inner class files)
$BT/d8 --min-api 26 --output $OUT \
  $OUT/classes/com/matrix/env/*.class

# 4. Copy assets
mkdir -p $OUT/assets
cp app/src/main/assets/matrix.json $OUT/assets/
cp app/src/main/assets/xposed_init $OUT/assets/

# 5. Compile resources & link APK
$BT/aapt2 compile -o $OUT/res.zip \
  app/src/main/res/values/arrays.xml

$BT/aapt2 link \
  -o $OUT/apps-matrix-unsigned.apk \
  -I $PLATFORM \
  --manifest app/src/main/AndroidManifest.xml \
  --min-sdk-version 26 \
  --target-sdk-version 35 \
  $OUT/res.zip

# 6. Inject dex + assets into APK
cd $OUT
zip -j apps-matrix-unsigned.apk classes.dex
zip -r apps-matrix-unsigned.apk assets/
cd ..

# 7. Align & sign
$BT/zipalign -f 4 $OUT/apps-matrix-unsigned.apk $OUT/apps-matrix.apk

# Generate debug keystore if needed
[ -f $OUT/debug.keystore ] || keytool -genkeypair -v \
  -keystore $OUT/debug.keystore -alias debug \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass android -keypass android \
  -dname "CN=Debug"

$BT/apksigner sign \
  --ks $OUT/debug.keystore --ks-pass pass:android \
  --out $OUT/apps-matrix-signed.apk \
  $OUT/apps-matrix.apk
```

### Install

```bash
adb install -r build/apps-matrix-signed.apk
```

After installing:
1. Open LSPosed Manager
2. Enable "AppsMatrix" module
3. Add target apps to the module's scope
4. Reboot (or force-stop target apps)

## Verifying Hooks

Check Xposed logs after launching a target app:

```bash
# Via logcat
adb logcat -s LSPosed AppsMatrix

# Expected output:
# AppsMatrix: handleLoadPackage called for com.jingdong.app.mall
# AppsMatrix: hooking com.jingdong.app.mall
# AppsMatrix: getSimOperator -> 46000
# AppsMatrix: hooked Locale -> zh_CN
# AppsMatrix: hooked TimeZone -> Asia/Shanghai
# AppsMatrix: all hooks installed for com.jingdong.app.mall
```

Apps not listed in `matrix.json` will log:
```
AppsMatrix: no matrix for com.example.app, skipping
```

## Requirements

- Rooted Android device (KernelSU or Magisk)
- Zygisk enabled
- LSPosed framework installed
- Android 8.0+ (API 26+)

## Limitations

- **Config is baked into the APK** — changing `matrix.json` requires rebuilding and reinstalling. A future improvement could read config from external storage or a ContentProvider.
- **Java-level hooks only** — apps using NDK/native code to query system properties directly (via `__system_property_get`) will bypass these hooks. For those, combine with `resetprop` or a native Zygisk module.
- **No IMEI/device ID spoofing** — this module focuses on SIM/network/locale/timezone identity only.

## License

Personal use. No warranty.
