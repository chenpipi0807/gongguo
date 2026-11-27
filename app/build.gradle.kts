plugins {
    id("com.android.application")
}

android {
    namespace = "com.nx.adcleaner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nx.adcleaner"
        minSdk = 26
        targetSdk = 36
        versionCode = 22
        versionName = "3.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}







