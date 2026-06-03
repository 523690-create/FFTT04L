import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.FFTT04M"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.FFTT04M"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    lint {
        disable.add("MissingPermission")
        abortOnError = false
    }
}

// Rename APK
project.afterEvaluate {
    val extension = project.extensions.getByName("android")
    if (extension is com.android.build.gradle.AppExtension) {
        extension.applicationVariants.all {
            outputs.all {
                if (this is com.android.build.gradle.internal.api.BaseVariantOutputImpl) {
                    val timestamp = SimpleDateFormat("yyyyMMdd-HHmm").format(Date())
                    outputFileName = "FFTT04M-$timestamp.apk"
                }
            }
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}