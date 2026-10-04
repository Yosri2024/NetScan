package com.example.netscan.mode;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.netscan.data.SettingsRepository;

// Fichier : mode/ModeManager.java
// Rôle : mémorise le mode Simple/Expert (Étapes 3-4).
// Depuis l'Étape 4 : DataStore chiffré (AES-GCM + Keystore) via
// SettingsRepository. La valeur héritée de l'Étape 3 (SharedPreferences
// en clair) est migrée une fois puis supprimée.
public final class ModeManager {

    // Héritage Étape 3 : lu une seule fois pour la migration.
    private static final String LEGACY_PREFS = "netscan_mode";
    private static final String LEGACY_KEY = "app_mode";

    private ModeManager() {
    }

    public static AppMode getMode(Context context) {
        migrateLegacy(context);
        return SettingsRepository.getInstance(context).getMode();
    }

    public static void setMode(Context context, AppMode mode) {
        migrateLegacy(context);
        SettingsRepository.getInstance(context).setMode(mode);
    }

    public static boolean isSimple(Context context) {
        return getMode(context) == AppMode.SIMPLE;
    }

    // Migration unique : recopie l'ancien choix puis efface le fichier clair.
    private static synchronized void migrateLegacy(Context context) {
        Context app = context.getApplicationContext();
        SharedPreferences legacy = app.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE);
        if (!legacy.contains(LEGACY_KEY)) {
            return;
        }
        String saved = legacy.getString(LEGACY_KEY, AppMode.SIMPLE.name());
        AppMode mode;
        try {
            mode = AppMode.valueOf(saved);
        } catch (IllegalArgumentException e) {
            mode = AppMode.SIMPLE;
        }
        SettingsRepository repo = SettingsRepository.getInstance(app);
        if (!repo.hasMode()) {
            repo.setMode(mode);
        }
        legacy.edit().clear().apply();
    }
}
