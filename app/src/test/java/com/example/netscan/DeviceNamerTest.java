package com.example.netscan;

import static org.junit.Assert.assertEquals;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.PortResult;
import com.example.netscan.utils.DeviceNamer;

import org.junit.Test;

// Fichier : test/.../DeviceNamerTest.java
// Rôle : les noms clairs du mode Simple suivent l'OS estimé, sans faux positifs.
public class DeviceNamerTest {

    private static Host host(String name) {
        return new Host("192.168.1.20", name, true, 10);
    }

    @Test
    public void cet_appareil_et_la_box_priment() {
        assertEquals("Votre téléphone",
                DeviceNamer.nameFor(host("x"), "192.168.1.20", "192.168.1.1"));
        assertEquals("Votre box Internet",
                DeviceNamer.nameFor(host("x"), "192.168.1.5", "192.168.1.20"));
    }

    @Test
    public void tv_par_mot_entier() {
        assertEquals("Votre TV connectée",
                DeviceNamer.nameFor(host("Salon-TV"), null, null));
    }

    @Test
    public void tv_au_milieu_d_un_mot_n_est_pas_une_tv() {
        assertEquals(DeviceNamer.UNKNOWN_NAME,
                DeviceNamer.nameFor(host("activitybox"), null, null));
    }

    @Test
    public void imprimante_par_port_9100() {
        Host printer = host("192.168.1.20");
        printer.addOpenPort(new PortResult(9100, true));
        assertEquals("Votre imprimante", DeviceNamer.nameFor(printer, null, null));
    }

    @Test
    public void windows_par_nom_par_defaut() {
        assertEquals("Un ordinateur Windows",
                DeviceNamer.nameFor(host("DESKTOP-4KQ9"), null, null));
    }

    @Test
    public void iphone_et_android() {
        assertEquals("Un iPhone ou iPad", DeviceNamer.nameFor(host("Yosris-iPhone"), null, null));
        assertEquals("Un téléphone Android",
                DeviceNamer.nameFor(host("android-1a2b3c4d5e6f7a8b"), null, null));
    }

    @Test
    public void indice_faible_reste_inconnu() {
        Host weak = host("192.168.1.20");
        weak.setTtl(128); // Windows « possible » seulement
        assertEquals(DeviceNamer.UNKNOWN_NAME, DeviceNamer.nameFor(weak, null, null));
    }

    @Test
    public void sous_texte_selon_statut() {
        assertEquals("Cet appareil", DeviceNamer.subtextFor(host("x"), true, "192.168.1.20"));
        assertEquals("À vérifier", DeviceNamer.subtextFor(host("x"), false, null));
    }
}
