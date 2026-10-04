package com.example.netscan.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

// Fichier : security/DatabaseKeyProvider.java
// Rôle : passphrase SQLCipher de 32 octets aléatoires (Étape 4).
// Tirée au sort une fois, chiffrée par le Keystore, stockée en privé.
// Jamais en clair, jamais en dur, exclue des sauvegardes (backup_rules).
public final class DatabaseKeyProvider {

    private static final String PREFS_NAME = "netscan_keys";
    private static final String KEY_ENCRYPTED_PASSPHRASE = "db_key_enc";
    private static final String KEYSTORE_ALIAS = "netscan_db_wrap";
    private static final int PASSPHRASE_BYTES = 32;

    private DatabaseKeyProvider() {
    }

    public static synchronized byte[] getPassphrase(Context context)
            throws GeneralSecurityException, IOException {
        Context app = context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_ENCRYPTED_PASSPHRASE, null);
        CryptoManager crypto = new CryptoManager(app, KEYSTORE_ALIAS);
        if (stored != null) {
            String base64 = crypto.decryptFromBase64(stored);
            return Base64.decode(base64, Base64.NO_WRAP);
        }
        byte[] passphrase = new byte[PASSPHRASE_BYTES];
        new SecureRandom().nextBytes(passphrase);
        String encoded = Base64.encodeToString(passphrase, Base64.NO_WRAP);
        prefs.edit().putString(KEY_ENCRYPTED_PASSPHRASE, crypto.encryptToBase64(encoded)).apply();
        return passphrase;
    }
}
