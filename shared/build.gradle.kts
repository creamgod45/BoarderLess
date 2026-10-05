import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// Foundation integration (NSFileCoordinator) needs the simulator's system services.
// Boot a simulator first and optionally select it using iosSimulatorArm64Test --device <UUID>.
// Standalone spawn lacks these services; do not skip native coordination tests to hide that.
tasks.withType<KotlinNativeSimulatorTest>().configureEach {
    standalone.set(false)
}

/**
 * Generates the `Strings` object from src/commonMain/composeResources/files/i18n/en.json, so every
 * namespaced key is a compile-checked Kotlin reference that carries its English default:
 *   {"status": {"opening": "Opening {title}…"}}  ->  Strings.status.opening(title)
 * A string leaf becomes a `LocalizedText` (no placeholders) or a function (one parameter per
 * `{name}`); an object holding only plural forms ("one"/"other") becomes `fun name(count: Int, …)`.
 */
@CacheableTask
abstract class GenerateI18nStrings : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val englishCatalog: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        @Suppress("UNCHECKED_CAST")
        val root = groovy.json.JsonSlurper().parse(englishCatalog.get().asFile, "UTF-8") as Map<String, Any?>
        val pluralForms = setOf("zero", "one", "two", "few", "many", "other")
        val keywords = setOf(
            "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface",
            "is", "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "when", "while",
        )
        val placeholder = Regex("""\{(\w+)\}""")
        fun id(name: String) = if (name in keywords) "`$name`" else name
        fun literal(text: String) = "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        fun params(vararg texts: String) = texts.flatMap { text -> placeholder.findAll(text).map { it.groupValues[1] } }.distinct()
        fun isPlural(value: Map<*, *>) = "other" in value && value.keys.all { it in pluralForms } && value.values.all { it is String }

        val out = StringBuilder()
        out.append("// Generated from composeResources/files/i18n/en.json by :shared:generateI18nStrings. Do not edit.\n")
        out.append("@file:Suppress(\"ClassName\", \"unused\")\n\npackage cg.creamgod.boarderless.i18n\n\n")
        fun emit(name: String, node: Map<*, *>, path: String, indent: String) {
            out.append("${indent}object ${id(name)} {\n")
            node.forEach { (rawKey, value) ->
                val key = rawKey as String
                val fullKey = if (path.isEmpty()) key else "$path.$key"
                val inner = "$indent    "
                when {
                    value is String -> {
                        val names = params(value)
                        if (names.isEmpty()) {
                            out.append("${inner}val ${id(key)} = LocalizedText(${literal(fullKey)}, ${literal(value)})\n")
                        } else {
                            val signature = names.joinToString { "${id(it)}: Any?" }
                            val args = names.joinToString { "${literal(it)} to ${id(it)}" }
                            out.append("${inner}fun ${id(key)}($signature): String = tr(${literal(fullKey)}, ${literal(value)}, $args)\n")
                        }
                    }
                    value is Map<*, *> && isPlural(value) -> {
                        val other = value["other"] as String
                        val one = value["one"] as String? ?: other
                        val names = params(one, other) - "count"
                        val signature = (listOf("count: Int") + names.map { "${id(it)}: Any?" }).joinToString()
                        val args = names.joinToString("") { ", ${literal(it)} to ${id(it)}" }
                        out.append("${inner}fun ${id(key)}($signature): String =\n")
                        out.append("$inner    trPlural(${literal(fullKey)}, ${literal(one)}, ${literal(other)}, count$args)\n")
                    }
                    value is Map<*, *> -> emit(key, value, fullKey, inner)
                    else -> throw GradleException("Unsupported value at $fullKey in en.json")
                }
            }
            out.append("$indent}\n")
        }
        emit("Strings", root, "", "")
        val target = outputDirectory.get().file("cg/creamgod/boarderless/i18n/Strings.kt").asFile
        target.parentFile.mkdirs()
        target.writeText(out.toString())
    }
}

val generateI18nStrings = tasks.register<GenerateI18nStrings>("generateI18nStrings") {
    englishCatalog.set(layout.projectDirectory.file("src/commonMain/composeResources/files/i18n/en.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/i18n/commonMain/kotlin"))
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask { useKarma { useChromeHeadless() } }
        }
        binaries.executable()
    }
    
    android {
       namespace = "cg.creamgod.boarderless.shared"
       compileSdk = libs.versions.android.compileSdk.get().toInt()
       minSdk = libs.versions.android.minSdk.get().toInt()
    
       compilerOptions {
           jvmTarget = JvmTarget.JVM_11
       }
       androidResources {
           enable = true
       }
       withHostTest {
           isIncludeAndroidResources = true
       }
       withDeviceTestBuilder {
           sourceSetTreeName = "test"
       }.configure {
           instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
       }
    }
    
    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.startup.runtime)
            implementation(libs.glide.gifdecoder)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.compose.uiTooling)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
        }
        commonMain {
            kotlin.srcDir(generateI18nStrings)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.engineDefaults)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.multiplatform.settings.noArg)
            implementation(libs.kotlincrypto.sha2)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}

// Gradle's test worker need not expose URLClassLoader URLs. Separate JVM fixtures must
// use the actual test runtime classpath, not the worker bootstrap jar or production prefs.
tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    if (name == "testAndroidHostTest") {
        val appApk = project(":androidApp").layout.buildDirectory.file("outputs/apk/debug/androidApp-debug.apk")
        dependsOn(":androidApp:assembleDebug")
        inputs.file(appApk)
        doFirst {
            (this as org.gradle.api.tasks.testing.Test).systemProperty("boarderless.test.android.apk", appApk.get().asFile.absolutePath)
        }
    }
    if (name == "jvmTest") {
        val liveMedia = providers.environmentVariable("BOARDERLESS_MEDIA_LIVE_ACCEPTANCE").orElse("false")
        inputs.property("mediaLiveAcceptance", liveMedia)
        inputs.property("mediaLiveBaseUrl", providers.environmentVariable("BOARDERLESS_MEDIA_LIVE_BASE_URL").orElse("unset"))
        listOf("USER", "WORKSPACE", "ASSET").forEach { field ->
            inputs.property("mediaLiveDownload$field", providers.environmentVariable("BOARDERLESS_MEDIA_LIVE_DOWNLOAD_$field").orElse("unset"))
        }
        inputs.files(rootProject.fileTree("backend/tests/fixtures/media"))
        // The backend's current state is not a Gradle input: a cached test report can never
        // certify a new live run. Ordinary runs leave the opt-in case explicitly skipped.
        outputs.upToDateWhen { liveMedia.get() != "true" }
        outputs.doNotCacheIf("Live media acceptance must actually contact the backend") { liveMedia.get() == "true" }
    }
    if (name == "jvmTest") doFirst {
        val testTask = this as org.gradle.api.tasks.testing.Test
        testTask.systemProperty("boarderless.test.classpath", testTask.classpath.asPath)
    }
}

// Karma loads these scripts outside the compiled Kotlin test bundle.
tasks.matching { it.name == "jsBrowserTest" || it.name == "wasmJsBrowserTest" }.configureEach {
    inputs.file(rootProject.layout.projectDirectory.file("webApp/src/webMain/resources/media-import.js"))
    inputs.file(rootProject.layout.projectDirectory.file("webApp/src/webMain/resources/video-playback.js"))
    inputs.file(layout.projectDirectory.file("src/webTest/resources/media-import-fixture.js"))
}
