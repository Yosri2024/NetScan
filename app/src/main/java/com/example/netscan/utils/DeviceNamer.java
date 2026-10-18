package com.example.netscan.utils;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.OsInfo;

// Fichier : utils/DeviceNamer.java
// Rôle : nom clair pour chaque appareil Simple (Étape 3).
// Le nom découle maintenant de l'identification du système (OsGuesser) :
// mots entiers et score, au lieu de chercher « tv » ou « phone » au milieu
// de n'importe quel nom. Une estimation faible n'est jamais présentée
// comme un fait : on reste sur « Appareil inconnu ».
public final class DeviceNamer {

    public static final String UNKNOWN_NAME = "Appareil inconnu";

    private DeviceNamer() {
    }

    // "Votre box Internet", "Votre téléphone" (cet appareil), "Votre TV connectée",
    // "Votre imprimante", "Un ordinateur Windows"... sinon "Appareil inconnu".
    public static String nameFor(Host host, String ownIp, String gatewayIp) {
        if (host == null) {
            return UNKNOWN_NAME;
        }
        if (host.getIpAddress().equals(ownIp)) {
            return "Votre téléphone";
        }
        if (host.getIpAddress().equals(gatewayIp)) {
            return "Votre box Internet";
        }
        OsInfo os = OsGuesser.analyze(host);
        if (!os.isKnown() || os.confidence() == OsInfo.Confidence.LOW) {
            return UNKNOWN_NAME;
        }
        switch (os.family()) {
            case TV:
                return "Votre TV connectée";
            case PRINTER:
                return "Votre imprimante";
            case WINDOWS:
                return "Un ordinateur Windows";
            case APPLE:
                return os.isMobile() ? "Un iPhone ou iPad" : "Un appareil Apple";
            case ANDROID:
                return "Un téléphone Android";
            case LINUX:
                return "Un ordinateur Linux";
            case NETWORK:
                return "Un équipement réseau";
            case IOT:
                return "Un objet connecté";
            default:
                return UNKNOWN_NAME;
        }
    }

    // Sous-texte court : « Cet appareil », « À vérifier », ou la qualité de
    // connexion. Le système d'exploitation a désormais sa propre pastille.
    public static String subtextFor(Host host, boolean recognized, String ownIp) {
        if (host.getIpAddress().equals(ownIp)) {
            return "Cet appareil";
        }
        if (!recognized) {
            return "À vérifier";
        }
        return TerminologyMapper.latencyLabel(host.getLatencyMs());
    }
}
