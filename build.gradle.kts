// Fichier : NetScan/build.gradle.kts
// Rôle : plugins communs à tous les modules (Étape 1).

plugins {
    // Android Gradle Plugin, version stable vérifiée (sept. 2026).
    alias(libs.plugins.android.application) apply false
}
