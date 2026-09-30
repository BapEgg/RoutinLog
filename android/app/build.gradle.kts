import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun String.asJavaLiteral() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r") + "\""

val releaseApiUrl = providers.gradleProperty("routinlog.apiBaseUrl")
    .orElse(providers.environmentVariable("ROUTINLOG_API_BASE_URL"))
    .orElse("").get().trim()
val debugApiUrl = providers.gradleProperty("routinlog.debugApiBaseUrl")
    .orElse("http://10.0.2.2:8080/").get().trim()

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
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            buildConfigField("String", "API_BASE_URL", debugApiUrl.asJavaLiteral())
        }
        release {
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
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
