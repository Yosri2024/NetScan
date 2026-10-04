package com.example.netscan.security;

import android.content.Context;
import android.util.Base64;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

// Fichier : security/CryptoManager.java
// Rôle : chiffre AES-256/GCM via le Keystore (Étape 4).
// Utilisé pour les réglages (DataStore) et pour protéger la passphrase SQLCipher.
// Format stocké : Base64(IV 12 octets || chiffré). IV généré par le Keystore
// à chaque appel (la clé exige un IV aléatoire non fourni par l'appelant).
public class CryptoManager {

    // GCM : 12 octets d'IV (recommandation NIST), tag 128 bits.
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SecretKey key;

    public CryptoManager(Context context, String alias)
            throws GeneralSecurityException, IOException {
        // Le context n'est pas stocké : la clé vit dans le Keystore.
        context.getApplicationContext();
        this.key = KeystoreHelper.getOrCreateAesKey(alias);
    }

    public String encryptToBase64(String plain) throws GeneralSecurityException {
        if (plain == null) {
            throw new GeneralSecurityException("Texte nul.");
        }
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] encrypted;
        try {
            // La clé est créée avec randomizedEncryptionRequired (défaut Keystore) :
            // l'IV est généré par le Keystore, pas par l'application. Fournir un
            // IV ici lève InvalidAlgorithmParameterException ("Caller-provided
            // IV not permitted") et ferait échouer toute écriture.
            cipher.init(Cipher.ENCRYPT_MODE, key);
            encrypted = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new GeneralSecurityException("Chiffrement impossible.", e);
        }
        byte[] iv = cipher.getIV();
        if (iv == null || iv.length != IV_BYTES) {
            throw new GeneralSecurityException("IV GCM inattendu.");
        }
        ByteBuffer packed = ByteBuffer.allocate(iv.length + encrypted.length);
        packed.put(iv);
        packed.put(encrypted);
        return Base64.encodeToString(packed.array(), Base64.NO_WRAP);
    }

    public String decryptFromBase64(String packed) throws GeneralSecurityException {
        if (packed == null) {
            throw new GeneralSecurityException("Donnée nulle.");
        }
        byte[] raw;
        try {
            raw = Base64.decode(packed, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Base64 invalide.", e);
        }
        if (raw.length <= IV_BYTES) {
            throw new GeneralSecurityException("Donnée trop courte.");
        }
        ByteBuffer buffer = ByteBuffer.wrap(raw);
        byte[] iv = new byte[IV_BYTES];
        buffer.get(iv);
        byte[] encrypted = new byte[buffer.remaining()];
        buffer.get(encrypted);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(encrypted);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Donnée altérée ou clé changée : échec explicite, jamais de clair.
            throw new GeneralSecurityException("Déchiffrement impossible.", e);
        }
    }
}
