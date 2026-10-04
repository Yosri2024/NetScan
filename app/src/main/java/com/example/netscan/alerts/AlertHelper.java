package com.example.netscan.alerts;

import com.example.netscan.data.model.Host;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Fichier : alerts/AlertHelper.java
// Rôle : construit le bandeau de résumé Simple (Étape 3).
// Vert "Tout est reconnu" si rien à vérifier, sinon orange nuancé.
// Un appareil est "reconnu" : box, téléphone, ou marqué par l'utilisateur.
// Les textes parlent de "réseau local" : Wi-Fi, Ethernet (câble), USB, VPN.
public final class AlertHelper {

    // Ordre d'affichage des appareils : ce qui doit être vérifié en premier.
    public static final int RANK_UNKNOWN = 0;     // rien dit de ce que c'est
    public static final int RANK_NETWORK = 1;     // la box / le routeur
    public static final int RANK_RECOGNIZED = 2;  // mon téléphone, appareil marqué

    private AlertHelper() {
    }

    // Rang d'un appareil dans la liste : les inconnus d'abord (c'est ce qui
    // intéresse l'utilisateur), puis la box, puis les appareils reconnus.
    public static int rankOf(Host host, Set<String> knownIps, String ownIp, String gatewayIp) {
        return host == null ? RANK_UNKNOWN
                : rankOf(host.getIpAddress(), knownIps, ownIp, gatewayIp);
    }

    public static int rankOf(String ip, Set<String> knownIps, String ownIp, String gatewayIp) {
        if (ip == null) {
            return RANK_UNKNOWN;
        }
        if (gatewayIp != null && ip.equals(gatewayIp)) {
            return RANK_NETWORK;
        }
        if (ownIp != null && ip.equals(ownIp)) {
            return RANK_RECOGNIZED;
        }
        if (knownIps != null && knownIps.contains(ip)) {
            return RANK_RECOGNIZED;
        }
        return RANK_UNKNOWN;
    }

    // Vrai si l'utilisateur l'a marqué, ou s'il s'agit de la box / du téléphone.
    public static boolean isRecognized(Host host, Set<String> knownIps,
                                       String ownIp, String gatewayIp) {
        if (host == null) {
            return false;
        }
        if (knownIps != null && knownIps.contains(host.getIpAddress())) {
            return true;
        }
        return host.getIpAddress().equals(ownIp)
                || host.getIpAddress().equals(gatewayIp);
    }

    // Bandeau unique de l'écran Résultats : OK ou WARNING.
    public static Alert summary(List<Host> hosts, Set<String> knownIps,
                               String ownIp, String gatewayIp) {
        int total = hosts == null ? 0 : hosts.size();
        int recognized = countRecognized(hosts, knownIps, ownIp, gatewayIp);
        int toCheck = total - recognized;
        if (total == 0) {
            return new Alert(Alert.Level.OK, "Aucun appareil",
                    "Aucun appareil détecté sur votre réseau local.");
        }
        if (toCheck == 0) {
            return new Alert(Alert.Level.OK, "Tout est reconnu",
                    total + " appareil(s), tout est reconnu.");
        }
        return new Alert(Alert.Level.WARNING,
                total + " appareil(s), " + recognized + " reconnu(s)",
                toCheck + " appareil(s) à vérifier. Pas d'inquiétude : "
                        + "il s'agit souvent d'un objet connecté oublié.");
    }

    public static int countRecognized(List<Host> hosts, Set<String> knownIps,
                                      String ownIp, String gatewayIp) {
        int count = 0;
        if (hosts == null) {
            return 0;
        }
        for (Host host : hosts) {
            if (isRecognized(host, knownIps, ownIp, gatewayIp)) {
                count++;
            }
        }
        return count;
    }

    // Liste des hôtes encore à vérifier (point orange dans la liste).
    public static List<Host> toCheck(List<Host> hosts, Set<String> knownIps,
                                     String ownIp, String gatewayIp) {
        List<Host> result = new ArrayList<>();
        if (hosts == null) {
            return result;
        }
        for (Host host : hosts) {
            if (!isRecognized(host, knownIps, ownIp, gatewayIp)) {
                result.add(host);
            }
        }
        return result;
    }
}
