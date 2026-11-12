plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}
// GitHub Actions may define API_BASE_URL as an empty string when the repository
// variable is unset. Provider.orElse() does not treat an empty value as missing.
val configuredApiBaseUrl = (
    providers.environmentVariable("API_BASE_URL").orNull
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?: "https://airdrop-hunter-z6qn.onrender.com/"
    ).let { if (it.endsWith("/")) it else "$it/" }

val configuredCommunitySnapshotBaseUrl = providers.environmentVariable("COMMUNITY_SNAPSHOT_BASE_URL").orNull?.trim()?.trimEnd('/').orEmpty()
if (configuredCommunitySnapshotBaseUrl.isNotEmpty()) {
    require(configuredCommunitySnapshotBaseUrl.startsWith("https://", ignoreCase = true)) {
        "COMMUNITY_SNAPSHOT_BASE_URL must use HTTPS."
    }
    require(!configuredCommunitySnapshotBaseUrl.contains('"') && !configuredCommunitySnapshotBaseUrl.contains('\\')) {
        "COMMUNITY_SNAPSHOT_BASE_URL contains invalid characters."
    }
}

val apiBaseScheme = configuredApiBaseUrl.substringBefore("://", "")
val apiBaseHost = configuredApiBaseUrl.substringAfter("://", "").substringBefore("/")
require(apiBaseScheme in setOf("http", "https") && apiBaseHost.isNotBlank()) {
    "API_BASE_URL must be a valid HTTP(S) URL with a host."
}

android {
    namespace = "com.airdrophunter.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.airdrophunter.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.10.0"
        buildConfigField("String", "REOWN_PROJECT_ID", "\"110ea7e938d97b015f5de7b326e20dd9\"")
        buildConfigField("String", "COMMUNITY_SNAPSHOT_BASE_URL", "\"${configuredCommunitySnapshotBaseUrl}\"")
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${configuredApiBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        )
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("com.google.accompanist:accompanist-navigation-material:0.36.0")
    implementation(platform("com.reown:android-bom:1.3.3"))
    implementation("com.reown:android-core")
    implementation("com.reown:appkit")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
