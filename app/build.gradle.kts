plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

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
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "123456"
            keyAlias = "silentguardian"
            keyPassword = "123456"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
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
