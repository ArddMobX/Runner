import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import java.io.File
import java.security.MessageDigest
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// ---------------------------------------------------------------------------
// Подпись release-сборки.
//
// Каждое значение берётся одним из двух способов:
//   1) RUNNER_ХХХ            — обычное свойство Gradle (локально, gradle.properties);
//   2) RUNNER_ХХХ_FILE       — путь к файлу со значением (в CI, где пароли
//                              передаются файлами: значение с обратным слешем,
//                              двоеточием или переводом строки сломало бы
//                              разбор файла свойств).
//
// Про окружение: в CI нужен префикс ORG_GRADLE_PROJECT_. Свои переменные с
// префиксом RUNNER_ использовать нельзя — GitHub Actions его резервирует, и
// переменная до шага не доходит. Именно на этом сборка падала: ключ
// расшифровывался, а Gradle его не видел и считал подпись ненастроенной.
//
// Секреты среды release-signing (Settings -> Environments):
//   KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
// ---------------------------------------------------------------------------
// Значения подписи берутся из файла свойств, путь к которому передаётся как
// -PsigningPropertiesFile. Так значения НЕ проходят через командную строку:
// GitHub подставляет секрет прямо в текст команды, и символы вроде кавычки,
// обратного слеша или $ ломают разбор строки шеллом — из-за этого пароль
// доходил до сборки, но не до SigningConfig.
//
// Для локальной сборки работает и обычный gradle.properties, и переменные
// окружения с префиксом ORG_GRADLE_PROJECT_.
val signingProps = Properties().apply {
    val path = findProperty("signingPropertiesFile")?.toString()
    if (!path.isNullOrBlank()) {
        val f = File(path)
        if (f.exists()) f.inputStream().use { load(it) }
        else println("::warning::Файл свойств подписи не найден: $path")
    }
}

fun secretValue(name: String): String {
    // Приоритет: файл свойств → свойство Gradle (gradle.properties, -P, env).
    val fromFile = signingProps.getProperty(name)?.trim()
    if (!fromFile.isNullOrBlank()) return fromFile
    val direct = findProperty(name)?.toString()?.trim()
    if (!direct.isNullOrBlank()) return direct
    val file = findProperty("${name}_FILE")?.toString() ?: return ""
    if (file.isBlank()) return ""
    val f = File(file)
    return if (f.exists()) f.readText().trim() else ""
}

val keystorePath: String = secretValue("RUNNER_KEYSTORE_PATH")
val keystorePassword: String = secretValue("RUNNER_KEYSTORE_PASSWORD")
val keyAlias: String = secretValue("RUNNER_KEY_ALIAS")
val keyPassword: String = secretValue("RUNNER_KEY_PASSWORD")
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
// Диагностика подписи. Печатается на этапе конфигурации и дополнительно
// дописывается в файл .ci/signing-debug.txt: логи шагов GitHub отдаёт только
// авторизованным, а этот файл workflow публикует в ветку ci-errors, которую
// можно прочитать без токена. Пароли показываются только длиной.
fun signingDiagnostics(): String = buildString {
    append("keyFileExists=").append(File(keystorePath).exists())
    append(" pathBlank=").append(keystorePath.isBlank())
    append(" storePassLen=").append(keystorePassword.length)
    append(" keyPassLen=").append(keyPassword.length)
    append(" keyAlias=").append(if (keyAlias.isBlank()) "ПУСТО" else keyAlias)
    append(" hasSigningConfig=").append(hasSigningConfig)
}

run {
    val line = signingDiagnostics()
    println("::notice::Подпись на этапе конфигурации: $line")
    try {
        val dir = File(rootProject.projectDir, ".ci")
        dir.mkdirs()
        File(dir, "signing-debug.txt").writeText(line + "\n")
    } catch (e: Exception) {
        println("::warning::Не удалось записать диагностику подписи: ${e.message}")
    }
}

gradle.taskGraph.whenReady {
    // Именно задачи сборки APK, а не любая с «Release» в имени.
    val wantsRelease = allTasks.any { it.name == "assembleRelease" || it.name == "packageRelease" }
    if (wantsRelease && !hasSigningConfig) {
        // Отчёт по каждому условию отдельно: иначе непонятно, что именно
        // не сошлось — путь, пароль или сам файл ключа.
        val report = buildString {
            appendLine("RUNNER_KEYSTORE_PATH    = '${keystorePath.ifBlank { "<пусто>" }}'")
            appendLine("  файл существует       : ${keystorePath.isNotBlank() && File(keystorePath).exists()}")
            appendLine("RUNNER_KEYSTORE_PASSWORD: ${if (keystorePassword.isBlank()) "ПУСТО" else "задан (" + keystorePassword.length + " симв.)"}")
            appendLine("RUNNER_KEY_ALIAS        : ${if (keyAlias.isBlank()) "ПУСТО" else "'$keyAlias'"}")
            appendLine("RUNNER_KEY_PASSWORD     : ${if (keyPassword.isBlank()) "ПУСТО" else "задан (" + keyPassword.length + " симв.)"}")
            append("рабочий каталог         : ${project.projectDir}")
        }
        throw GradleException(
            "Сборка release запрошена, но ключ подписи не настроен.\n" +
                    "Что видно сборке:\n$report\n" +
                    "В CI свойства приходят как ORG_GRADLE_PROJECT_RUNNER_* " +
                    "(префикс RUNNER_ в переменных GitHub Actions зарезервирован " +
                    "и до шага не доходит).\n" +
                    "Локально их можно положить в ~/.gradle/gradle.properties.\n" +
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
