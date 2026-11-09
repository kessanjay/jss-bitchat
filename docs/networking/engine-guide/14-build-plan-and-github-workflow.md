# 14 — Local Build Plan and GitHub Workflow (Windows)

## 1. Prerequisites

- Android Studio installed.
- JDK version required by the project's Gradle/Android Gradle Plugin.
- Android SDK and platform tools.
- Git for Windows.
- Google Antigravity configured to open the repository.
- At least two physical Android phones for initial BLE work; four for the final mesh demo.

Use the Gradle wrapper supplied by Android Studio. Do not install an arbitrary Gradle version globally.

## 2. Create/verify the Android project

If the repository currently contains only Markdown documentation:
1. Open Android Studio.
2. Create a new Empty Activity project or use the existing project wizard.
3. Name: JSS BitChat.
4. Package: `com.jss.bitchat`.
5. Language: Kotlin.
6. Minimum SDK: API 26.
7. Choose Jetpack Compose if offered.
8. Create it in the existing repository folder only if Android Studio supports safely adding the project without overwriting docs; otherwise create in a temporary folder and copy the generated app/Gradle files carefully.
9. Verify `gradlew.bat` exists.
10. Run the app on an emulator/device before networking implementation.

Do not replace the repository root with a second nested Git repository.

## 3. Windows PowerShell build commands

From the repository root:

```powershell
# Check repository state
git status
git branch --show-current

# Build debug app
.\gradlew.bat assembleDebug

# Run JVM unit tests
.\gradlew.bat testDebugUnitTest

# Run Android lint (if configured)
.\gradlew.bat lintDebug
```

If the Gradle wrapper is not present, first create/complete the Android Studio project. Do not run these commands from the docs-only repository.

## 4. Suggested branch plan

Use the actual default branch name shown by `git branch --show-current`.

```powershell
git switch -c feature/network-protocol
```

After protocol milestone:
```powershell
git add .
git commit -m "Implement versioned mesh packet protocol"
git push -u origin feature/network-protocol
```

Other networking branches:
- `feature/mesh-routing`
- `feature/ble-discovery`
- `feature/ble-gatt-transport`
- `feature/ble-framing`
- `feature/network-integration`

Avoid having multiple branches edit the same core files simultaneously.

## 5. Commit sequence

1. Add protocol models and tests.
2. Add codec and validator tests.
3. Add mesh simulator and routing.
4. Add BLE permission/capability checks.
5. Add scan/advertising.
6. Add GATT client/server and two-phone tests.
7. Add fragmentation/reassembly.
8. Integrate BLE transport with mesh engine.
9. Add four-phone tests and diagnostics.
10. Integrate UI and persistence contracts.

## 6. Before every push

```powershell
git status
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
git diff --check
git add .
git commit -m "Describe the completed milestone"
git push
```

Use pull requests and review changes. Do not commit secrets, signing keys, local SDK paths, private test data or generated build outputs.
