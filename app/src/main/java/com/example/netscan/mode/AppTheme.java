package com.example.netscan.mode;

import androidx.appcompat.app.AppCompatDelegate;

// Fichier : mode/AppTheme.java
// Rôle : thème choisi par l'utilisateur (étape mode nuit). SYSTEM suit le
// réglage du téléphone ; LIGHT et DARK forcent le mode nuit ou le thème
// clair, quoi que fasse le système.
public enum AppTheme {

    SYSTEM,
    LIGHT,
    DARK;

    // Valeur attendue par AppCompatDelegate (et non par AndroidUiMode).
    public int toNightMode() {
        switch (this) {
            case LIGHT:
                return AppCompatDelegate.MODE_NIGHT_NO;
            case DARK:
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    // Tolérant : une valeur illisible ou absente retombe sur le système.
    public static AppTheme fromName(String name) {
        if (name == null) {
            return SYSTEM;
        }
        try {
            return valueOf(name);
        } catch (IllegalArgumentException e) {
            return SYSTEM;
        }
    }
}