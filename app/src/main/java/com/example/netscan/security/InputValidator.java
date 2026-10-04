package com.example.netscan.security;

import com.example.netscan.data.model.ScanConfig;
import com.example.netscan.network.SubnetCalculator;

// Fichier : security/InputValidator.java
// Rôle : validation stricte des saisies Expert (Étape 2).
// Retourne null si valide, sinon un message d'erreur en français.
// Pur Java : testé par InputValidatorTest.
public final class InputValidator {

    public static final int MIN_TIMEOUT_MS = 100;
    public static final int MAX_TIMEOUT_MS = 10000;
    public static final int MIN_THREADS = 1;
    public static final int MAX_THREADS = 256;
    // Limite anti-abus pour les listes personnalisées de ports.
    public static final int MAX_CUSTOM_PORTS = 500;

    private InputValidator() {
    }

    public static String validateCidr(String cidr) {
        if (cidr == null || cidr.trim().isEmpty()) {
            return "Plage CIDR vide (ex. 192.168.1.0/24).";
        }
        try {
            SubnetCalculator.listHosts(cidr);
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    public static String validateTimeout(String text) {
        int value = parsePositiveInt(text);
        if (value < 0) {
            return "Timeout invalide (nombre en ms).";
        }
        if (value < MIN_TIMEOUT_MS || value > MAX_TIMEOUT_MS) {
            return "Timeout entre " + MIN_TIMEOUT_MS + " et " + MAX_TIMEOUT_MS + " ms.";
        }
        return null;
    }

    public static String validateThreads(String text) {
        int value = parsePositiveInt(text);
        if (value < 0) {
            return "Nombre de threads invalide.";
        }
        if (value < MIN_THREADS || value > MAX_THREADS) {
            return "Threads entre " + MIN_THREADS + " et " + MAX_THREADS + ".";
        }
        return null;
    }

    public static String validateCustomPorts(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "Liste de ports vide (ex. 80, 443, 8080-8082).";
        }
        try {
            if (ScanConfig.parseCustomPorts(text).size() > MAX_CUSTOM_PORTS) {
                return "Trop de ports (max " + MAX_CUSTOM_PORTS + ").";
            }
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    // Entier >= 0, ou -1 si le texte n'est pas un nombre.
    private static int parsePositiveInt(String text) {
        if (text == null) {
            return -1;
        }
        try {
            int value = Integer.parseInt(text.trim());
            return value < 0 ? -1 : value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
