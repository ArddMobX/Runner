plugins {
    // Последняя версия линейки 8.x. Девятка сознательно не взята: в AGP 9 удалён
    // legacy-путь applicationVariants, которым ниже переименовывается APK, а
    // публичной замены нет — в API androidComponents у BuiltArtifact только
    // геттер outputFile (проверено по байткоду gradle-api 8.13.2).
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // KSP нужен для кодогенерации Room. Версия обязана совпадать с версией Kotlin.
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
