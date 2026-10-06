plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI passes the build number so every APK can be installed over the previous one.
val buildNumber = (System.getenv("MESHGEN_BUILD_NUMBER") ?: "1").toInt()

// Private release key (from GitHub secrets) if configured; otherwise the public test key in /signing.
val releaseKeystorePath: String? = System.getenv("MESHGEN_KEYSTORE_FILE")?.takeIf { it.isNotBlank() }

android {
    namespace = "com.meshgen.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.meshgen.app"
        minSdk = 29
        targetSdk = 35
        versionCode = buildNumber
        versionName = "0.0.$buildNumber"

        ndk {
            // All phones that can run the on-device models are 64-bit ARM.
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared")
            }
        }
    }

    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    packaging {
        jniLibs {
            // llama.cpp picks the best CPU variant at runtime by loading .so files from disk.
            useLegacyPackaging = true
        }
    }

    signingConfigs {
        create("test") {
            storeFile = rootProject.file("signing/meshgen-test.jks")
            storePassword = "meshgen-test"
            keyAlias = "meshgen-test"
            keyPassword = "meshgen-test"
        }
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("MESHGEN_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MESHGEN_KEY_ALIAS")
                keyPassword = System.getenv("MESHGEN_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            signingConfig = signingConfigs.getByName("test")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("test")
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
}

// Pinned llama.cpp source (see tools/fetch_llama_cpp.sh); skipped when already present.
val fetchLlamaCpp by tasks.registering(Exec::class) {
    commandLine("bash", rootProject.file("tools/fetch_llama_cpp.sh").absolutePath)
}
tasks.named("preBuild") { dependsOn(fetchLlamaCpp) }

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
