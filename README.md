This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
  - Development QA Workbench: `BOARDERLESS_QA_MODE=true ./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there. The simulator uses
  `127.0.0.1:3000` on the simulator; physical iPhone builds use the current development LAN host
  `192.168.68.65:3000` via the `sdk=iphoneos*` setting in `iosApp/Configuration/Config.xcconfig`.
  For another network, change that device setting or explicitly override `BOARDERLESS_BACKEND_HOST`
  when building. Rebuild/reinstall after changing it; allow Local Network access on the iPhone.
  The API and the storage host issued in signed tickets must both be reachable from the device.
  These HTTP/LAN settings are for development, not production deployment.
  The Xcode build phase automatically finds a system JDK, Android Studio, IntelliJ IDEA, or the JetBrains
  Toolbox runtime; if none is available it reports an actionable error.

### Development QA Workbench

The QA Workbench is excluded from the normal product UI unless the environment flag is explicitly enabled. On Desktop, start it with:

```bash
BOARDERLESS_QA_MODE=true ./gradlew :desktopApp:run
```

Windows PowerShell:

```powershell
$env:BOARDERLESS_QA_MODE = "true"
.\gradlew.bat :desktopApp:run
```

The `QA` control opens an acceptance questionnaire based on [`docs/產品 QA 品質檢驗 SOP.md`](docs/%E7%94%A2%E5%93%81%20QA%20%E5%93%81%E8%B3%AA%E6%AA%A2%E9%A9%97%20SOP.md). It can capture the BoarderLess window, attach existing PNG/JPEG evidence from mobile devices, and generate self-contained HTML and PDF reports in `Documents/BoarderLess QA`. Screenshot bytes and form answers remain in the QA session and exported files; they are not written into Workspace content or sent to the backend.

### Release packaging

See [Release 打包設定](docs/RELEASE_PACKAGING.md) for GitHub Actions releases, Android signing, and iOS simulator/archive/IPA packaging. Local iOS: `APP_VERSION=1.2.3 ./scripts/package-ios.sh`.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).
