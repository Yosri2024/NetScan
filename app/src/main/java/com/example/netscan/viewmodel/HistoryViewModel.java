package com.example.netscan.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.example.netscan.data.KnownDeviceRepository;
import com.example.netscan.data.ScanRepository;
import com.example.netscan.data.db.KnownDeviceEntity;
import com.example.netscan.data.db.ScanEntity;

import java.util.List;

// Fichier : viewmodel/HistoryViewModel.java
// Rôle : historiques Simple + Expert (Étape 4). Les listes de scans et
// d'appareils reconnus sont des LiveData Room (mises à jour seules) ; le
// détail d'un scan est chargé à la demande.
public class HistoryViewModel extends AndroidViewModel {

    private final ScanRepository scans;
    private final KnownDeviceRepository known;

    public HistoryViewModel(@NonNull Application application) {
        super(application);
        scans = ScanRepository.getInstance(application);
        known = KnownDeviceRepository.getInstance(application);
    }

    public LiveData<List<ScanEntity>> getScans() {
        return scans.observeScans();
    }

    public LiveData<List<KnownDeviceEntity>> getKnownDevices() {
        return known.observeAll();
    }

    public void deleteScan(long scanId) {
        scans.deleteScan(scanId, null);
    }

    public void clearHistory() {
        scans.clearAll(null);
    }

    public void checkNewcomers(ScanRepository.TwoScansCallback callback) {
        scans.getLatestTwo(callback);
    }

    public void markKnown(String ip, String label) {
        known.markKnown(ip, label);
    }

    public ScanRepository repository() {
        return scans;
    }
}
