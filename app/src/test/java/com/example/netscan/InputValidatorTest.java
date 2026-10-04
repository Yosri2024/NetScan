package com.example.netscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import com.example.netscan.security.InputValidator;

import org.junit.Test;

// Fichier : test/.../InputValidatorTest.java
// Rôle : tests unitaires locaux du validateur (Étape 2).
// Lancer : ./gradlew testDebugUnitTest (ou bouton "Run" dans Android Studio).
public class InputValidatorTest {

    @Test
    public void cidr_valide_ne_renvoie_pas_d_erreur() {
        assertNull(InputValidator.validateCidr("192.168.1.0/24"));
    }

    @Test
    public void cidr_vide_renvoie_une_erreur() {
        assertNotNull(InputValidator.validateCidr(""));
        assertNotNull(InputValidator.validateCidr(null));
    }

    @Test
    public void cidr_trop_large_est_refuse() {
        // Au-delà de /16 : plage gigantesque, scan déraisonnable.
        assertNotNull(InputValidator.validateCidr("10.0.0.0/8"));
    }

    @Test
    public void cidr_malforme_est_refuse() {
        assertNotNull(InputValidator.validateCidr("192.168.1.0"));
        assertNotNull(InputValidator.validateCidr("999.1.1.0/24"));
        assertNotNull(InputValidator.validateCidr("192.168.1.0/33"));
    }

    @Test
    public void timeout_hors_plage_est_refuse() {
        assertNull(InputValidator.validateTimeout("800"));
        assertNotNull(InputValidator.validateTimeout("50"));
        assertNotNull(InputValidator.validateTimeout("20000"));
        assertNotNull(InputValidator.validateTimeout("abc"));
    }

    @Test
    public void threads_hors_plage_sont_refuses() {
        assertNull(InputValidator.validateThreads("50"));
        assertNotNull(InputValidator.validateThreads("0"));
        assertNotNull(InputValidator.validateThreads("500"));
    }

    @Test
    public void ports_personnalises_valides() {
        assertNull(InputValidator.validateCustomPorts("80, 443, 8080-8082"));
    }

    @Test
    public void ports_personnalises_invalides() {
        assertNotNull(InputValidator.validateCustomPorts(""));
        assertNotNull(InputValidator.validateCustomPorts("0"));
        assertNotNull(InputValidator.validateCustomPorts("70000"));
        assertNotNull(InputValidator.validateCustomPorts("100-50"));
    }

    @Test
    public void message_erreur_cidr_trop_large_mentionne_le_prefixe() {
        // Vérifie un contenu utile, pas seulement la présence d'une erreur.
        String message = InputValidator.validateCidr("10.0.0.0/8");
        assertNotNull(message);
        assertEquals(true, message.contains("/16"));
    }
}
