package com.example.netscan;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.netscan.data.db.AppDatabase;
import com.example.netscan.data.db.HostEntity;
import com.example.netscan.data.db.KnownDeviceEntity;
import com.example.netscan.data.db.ScanEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

// Fichier : androidTest/.../ScanRepositoryTest.java
// Rôle : l'historique CHIFFRÉ se garde et se relit (Étape 4).
// TEST SUR TÉLÉPHONE : ./gradlew connectedDebugAndroidTest.
@RunWith(AndroidJUnit4.class)
public class ScanRepositoryTest {

    private AppDatabase db;

    @Before
    public void ouvre_base_chiffree_en_memoire() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        byte[] passphrase = com.example.netscan.security.DatabaseKeyProvider
                .getPassphrase(context);
        db = AppDatabase.inMemory(context, passphrase);
    }

    @After
    public void ferme_base() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void scan_et_hotes_sauvegardes_puis_relus() {
        long id = db.scanDao().insertScan(new ScanEntity(
                System.currentTimeMillis(), "TestWiFi",
                "192.168.1.10", "192.168.1.1", 2, 1, 2, 1500));
        HostEntity host = new HostEntity(id, "192.168.1.20", "box", 12, "80,443", null, null);
        db.scanDao().insertHosts(Collections.singletonList(host));

        List<HostEntity> relus = db.scanDao().hostsForScan(id);
        assertEquals(1, relus.size());
        assertEquals("192.168.1.20", relus.get(0).ip);
        assertEquals("80,443", relus.get(0).openPortsCsv);
    }

    @Test
    public void suppression_puis_historique_vide() {
        long id = db.scanDao().insertScan(new ScanEntity(
                System.currentTimeMillis(), null, null, null, 0, 0, 0, 0));
        db.scanDao().deleteHostsForScan(id);
        db.scanDao().deleteScan(id);
        assertEquals(0, db.scanDao().hostsForScan(id).size());
    }

    @Test
    public void appareil_marque_persiste() {
        db.knownDeviceDao().upsert(new KnownDeviceEntity(
                "192.168.1.20", "Votre TV connectée", System.currentTimeMillis()));
        List<KnownDeviceEntity> tous = db.knownDeviceDao().allSync();
        assertEquals(1, tous.size());
        assertEquals("192.168.1.20", tous.get(0).ip);
    }
}
