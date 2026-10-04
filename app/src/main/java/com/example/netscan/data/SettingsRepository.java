package com.example.netscan.data;

import android.content.Context;
import android.util.Log;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.core.PreferenceDataStoreFactory;

import com.example.netscan.mode.AppMode;
import com.example.netscan.mode.AppTheme;
import com.example.netscan.security.CryptoManager;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.flow.FlowKt;

// Fichier : data/SettingsRepository.java
// Rôle : réglages persistés en DataStore, valeurs chiffrées AES-GCM (Étape 4).
// DataStore est asynchrone (coroutines) : ce dépôt expose une API sûre
// depuis le thread UI via un cache mémoire + écritures en tâche de fond.
// Les lectures ne bloquent JAMAIS (cache ou défaut). Ne pas appeler
// readBlocking/writeBlocking depuis le code applicatif.
public class SettingsRepository {

    private static final String TAG = "SettingsRepository";
    private static final String KEYSTORE_ALIAS = "netscan_settings";
    private static final String KEY_MODE = "app_mode";
    // Thème choisi par l'utilisateur (mode nuit) : "SYSTEM", "LIGHT", "DARK".
    private static final String KEY_THEME = "app_theme";
    // Étape 5 : premier lancement + avertissement légal.
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    private static final String KEY_LEGAL_ACCEPTED = "legal_accepted";

    private static volatile SettingsRepository instance;

    private final Context appContext;
    private final DataStore<Preferences> dataStore;
    private final CryptoManager crypto;
    // Cache nom -> valeur CHIFFRÉE (Base64). Le clair n'est jamais stocké.
    private final Map<String, String> cache = new ConcurrentHashMap<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor();
    private final Preferences.Key<String> modeKey = PreferencesKeys.stringKey(KEY_MODE);

    public static SettingsRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (SettingsRepository.class) {
                if (instance == null) {
                    instance = new SettingsRepository(context);
                }
            }
        }
        return instance;
    }

    private SettingsRepository(Context context) {
        this.appContext = context.getApplicationContext();
        File dir = new File(appContext.getFilesDir(), "datastore");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        final File file = new File(dir, "netscan.preferences_pb");
        // Factory Kotlin (object) : INSTANCE + surcharge simple (portée interne).
        dataStore = PreferenceDataStoreFactory.INSTANCE.create(() -> file);
        CryptoManager created = null;
        try {
            created = new CryptoManager(appContext, KEYSTORE_ALIAS);
        } catch (Exception e) {
            // Keystore indisponible : réglages en mémoire seule (jamais en clair).
            Log.e(TAG, "Keystore indisponible, réglages non persistés.", e);
        }
        crypto = created;
        prefetch();
    }

    // Précharge le cache en tâche de fond (n'écrase pas les écritures récentes).
    private void prefetch() {
        writer.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Preferences prefs = readBlocking();
                    for (Map.Entry<Preferences.Key<?>, Object> entry : prefs.asMap().entrySet()) {
                        Object value = entry.getValue();
                        if (value instanceof String) {
                            cache.putIfAbsent(entry.getKey().getName(), (String) value);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Préchargement des réglages impossible.", e);
                }
            }
        });
    }

    // Lecture DataStore bloquante : THREAD DE FOND UNIQUEMENT.
    private Preferences readBlocking() {
        try {
            Object result = BuildersKt.<Preferences>runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    new Function2<CoroutineScope, Continuation<? super Preferences>, Object>() {
                        @Override
                        public Object invoke(CoroutineScope scope,
                                             Continuation<? super Preferences> cont) {
                            return FlowKt.first(dataStore.getData(), cont);
                        }
                    });
            return (Preferences) result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Lecture des réglages interrompue.", e);
        }
    }

    // Écriture DataStore bloquante : THREAD DE FOND UNIQUEMENT.
    private void writeBlocking(final Preferences.Key<String> key, final String encrypted) {
        try {
            BuildersKt.<Preferences>runBlocking(
                    EmptyCoroutineContext.INSTANCE,
                    new Function2<CoroutineScope, Continuation<? super Preferences>, Object>() {
                        @Override
                        public Object invoke(CoroutineScope scope,
                                             Continuation<? super Preferences> cont) {
                            return dataStore.updateData(
                                    new Function2<Preferences, Continuation<? super Preferences>, Object>() {
                                        @Override
                                        public Object invoke(Preferences prefs,
                                                             Continuation<? super Preferences> c) {
                                            MutablePreferences mutable = prefs.toMutablePreferences();
                                            mutable.set(key, encrypted);
                                            return mutable;
                                        }
                                    }, cont);
                        }
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Écriture des réglages interrompue.", e);
        }
    }

    public AppMode getMode() {
        String encrypted = cache.get(KEY_MODE);
        if (encrypted == null || crypto == null) {
            return AppMode.SIMPLE;
        }
        try {
            return AppMode.valueOf(crypto.decryptFromBase64(encrypted));
        } catch (Exception e) {
            Log.e(TAG, "Mode illisible, retour au Simple.", e);
            return AppMode.SIMPLE;
        }
    }

    public void setMode(final AppMode mode) {
        String encrypted = encryptNow(mode.name());
        if (encrypted == null) {
            return;
        }
        // Le cache est mis à jour AVANT l'écriture disque : l'activité qui
        // démarre juste après (bascule de mode) lit la nouvelle valeur. Sinon
        // l'app afficherait l'ancien mode jusqu'au redémarrage suivant.
        cache.put(KEY_MODE, encrypted);
        final String value = encrypted;
        writer.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    writeBlocking(modeKey, value);
                } catch (Exception e) {
                    Log.e(TAG, "Sauvegarde du mode impossible.", e);
                }
            }
        });
    }

    // Chiffrement immédiat : quelques millisecondes de Keystore, mais seule
    // façon que la valeur soit lisible par l'écran qui vient d'être créé.
    private String encryptNow(String plain) {
        if (crypto == null) {
            return null;
        }
        try {
            return crypto.encryptToBase64(plain);
        } catch (Exception e) {
            Log.e(TAG, "Chiffrement impossible.", e);
            return null;
        }
    }

    // Vrai si un mode est déjà persisté (sert à la migration Étape 3 -> 4).
    public boolean hasMode() {
        return cache.containsKey(KEY_MODE);
    }

    // Thème : "Système" par défaut, lu depuis le cache chiffré.
    public AppTheme getTheme() {
        String encrypted = cache.get(KEY_THEME);
        if (encrypted == null || crypto == null) {
            return AppTheme.SYSTEM;
        }
        try {
            return AppTheme.fromName(crypto.decryptFromBase64(encrypted));
        } catch (Exception e) {
            Log.e(TAG, "Thème illisible, retour au système.", e);
            return AppTheme.SYSTEM;
        }
    }

    public void setTheme(final AppTheme theme) {
        String encrypted = encryptNow(theme.name());
        if (encrypted == null) {
            return;
        }
        cache.put(KEY_THEME, encrypted);
        final String value = encrypted;
        final Preferences.Key<String> prefKey = PreferencesKeys.stringKey(KEY_THEME);
        writer.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    writeBlocking(prefKey, value);
                } catch (Exception e) {
                    Log.e(TAG, "Sauvegarde du thème impossible.", e);
                }
            }
        });
    }

    // Exécute r sur le thread d'écriture, DONC après le préchargement
    // (file unique : ordre garanti). Sert au routage du premier lancement.
    public void awaitReady(Runnable r) {
        writer.execute(r);
    }

    // Premier lancement terminé (présentation vue + mode choisi).
    public boolean isOnboardingDone() {
        return getFlag(KEY_ONBOARDING_DONE);
    }

    public void setOnboardingDone() {
        setFlag(KEY_ONBOARDING_DONE, true);
    }

    // Avertissement légal accepté ("Ne scannez que vos propres réseaux…").
    public boolean isLegalAccepted() {
        return getFlag(KEY_LEGAL_ACCEPTED);
    }

    public void setLegalAccepted() {
        setFlag(KEY_LEGAL_ACCEPTED, true);
    }

    // Drapeau booléen stocké CHIFFRÉ ("1"/"0"). Lecture : cache ou faux.
    private boolean getFlag(String key) {
        String encrypted = cache.get(key);
        if (encrypted == null || crypto == null) {
            return false;
        }
        try {
            return "1".equals(crypto.decryptFromBase64(encrypted));
        } catch (Exception e) {
            Log.e(TAG, "Drapeau illisible : " + key, e);
            return false;
        }
    }

    private void setFlag(final String key, final boolean value) {
        String encrypted = encryptNow(value ? "1" : "0");
        if (encrypted == null) {
            return;
        }
        // Comme setMode : cache d'abord, disque ensuite (l'onboarding route
        // vers l'accueil juste après avoir écrit ces drapeaux).
        cache.put(key, encrypted);
        final Preferences.Key<String> prefKey = PreferencesKeys.stringKey(key);
        writer.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    writeBlocking(prefKey, encrypted);
                } catch (Exception e) {
                    Log.e(TAG, "Sauvegarde impossible : " + key, e);
                }
            }
        });
    }
}
