package com.example.netscan.data.model;

// Fichier : data/model/ExpertConfig.java
// Rôle : réglages saisis sur l'écran Expert, tels que l'utilisateur les a
// tapés (texte brut, non validé). Ils sont mémorisés pour qu'un aller-retour
// (mode Simple, réglages, historique, rotation, redémarrage) ne force plus à
// tout reconfigurer. Pur Java : testé sans téléphone (ExpertConfigTest).
//
// La plage réseau n'est mémorisée QUE si l'utilisateur l'a modifiée à la main :
// sinon elle suit le réseau actuel (une plage périmée d'un autre Wi-Fi
// scannerait le mauvais réseau).
public final class ExpertConfig {

    public static final int DEFAULT_TIMEOUT_MS = 800;
    public static final int DEFAULT_THREADS = 50;

    private static final String VERSION = "v1";
    // Séparateur ASCII « unit separator » : impossible à saisir dans un champ.
    private static final char SEP = '\u001F';

    // Plage saisie à la main ; vide = suivre le réseau détecté.
    public String cidrOverride = "";
    public int portPreset = ScanConfig.PRESET_COMMON_PORTS;
    public String customPorts = "";
    public String timeoutText = String.valueOf(DEFAULT_TIMEOUT_MS);
    public String threadsText = String.valueOf(DEFAULT_THREADS);
    public boolean osDetection = true;

    public String encode() {
        return VERSION + SEP + clean(cidrOverride) + SEP + portPreset + SEP
                + clean(customPorts) + SEP + clean(timeoutText) + SEP
                + clean(threadsText) + SEP + (osDetection ? "1" : "0");
    }

    // Valeurs par défaut si le texte est absent ou illisible : jamais d'exception.
    public static ExpertConfig decode(String text) {
        ExpertConfig config = new ExpertConfig();
        if (text == null || text.isEmpty()) {
            return config;
        }
        String[] parts = text.split(String.valueOf(SEP), -1);
        if (parts.length < 7 || !VERSION.equals(parts[0])) {
            return config;
        }
        try {
            int preset = Integer.parseInt(parts[2]);
            if (preset < ScanConfig.PRESET_COMMON_PORTS || preset > ScanConfig.PRESET_CUSTOM) {
                return config;
            }
            config.cidrOverride = parts[1];
            config.portPreset = preset;
            config.customPorts = parts[3];
            config.timeoutText = parts[4];
            config.threadsText = parts[5];
            config.osDetection = "1".equals(parts[6]);
        } catch (NumberFormatException e) {
            return new ExpertConfig();
        }
        return config;
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace(String.valueOf(SEP), "");
    }
}
