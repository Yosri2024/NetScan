package com.example.netscan;

import static org.junit.Assert.assertEquals;

import com.example.netscan.alerts.Alert;
import com.example.netscan.alerts.AlertHelper;
import com.example.netscan.data.model.Host;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Fichier : test/.../AlertHelperTest.java
// Rôle : bandeau vert/orange nuancé, jamais alarmiste (Étape 3).
public class AlertHelperTest {

    private static final String OWN = "192.168.1.10";
    private static final String GATEWAY = "192.168.1.1";

    private static Host host(String ip) {
        return new Host(ip, ip, true, 20);
    }

    @Test
    public void tout_reconnu_donne_bandeau_vert() {
        List<Host> hosts = new ArrayList<>();
        hosts.add(host(GATEWAY));
        hosts.add(host(OWN));
        Set<String> known = new HashSet<>();
        known.add("192.168.1.20");
        hosts.add(host("192.168.1.20"));

        Alert alert = AlertHelper.summary(hosts, known, OWN, GATEWAY);
        assertEquals(Alert.Level.OK, alert.getLevel());
        assertEquals("Tout est reconnu", alert.getTitle());
    }

    @Test
    public void appareil_inconnu_donne_bandeau_orange_nuance() {
        List<Host> hosts = new ArrayList<>();
        hosts.add(host(GATEWAY));
        hosts.add(host(OWN));
        hosts.add(host("192.168.1.99"));

        Alert alert = AlertHelper.summary(hosts, new HashSet<String>(), OWN, GATEWAY);
        assertEquals(Alert.Level.WARNING, alert.getLevel());
        // Nuancé : on parle de "vérifier", jamais de "vulnérable" ou "intrus".
        assertEquals(false, alert.getTitle().toLowerCase().contains("vuln"));
        assertEquals(false, alert.getMessage().toLowerCase().contains("intrus"));
    }

    @Test
    public void box_et_telephone_sont_reconnus_sans_marquage() {
        Set<String> known = new HashSet<>();
        assertEquals(true, AlertHelper.isRecognized(host(GATEWAY), known, OWN, GATEWAY));
        assertEquals(true, AlertHelper.isRecognized(host(OWN), known, OWN, GATEWAY));
        assertEquals(false, AlertHelper.isRecognized(host("192.168.1.99"), known, OWN, GATEWAY));
    }

    @Test
    public void appareil_marque_devient_reconnu() {
        Set<String> known = new HashSet<>();
        known.add("192.168.1.99");
        assertEquals(true, AlertHelper.isRecognized(host("192.168.1.99"), known, OWN, GATEWAY));

        List<Host> hosts = new ArrayList<>();
        hosts.add(host(OWN));
        hosts.add(host("192.168.1.99"));
        hosts.add(host("192.168.1.100"));
        // Seul le .100 reste à vérifier.
        assertEquals(1, AlertHelper.toCheck(hosts, known, OWN, GATEWAY).size());
        assertEquals("192.168.1.100",
                AlertHelper.toCheck(hosts, known, OWN, GATEWAY).get(0).getIpAddress());
    }

    // Ordre d'affichage : ce qu'il faut vérifier en premier.
    @Test
    public void rang_inconnu_avant_box_avant_reconnu() {
        Set<String> known = new HashSet<>();
        known.add("192.168.1.42");
        assertEquals(AlertHelper.RANK_UNKNOWN,
                AlertHelper.rankOf("192.168.1.77", known, OWN, GATEWAY));
        assertEquals(AlertHelper.RANK_NETWORK,
                AlertHelper.rankOf(GATEWAY, known, OWN, GATEWAY));
        assertEquals(AlertHelper.RANK_RECOGNIZED,
                AlertHelper.rankOf(OWN, known, OWN, GATEWAY));
        assertEquals(AlertHelper.RANK_RECOGNIZED,
                AlertHelper.rankOf("192.168.1.42", known, OWN, GATEWAY));
        // Sans adresse de passerelle, la box n'est plus identifiable.
        assertEquals(AlertHelper.RANK_UNKNOWN,
                AlertHelper.rankOf(GATEWAY, known, OWN, null));
        assertEquals(AlertHelper.RANK_UNKNOWN, AlertHelper.rankOf((Host) null, known, OWN, GATEWAY));
    }
}
