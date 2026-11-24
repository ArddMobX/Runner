# Add project specific ProGuard / R8 rules here.
# Сохраняем имена файлов и номера строк для читаемых отчётов о сбоях (CrashReportManager)
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod

-keepclassmembers class * {
    @androidx.annotation.Keep <fields>;
    @androidx.annotation.Keep <methods>;
}

# --- Kotlin & Coroutines ---
-keep class kotlin.Metadata { *; }
-dontwarn kotlinx.coroutines.debug.**
-dontwarn kotlinx.coroutines.flow.**

# --- Android Manifest components & Room database ---
-keep class com.runner.app.MainActivity { *; }
-keep class com.runner.app.ui.OverlayActivity { *; }
-keep class com.runner.app.service.** { *; }
-keep class com.runner.app.data.db.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# --- PDFBox-Android (com.tom-roush:pdfbox-android:2.0.27.0) ---
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
# Опциональные JPEG2000 / BouncyCastle / AWT классы, отсутствующие в Android runtime
-dontwarn com.gemalto.jp2.**
-dontwarn com.tom_roush.pdfbox.filter.JPXFilter
-dontwarn org.bouncycastle.**
-dontwarn org.spongycastle.**
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontnote sun.java2d.cmm.kcms.KcmsServiceProvider

# --- Zip4j (net.lingala.zip4j:zip4j:2.11.5) ---
-keep class net.lingala.zip4j.** { *; }
-dontwarn net.lingala.zip4j.**

# --- Jsoup (org.jsoup:jsoup:1.18.3) ---
-keep class org.jsoup.nodes.** { *; }
-dontwarn org.jsoup.**
-dontwarn org.jspecify.**
-dontwarn javax.annotation.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# --- Удаление отладочных логов в релизных минифицированных сборках (warn/error сохраняются) ---
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
