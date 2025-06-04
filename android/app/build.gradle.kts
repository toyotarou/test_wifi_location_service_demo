plugins {
    id("com.android.application")
    id("kotlin-android")
    // Flutter Gradle プラグインは Android / Kotlin プラグインの後に適用
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.example.wifi_location_service_demo"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = "27.0.12077973"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    defaultConfig {
        applicationId = "com.example.wifi_location_service_demo"
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // `flutter run --release` でも動くよう、デバッグ鍵を暫定使用
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // ★ 位置情報を取得するための Google Play Services ライブラリ
    implementation("com.google.android.gms:play-services-location:21.0.1")
}

flutter {
    // Flutter プロジェクトのルートを示す
    source = "../.."
}
