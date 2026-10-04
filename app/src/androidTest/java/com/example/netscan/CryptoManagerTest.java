package com.example.netscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.netscan.security.CryptoManager;

import org.junit.Test;
import org.junit.runner.RunWith;

// Fichier : androidTest/.../CryptoManagerTest.java
// Rôle : le chiffrement AES-GCM/Keystore protège les réglages (Étape 4).
// TEST SUR TÉLÉPHONE : ./gradlew connectedDebugAndroidTest (le Keystore
// exige un appareil ou un émulateur, jamais la JVM locale).
@RunWith(AndroidJUnit4.class)
public class CryptoManagerTest {

    @Test
    public void chiffrement_different_a_chaque_appel_mais_dechiffre_pareil() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        CryptoManager crypto = new CryptoManager(context, "netscan_test_settings");
        String secret = "192.168.1.0/24";

        String first = crypto.encryptToBase64(secret);
        String second = crypto.encryptToBase64(secret);
        // IV aléatoire : deux chiffrés du même clair diffèrent…
        assertNotEquals(first, second);
        // …mais retrouvent le même clair.
        assertEquals(secret, crypto.decryptFromBase64(first));
        assertEquals(secret, crypto.decryptFromBase64(second));
    }

    @Test
    public void donnee_alteree_refusee() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        CryptoManager crypto = new CryptoManager(context, "netscan_test_settings");
        String packed = crypto.encryptToBase64("secret");
        // Corrompt le dernier caractère Base64.
        char last = packed.charAt(packed.length() - 1);
        String altered = packed.substring(0, packed.length() - 1) + (last == 'A' ? 'B' : 'A');
        try {
            crypto.decryptFromBase64(altered);
            fail("Une donnée altérée doit être rejetée (tag GCM).");
        } catch (Exception expected) {
            // Comportement attendu : échec explicite, jamais de clair.
        }
    }
}
