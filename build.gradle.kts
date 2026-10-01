plugins {
    id("com.android.application") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    // KSP нужен для кодогенерации Room. Версия обязана совпадать с версией Kotlin.
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}
