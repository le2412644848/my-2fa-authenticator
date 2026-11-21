import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// 读取 local.properties 中的签名配置（不入库）
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun resolveSigningProp(key: String): String? {
    val fromProps = keystoreProps.getProperty(key)?.takeIf { it.isNotBlank() }
    if (fromProps != null) return fromProps
    return System.getenv(key)?.takeIf { it.isNotBlank() }
}

android {
    namespace = "com.f2a.authenticator"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.f2a.authenticator"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("zh", "zh-rCN", "en")
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            val storePath = resolveSigningProp("RELEASE_STORE_FILE")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = resolveSigningProp("RELEASE_STORE_PASSWORD")
                keyAlias = resolveSigningProp("RELEASE_KEY_ALIAS")
                keyPassword = resolveSigningProp("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val releaseConfig = signingConfigs.getByName("release")
            if (releaseConfig.storeFile != null) {
                signingConfig = releaseConfig
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    lint {
        // 安全关键状态（PIN、锁定计数、自毁标记）必须同步落盘，commit() 为有意为之
        disable += "ApplySharedPref"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

gradle.taskGraph.whenReady {
    val needsReleaseSigning = allTasks.any { task ->
        task.project == project && (
            task.name.equals("assembleRelease", ignoreCase = true) ||
                task.name.equals("bundleRelease", ignoreCase = true) ||
                task.name.equals("packageRelease", ignoreCase = true)
            )
    }
    if (needsReleaseSigning) {
        val releaseConfig = android.signingConfigs.findByName("release")
        val store = releaseConfig?.storeFile
        if (store == null || !store.exists() ||
            releaseConfig.storePassword.isNullOrBlank() ||
            releaseConfig.keyAlias.isNullOrBlank() ||
            releaseConfig.keyPassword.isNullOrBlank()
        ) {
            throw GradleException(
                "Release 签名配置缺失或 keystore 文件不存在：请在 local.properties 或环境变量中配置 " +
                    "RELEASE_STORE_FILE、RELEASE_STORE_PASSWORD、RELEASE_KEY_ALIAS、RELEASE_KEY_PASSWORD。"
            )
        }
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // CameraX 原生相机硬件套件
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Compose BOM & UI
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Fragment 1.8.9：覆盖 biometric:1.2.0-alpha05 传递的旧版 fragment:1.2.5，防止 FragmentActivity 与 ActivityResultLauncher 的 16-bit requestCode 崩溃
    implementation("androidx.fragment:fragment:1.8.9")

    // Biometric
    implementation("androidx.biometric:biometric:1.2.0-alpha05")

    // Gson for JSON import/export & local storage
    implementation("com.google.code.gson:gson:2.11.0")

    // JVM Unit Tests (offline cached)
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
