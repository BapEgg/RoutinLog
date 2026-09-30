plugins {
    id("com.android.application") version "9.1.1" apply false
    // AGP supplies Kotlin 2.2.10; a separate kotlin-android plugin would conflict.
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
