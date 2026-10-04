package com.example.netscan.mode;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatDelegate;

import com.example.netscan.data.SettingsRepository;

// Fichier : mode/ThemeManager.java
// Rôle : mode nuit. Le thème choisi par l'utilisateur est stocké
// CHIFFRÉ dans le DataStore (comme le mode) puis appliqué via
// AppCompatDelegate, qui recrée les activités à la volée.
// apply() est à appeler sur le thread UI.
public final class ThemeManager {

    private ThemeManager() {
    }

    public static AppTheme getTheme(Context context) {
        return SettingsRepository.getInstance(context).getTheme();
    }

    public static void setTheme(Context context, AppTheme theme) {
        SettingsRepository.getInstance(context).setTheme(theme);
    }

    // Applique immédiatement le thème (recréation des activités).
    public static void apply(AppTheme theme) {
        if (AppCompatDelegate.getDefaultNightMode() != theme.toNightMode()) {
            AppCompatDelegate.setDefaultNightMode(theme.toNightMode());
        }
    }

    // Au démarrage : lit le thème après préchargement du DataStore, puis
    // l'applique sur le thread UI. Sans cela, le premier écran s'affiche
    // en clair puis bascule : à proscrire.
    public static void applyStoredAsync(Context context) {
        final Context app = context.getApplicationContext();
        final Handler main = new Handler(Looper.getMainLooper());
        SettingsRepository.getInstance(app).awaitReady(new Runnable() {
            @Override
            public void run() {
                final AppTheme stored = getTheme(app);
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        apply(stored);
                    }
                });
            }
        });
    }
}