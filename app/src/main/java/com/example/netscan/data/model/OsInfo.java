package com.example.netscan.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

// Fichier : data/model/OsInfo.java
// Rôle : résultat de l'identification du système d'exploitation d'un appareil.
// Sans root, Android ne permet pas un vrai fingerprinting TCP/IP : l'OS est
// donc une ESTIMATION, toujours accompagnée d'un niveau de confiance et de la
// liste des indices retenus (« preuves »). Pur Java : testé sans téléphone.
public final class OsInfo {

    // Ordre = priorité en cas d'égalité de score (Apple d'abord, comme avant).
    public enum Family {
        APPLE("Apple iOS / macOS (%s)", "Apple"),
        TV("Android TV / TV connectée (%s)", "TV connectée"),
        ANDROID("Android (%s)", "Android"),
        WINDOWS("Windows (%s)", "Windows"),
        PRINTER("Imprimante (Linux embarqué, %s)", "Imprimante"),
        NETWORK("Box / routeur / NAS (Linux embarqué, %s)", "Box / NAS"),
        LINUX("Linux / Unix (%s)", "Linux / Unix"),
        IOT("Objet connecté (Linux embarqué, %s)", "Objet connecté"),
        MOBILE("Smartphone ou objet connecté (%s)", "Smartphone / objet"),
        UNKNOWN("Système inconnu", "Inconnu");

        private final String pattern;
        private final String shortName;

        Family(String pattern, String shortName) {
            this.pattern = pattern;
            this.shortName = shortName;
        }

        public String shortName() {
            return shortName;
        }
    }

    public enum Confidence {
        HIGH("élevée", "probable"),
        MEDIUM("moyenne", "probable"),
        LOW("faible", "possible"),
        NONE("aucune", "");

        private final String description;
        private final String qualifier;

        Confidence(String description, String qualifier) {
            this.description = description;
            this.qualifier = qualifier;
        }

        // « élevée », « moyenne », « faible » (fiche détaillée).
        public String description() {
            return description;
        }

        // « probable » / « possible » (libellé court).
        public String qualifier() {
            return qualifier;
        }
    }

    private static final OsInfo UNKNOWN_INFO = new OsInfo(Family.UNKNOWN, Confidence.NONE,
            Collections.<String>emptyList(), false, null);

    private final Family family;
    private final Confidence confidence;
    private final List<String> evidence;
    private final boolean mobile;
    // Libellé exact (ex. « Android 15 » pour le téléphone qui scanne), sinon null.
    private final String detail;

    private OsInfo(Family family, Confidence confidence, List<String> evidence,
                   boolean mobile, String detail) {
        this.family = family;
        this.confidence = confidence;
        this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
        this.mobile = mobile;
        this.detail = detail;
    }

    public static OsInfo unknown() {
        return UNKNOWN_INFO;
    }

    public static OsInfo of(Family family, Confidence confidence, List<String> evidence,
                            boolean mobile) {
        if (family == null || family == Family.UNKNOWN) {
            return UNKNOWN_INFO;
        }
        return new OsInfo(family, confidence == null ? Confidence.LOW : confidence,
                evidence == null ? Collections.<String>emptyList() : evidence, mobile, null);
    }

    // Système connu avec certitude (ex. le téléphone qui exécute NetScan).
    public static OsInfo exact(Family family, String detail, String reason) {
        List<String> why = new ArrayList<>();
        if (reason != null) {
            why.add(reason);
        }
        return new OsInfo(family, Confidence.HIGH, why, family == Family.ANDROID, detail);
    }

    public Family family() {
        return family;
    }

    public Confidence confidence() {
        return confidence;
    }

    public List<String> evidence() {
        return evidence;
    }

    // Téléphone / tablette (icône et nom d'appareil plus parlants).
    public boolean isMobile() {
        return mobile;
    }

    // Libellé exact (« Android 15 ») ou null : sert à reconstruire une fiche.
    public String detail() {
        return detail;
    }

    public boolean isKnown() {
        return family != Family.UNKNOWN;
    }

    // Libellé complet : « Windows (probable) », « Android 15 », « Système inconnu ».
    public String label() {
        if (family == Family.UNKNOWN) {
            return family.pattern;
        }
        if (detail != null) {
            return detail;
        }
        return String.format(Locale.FRENCH, family.pattern, confidence.qualifier());
    }

    // Libellé court pour une pastille : « Windows », « Box / NAS », « Inconnu ».
    public String shortLabel() {
        return detail != null ? detail : family.shortName;
    }

    // Forme compacte persistée dans l'historique : « WINDOWS|HIGH|0 ».
    public String encode() {
        return family.name() + "|" + confidence.name() + "|" + (mobile ? "1" : "0");
    }

    // Relit une valeur persistée ; null si illisible (l'appelant retombe sur l'analyse).
    public static OsInfo decode(String code) {
        if (code == null) {
            return null;
        }
        String[] parts = code.split("\\|", -1);
        if (parts.length < 2) {
            return null;
        }
        try {
            Family family = Family.valueOf(parts[0]);
            Confidence confidence = Confidence.valueOf(parts[1]);
            boolean mobile = parts.length > 2 && "1".equals(parts[2]);
            if (family == Family.UNKNOWN) {
                return UNKNOWN_INFO;
            }
            return new OsInfo(family, confidence, Collections.<String>emptyList(), mobile, null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public String toString() {
        return label();
    }
}
