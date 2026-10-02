plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.runner.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.runner.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Базовое имя архивов: Runner-*.apk / Runner-*.aab.
// Полное имя с версией задаётся ниже через androidComponents (AGP 8 API).
base {
    archivesName.set("Runner")
}

// Переименование APK в Runner-<versionName>-<buildType>.apk.
// Выбран официальный AGP 8 API: androidComponents.onVariants.
// Причина: applicationVariants + BaseVariantOutputImpl — внутренний API и deprecated в AGP 8,
// а один только base.archivesName даёт Runner-debug.apk без версии и не выполняет требование.
androidComponents {
    onVariants { variant ->
        val buildTypeName = variant.buildType ?: "unknown"
        // Flavor нет, versionName одинаков для всех вариантов — читаем из defaultConfig,
        // чтобы не зависеть от различий variant.versionName API между минорными AGP 8.x.
        val versionName = android.defaultConfig.versionName ?: "1.0"
        variant.outputs.forEach { output ->
            output.outputFileName.set("Runner-${versionName}-${buildTypeName}.apk")
        }
    }
}

// Генерация <имя>.apk.sha256 после сборки (формат как у sha256sum: "<hash>  <filename>").
tasks.register("generateApkSha256") {
    group = "build"
    description = "Генерирует .sha256 для каждого APK в build/outputs/apk"
    mustRunAfter("assembleDebug", "assembleRelease", "assemble")
    doLast {
        val outputsDir = layout.buildDirectory.dir("outputs/apk").get().asFile
        val apks = outputsDir.walkTopDown().filter { it.isFile && it.extension == "apk" }.toList()
        check(apks.isNotEmpty()) { "APK не найдены в $outputsDir" }
        apks.forEach { apk ->
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    digest.update(buf, 0, n)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val shaFile = java.io.File(apk.parentFile, "${apk.name}.sha256")
            shaFile.writeText("$hash  ${apk.name}\n")
            logger.lifecycle("SHA-256: ${apk.name} -> ${shaFile.name}")
        }
    }
}

// Чтобы ./gradlew assembleDebug / assembleRelease сразу давали и .sha256.
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
    finalizedBy("generateApkSha256")
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")

    // OkHttp 4.x for Groq API
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Zip4j for archive operations
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    // Room — история чатов
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // DataStore — настройки темы и приложения
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // MaterialKolor — генерация Material Design 3 ColorScheme из seed-цветов через Google Material Color Utilities
    implementation("com.materialkolor:material-kolor:1.7.1")

    // Debugging Compose
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
