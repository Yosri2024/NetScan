package com.example.netscan.utils;

import com.example.netscan.data.model.PortResult;

import java.util.List;

// Fichier : utils/TerminologyMapper.java
// Rôle : traduit le jargon en langage courant (Étape 3, mode Simple).
// "hôte actif" -> "appareil connecté", "latence 45 ms" -> "connexion rapide".
// Pur Java : testé par TerminologyMapperTest.
public final class TerminologyMapper {

    private TerminologyMapper() {
    }

    // "latence 45 ms" -> "Connexion rapide" (seuils indicatifs).
    public static String latencyLabel(long latencyMs) {
        if (latencyMs < 0) {
            return "Vitesse inconnue";
        }
        if (latencyMs < 80) {
            return "Connexion rapide";
        }
        if (latencyMs < 200) {
            return "Connexion correcte";
        }
        return "Connexion lente";
    }

    // "port 443 ouvert" -> "connexion sécurisée détectée".
    public static String portsLabel(List<PortResult> openPorts) {
        if (openPorts == null || openPorts.isEmpty()) {
            return "Aucune connexion détectée";
        }
        for (PortResult port : openPorts) {
            if (port.getPort() == 443) {
                return "Connexion sécurisée détectée";
            }
        }
        return "Connexion détectée";
    }

    // "sous-réseau /24" -> "votre réseau local" (Wi-Fi ou Ethernet).
    public static String networkLabel() {
        return "votre réseau local";
    }

    // Résumé de l'historique : "3 appareils, tout est reconnu".
    public static String scanSummary(int total, int recognized) {
        if (total <= 0) {
            return "Aucun appareil détecté";
        }
        if (recognized >= total) {
            return total + " appareil(s), tout est reconnu";
        }
        return total + " appareil(s), " + (total - recognized) + " à vérifier";
    }
}
