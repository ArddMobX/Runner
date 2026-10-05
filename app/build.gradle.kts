import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.File
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// Подпись release-сборки.
//
// Ключ и пароли приходят из окружения: в CI — из секретов GitHub, локально —
// из ~/.gradle/gradle.properties. В репозиторий они не попадают.
//
// Секреты (Settings -> Secrets and variables -> Actions):
//   KEYSTORE_BASE64     — keystore, закодированный в base64
//   KEYSTORE_PASSWORD   — пароль хранилища
//   KEY_ALIAS           — имя ключа
//   KEY_PASSWORD        — пароль ключа
// ---------------------------------------------------------------------------
val keystorePath: String = findProperty("RUNNER_KEYSTORE_PATH")?.toString().orEmpty()
val keystorePassword: String = findProperty("RUNNER_KEYSTORE_PASSWORD")?.toString().orEmpty()
val keyAlias: String = findProperty("RUNNER_KEY_ALIAS")?.toString().orEmpty()
val keyPassword: String = findProperty("RUNNER_KEY_PASSWORD")?.toString().orEmpty()
val hasSigningConfig = keystorePath.isNotBlank() &&
        keystorePassword.isNotBlank() &&
        keyAlias.isNotBlank() &&
        keyPassword.isNotBlank() &&
        File(keystorePath).exists()

android {
    namespace = "com.runner.app"
    // 35 (Android 15). AGP 8.13.2 поддерживает и 36, но поднимать сразу два
    // уровня рискованно: у каждого свои изменения поведения, а проверить
    // на устройстве сразу два не получится.
    compileSdk = 35

    defaultConfig {
        applicationId = "com.runner.app"
        minSdk = 26
        targetSdk = 35
        // Номера версий приходят из CI, чтобы каждый релиз был новее предыдущего.
        // Локально берутся значения по умолчанию.
        versionCode = (findProperty("runnerVersionCode") as? String)?.toIntOrNull() ?: 1
        versionName = (findProperty("runnerVersionName") as? String) ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = File(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
                // v1 обязателен для Android 8 (minSdk 26), v2/v3 — для новых.
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            // R8 выключен сознательно: релиз с подписью выходит впервые, и
            // ломать его минификацией в том же шаге — способ не понять, что
            // именно сломалось. Включать отдельным коммитом и проверять на устройстве.
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
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

// Неподписанный release ставить на устройство нельзя, и молча собирать его
// нельзя тем более: без этой проверки assembleRelease на машине без ключа
// выдаёт APK, который выглядит готовым, но никуда не устанавливается.
gradle.taskGraph.whenReady {
    // Именно задачи сборки APK, а не любая с «Release» в имени.
    val wantsRelease = allTasks.any { it.name == "assembleRelease" || it.name == "packageRelease" }
    if (wantsRelease && !hasSigningConfig) {
        throw GradleException(
            "Сборка release запрошена, но ключ подписи не настроен.\n" +
                    "Ожидаются свойства: RUNNER_KEYSTORE_PATH, RUNNER_KEYSTORE_PASSWORD, " +
                    "RUNNER_KEY_ALIAS, RUNNER_KEY_PASSWORD.\n" +
                    "В CI они собираются из секретов KEYSTORE_BASE64, KEYSTORE_PASSWORD, " +
                    "KEY_ALIAS, KEY_PASSWORD. Локально их можно положить в " +
                    "~/.gradle/gradle.properties.\n" +
                    "Для обычной разработки используйте assembleDebug."
        )
    }
}

// Базовое имя архивов: Runner-*.apk / Runner-*.aab.
// Полное имя с версией задаётся ниже через applicationVariants.
base {
    archivesName.set("Runner")
}

// Переименование APK в Runner-<versionName>-<buildType>.apk.
//
// Почему legacy-путь, хотя он и устарел. Публичной замены в androidComponents
// нет: у BuiltArtifact в gradle-api есть только геттер getOutputFile(), а у
// VariantOutput свойства outputFileName нет вовсе — проверено разбором байткода
// gradle-api 8.4.2 и 8.13.2. Класс BaseVariantOutputImpl жив и в 8.13.2
// (com/android/build/gradle/internal/api/BaseVariantOutputImpl), поэтому способ
// доживает до конца линейки 8.x.
//
// ВАЖНО для будущего апгрейда на AGP 9: там legacy-путь удалён, и этот блок
// нужно заменить на трансформацию артефактов (BuiltArtifactsTransformation)
// либо отказаться от переименования и разбирать фактическое имя из
// outputs/apk (например, в generateApkSha256 ниже). Молчаливой поломки тут
// быть не должно: исчезнувший класс — это ошибка компиляции, а не тихая смена имени.
android.applicationVariants.all {
    val buildTypeName = buildType.name
    val variantVersion = versionName ?: "1.0"
    outputs.all {
        (this as BaseVariantOutputImpl).outputFileName =
            "Runner-${variantVersion}-${buildTypeName}.apk"
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
            val digest = MessageDigest.getInstance("SHA-256")
            apk.inputStream().use { input ->
                val buf = ByteArray(8192)
                var n: Int
                while (input.read(buf).also { n = it } != -1) {
                    digest.update(buf, 0, n)
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val shaFile = File(apk.parentFile, "${apk.name}.sha256")
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
