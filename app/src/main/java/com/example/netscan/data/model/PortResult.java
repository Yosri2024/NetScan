package com.example.netscan.data.model;

// Fichier : data/model/PortResult.java
// Rôle : résultat du scan pour un port TCP (Étape 2).
public class PortResult {

    private final int port;
    private final boolean open;
    // Bannière lue sur le port (peut être null : ex. TLS sans handshake).
    private String banner;

    public PortResult(int port, boolean open) {
        this.port = port;
        this.open = open;
    }

    public int getPort() {
        return port;
    }

    public boolean isOpen() {
        return open;
    }

    public String getBanner() {
        return banner;
    }

    public void setBanner(String banner) {
        this.banner = banner;
    }
}
