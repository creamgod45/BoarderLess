import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)
    implementation(libs.ktor.client.core)
    // Native macOS menu bar parts that Swing cannot express (see MacNativeMenus).
    implementation("net.java.dev.jna:jna:5.17.0")
    // COM helpers for the Windows jump list (see WindowsJumpList).
    implementation("net.java.dev.jna:jna-platform:5.17.0")

    implementation(libs.compose.uiToolingPreview)

    testImplementation(kotlin("test"))
    // Only the host's FFmpeg/JNI binaries, not JavaCV's unrelated OpenCV/camera bundles.
    implementation("org.bytedeco:javacv:1.5.14") { isTransitive = false }
    implementation("org.bytedeco:javacpp:1.5.14")
    implementation("org.bytedeco:ffmpeg:8.1.2-1.5.14")
    val nativeOs = when {
        System.getProperty("os.name").startsWith("Mac") -> "macosx"
        System.getProperty("os.name").startsWith("Windows") -> "windows"
        System.getProperty("os.name").startsWith("Linux") -> "linux"
        else -> error("Unsupported desktop video OS")
    }
    val nativeArch = when (System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "arm64"
        "amd64", "x86_64" -> "x86_64"
        else -> error("Unsupported desktop video architecture")
    }
    runtimeOnly("org.bytedeco:javacpp:1.5.14:$nativeOs-$nativeArch")
    runtimeOnly("org.bytedeco:ffmpeg:8.1.2-1.5.14:$nativeOs-$nativeArch")
}

compose.desktop {
    application {
        mainClass = "cg.creamgod.boarderless.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb, TargetFormat.Rpm)
            packageName = "BoarderLess"
            packageVersion = providers.gradleProperty("appVersion").orElse("1.0.0").get()
            macOS {
                bundleID = "cg.creamgod.boarderless"
                iconFile.set(project.file("icons/app.icns"))
                infoPlist {
                    // Declared languages decide the language of macOS's own menu items (Window > Fill, Center, …).
                    extraKeysRawXml = """
                        <key>CFBundleLocalizations</key>
                        <array>
                            <string>en</string>
                            <string>zh-Hant</string>
                        </array>
                    """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.file("icons/app.ico"))
            }
            linux {
                iconFile.set(project.file("icons/app.png"))
            }
        }
    }
}

// Localized Finder/Dock names. jpackage cannot add Contents/Resources/*.lproj, so they are copied into
// the app image, which packageDmg then packages.
tasks.withType<org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask>()
    .matching { it.name.startsWith("create") && it.name.endsWith("Distributable") }
    .configureEach {
        val macLocalizations = project.file("macos/localizations")
        val appImageDir = destinationDir
        doLast {
            val resources = appImageDir.get().asFile.resolve("BoarderLess.app/Contents/Resources")
            if (resources.isDirectory) macLocalizations.copyRecursively(resources, overwrite = true)
        }
    }
