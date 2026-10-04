package com.example.netscan.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

// Fichier : data/db/ScanEntity.java
// Rôle : un scan horodaté dans la base CHIFFRÉE (Étape 4).
// Les agrégats (totaux) évitent des requêtes N+1 dans les historiques.
@Entity(tableName = "scans")
public class ScanEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long timestamp;
    public String ssid;
    public String ownIp;
    public String gatewayIp;
    public int totalHosts;
    public int recognizedCount;
    public int openPortsTotal;
    // Durée du scan en ms (statistique Expert).
    public long durationMs;

    public ScanEntity(long timestamp, String ssid, String ownIp, String gatewayIp,
                      int totalHosts, int recognizedCount, int openPortsTotal, long durationMs) {
        this.timestamp = timestamp;
        this.ssid = ssid;
        this.ownIp = ownIp;
        this.gatewayIp = gatewayIp;
        this.totalHosts = totalHosts;
        this.recognizedCount = recognizedCount;
        this.openPortsTotal = openPortsTotal;
        this.durationMs = durationMs;
    }
}
