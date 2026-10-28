import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

// Optional release signing, never committed: keystore.properties next to settings.gradle.kts
// (storeFile, storePassword, keyAlias, keyPassword) or AS_* environment variables.
val signingProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(key: String, env: String): String? = signingProps.getProperty(key) ?: System.getenv(env)
val releaseStoreFile = signingValue("storeFile", "AS_STORE_FILE")

// Optional contact e-mail for the free MyMemory translation service (raises its daily quota); not a secret.
// Set mymemory.email=... in local.properties or -PmymemoryEmail=...; empty by default.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
// v1.2: AniList login (OAuth implicit grant). The client id is public (not a secret) and empty by default: the login
// button then explains that it isn't activated yet. Set -PanilistClientId=12345 or anilist.clientId=12345 in local.properties.
val aniListClientId: String = ((project.findProperty("anilistClientId") as String?) ?: localProps.getProperty("anilist.clientId") ?: "").trim()
// v1.2: Google's TEST ad units only (never real ones in this build). -PadsEnabled=false removes the banner.
val adsEnabled: Boolean = ((project.findProperty("adsEnabled") as String?) ?: "true").toBoolean()
// v1.3: Google sign-in (Credential Manager). The Web client ID of Madyan's Google Cloud project (public, not a secret).
// It only works once that project also has an Android OAuth client for com.animeshadow.app + the signing SHA-1
// (76:29:9F:69:58:16:51:2D:43:E7:F0:DF:6D:1E:45:6C:11:D9:B2:BF) — see GOOGLE_LOGIN_AR.md. Override: -PgoogleWebClientId=… or
// google.webClientId=… in local.properties; -PgoogleWebClientId= (empty) builds the login as «not activated».
val googleWebClientId: String = ((project.findProperty("googleWebClientId") as String?) ?: localProps.getProperty("google.webClientId")
    ?: "674853191136-m6gc2oct6agk6bbm8kga5muvdco96ro3.apps.googleusercontent.com").trim()
val myMemoryEmail: String = (project.findProperty("mymemoryEmail") as String?) ?: localProps.getProperty("mymemory.email") ?: ""

android {
    namespace = "com.animeshadow.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.animeshadow.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
        buildConfigField("String", "MYMEMORY_EMAIL", "\"${myMemoryEmail.replace("\"", "")}\"")
        buildConfigField("String", "ANILIST_CLIENT_ID", "\"${aniListClientId.filter { it.isDigit() }}\"")
        buildConfigField("String", "ANILIST_REDIRECT", "\"animeshadow://anilist-auth\"")
        buildConfigField("boolean", "ADS_ENABLED", adsEnabled.toString())
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${googleWebClientId.replace("\"", "")}\"")
        buildConfigField("String", "ADS_BANNER_UNIT", "\"ca-app-pub-3940256099942544/9214589741\"")
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = signingValue("storePassword", "AS_STORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "AS_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "AS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // v1.1.1: R8 (shrink + optimise) and resource shrinking: smaller APK, faster start.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Without a release keystore the release build is signed with the same debug key as earlier versions,
            // so it installs over them as an update and keeps the user's data.
            signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.systemProperty("robolectric.graphicsMode", "NATIVE")
                it.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                it.systemProperty("roborazzi.test.record", "true")
                it.maxHeapSize = "1280m"
            }
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion", "MissingTranslation")
        xmlReport = true
        htmlReport = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/LICENSE*.md")
        // Store native libs compressed in the APK (smaller download; extracted once at install).
        jniLibs.useLegacyPackaging = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.play.services.ads)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
