package com.example.netscan.data.model;

import java.util.ArrayList;
import java.util.List;

// Fichier : data/model/ScanConfig.java
// Rôle : paramètres du scan saisis en mode Expert (Étape 2).
// Pur Java : aucune dépendance Android, donc testable sans téléphone.
public class ScanConfig {

    // Préréglages de la liste déroulante des ports.
    public static final int PRESET_COMMON_PORTS = 0; // 1-1024
    public static final int PRESET_ALL_PORTS = 1;    // 1-65535
    public static final int PRESET_CUSTOM = 2;       // liste saisie

    private String cidr = "";
    private int portPreset = PRESET_COMMON_PORTS;
    private String customPortsText = "";
    private int timeoutMs = 800;
    private int threadCount = 50;
    // Mode Simple : découverte des appareils sans scan de ports (rapide).
    // Mode Expert : true (scan complet). Modifiable, défaut true.
    private boolean portScanEnabled = true;

    public String getCidr() {
        return cidr;
    }

    public void setCidr(String cidr) {
        this.cidr = cidr == null ? "" : cidr.trim();
    }

    public int getPortPreset() {
        return portPreset;
    }

    public void setPortPreset(int portPreset) {
        this.portPreset = portPreset;
    }

    public String getCustomPortsText() {
        return customPortsText;
    }

    public void setCustomPortsText(String customPortsText) {
        this.customPortsText = customPortsText == null ? "" : customPortsText.trim();
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getThreadCount() {
        return threadCount;
    }

    public void setThreadCount(int threadCount) {
        this.threadCount = threadCount;
    }

    public boolean isPortScanEnabled() {
        return portScanEnabled;
    }

    public void setPortScanEnabled(boolean portScanEnabled) {
        this.portScanEnabled = portScanEnabled;
    }

    // Liste des ports à scanner selon le préréglage choisi.
    // Le préréglage "tous les ports" est volontairement long : la
    // progression affichée permet de suivre et d'annuler.
    public List<Integer> getPortsToScan() {
        if (portPreset == PRESET_CUSTOM) {
            return parseCustomPorts(customPortsText);
        }
        int end = (portPreset == PRESET_ALL_PORTS) ? 65535 : 1024;
        List<Integer> ports = new ArrayList<>(end);
        for (int p = 1; p <= end; p++) {
            ports.add(p);
        }
        return ports;
    }

    // Analyse "80, 443, 8080-8082" en liste triée sans doublon.
    public static List<Integer> parseCustomPorts(String text) {
        List<Integer> ports = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("Liste de ports vide.");
        }
        for (String part : text.split(",")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (part.contains("-")) {
                String[] bounds = part.split("-", -1);
                if (bounds.length != 2) {
                    throw new IllegalArgumentException("Intervalle invalide : " + part);
                }
                int from = parsePort(bounds[0].trim());
                int to = parsePort(bounds[1].trim());
                if (from > to) {
                    throw new IllegalArgumentException("Intervalle inversé : " + part);
                }
                for (int p = from; p <= to; p++) {
                    if (!ports.contains(p)) {
                        ports.add(p);
                    }
                }
            } else {
                int p = parsePort(part);
                if (!ports.contains(p)) {
                    ports.add(p);
                }
            }
        }
        if (ports.isEmpty()) {
            throw new IllegalArgumentException("Liste de ports vide.");
        }
        return ports;
    }

    private static int parsePort(String text) {
        int p;
        try {
            p = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port invalide : " + text);
        }
        if (p < 1 || p > 65535) {
            throw new IllegalArgumentException("Port hors plage 1-65535 : " + p);
        }
        return p;
    }
}
