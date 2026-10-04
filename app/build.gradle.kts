// Fichier : NetScan/app/build.gradle.kts
// Rôle : configuration du module applicatif (Étape 1 : base qui compile).

plugins {
    alias(libs.plugins.android.application)
}

android {
    // SDK stables : Android 16 (API 36), minSdk 24 selon la spec.
    namespace = "com.example.netscan"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.example.netscan"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
        // Runner des tests instrumentés (Étape 4, sur téléphone).
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            // R8/ProGuard activé en release (exigence sécurité de la spec).
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        // Java 17 imposé par la spec.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        // UI en XML avec ViewBinding (architecture imposée).
        viewBinding = true
    }
}

dependencies {
    // Étape 1 : uniquement l'UI de base. Room/SQLCipher/DataStore/Gson
    // sont déclarés dans le catalogue et seront ajoutés à l'Étape 4.
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.activity)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // Étape 4 : historique chiffré + réglages + export.
    implementation(libs.room.runtime)
    implementation(libs.androidx.sqlite)
    implementation(libs.sqlcipher.android)
    implementation(libs.datastore.preferences)
    implementation(libs.gson)
    annotationProcessor(libs.room.compiler)

    // Tests unitaires locaux (Étapes 2-3) : JUnit 4.
    testImplementation(libs.junit)
    // Tests instrumentés (Étape 4, sur téléphone) : runner + JUnit.
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
}
