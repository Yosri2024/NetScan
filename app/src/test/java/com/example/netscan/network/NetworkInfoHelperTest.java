package com.example.netscan.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.netscan.data.model.NetworkDetails;

import org.junit.Test;

import java.net.InetAddress;

// Fichier : src/test/.../network/NetworkInfoHelperTest.java
// Rôle : la plage CIDR automatique est calculée depuis l'interface réseau,
// pas depuis le Wi-Fi : un câble Ethernet (/24 comme /16) s'analyse sans
// téléphone. Test JVM : logique pure, aucun accès réseau.
public class NetworkInfoHelperTest {

    private static NetworkDetails ethernet(String ip, int prefix) {
        return new NetworkDetails(NetworkDetails.Transport.ETHERNET, "eth0", null,
                ip, prefix, "192.168.1.1", null, -1, "Filaire (Ethernet)");
    }

    @Test
    public void cidr_de_l_interface_filaire() {
        assertEquals("192.168.1.0/24",
                NetworkInfoHelper.getScanCidr(ethernet("192.168.1.42", 24)));
    }

    @Test
    public void cidr_avec_prefixe_de_l_interface() {
        assertEquals("10.0.5.0/24", NetworkInfoHelper.getScanCidr(ethernet("10.0.5.7", 24)));
        assertEquals("172.16.0.0/16", NetworkInfoHelper.getScanCidr(ethernet("172.16.3.9", 16)));
    }

    @Test
    public void prefixe_hors_plages_rationnel_encadre() {
        // Trop ouvert (/8 : 16 millions d'adresses) : remonté au /16, la plus
        // petite plage que SubnetCalculator accepte (65 534 adresses).
        assertEquals("10.1.0.0/16", NetworkInfoHelper.getScanCidr(ethernet("10.1.2.3", 8)));
        // Trop restreint (/28) : élargi au /24 qui contient l'appareil, sinon
        // 14 adresses ne suffiraient pas à voir le voisinage.
        assertEquals("192.168.1.0/24",
                NetworkInfoHelper.getScanCidr(ethernet("192.168.1.20", 28)));
    }

    @Test
    public void prefixe_manquant_retombe_sur_24() {
        assertEquals("192.168.1.0/24", NetworkInfoHelper.getScanCidr(ethernet("192.168.1.5", 0)));
    }

    @Test
    public void pas_d_adresse_pas_de_plage() {
        assertNull(NetworkInfoHelper.getScanCidr(ethernet(null, 24)));
        assertNull(NetworkInfoHelper.getScanCidr(null));
    }

    @Test
    public void adresses_privees_reconnues_les_reseaux_mobiles_ecartes() throws Exception {
        assertTrue(NetworkInfoHelper.isPrivateAddress(InetAddress.getByName("192.168.1.1")));
        assertTrue(NetworkInfoHelper.isPrivateAddress(InetAddress.getByName("10.0.0.5")));
        assertTrue(NetworkInfoHelper.isPrivateAddress(InetAddress.getByName("172.16.4.1")));
        assertTrue(NetworkInfoHelper.isPrivateAddress(InetAddress.getByName("169.254.3.3")));
        assertFalse(NetworkInfoHelper.isPrivateAddress(InetAddress.getByName("172.32.0.1")));
        assertFalse(NetworkInfoHelper.isPrivateAddress(InetAddress.getByName("8.8.8.8")));
        assertFalse(NetworkInfoHelper.isPrivateAddress(InetAddress.getByName("127.0.0.1")));
    }
}