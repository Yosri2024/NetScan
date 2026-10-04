package com.example.netscan.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.netscan.data.db.AppDatabase;
import com.example.netscan.data.db.KnownDeviceEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Fichier : data/KnownDeviceRepository.java
// Rôle : appareils marqués "C'est mon appareil", persistés CHIFFRÉS (Étape 4).
// Remplace la mémoire vive de l'Étape 3 (migration transparente : le premier
// marquage en base fait foi ; l'ancien set mémoire reste en repli d'affichage).
// Base inaccessible : listes vides, marquages ignorés, aucun plantage.
public class KnownDeviceRepository {

    private static final String TAG = "KnownDeviceRepository";

    private static volatile KnownDeviceRepository instance;
    private final AppDatabase db;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    public static KnownDeviceRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (KnownDeviceRepository.class) {
                if (instance == null) {
                    instance = new KnownDeviceRepository(context);
                }
            }
        }
        return instance;
    }

    private KnownDeviceRepository(Context context) {
        db = AppDatabase.getInstance(context);
    }

    // Jamais null : une base indisponible se lit comme une liste vide.
    public LiveData<List<KnownDeviceEntity>> observeAll() {
        if (db == null) {
            return new MutableLiveData<>(new ArrayList<KnownDeviceEntity>());
        }
        return db.knownDeviceDao().observeAll();
    }

    public void markKnown(final String ip, final String label) {
        if (ip == null || db == null) {
            return;
        }
        writer.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    db.knownDeviceDao().upsert(
                            new KnownDeviceEntity(ip, label, System.currentTimeMillis()));
                } catch (Exception e) {
                    Log.e(TAG, "Marquage impossible.", e);
                }
            }
        });
    }

    public void unmark(final String ip) {
        if (db == null) {
            return;
        }
        writer.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    db.knownDeviceDao().delete(ip);
                } catch (Exception e) {
                    Log.e(TAG, "Demarquage impossible.", e);
                }
            }
        });
    }
}
