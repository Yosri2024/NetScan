package com.example.netscan;

import static org.junit.Assert.assertEquals;

import com.example.netscan.utils.TerminologyMapper;

import org.junit.Test;

import java.util.Collections;

// Fichier : test/.../TerminologyMapperTest.java
// Rôle : le jargon est bien traduit en langage courant (Étape 3).
public class TerminologyMapperTest {

    @Test
    public void latence_45ms_donne_connexion_rapide() {
        assertEquals("Connexion rapide", TerminologyMapper.latencyLabel(45));
    }

    @Test
    public void latences_graduees() {
        assertEquals("Connexion rapide", TerminologyMapper.latencyLabel(10));
        assertEquals("Connexion correcte", TerminologyMapper.latencyLabel(120));
        assertEquals("Connexion lente", TerminologyMapper.latencyLabel(500));
        assertEquals("Vitesse inconnue", TerminologyMapper.latencyLabel(-1));
    }

    @Test
    public void resume_tout_reconnu() {
        assertEquals("3 appareil(s), tout est reconnu",
                TerminologyMapper.scanSummary(3, 3));
    }

    @Test
    public void resume_avec_appareils_a_verifier() {
        assertEquals("4 appareil(s), 1 à vérifier",
                TerminologyMapper.scanSummary(4, 3));
    }

    @Test
    public void resume_sans_appareil() {
        assertEquals("Aucun appareil détecté",
                TerminologyMapper.scanSummary(0, 0));
    }

    @Test
    public void ports_vides_sans_jargon() {
        String label = TerminologyMapper.portsLabel(Collections.emptyList());
        // Aucun terme technique (pas de "port", pas de chiffre).
        assertEquals("Aucune connexion détectée", label);
    }
}
