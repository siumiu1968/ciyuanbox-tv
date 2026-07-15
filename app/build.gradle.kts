plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val tvKeystorePath = providers.environmentVariable("TV_KEYSTORE_PATH").orNull

android {
    namespace = "com.jing.sakura"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.codex.ciyuanbox.tv"
        minSdk = 21
        targetSdk = 34
        versionCode = 1011
        versionName = "2.8.1"

    }
    packaging {
        jniLibs {
            excludes.add("META-INF/*")
        }
        resources {
            excludes.add("META-INF/*")
        }
    }
    signingConfigs {
        create("release") {
            if (!tvKeystorePath.isNullOrBlank()) {
                storeFile = file(tvKeystorePath)
                storePassword = providers.environmentVariable("TV_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("TV_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("TV_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            signingConfig = if (tvKeystorePath.isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles("proguard-rules.pro")
        }
    }


    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    kotlin {
        jvmToolchain(17)
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.4"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
}

dependencies {

    val roomVersion = "2.6.1"
    val composeTvMaterialVersion = "1.0.1"
    val media3Version = "1.4.1"

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("androidx.leanback:leanback-tab:1.1.0-beta01")
    implementation("androidx.leanback:leanback-paging:1.1.0-alpha09") {
        exclude(group = "androidx.leanback", module = "leanback")
    }

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.5.4")

    implementation("com.google.accompanist:accompanist-permissions:0.30.1")

    // paging
    implementation("androidx.paging:paging-compose:3.3.0-alpha02")

    // compose tv
    implementation("androidx.tv:tv-material:$composeTvMaterialVersion")

    // room
    implementation("androidx.room:room-runtime:$roomVersion")
    annotationProcessor("androidx.room:room-compiler:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("androidx.room:room-paging:$roomVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui-leanback:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation(files("libs/mpv-android-lib-v0.1.10.aar"))


    implementation("io.coil-kt:coil:2.4.0")
    implementation("io.coil-kt:coil-compose:2.4.0")
    // 升级jsoup会导致android6中崩溃
    implementation("org.jsoup:jsoup:1.16.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")


    // koin
    implementation("io.insert-koin:koin-core:3.5.0")
    implementation("io.insert-koin:koin-android:3.5.0")
    implementation("io.insert-koin:koin-androidx-compose:3.4.5")

    // https://mvnrepository.com/artifact/com.google.zxing/core
    implementation("com.google.zxing:core:3.5.1")

    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")

    implementation("androidx.paging:paging-common-ktx:3.2.1")
    implementation("androidx.paging:paging-runtime-ktx:3.2.1")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.github.houbb:opencc4j:1.7.2")

    implementation("androidx.webkit:webkit:1.9.0")

    testImplementation("junit:junit:4.13.2")

}
