plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.talkietalkie"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.talkietalkie"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        val supabaseUrl = providers.gradleProperty("SUPABASE_URL").getOrElse("")
        val supabaseAnonKey = providers.gradleProperty("SUPABASE_ANON_KEY").getOrElse("")

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "BUCKET", "\"talkie-pic\"")
        buildConfigField("int", "MAX_PHOTOS", "1000")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
}
