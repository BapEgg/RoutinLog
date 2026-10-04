import java.net.URI
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asJavaLiteral() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r") + "\""

// Machine-only public identifiers; local.properties is excluded from version control.
val localSettings = Properties().apply {
    val settingsFile = rootProject.file("local.properties")
    if (settingsFile.exists()) settingsFile.inputStream().use { load(it) }
}

val releaseApiUrl = providers.gradleProperty("routinlog.apiBaseUrl")
    .orElse(providers.environmentVariable("ROUTINLOG_API_BASE_URL"))
    .orElse("").get().trim()
val debugApiUrl = providers.gradleProperty("routinlog.debugApiBaseUrl")
    .orElse("http://10.0.2.2:8080/").get().trim()
// OAuth web client IDs are public identifiers. Client secrets must never be packaged in the app.
val googleWebClientId = providers.gradleProperty("routinlog.googleWebClientId")
    .orElse(providers.environmentVariable("GOOGLE_WEB_CLIENT_ID"))
    .orElse(localSettings.getProperty("routinlog.googleWebClientId", "")).get().trim()
val signingPath=providers.environmentVariable("ROUTINLOG_KEYSTORE").orElse("").get()
val signingStorePassword=providers.environmentVariable("ROUTINLOG_STORE_PASSWORD").orElse("").get()
val signingAlias=providers.environmentVariable("ROUTINLOG_KEY_ALIAS").orElse("").get()
val signingKeyPassword=providers.environmentVariable("ROUTINLOG_KEY_PASSWORD").orElse("").get()
val privacyUrl=providers.environmentVariable("ROUTINLOG_PRIVACY_URL").orElse("").get()
val termsUrl=providers.environmentVariable("ROUTINLOG_TERMS_URL").orElse("").get()
val supportEmail=providers.environmentVariable("ROUTINLOG_SUPPORT_EMAIL").orElse("").get()

android {
    namespace = "com.bapegg.routinlog"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.bapegg.routinlog"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", googleWebClientId.asJavaLiteral())
        buildConfigField("String", "PRIVACY_URL", privacyUrl.asJavaLiteral())
        buildConfigField("String", "TERMS_URL", termsUrl.asJavaLiteral())
        buildConfigField("String", "SUPPORT_EMAIL", supportEmail.asJavaLiteral())
    }

    signingConfigs {
        if(signingPath.isNotBlank())create("production") {
            storeFile=file(signingPath)
            storePassword=signingStorePassword
            keyAlias=signingAlias
            keyPassword=signingKeyPassword
        }
    }

    buildTypes {
        debug {
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", debugApiUrl.asJavaLiteral())
        }
        release {
            if(signingPath.isNotBlank())signingConfig=signingConfigs.getByName("production")
            isMinifyEnabled = true
            buildConfigField("String", "API_BASE_URL", releaseApiUrl.asJavaLiteral())
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    doFirst {
        val endpoint = runCatching { URI(releaseApiUrl) }.getOrNull()
        require(endpoint?.scheme == "https" && !endpoint.host.isNullOrBlank() &&
            endpoint.userInfo == null && endpoint.query == null && endpoint.fragment == null &&
            releaseApiUrl.endsWith("/")) {
            "Release requires routinlog.apiBaseUrl / ROUTINLOG_API_BASE_URL: a real HTTPS base URL ending in /."
        }
        require(googleWebClientId.endsWith(".apps.googleusercontent.com")){"Release requires the production Google web client ID."}
        require(signingPath.isNotBlank()&&file(signingPath).isFile&&signingStorePassword.isNotBlank()&&signingAlias.isNotBlank()&&signingKeyPassword.isNotBlank()) {
            "Release requires ROUTINLOG_KEYSTORE, ROUTINLOG_STORE_PASSWORD, ROUTINLOG_KEY_ALIAS and ROUTINLOG_KEY_PASSWORD."
        }
        require(listOf(privacyUrl,termsUrl).all { val u=runCatching { URI(it) }.getOrNull();u?.scheme=="https"&&!u.host.isNullOrBlank()&&u.userInfo==null }&&supportEmail.contains('@')) {
            "Release requires published ROUTINLOG_PRIVACY_URL, ROUTINLOG_TERMS_URL and ROUTINLOG_SUPPORT_EMAIL."
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("com.google.mlkit:text-recognition-korean:16.0.1")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.android.gms:play-services-fitness:21.2.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Stable releases verified against the Android/Google Identity release notes.
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    // 1.2.1 requires Kotlin metadata 2.4; use the release compatible with AGP's Kotlin compiler.
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
