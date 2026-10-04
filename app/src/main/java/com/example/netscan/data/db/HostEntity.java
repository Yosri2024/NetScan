package com.example.netscan.data.db;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

// Fichier : data/db/HostEntity.java
// Rôle : un hôte joignable rattaché à un scan (Étape 4).
// Ports stockés en CSV ("80,443") + première bannière : compact et lisible.
@Entity(tableName = "hosts", indices = {@Index("scanId")})
public class HostEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long scanId;
    public String ip;
    public String hostname;
    public long latencyMs;
    public String openPortsCsv;
    public String banner;
    public String serviceName;

    public HostEntity(long scanId, String ip, String hostname, long latencyMs,
                      String openPortsCsv, String banner, String serviceName) {
        this.scanId = scanId;
        this.ip = ip;
        this.hostname = hostname;
        this.latencyMs = latencyMs;
        this.openPortsCsv = openPortsCsv;
        this.banner = banner;
        this.serviceName = serviceName;
    }
}
