package com.example.netscan.utils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Fichier : utils/ScanComparator.java
// Rôle : détecte les nouveaux appareils entre deux scans (Étape 4).
// Travaille sur des listes d'IP : pur Java, testé par ScanComparatorTest.
public final class ScanComparator {

    private ScanComparator() {
    }

    // IP présentes dans le scan courant mais absentes du précédent (ordre conservé).
    public static List<String> findNewIps(List<String> currentIps, List<String> previousIps) {
        List<String> current = currentIps == null ? new ArrayList<String>() : currentIps;
        Set<String> previous = new HashSet<>();
        if (previousIps != null) {
            previous.addAll(previousIps);
        }
        List<String> newcomers = new ArrayList<>();
        for (String ip : current) {
            if (!previous.contains(ip)) {
                newcomers.add(ip);
            }
        }
        return newcomers;
    }

    // Vrai si au moins un nouvel appareil est apparu (et qu'il y a un précédent).
    public static boolean hasNewDevices(List<String> currentIps, List<String> previousIps) {
        if (previousIps == null || previousIps.isEmpty()) {
            // Premier scan : rien à comparer, pas de bandeau.
            return false;
        }
        return !findNewIps(currentIps, previousIps).isEmpty();
    }
}
