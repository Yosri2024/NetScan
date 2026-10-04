package com.example.netscan.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

// Fichier : security/KeystoreHelper.java
// Rôle : clés AES-GCM dans l'Android Keystore (Étape 4).
// La clé ne sort jamais du Keystore : seul le matériel chiffré est stocké.
public final class KeystoreHelper {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";

    private KeystoreHelper() {
    }

    // Retourne la clé existante ou la crée (AES-256/GCM, sans padding).
    public static synchronized SecretKey getOrCreateAesKey(String alias)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(alias)) {
            java.security.Key existing = keyStore.getKey(alias, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
            // Alias corrompu : on le remplace par une clé saine.
            keyStore.deleteEntry(alias);
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }
}
