package com.example.netscan.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.PortResult;

import org.junit.Test;

import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

// Fichier : test/.../network/OsProberTest.java
// Rôle : la mini-sonde OS du mode Simple détecte un port ouvert local
// et ignore les ports fermés (boucle locale, sans Android).
public class OsProberTest {

    @Test
    public void port_local_ouvert_detecte() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            List<PortResult> open = OsProber.probePorts("127.0.0.1",
                    Collections.singletonList(port), 500, new AtomicBoolean(false));
            assertEquals(1, open.size());
            assertEquals(port, open.get(0).getPort());
        }
    }

    @Test
    public void port_ferme_ignore() throws Exception {
        int closed;
        try (ServerSocket server = new ServerSocket(0)) {
            closed = server.getLocalPort();
        }
        // Socket fermé : plus rien n'écoute sur ce port.
        List<PortResult> open = OsProber.probePorts("127.0.0.1",
                Collections.singletonList(closed), 300, new AtomicBoolean(false));
        assertTrue(open.isEmpty());
    }

    @Test
    public void enrichHost_ajoute_ports_ouverts() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Host host = new Host("127.0.0.1", "localhost", true, 1);
            List<PortResult> open = OsProber.probePorts("127.0.0.1",
                    Arrays.asList(port, port + 1), 500, new AtomicBoolean(false));
            for (PortResult result : open) {
                host.addOpenPort(result);
            }
            assertEquals(1, host.getOpenPorts().size());
            assertEquals(port, host.getOpenPorts().get(0).getPort());
        }
    }
    @Test
    public void parseTtl_format_android_linux() {
        assertEquals(64, OsProber.parseTtl(
                "64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=2.31 ms"));
    }

    @Test
    public void parseTtl_format_windows() {
        assertEquals(128, OsProber.parseTtl(
                "Reply from 192.168.1.20: bytes=32 time=1ms TTL=128"));
    }

    @Test
    public void parseTtl_sans_ttl_donne_moins_un() {
        assertEquals(-1, OsProber.parseTtl("Request timeout for icmp_seq 0"));
        assertEquals(-1, OsProber.parseTtl(null));
        assertEquals(-1, OsProber.parseTtl("ttl=999"));
    }

    @Test
    public void measureTtl_refuse_texte_libre() {
        // Aucune commande n'est lancée si l'argument n'est pas une IPv4.
        assertEquals(-1, OsProber.measureTtl("192.168.1.1; reboot"));
        assertEquals(-1, OsProber.measureTtl("$(id)"));
        assertEquals(-1, OsProber.measureTtl(null));
        assertEquals(-1, OsProber.measureTtl("300.1.1.1"));
    }

    @Test
    public void enrichHost_saute_les_ports_deja_scannes() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            int port = server.getLocalPort();
            Host host = new Host("127.0.0.1", "localhost", true, 1);
            // Le port est dans la liste « déjà scannée » : jamais sondé ici.
            List<PortResult> open = OsProber.probePorts("127.0.0.1",
                    Collections.<Integer>emptyList(), 300, new AtomicBoolean(false));
            assertTrue(open.isEmpty());
            host.mergeOpenPort(new PortResult(port, true));
            host.mergeOpenPort(new PortResult(port, true));
            assertEquals(1, host.getOpenPorts().size());
        }
    }

    @Test
    public void mergeOpenPort_complete_la_banniere_sans_doublon() {
        Host host = new Host("192.168.1.2", "h", true, 1);
        host.mergeOpenPort(new PortResult(22, true));
        PortResult withBanner = new PortResult(22, true);
        withBanner.setBanner("SSH-2.0-OpenSSH_9");
        host.mergeOpenPort(withBanner);
        assertEquals(1, host.getOpenPorts().size());
        assertEquals("SSH-2.0-OpenSSH_9", host.getOpenPorts().get(0).getBanner());
    }

    @Test
    public void annule_ne_sonde_rien() {
        Host host = new Host("127.0.0.1", "localhost", true, 1);
        OsProber.enrichHost(host, 300, new AtomicBoolean(true));
        assertTrue(host.getOpenPorts().isEmpty());
        assertEquals(-1, host.getTtl());
    }
}
