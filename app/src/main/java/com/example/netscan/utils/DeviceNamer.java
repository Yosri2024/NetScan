package com.example.netscan.utils;

import com.example.netscan.data.model.Host;

import java.util.Locale;

// Fichier : utils/DeviceNamer.java
// Rôle : nom clair pour chaque appareil Simple (Étape 3).
// Heuristiques sur IP, nom d'hôte et service mDNS : le résultat
// reste approximatif (rappelé à l'utilisateur).
public final class DeviceNamer {

    private DeviceNamer() {
    }

    // "Votre box Internet", "Votre téléphone", "Votre TV connectée",
    // "Votre imprimante", sinon "Appareil inconnu".
    public static String nameFor(Host host, String ownIp, String gatewayIp) {
        if (host == null) {
            return "Appareil inconnu";
        }
        if (host.getIpAddress().equals(ownIp)) {
            return "Votre téléphone";
        }
        if (host.getIpAddress().equals(gatewayIp)) {
            return "Votre box Internet";
        }
        String haystack = ((host.getHostname() == null ? "" : host.getHostname()) + " "
                + (host.getServiceName() == null ? "" : host.getServiceName()))
                .toLowerCase(Locale.FRENCH);
        if (haystack.contains("tv") || haystack.contains("television")
                || haystack.contains("shield") || haystack.contains("chromecast")) {
            return "Votre TV connectée";
        }
        if (haystack.contains("print") || haystack.contains("imprim")) {
            return "Votre imprimante";
        }
        if (haystack.contains("phone") || haystack.contains("iphone")
                || haystack.contains("android") || haystack.contains("galaxy")) {
            return "Un téléphone";
        }
        return "Appareil inconnu";
    }

    // Sous-texte court : qualité de connexion, "Cet appareil", "À vérifier".
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
