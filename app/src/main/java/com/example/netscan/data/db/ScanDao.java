package com.example.netscan.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

// Fichier : data/db/ScanDao.java
// Rôle : accès aux scans et à leurs hôtes (Étape 4).
// Les listes exposées en LiveData se mettent à jour seules.
@Dao
public interface ScanDao {

    @Insert
    long insertScan(ScanEntity scan);

    @Insert
    void insertHosts(List<HostEntity> hosts);

    @Query("SELECT * FROM scans ORDER BY timestamp DESC")
    LiveData<List<ScanEntity>> observeScans();

    @Query("SELECT * FROM hosts WHERE scanId = :scanId ORDER BY ip")
    List<HostEntity> hostsForScan(long scanId);

    @Query("SELECT * FROM scans ORDER BY timestamp DESC LIMIT 1")
    ScanEntity latestSync();

    @Query("SELECT * FROM scans ORDER BY timestamp DESC LIMIT 1 OFFSET 1")
    ScanEntity previousSync();

    @Query("DELETE FROM hosts WHERE scanId = :scanId")
    void deleteHostsForScan(long scanId);

    @Query("DELETE FROM scans WHERE id = :scanId")
    void deleteScan(long scanId);

    @Query("DELETE FROM hosts")
    void clearHosts();

    @Query("DELETE FROM scans")
    void clearScans();
}
