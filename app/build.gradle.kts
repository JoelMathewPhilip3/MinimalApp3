plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.joel.minimallauncher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.joel.minimallauncher"
        minSdk = 26
        targetSdk = 36
        versionCode = 8
        versionName = "8.0.0"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val verifyBibleAssets by tasks.registering {
    group = "verification"
    description = "Requires the bundled full KJV database and 1,095 reference-only reading plans."
    doLast {
        val database = file("src/main/assets/kjv.sqlite")
        val plans = file("src/main/assets/daily_reading_refs.json")
        require(database.exists() && database.length() > 5_000_000L) {
            "Missing or incomplete src/main/assets/kjv.sqlite"
        }
        require(plans.exists()) { "Missing src/main/assets/daily_reading_refs.json" }
        val text = plans.readText()
        val mainCount = Regex("\\\"main\\\"\\s*:").findAll(text).count()
        require(mainCount == 1_095) {
            "Daily reading plan contains $mainCount entries; expected 1,095."
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyBibleAssets)
}
