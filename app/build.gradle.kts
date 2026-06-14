import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// [Failsafe] Signing credentials are loaded from app/keystore.properties (gitignored).
// Community contributors who clone without it still get a buildable release variant
// (just unsigned), so the repo can be public without leaking the production key.
val keystoreProperties = Properties().apply {
    val f = rootProject.file("app/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigningConfig = keystoreProperties.containsKey("storeFile")

android {
    namespace = "com.yestek.silentguardian"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yestek.silentguardian"
        minSdk = 24
        targetSdk = 34
        versionCode = 14
        versionName = "0.1.12"
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Falls back to unsigned when keystore.properties is absent, so the repo
            // remains buildable without the production signing key.
            signingConfig = if (hasSigningConfig) signingConfigs.getByName("release") else null
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    // ViewBinding generates Java code, which triggers javac and fails on JDK 26 due to jlink bug.
    // We will use standard findViewById instead.
    buildFeatures {
        viewBinding = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // 第三方核心基础库
    implementation("com.tencent:mmkv:1.3.3") // 极速本地存储
    implementation("com.github.getActivity:XXPermissions:18.6") // 权限管理框架
    implementation("com.blankj:utilcodex:1.31.1") // 基础工具库
    implementation("com.microsoft.clarity:clarity:3.+") // Microsoft Clarity

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
