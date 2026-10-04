package com.example.netscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.example.netscan.network.SubnetCalculator;

import org.junit.Test;

import java.util.List;

// Fichier : test/.../SubnetCalculatorTest.java
// Rôle : tests unitaires locaux du calcul de sous-réseau (Étape 2).
public class SubnetCalculatorTest {

    @Test
    public void slash24_donne_254_hotes_sans_reseau_ni_broadcast() {
        List<String> hosts = SubnetCalculator.listHosts("192.168.1.0/24");
        assertEquals(254, hosts.size());
        assertEquals("192.168.1.1", hosts.get(0));
        assertEquals("192.168.1.254", hosts.get(hosts.size() - 1));
    }

    @Test
    public void slash30_donne_2_hotes() {
        List<String> hosts = SubnetCalculator.listHosts("192.168.1.0/30");
        assertEquals(2, hosts.size());
        assertEquals("192.168.1.1", hosts.get(0));
        assertEquals("192.168.1.2", hosts.get(1));
    }

    @Test
    public void slash16_accepte_mais_pas_en_dessous() {
        assertEquals(65534, SubnetCalculator.listHosts("10.0.0.0/16").size());
        try {
            SubnetCalculator.listHosts("10.0.0.0/15");
            fail("Un /15 doit être refusé.");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void adresses_invalides_sont_rejetees() {
        String[] invalides = {"192.168.1.0", "192.168.1.0/33", "300.1.1.0/24",
                "2001:db8::/64", null};
        for (String cidr : invalides) {
            try {
                SubnetCalculator.listHosts(cidr);
                fail("Aurait dû rejeter : " + cidr);
            } catch (IllegalArgumentException expected) {
            }
        }
    }

    @Test
    public void cidrFromIp_calcule_le_reseau() {
        assertEquals("192.168.1.0/24", SubnetCalculator.cidrFromIp("192.168.1.10", 24));
        assertEquals("10.0.0.0/16", SubnetCalculator.cidrFromIp("10.0.5.23", 16));
    }

    @Test
    public void conversion_ip_entier_est_reversible() {
        assertEquals("192.168.1.10",
                SubnetCalculator.intToIp(SubnetCalculator.ipToInt("192.168.1.10")));
    }
}
