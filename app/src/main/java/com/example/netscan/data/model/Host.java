package com.example.netscan.data.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Fichier : data/model/Host.java
// Rôle : un appareil détecté sur le réseau local (Étape 2).
// Pas d'adresse MAC : Android 10+ bloque sa lecture (voir spec).
public class Host {

    private final String ipAddress;
    private String hostname;
    private final boolean reachable;
    // Latence en ms, -1 si injoignable.
    private final long latencyMs;
    private final List<PortResult> openPorts = new ArrayList<>();
    // Nom de service mDNS/NSD, peut rester null (identification approximative).
    private String serviceName;

    public Host(String ipAddress, String hostname, boolean reachable, long latencyMs) {
        this.ipAddress = ipAddress;
        this.hostname = hostname;
        this.reachable = reachable;
        this.latencyMs = latencyMs;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public boolean isReachable() {
        return reachable;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public List<PortResult> getOpenPorts() {
        return Collections.unmodifiableList(openPorts);
    }

    public void addOpenPort(PortResult port) {
        openPorts.add(port);
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
