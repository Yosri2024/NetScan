package com.example.netscan.data.db;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

// Fichier : data/db/KnownDeviceEntity.java
// Rôle : appareil marqué "C'est mon appareil" (Étape 4).
// L'IP est la clé : un appareil garde son statut entre les scans.
@Entity(tableName = "known_devices")
public class KnownDeviceEntity {

    // Clé primaire TEXT : Room exige @NonNull (NULL interdit en PK SQLite).
    @PrimaryKey
    @NonNull
    public String ip;

    public String label;
    public long markedAt;

    public KnownDeviceEntity(String ip, String label, long markedAt) {
        this.ip = ip;
        this.label = label;
        this.markedAt = markedAt;
    }
}
