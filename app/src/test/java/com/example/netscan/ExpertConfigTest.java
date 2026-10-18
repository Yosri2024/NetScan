package com.example.netscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.netscan.data.model.ExpertConfig;
import com.example.netscan.data.model.ScanConfig;

import org.junit.Test;

// Fichier : test/.../ExpertConfigTest.java
// Rôle : la config Expert survit à l'aller-retour (encodage/décodage).
public class ExpertConfigTest {

    @Test
    public void aller_retour_complet() {
        ExpertConfig original = new ExpertConfig();
        original.cidrOverride = "10.0.0.0/24";
        original.portPreset = ScanConfig.PRESET_CUSTOM;
        original.customPorts = "22, 80, 8000-8010";
        original.timeoutText = "1200";
        original.threadsText = "80";
        original.osDetection = false;

        ExpertConfig restored = ExpertConfig.decode(original.encode());
        assertEquals("10.0.0.0/24", restored.cidrOverride);
        assertEquals(ScanConfig.PRESET_CUSTOM, restored.portPreset);
        assertEquals("22, 80, 8000-8010", restored.customPorts);
        assertEquals("1200", restored.timeoutText);
        assertEquals("80", restored.threadsText);
        assertFalse(restored.osDetection);
    }

    @Test
    public void texte_absent_donne_les_valeurs_par_defaut() {
        ExpertConfig config = ExpertConfig.decode(null);
        assertEquals("", config.cidrOverride);
        assertEquals("800", config.timeoutText);
        assertEquals("50", config.threadsText);
        assertTrue(config.osDetection);
        assertEquals(ScanConfig.PRESET_COMMON_PORTS, config.portPreset);
    }

    @Test
    public void texte_illisible_donne_les_valeurs_par_defaut() {
        assertEquals("800", ExpertConfig.decode("n'importe quoi").timeoutText);
        assertEquals("800", ExpertConfig.decode("v1\u001Fa\u001Fx\u001Fb\u001Fc\u001Fd\u001F1").timeoutText);
        assertEquals("800", ExpertConfig.decode("v1\u001Fa\u001F9\u001Fb\u001Fc\u001Fd\u001F1").timeoutText);
    }

    @Test
    public void champs_vides_conserves() {
        ExpertConfig original = new ExpertConfig();
        original.customPorts = "";
        ExpertConfig restored = ExpertConfig.decode(original.encode());
        assertEquals("", restored.customPorts);
        assertEquals("", restored.cidrOverride);
    }

    @Test
    public void separateur_saisi_est_retire() {
        ExpertConfig original = new ExpertConfig();
        original.customPorts = "80\u001F443";
        assertEquals("80443", ExpertConfig.decode(original.encode()).customPorts);
    }
}
