package com.example.netscan.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

// Fichier : data/db/KnownDeviceDao.java
// Rôle : appareils reconnus par l'utilisateur (Étape 4).
@Dao
public interface KnownDeviceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(KnownDeviceEntity device);

    @Query("DELETE FROM known_devices WHERE ip = :ip")
    void delete(String ip);

    @Query("SELECT * FROM known_devices ORDER BY markedAt DESC")
    LiveData<List<KnownDeviceEntity>> observeAll();

    @Query("SELECT * FROM known_devices ORDER BY markedAt DESC")
    List<KnownDeviceEntity> allSync();

    @Query("DELETE FROM known_devices")
    void clearAll();
}
