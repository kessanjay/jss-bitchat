# 09 — Android Runtime, Permissions and Background Behaviour

## 1. SDK baseline

Project baseline is minSdk 26. Target SDK should follow the Android Studio project and current Play/Android requirements. Do not set target SDK blindly; verify the current project configuration and Android release requirements before release.

## 2. Permissions

For apps targeting Android 12 (API 31) or later:
- `BLUETOOTH_SCAN` for scanning.
- `BLUETOOTH_ADVERTISE` for advertising.
- `BLUETOOTH_CONNECT` for connecting/communicating.

These are runtime permissions. Request only the permissions needed by the feature being started and handle denial gracefully.

For Android 11 and lower, legacy Bluetooth permissions apply; BLE scanning may require location permission depending on OS/target configuration. If the app does not derive physical location from scan results, review Android's `neverForLocation` guidance and legacy location permission max SDK rules.

Do not copy a manifest blindly. Check current Android documentation and the actual target SDK.

## 3. Hardware capability checks

Check:
- Bluetooth adapter exists.
- Bluetooth is enabled.
- BLE feature exists.
- Peripheral/advertising capability is supported.
- Advertiser is available.
- Required permissions are granted.
- Scan/advertising start callbacks report success.

A phone can support BLE central mode but not necessarily the exact peripheral/advertising capabilities needed for the chosen mesh topology.

## 4. Runtime lifecycle

For the first demo, keep the app visible and user-started. Provide clear Start Mesh / Stop Mesh controls and a visible session status.

If the product later needs to maintain connections while the user switches apps, investigate a foreground service with the `connectedDevice` type or Companion Device APIs as appropriate. Android places restrictions on starting foreground services from the background, and process death can terminate connections. Do not promise always-on mesh behaviour.

## 5. Battery and scan policy

- Avoid continuous high-duty scanning unless required.
- Use scan windows and pauses.
- Avoid repeated advertising restarts.
- Keep connection count bounded.
- Expose battery-conscious mode later if needed.
- Measure on physical phones rather than relying on emulator results.

## 6. Official references

- Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- BLE overview: https://developer.android.com/develop/connectivity/bluetooth/ble/ble-overview
- BLE background communication: https://developer.android.com/develop/connectivity/bluetooth/ble/background

Re-check these pages when implementing because Android platform behaviour and target-SDK requirements can change.
