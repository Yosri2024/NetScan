package com.example.netscan.data.db;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.netscan.security.DatabaseKeyProvider;

import net.zetetic.database.sqlcipher.SupportOpenHelperFactory;

// Fichier : data/db/AppDatabase.java
// Rôle : base Room CHIFFRÉE par SQLCipher (Étape 4).
// La passphrase (32 octets aléatoires) est protégée par le Keystore :
// aucun secret en dur, rien en clair.
// Si la clé ou le fichier de base sont inaccessibles (Keystore révoqué,
// données corrompues, sauvegarde restaurée ailleurs), getInstance renvoie
// null au lieu de lever : l'historique se vide, l'application reste
// utilisable. Jamais de repli en base non chiffrée.
@Database(entities = {ScanEntity.class, HostEntity.class, KnownDeviceEntity.class},
        version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String TAG = "AppDatabase";
    private static final String DB_NAME = "netscan.db";
    private static volatile AppDatabase instance;
    private static boolean nativeLoaded;

    public abstract ScanDao scanDao();

    public abstract KnownDeviceDao knownDeviceDao();

    // Base de l'application, ou null si indisponible (voir build()).
    @Nullable
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = build(context);
                }
            }
        }
        return instance;
    }

    private static AppDatabase build(Context context) {
        Context app = context.getApplicationContext();
        if (!loadNativeLibrary(app)) {
            return null;
        }
        final byte[] passphrase;
        try {
            passphrase = DatabaseKeyProvider.getPassphrase(app);
        } catch (Exception e) {
            // Sans passphrase, pas de base : échec explicite, jamais de repli en clair.
            Log.e(TAG, "Base chiffrée inaccessible (clé Keystore).", e);
            return null;
        }
        try {
            return Room.databaseBuilder(app, AppDatabase.class, DB_NAME)
                    .openHelperFactory(new SupportOpenHelperFactory(passphrase))
                    .build();
        } catch (Exception e) {
            Log.e(TAG, "Ouverture de la base chiffrée impossible.", e);
            return null;
        }
    }

    // SQLCipher pour Android n'auto-charge PAS sa bibliotheque native :
    // sans cet appel explicite, toute requete leve
    // "UnsatisfiedLinkError: No implementation found ... nativeOpen".
    // Chargée une seule fois par processus ; un échec est journalisé, jamais fatal.
    private static synchronized boolean loadNativeLibrary(Context app) {
        if (nativeLoaded) {
            return true;
        }
        try {
            System.loadLibrary("sqlcipher");
            nativeLoaded = true;
        } catch (UnsatisfiedLinkError e) {
            Log.e(TAG, "libsqlcipher.so absente pour cette architecture.", e);
        }
        return nativeLoaded;
    }

    // Variante de test : base en mémoire (instrumentation uniquement).
    @NonNull
    public static AppDatabase inMemory(Context context, byte[] passphrase) {
        System.loadLibrary("sqlcipher");
        return Room.inMemoryDatabaseBuilder(context.getApplicationContext(), AppDatabase.class)
                .openHelperFactory(new SupportOpenHelperFactory(passphrase))
                .allowMainThreadQueries()
                .build();
    }
}