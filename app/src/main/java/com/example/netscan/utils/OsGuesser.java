package com.example.netscan.utils;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.OsInfo;
import com.example.netscan.data.model.OsInfo.Confidence;
import com.example.netscan.data.model.OsInfo.Family;
import com.example.netscan.data.model.PortResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// Fichier : utils/OsGuesser.java
// Rôle : identification du système d'exploitation (modes Simple ET Expert).
// Sans root, pas de vrai fingerprinting façon nmap : on croise le nom d'hôte,
// les services mDNS, les ports ouverts, les bannières (SSH, en-tête HTTP
// « Server ») et le TTL du ping. Chaque indice ajoute des points à une famille
// d'OS ; la meilleure famille gagne, et le total donne la confiance.
// Les noms courts (« nas », « cam », « hue »...) ne sont reconnus qu'en MOT
// ENTIER : « banana » ne devient plus un NAS. Pur Java, testé par OsGuesserTest.
public final class OsGuesser {

    public static final String UNKNOWN = OsInfo.unknown().label();

    private static final double HIGH_SCORE = 5.0;
    private static final double MEDIUM_SCORE = 2.0;

    private OsGuesser() {
    }

    // Libellé en français, jamais null. Ex. « Windows (probable) ».
    public static String guessOs(Host host) {
        return analyze(host).label();
    }

    // Vrai si une estimation a été trouvée (tout sauf « Système inconnu »).
    public static boolean isKnownOs(String guess) {
        return guess != null && !UNKNOWN.equals(guess);
    }

    // Analyse complète. Si l'OS a été figé (fin de scan, historique), on le
    // réutilise ; sinon on le calcule à partir des indices disponibles.
    public static OsInfo analyze(Host host) {
        if (host == null) {
            return OsInfo.unknown();
        }
        OsInfo stored = host.getOs();
        if (stored != null) {
            return stored;
        }
        return compute(host);
    }

    // Recalcule toujours (utilisé pour figer le résultat en fin de scan).
    public static OsInfo compute(Host host) {
        if (host == null) {
            return OsInfo.unknown();
        }
        Scorer scorer = new Scorer();
        Text name = new Text(safe(host.getHostname()) + " " + safe(host.getServiceName()));
        Text hostOnly = new Text(safe(host.getHostname()));
        Text banners = new Text(joinBanners(host));
        Set<Integer> ports = openPorts(host);

        scoreNames(scorer, name, hostOnly);
        scorePorts(scorer, ports);
        scoreBanners(scorer, banners);
        scoreTtl(scorer, host.getTtl(), ports);

        return scorer.result();
    }

    // ------------------------------------------------------------------
    // Indices : noms d'hôte et services mDNS
    // ------------------------------------------------------------------

    private static void scoreNames(Scorer s, Text name, Text hostOnly) {
        String m;

        // Apple.
        if ((m = name.sub("iphone", "ipad", "ipod")) != null) {
            s.add(Family.APPLE, 4, "Nom ou service « " + m + " »");
            s.mobile = true;
        } else if ((m = name.sub("macbook", "imac", "mac-mini", "macmini", "mac-studio",
                "macstudio", "apple-tv", "appletv", "homepod", "airport", "itunes")) != null) {
            s.add(Family.APPLE, 4, "Nom ou service « " + m + " »");
        } else if ((m = name.tok("mac", "macos", "apple")) != null
                || (m = name.sub("apple")) != null) {
            s.add(Family.APPLE, 3, "Nom ou service « " + m + " »");
        }
        if ((m = name.sub("_airplay", "airplay", "_raop", "_companion-link",
                "_apple-mobdev", "_device-info")) != null) {
            s.add(Family.APPLE, 4, "Service mDNS Apple (" + m + ")");
        }

        // TV connectée / Chromecast : avant Android générique.
        if ((m = name.sub("chromecast", "_googlecast", "androidtv", "android-tv",
                "_androidtvremote", "google-tv", "googletv", "bravia", "webos", "tizen",
                "roku", "fire-tv", "firetv", "smart-tv", "smarttv", "shield")) != null) {
            s.add(Family.TV, 4, "Nom ou service « " + m + " »");
        } else if ((m = name.tok("tv", "television", "tele")) != null) {
            s.add(Family.TV, 3, "Nom contenant « " + m + " »");
        }

        // Android (téléphones, tablettes).
        if ((m = name.sub("android", "galaxy", "redmi", "xiaomi", "oneplus", "realme",
                "huawei", "motorola")) != null) {
            s.add(Family.ANDROID, 4, "Nom ou service « " + m + " »");
            s.mobile = true;
        } else if ((m = name.tok("pixel", "oppo", "vivo", "poco", "honor")) != null) {
            s.add(Family.ANDROID, 3, "Nom contenant « " + m + " »");
            s.mobile = true;
        } else if (name.matches("(^|[^a-z0-9])sm-[a-z]\\d{3}")) {
            s.add(Family.ANDROID, 3, "Modèle Samsung dans le nom");
            s.mobile = true;
        } else if ((m = name.tok("samsung")) != null) {
            s.add(Family.ANDROID, 2, "Nom contenant « " + m + " »");
        }

        // Windows : noms d'hôte par défaut (DESKTOP-XXXX, LAPTOP-XXXX...).
        String first = hostOnly.firstToken();
        if (first != null && hostOnly.tokenCount() > 1 && isWindowsPrefix(first)) {
            s.add(Family.WINDOWS, 4, "Nom d'hôte Windows par défaut (« " + first + "-… »)");
        }
        if ((m = name.sub("windows", "microsoft", "win10", "win11", "msft", "surface")) != null) {
            s.add(Family.WINDOWS, 4, "Nom ou service « " + m + " »");
        }
        if (name.sub("_smb") != null) {
            s.add(Family.WINDOWS, 1, "Partage de fichiers SMB annoncé");
        }

        // Imprimantes.
        if ((m = name.sub("print", "imprim", "_ipp", "_printer", "_pdl-datastream",
                "laserjet", "deskjet", "officejet", "pixma", "epson", "brother", "lexmark",
                "kyocera", "xerox", "canon")) != null) {
            s.add(Family.PRINTER, 4, "Nom ou service « " + m + " »");
        }

        // Box / routeur / NAS.
        if ((m = name.sub("synology", "diskstation", "qnap", "truenas", "freenas", "unraid",
                "fritz", "freebox", "livebox", "bbox", "neufbox", "routeur", "router",
                "gateway", "passerelle", "openwrt", "mikrotik", "ubiquiti", "unifi",
                "tp-link", "tplink", "netgear", "dd-wrt", "pfsense", "opnsense")) != null) {
            s.add(Family.NETWORK, 4, "Nom ou service « " + m + " »");
        } else if ((m = name.tok("nas")) != null) {
            s.add(Family.NETWORK, 4, "Nom contenant « " + m + " »");
        }

        // Linux / Unix.
        if ((m = name.sub("ubuntu", "debian", "raspbian", "raspberry", "fedora", "centos",
                "archlinux", "linux", "unix", "proxmox", "docker", "homeassistant",
                "pi-hole", "pihole", "openmediavault", "manjaro")) != null) {
            s.add(Family.LINUX, 4, "Nom ou service « " + m + " »");
        }
        if ((m = name.sub("_workstation", "_ssh", "_sftp")) != null) {
            s.add(Family.LINUX, 2, "Service mDNS Linux/Unix (" + m + ")");
        }

        // Objets connectés, caméras, domotique.
        if ((m = name.sub("esp32", "esp8266", "espressif", "tasmota", "esphome", "shelly",
                "sonos", "philips-hue", "hue-bridge", "homekit", "hikvision", "dahua",
                "ipcam", "wyze", "tuya", "smartthings", "yeelight", "roomba", "_hap")) != null) {
            s.add(Family.IOT, 4, "Nom ou service « " + m + " »");
        } else if ((m = name.tok("cam", "camera", "hue", "echo", "alexa", "nest", "arlo",
                "plug", "bulb", "thermostat")) != null) {
            s.add(Family.IOT, 3, "Nom contenant « " + m + " »");
        }
    }

    private static boolean isWindowsPrefix(String token) {
        return "desktop".equals(token) || "laptop".equals(token) || "win".equals(token)
                || "win10".equals(token) || "win11".equals(token) || "pc".equals(token)
                || "surface".equals(token) || "workstation".equals(token);
    }

    // ------------------------------------------------------------------
    // Indices : ports ouverts
    // ------------------------------------------------------------------

    private static void scorePorts(Scorer s, Set<Integer> ports) {
        // Apple.
        if (ports.contains(62078)) {
            s.add(Family.APPLE, 5, "Port 62078 ouvert (synchronisation iOS)");
            s.mobile = true;
        }
        if (ports.contains(548)) {
            s.add(Family.APPLE, 3, "Port 548 ouvert (AFP, partage macOS)");
        }
        if (ports.contains(7000)) {
            s.add(Family.APPLE, 2, "Port 7000 ouvert (AirPlay)");
        }

        // Windows.
        if (ports.contains(3389)) {
            s.add(Family.WINDOWS, 4, "Port 3389 ouvert (Bureau à distance RDP)");
        }
        if (ports.contains(135)) {
            s.add(Family.WINDOWS, 3, "Port 135 ouvert (RPC Windows)");
        }
        if (ports.contains(5357)) {
            s.add(Family.WINDOWS, 3, "Port 5357 ouvert (découverte Windows WSD)");
        }
        if (ports.contains(445)) {
            s.add(Family.WINDOWS, 1, "Port 445 ouvert (SMB)");
        }
        if (ports.contains(139)) {
            s.add(Family.WINDOWS, 1, "Port 139 ouvert (NetBIOS)");
        }
        if (ports.contains(135) && ports.contains(445)) {
            s.add(Family.WINDOWS, 2, "SMB + RPC ensemble : combinaison typique de Windows");
        }

        // Imprimantes.
        if (ports.contains(9100)) {
            s.add(Family.PRINTER, 5, "Port 9100 ouvert (JetDirect)");
        }
        if (ports.contains(515)) {
            s.add(Family.PRINTER, 3, "Port 515 ouvert (LPD)");
        }
        if (ports.contains(631)) {
            s.add(Family.PRINTER, 3, "Port 631 ouvert (IPP / CUPS)");
        }

        // TV / Chromecast / Android.
        if (ports.contains(8008) || ports.contains(8009)) {
            s.add(Family.TV, 4, "Port 8008/8009 ouvert (Google Cast)");
        }
        if (ports.contains(8001) || ports.contains(8002)) {
            s.add(Family.TV, 4, "Port 8001/8002 ouvert (TV Samsung)");
        }
        if (ports.contains(5555)) {
            s.add(Family.ANDROID, 4, "Port 5555 ouvert (débogage Android ADB)");
        }

        // Box / NAS.
        if (ports.contains(5001)) {
            s.add(Family.NETWORK, 2, "Port 5001 ouvert (interface NAS Synology)");
        }
        if (ports.contains(53)) {
            s.add(Family.NETWORK, 2, "Port 53 ouvert (serveur DNS : box ou routeur)");
        }

        // Caméras.
        if (ports.contains(554)) {
            s.add(Family.IOT, 4, "Port 554 ouvert (flux vidéo RTSP : caméra)");
        }

        // Linux / Unix.
        if (ports.contains(22)) {
            s.add(Family.LINUX, 2, "Port 22 ouvert (SSH)");
        }
    }

    // ------------------------------------------------------------------
    // Indices : bannières (SSH, en-tête HTTP Server...)
    // ------------------------------------------------------------------

    private static void scoreBanners(Scorer s, Text banners) {
        if (banners.isEmpty()) {
            return;
        }
        String m;
        if (banners.sub("for_windows", "microsoft", "windows") != null) {
            s.add(Family.WINDOWS, 5, "Bannière Microsoft / Windows");
        }
        if ((m = banners.sub("ubuntu", "debian", "raspbian", "fedora", "centos")) != null) {
            s.add(Family.LINUX, 4, "Bannière de service « " + m + " »");
        }
        if (banners.sub("openssh", "ssh-") != null) {
            s.add(Family.LINUX, 1, "Bannière SSH (OpenSSH)");
        }
        if (banners.sub("android") != null) {
            s.add(Family.ANDROID, 3, "Bannière « android »");
        }
        if ((m = banners.sub("synology", "qnap", "fritz", "busybox", "openwrt", "routeros",
                "mikrotik", "dropbear", "uhttpd", "router", "gateway")) != null) {
            s.add(Family.NETWORK, 3, "Bannière d'équipement réseau (« " + m + " »)");
        } else if (banners.tok("nas") != null) {
            s.add(Family.NETWORK, 3, "Bannière contenant « nas »");
        }
        if (banners.tok("cups") != null || banners.tok("ipp") != null
                || banners.sub("printer", "jetdirect") != null) {
            s.add(Family.PRINTER, 3, "Bannière d'impression (CUPS / IPP)");
        }
        if ((m = banners.sub("lighttpd", "upnp", "sonos", "philips-hue", "tasmota",
                "esphome", "shelly", "hikvision", "dahua", "goahead", "mini_httpd")) != null
                || (m = banners.tok("axis", "boa", "camera")) != null) {
            s.add(Family.IOT, 3, "Serveur web d'objet connecté (« " + m + " »)");
        }
        if (banners.tok("nginx") != null || banners.tok("apache") != null) {
            s.add(Family.LINUX, 1, "Serveur web Unix (nginx / Apache)");
        }
    }

    // ------------------------------------------------------------------
    // Indice : TTL du ping
    // ------------------------------------------------------------------

    // 64 = famille Unix (Linux, Android, Apple), 128 = Windows, 255 = équipement
    // réseau. Le TTL reçu est celui de départ moins les sauts : sur un réseau
    // local, il reste dans la même tranche.
    static int ttlClass(int ttl) {
        if (ttl <= 0) {
            return 0;
        }
        if (ttl <= 64) {
            return 64;
        }
        if (ttl <= 128) {
            return 128;
        }
        return 255;
    }

    private static void scoreTtl(Scorer s, int ttl, Set<Integer> ports) {
        int cls = ttlClass(ttl);
        if (cls == 0) {
            return;
        }
        boolean hasOtherEvidence = s.best() > 0;
        if (cls == 128) {
            s.add(Family.WINDOWS, 1.5, "TTL " + ttl + " (typique de Windows)");
            s.add(Family.LINUX, -1, null);
            s.add(Family.ANDROID, -1, null);
            s.add(Family.APPLE, -1, null);
        } else if (cls == 255) {
            s.add(Family.NETWORK, 2, "TTL " + ttl + " (typique d'un équipement réseau)");
            s.add(Family.WINDOWS, -1, null);
            s.add(Family.ANDROID, -1, null);
        } else {
            // TTL 64 : cohérent avec toutes les familles Unix, sans les départager.
            s.add(Family.WINDOWS, -1.5, null);
            if (hasOtherEvidence) {
                s.confirmUnixTtl(ttl);
            } else if (ports.isEmpty()) {
                s.add(Family.MOBILE, 1.5, "TTL " + ttl + " et aucun port ouvert : "
                        + "profil d'un smartphone ou d'un objet connecté");
            } else if (onlyWebPorts(ports)) {
                s.add(Family.IOT, 1.5, "TTL " + ttl + " et seulement un serveur web ouvert");
            } else {
                s.add(Family.LINUX, 1.5, "TTL " + ttl + " (famille Linux / Unix)");
            }
        }
    }

    private static boolean onlyWebPorts(Set<Integer> ports) {
        for (int port : ports) {
            if (port != 80 && port != 443 && port != 8080 && port != 8443) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Données de l'hôte
    // ------------------------------------------------------------------

    private static Set<Integer> openPorts(Host host) {
        Set<Integer> ports = new HashSet<>();
        for (PortResult port : host.getOpenPorts()) {
            if (port != null && port.isOpen()) {
                ports.add(port.getPort());
            }
        }
        return ports;
    }

    private static String joinBanners(Host host) {
        StringBuilder sb = new StringBuilder();
        for (PortResult port : host.getOpenPorts()) {
            if (port != null && port.getBanner() != null) {
                sb.append(port.getBanner()).append(' ');
            }
        }
        return sb.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------
    // Texte normalisé : recherche par sous-chaîne OU par mot entier
    // ------------------------------------------------------------------

    private static final class Text {
        private final String raw;
        private final List<String> tokens = new ArrayList<>();

        Text(String value) {
            raw = value == null ? "" : value.toLowerCase(Locale.ROOT);
            StringBuilder current = new StringBuilder();
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);
                if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                    current.append(c);
                } else if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            }
            if (current.length() > 0) {
                tokens.add(current.toString());
            }
        }

        boolean isEmpty() {
            return raw.trim().isEmpty();
        }

        // Premier mot trouvé comme sous-chaîne (noms longs et distinctifs).
        String sub(String... needles) {
            for (String needle : needles) {
                if (raw.contains(needle)) {
                    return needle;
                }
            }
            return null;
        }

        // Premier mot trouvé en MOT ENTIER (noms courts, risque de faux positif).
        String tok(String... words) {
            for (String word : words) {
                if (tokens.contains(word)) {
                    return word;
                }
            }
            return null;
        }

        boolean matches(String regex) {
            return java.util.regex.Pattern.compile(regex).matcher(raw).find();
        }

        String firstToken() {
            return tokens.isEmpty() ? null : tokens.get(0);
        }

        int tokenCount() {
            return tokens.size();
        }
    }

    // ------------------------------------------------------------------
    // Cumul des points par famille
    // ------------------------------------------------------------------

    private static final class Scorer {
        private final double[] score = new double[Family.values().length];
        private final List<List<String>> why = new ArrayList<>();
        boolean mobile;

        Scorer() {
            for (int i = 0; i < score.length; i++) {
                why.add(new ArrayList<String>());
            }
        }

        // reason == null : simple pénalité, sans preuve à afficher.
        void add(Family family, double weight, String reason) {
            int i = family.ordinal();
            score[i] += weight;
            if (reason != null) {
                why.get(i).add(reason);
            }
        }

        double best() {
            double best = 0;
            for (Family family : Family.values()) {
                if (family != Family.UNKNOWN && score[family.ordinal()] > best) {
                    best = score[family.ordinal()];
                }
            }
            return best;
        }

        // Un TTL de la famille Unix conforte (un peu) les familles déjà retenues.
        void confirmUnixTtl(int ttl) {
            for (Family family : new Family[]{Family.APPLE, Family.ANDROID, Family.LINUX,
                    Family.TV, Family.IOT, Family.PRINTER}) {
                if (score[family.ordinal()] > 0) {
                    score[family.ordinal()] += 0.5;
                }
            }
            // La preuve n'est ajoutée qu'à la famille gagnante (voir result()).
            unixTtlNote = "TTL " + ttl + " (cohérent avec Linux, Android ou Apple)";
        }

        private String unixTtlNote;

        OsInfo result() {
            Family best = Family.UNKNOWN;
            double bestScore = 0;
            double second = 0;
            // Ordre de l'enum = priorité en cas d'égalité (le premier gagne).
            for (Family family : Family.values()) {
                if (family == Family.UNKNOWN) {
                    continue;
                }
                double value = score[family.ordinal()];
                if (value > bestScore) {
                    second = bestScore;
                    bestScore = value;
                    best = family;
                } else if (value > second) {
                    second = value;
                }
            }
            if (best == Family.UNKNOWN || bestScore <= 0) {
                return OsInfo.unknown();
            }
            Confidence confidence = bestScore >= HIGH_SCORE ? Confidence.HIGH
                    : bestScore >= MEDIUM_SCORE ? Confidence.MEDIUM : Confidence.LOW;
            List<String> proofs = new ArrayList<>(why.get(best.ordinal()));
            // Deux familles très proches : l'estimation est ambiguë (un « élevé » devient « moyen »).
            if (second >= MEDIUM_SCORE && second >= bestScore * 0.75) {
                if (confidence == Confidence.HIGH) {
                    confidence = Confidence.MEDIUM;
                }
                proofs.add("Indices partagés avec une autre famille : estimation ambiguë");
            }
            if (unixTtlNote != null && best != Family.WINDOWS && best != Family.NETWORK) {
                proofs.add(unixTtlNote);
            }
            return OsInfo.of(best, confidence, proofs, mobile && best != Family.WINDOWS);
        }
    }
}
