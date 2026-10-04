package com.example.netscan;

import android.app.Application;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.ScanConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Fichier : NetScanApp.java
// Rôle : classe Application (Étape 1 + mémoire partagée Étape 3).
// Garde le dernier scan et les appareils marqués "à moi" pour que la
// bascule Simple/Expert ne perde pas l'état (migré vers Room à l'Étape 4).
// Applique aussi le thème choisi par l'utilisateur (mode nuit).
public class NetScanApp extends Application {

    // Dernier scan terminé (copie). Lu par SimpleResultsActivity.
    private final List<Host> lastHosts = new ArrayList<>();
    private long lastScanTimestamp;
    private String lastOwnIp;
    private String lastGatewayIp;
    // Appareils marqués "C'est mon appareil" (mémoire vive ; base Étape 4).
    private final Set<String> knownIps = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        com.example.netscan.mode.ThemeManager.applyStoredAsync(this);
    }

    public synchronized void storeLastScan(List<Host> hosts, ScanConfig config,
                                           String ownIp, String gatewayIp) {
        lastHosts.clear();
        if (hosts != null) {
            lastHosts.addAll(hosts);
        }
        lastScanTimestamp = System.currentTimeMillis();
        lastOwnIp = ownIp;
        lastGatewayIp = gatewayIp;
    }

    public synchronized List<Host> getLastHosts() {
        return new ArrayList<>(lastHosts);
    }

    public synchronized long getLastScanTimestamp() {
        return lastScanTimestamp;
    }

    public synchronized String getLastOwnIp() {
        return lastOwnIp;
    }

    public synchronized String getLastGatewayIp() {
        return lastGatewayIp;
    }

    public synchronized Set<String> getKnownIps() {
        return new HashSet<>(knownIps);
    }

    public synchronized void markKnown(String ip) {
        if (ip != null) {
            knownIps.add(ip);
        }
    }
}
