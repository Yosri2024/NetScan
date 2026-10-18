package com.example.netscan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.OsInfo;
import com.example.netscan.data.model.PortResult;
import com.example.netscan.utils.OsGuesser;

import org.junit.Test;

// Fichier : test/.../OsGuesserTest.java
// Rôle : l'estimation d'OS des appareils inconnus (nom, mDNS, ports, bannières).
public class OsGuesserTest {

    private static Host host(String hostname, String service) {
        Host host = new Host("192.168.1.10", hostname, true, 12);
        host.setServiceName(service);
        return host;
    }

    private static void addPort(Host host, int port, String banner) {
        PortResult result = new PortResult(port, true);
        result.setBanner(banner);
        host.addOpenPort(result);
    }

    @Test
    public void null_donne_systeme_inconnu() {
        assertEquals(OsGuesser.UNKNOWN, OsGuesser.guessOs(null));
    }

    @Test
    public void iphone_par_nom() {
        assertEquals("Apple iOS / macOS (probable)",
                OsGuesser.guessOs(host("iPhone-de-Yosri", null)));
    }

    @Test
    public void airplay_par_service() {
        assertEquals("Apple iOS / macOS (probable)",
                OsGuesser.guessOs(host("android-xyz", "Mon-Salon._airplay._tcp")));
    }

    @Test
    public void windows_par_smb_et_rdp() {
        Host host = host("DESKTOP-ABC123", null);
        addPort(host, 135, null);
        addPort(host, 445, null);
        addPort(host, 3389, null);
        assertEquals("Windows (probable)", OsGuesser.guessOs(host));
    }

    @Test
    public void linux_par_ssh() {
        Host host = host("raspberrypi", null);
        addPort(host, 22, "SSH-2.0-OpenSSH_9.2p1 Debian-2");
        assertEquals("Linux / Unix (probable)", OsGuesser.guessOs(host));
    }

    @Test
    public void imprimante_par_port_9100() {
        Host host = host("HP-LaserJet", null);
        addPort(host, 9100, null);
        assertEquals("Imprimante (Linux embarqué, probable)", OsGuesser.guessOs(host));
    }

    @Test
    public void chromecast_par_service() {
        assertEquals("Android TV / TV connectée (probable)",
                OsGuesser.guessOs(host("Chromecast-Salon", "Chromecast._googlecast._tcp")));
    }

    @Test
    public void sans_indice_donne_systeme_inconnu() {
        Host host = host("192.168.1.10", null);
        assertEquals(OsGuesser.UNKNOWN, OsGuesser.guessOs(host));
        assertFalse(OsGuesser.isKnownOs(OsGuesser.guessOs(host)));
    }

    @Test
    public void estimation_connue_reconnue() {
        assertTrue(OsGuesser.isKnownOs("Windows (probable)"));
    }
    // ----- Nouveautés : confiance, TTL, faux positifs, bannières -----

    @Test
    public void mot_court_en_mot_entier_seulement() {
        // « nas » dans « banana » ne doit pas faire un NAS.
        assertEquals(OsGuesser.UNKNOWN, OsGuesser.guessOs(host("banana-pc-lab", null)));
        assertEquals("Box / routeur / NAS (Linux embarqué, probable)",
                OsGuesser.guessOs(host("NAS-Salon", null)));
    }

    @Test
    public void windows_certain_avec_ports_nom_et_ttl() {
        Host host = host("DESKTOP-9KQ2", null);
        addPort(host, 135, null);
        addPort(host, 445, null);
        host.setTtl(127);
        OsInfo info = OsGuesser.analyze(host);
        assertEquals(OsInfo.Family.WINDOWS, info.family());
        assertEquals(OsInfo.Confidence.HIGH, info.confidence());
        assertFalse(info.evidence().isEmpty());
    }

    @Test
    public void ttl_128_seul_donne_windows_possible() {
        Host host = host("192.168.1.10", null);
        host.setTtl(128);
        OsInfo info = OsGuesser.analyze(host);
        assertEquals(OsInfo.Family.WINDOWS, info.family());
        assertEquals(OsInfo.Confidence.LOW, info.confidence());
        assertEquals("Windows (possible)", info.label());
    }

    @Test
    public void ttl_64_sans_port_donne_smartphone_ou_objet() {
        Host host = host("192.168.1.10", null);
        host.setTtl(64);
        OsInfo info = OsGuesser.analyze(host);
        assertEquals(OsInfo.Family.MOBILE, info.family());
        assertEquals(OsInfo.Confidence.LOW, info.confidence());
    }

    @Test
    public void ttl_255_donne_equipement_reseau() {
        Host host = host("192.168.1.1", null);
        host.setTtl(255);
        assertEquals(OsInfo.Family.NETWORK, OsGuesser.analyze(host).family());
    }

    @Test
    public void ttl_64_contredit_windows() {
        // SMB seul + TTL 64 : plus Windows (Samba / NAS / Mac possibles).
        Host host = host("192.168.1.10", null);
        addPort(host, 445, null);
        host.setTtl(64);
        assertFalse(OsInfo.Family.WINDOWS == OsGuesser.analyze(host).family()
                && OsGuesser.analyze(host).confidence() != OsInfo.Confidence.LOW);
    }

    @Test
    public void android_par_nom_dhcp_par_defaut() {
        OsInfo info = OsGuesser.analyze(host("android-8a2f1c3d4e5f6a7b", null));
        assertEquals(OsInfo.Family.ANDROID, info.family());
        assertTrue(info.isMobile());
    }

    @Test
    public void modele_samsung_dans_le_nom() {
        assertEquals(OsInfo.Family.ANDROID,
                OsGuesser.analyze(host("SM-G991B", null)).family());
    }

    @Test
    public void iphone_est_mobile() {
        OsInfo info = OsGuesser.analyze(host("Yosris-iPhone", null));
        assertEquals(OsInfo.Family.APPLE, info.family());
        assertTrue(info.isMobile());
    }

    @Test
    public void samsung_tv_par_ports() {
        Host host = host("192.168.1.30", null);
        addPort(host, 8001, null);
        addPort(host, 8002, null);
        assertEquals(OsInfo.Family.TV, OsGuesser.analyze(host).family());
    }

    @Test
    public void camera_par_rtsp() {
        Host host = host("192.168.1.31", null);
        addPort(host, 554, null);
        addPort(host, 80, "HTTP 200 · Server: GoAhead-Webs");
        assertEquals(OsInfo.Family.IOT, OsGuesser.analyze(host).family());
    }

    @Test
    public void iis_donne_windows() {
        Host host = host("192.168.1.32", null);
        addPort(host, 80, "HTTP 200 · Server: Microsoft-IIS/10.0");
        assertEquals(OsInfo.Family.WINDOWS, OsGuesser.analyze(host).family());
    }

    @Test
    public void openssh_for_windows_donne_windows() {
        Host host = host("192.168.1.33", null);
        addPort(host, 22, "SSH-2.0-OpenSSH_for_Windows_8.1");
        assertEquals(OsInfo.Family.WINDOWS, OsGuesser.analyze(host).family());
    }

    @Test
    public void dropbear_donne_equipement_reseau() {
        Host host = host("192.168.1.1", null);
        addPort(host, 22, "SSH-2.0-dropbear_2020.81");
        addPort(host, 53, null);
        assertEquals(OsInfo.Family.NETWORK, OsGuesser.analyze(host).family());
    }

    @Test
    public void os_fige_est_reutilise() {
        Host host = host("192.168.1.10", null);
        host.setOs(OsInfo.decode("APPLE|HIGH|1"));
        OsInfo info = OsGuesser.analyze(host);
        assertEquals(OsInfo.Family.APPLE, info.family());
        assertTrue(info.isMobile());
    }

    @Test
    public void compute_ignore_l_os_fige() {
        Host host = host("DESKTOP-ABC", null);
        host.setOs(OsInfo.unknown());
        assertEquals(OsInfo.Family.WINDOWS, OsGuesser.compute(host).family());
    }

    @Test
    public void encode_decode_aller_retour() {
        OsInfo original = OsGuesser.analyze(host("Yosris-iPhone", null));
        OsInfo restored = OsInfo.decode(original.encode());
        assertNotNull(restored);
        assertEquals(original.family(), restored.family());
        assertEquals(original.confidence(), restored.confidence());
        assertEquals(original.isMobile(), restored.isMobile());
    }

    @Test
    public void decode_valeur_illisible_donne_null() {
        assertNull(OsInfo.decode("n'importe quoi"));
        assertNull(OsInfo.decode(null));
        assertNull(OsInfo.decode("INCONNU|HIGH|0"));
    }

    @Test
    public void os_exact_garde_son_libelle() {
        OsInfo info = OsInfo.exact(OsInfo.Family.ANDROID, "Android 15", "Cet appareil");
        assertEquals("Android 15", info.label());
        assertEquals(OsInfo.Confidence.HIGH, info.confidence());
    }

    @Test
    public void ttl_class_tranches() {
        assertEquals(0, OsGuesserAccess.ttlClass(-1));
        assertEquals(64, OsGuesserAccess.ttlClass(60));
        assertEquals(128, OsGuesserAccess.ttlClass(120));
        assertEquals(255, OsGuesserAccess.ttlClass(254));
    }

    // ttlClass est package-private : on le teste via une analyse équivalente.
    private static final class OsGuesserAccess {
        static int ttlClass(int ttl) {
            Host host = new Host("192.168.1.10", "192.168.1.10", true, 1);
            host.setTtl(ttl);
            OsInfo info = OsGuesser.analyze(host);
            switch (info.family()) {
                case WINDOWS: return 128;
                case NETWORK: return 255;
                case MOBILE: return 64;
                default: return 0;
            }
        }
    }
}
