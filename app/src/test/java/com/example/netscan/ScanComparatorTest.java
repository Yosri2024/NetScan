package com.example.netscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.netscan.utils.ScanComparator;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

// Fichier : test/.../ScanComparatorTest.java
// Rôle : le bandeau "nouvel appareil" ne se déclenche qu'à raison (Étape 4).
public class ScanComparatorTest {

    @Test
    public void nouvel_appareil_detecte() {
        List<String> current = Arrays.asList("192.168.1.1", "192.168.1.10", "192.168.1.99");
        List<String> previous = Arrays.asList("192.168.1.1", "192.168.1.10");
        assertEquals(Collections.singletonList("192.168.1.99"),
                ScanComparator.findNewIps(current, previous));
        assertTrue(ScanComparator.hasNewDevices(current, previous));
    }

    @Test
    public void sans_changement_pas_de_bandeau() {
        List<String> scans = Arrays.asList("192.168.1.1", "192.168.1.10");
        assertTrue(ScanComparator.findNewIps(scans, scans).isEmpty());
        assertFalse(ScanComparator.hasNewDevices(scans, scans));
    }

    @Test
    public void premier_scan_pas_de_bandeau() {
        // Pas de précédent : tout est "nouveau" mais on n'alerte pas.
        List<String> current = Arrays.asList("192.168.1.1", "192.168.1.10");
        assertEquals(2, ScanComparator.findNewIps(current, null).size());
        assertFalse(ScanComparator.hasNewDevices(current, null));
        assertFalse(ScanComparator.hasNewDevices(current, Collections.<String>emptyList()));
    }

    @Test
    public void appareil_revenu_apres_absence_redeclenche() {
        List<String> current = Arrays.asList("192.168.1.1", "192.168.1.50");
        List<String> previous = Arrays.asList("192.168.1.1");
        assertEquals(Collections.singletonList("192.168.1.50"),
                ScanComparator.findNewIps(current, previous));
    }
}
