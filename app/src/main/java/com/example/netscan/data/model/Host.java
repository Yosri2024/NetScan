package com.example.netscan.data.model;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Fichier : data/model/Host.java
// Rôle : un appareil détecté sur le réseau local (Étape 2).
// Pas d'adresse MAC : Android 10+ bloque sa lecture (voir spec).
// Les champs enrichis après coup (nom, service mDNS, TTL, OS, ports) sont
// lus par l'interface pendant que le scan les complète : ils sont donc
// volatils / concurrents.
public class Host {

    private final String ipAddress;
    private volatile String hostname;
    private final boolean reachable;
    // Latence en ms, -1 si injoignable.
    private final long latencyMs;
    private final List<PortResult> openPorts = new CopyOnWriteArrayList<>();
    // Noms de services mDNS/NSD (séparés par " | "), peut rester null.
    private volatile String serviceName;
    // TTL observé via ping (64 = Linux/Android/Apple, 128 = Windows, 255 = réseau), -1 si inconnu.
    private volatile int ttl = -1;
    // OS figé en fin de scan (ou relu de l'historique) ; null = à recalculer.
    private volatile OsInfo os;

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

    // Le port ouvert correspondant, ou null.
    public PortResult findOpenPort(int port) {
        for (PortResult candidate : openPorts) {
            if (candidate != null && candidate.getPort() == port) {
                return candidate;
            }
        }
        return null;
    }

    // Ajoute le port, ou complète la bannière du port déjà connu : une même
    // porte ne doit jamais apparaître deux fois (sonde OS puis scan complet).
    public void mergeOpenPort(PortResult port) {
        if (port == null) {
            return;
        }
        PortResult existing = findOpenPort(port.getPort());
        if (existing == null) {
            openPorts.add(port);
        } else if (existing.getBanner() == null && port.getBanner() != null) {
            existing.setBanner(port.getBanner());
        }
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public int getTtl() {
        return ttl;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public OsInfo getOs() {
        return os;
    }

    public void setOs(OsInfo os) {
        this.os = os;
    }
}
