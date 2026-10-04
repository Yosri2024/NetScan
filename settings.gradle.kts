// Fichier : NetScan/settings.gradle.kts
// Rôle : déclare les dépôts et les modules du projet (Étape 1).

pluginManagement {
    // Dépôts pour les plugins (AGP, etc.).
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Mode strict : les dépôts sont imposés ici (pas dans les modules).
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

// Module unique de l'application.
include(":app")
