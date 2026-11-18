plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.s2jarvis.ultra"
    compileSdk = 35
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    defaultConfig {
        applicationId = "com.s2jarvis.ultra"
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "4.0 Siri Style"
    }
}


// Keep Kotlin bytecode target aligned with Java 8, regardless of the installed JDK.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}
