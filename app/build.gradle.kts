import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        // Versioning starts at major 2 with a build timestamp: "2.<yyMMdd.HHmm>".
        // versionCode = whole minutes since the Unix epoch (monotonic, fits Int until ~2065).
        versionCode = (System.currentTimeMillis() / 60000L).toInt()
        versionName = "2.${SimpleDateFormat("yyMMdd.HHmm", Locale.US).format(Date())}"

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