plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // Firebase plugins disabled for AGP 8.13 compatibility
    // alias(libs.plugins.firebase.crashlytics)
    // alias(libs.plugins.firebase.perf)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

android {
    namespace = "com.mdaopay.app"
    compileSdk = 35

    flavorDimensions += "environment"

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
            buildConfigField("String", "BACKEND_URL", "\"http://10.0.2.2:8080\"")
            buildConfigField("String", "BUNDLER_URL", "\"${project.findProperty("BUNDLER_URL_DEV") ?: ""}\"")
            buildConfigField("String", "ETHERSCAN_API_KEY", "\"${project.findProperty("ETHERSCAN_API_KEY") ?: ""}\"")
            buildConfigField("String", "PAYMASTER_CONTRACT", "\"${project.findProperty("PAYMASTER_CONTRACT") ?: "0x0000000000000000000000000000000000000000"}\"")
            // F-023: RPC URLs — override with private RPC URLs via project properties
            buildConfigField("String", "RPC_URL_1", "\"${project.findProperty("RPC_URL_1_DEV") ?: "https://ethereum-sepolia.publicnode.com"}\"")
            buildConfigField("String", "RPC_URL_2", "\"${project.findProperty("RPC_URL_2_DEV") ?: "https://rpc.ankr.com/eth_sepolia"}\"")
            buildConfigField("String", "RPC_URL_3", "\"${project.findProperty("RPC_URL_3_DEV") ?: "https://ethereum-sepolia-rpc.com"}\"")
            // F-063: Passkey RP ID
            buildConfigField("String", "PASSKEY_RP_ID", "\"${project.findProperty("PASSKEY_RP_ID_DEV") ?: "mdaopay.app"}\"")
            // F-104: Relay URL
            buildConfigField("String", "RELAY_URL", "\"https://mdaopay-relay-dev.ekzent.workers.dev\"")
        }
        create("staging") {
            dimension = "environment"
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            buildConfigField("String", "BACKEND_URL", "\"https://staging-api.mdaopay.com\"")
            buildConfigField("String", "BUNDLER_URL", "\"${project.findProperty("BUNDLER_URL_STAGING") ?: ""}\"")
            buildConfigField("String", "ETHERSCAN_API_KEY", "\"${project.findProperty("ETHERSCAN_API_KEY") ?: ""}\"")
            buildConfigField("String", "PAYMASTER_CONTRACT", "\"${project.findProperty("PAYMASTER_CONTRACT_STAGING") ?: "0x0000000000000000000000000000000000000000"}\"")
            buildConfigField("String", "RPC_URL_1", "\"${project.findProperty("RPC_URL_1_STAGING") ?: "https://ethereum-sepolia.publicnode.com"}\"")
            buildConfigField("String", "RPC_URL_2", "\"${project.findProperty("RPC_URL_2_STAGING") ?: "https://rpc.ankr.com/eth_sepolia"}\"")
            buildConfigField("String", "RPC_URL_3", "\"${project.findProperty("RPC_URL_3_STAGING") ?: "https://ethereum-sepolia-rpc.com"}\"")
            buildConfigField("String", "PASSKEY_RP_ID", "\"${project.findProperty("PASSKEY_RP_ID_STAGING") ?: "mdaopay.app"}\"")
            buildConfigField("String", "RELAY_URL", "\"https://mdaopay-relay-staging.ekzent.workers.dev\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "BACKEND_URL", "\"https://api.mdaopay.com\"")
            buildConfigField("String", "BUNDLER_URL", "\"${project.findProperty("BUNDLER_URL_PROD") ?: ""}\"")
            buildConfigField("String", "ETHERSCAN_API_KEY", "\"${project.findProperty("ETHERSCAN_API_KEY") ?: ""}\"")
            buildConfigField("String", "PAYMASTER_CONTRACT", "\"${project.findProperty("PAYMASTER_CONTRACT_PROD") ?: ""}\"")
            buildConfigField("String", "RPC_URL_1", "\"${project.findProperty("RPC_URL_1_PROD") ?: ""}\"")
            buildConfigField("String", "RPC_URL_2", "\"${project.findProperty("RPC_URL_2_PROD") ?: ""}\"")
            buildConfigField("String", "RPC_URL_3", "\"${project.findProperty("RPC_URL_3_PROD") ?: ""}\"")
            buildConfigField("String", "PASSKEY_RP_ID", "\"${project.findProperty("PASSKEY_RP_ID_PROD") ?: "mdaopay.app"}\"")
            buildConfigField("String", "RELAY_URL", "\"https://mdaopay-relay.ekzent.workers.dev\"")
        }
    }

    defaultConfig {
        applicationId = "com.mdaopay.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // F-106: Certificate pinning hashes — replace with real values via CI/project properties
        buildConfigField("String", "CERT_PIN_API", "\"${project.findProperty("CERT_PIN_API") ?: "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="}\"")
        buildConfigField("String", "CERT_PIN_BACKUP", "\"${project.findProperty("CERT_PIN_BACKUP") ?: "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="}\"")
    }

    buildTypes {
        debug {
            versionNameSuffix = "-debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/FastDoubleParser-NOTICE"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/DISCLAIMER"
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/FastDoubleParser-LICENSE"
            excludes += "**/*.kotlin_module"
            excludes += "META-INF/services/*"
            excludes += "META-INF/ASL2.0"
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/FastDoubleParser-LGPL"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // Network
    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Lifecycle
    implementation(libs.androidx.lifecycle.process)

    // Security
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
    implementation(libs.androidx.play.integrity)
    implementation(libs.rootbeer)

    // Blockchain
    implementation(libs.web3j.core)
    implementation(libs.web3j.crypto)

    // Force BouncyCastle >= 1.80 for CVE safety
    constraints {
        implementation(libs.bcprov) {
            because("CVE-2024-30171, CVE-2023-33259 — fixed in bcprov-jdk18on 1.77+")
        }
    }

    // QR
    implementation(libs.zxing.core)
    implementation(libs.zxing.android)

    // Firebase (BoM controls all Firebase versions)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.perf)

    // WorkManager
    implementation(libs.androidx.work.runtime)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test)
    androidTestImplementation("androidx.work:work-testing:2.10.0")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("io.mockk:mockk-android:1.13.13")
}
