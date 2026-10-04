package com.example.netscan.data;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.netscan.alerts.AlertHelper;
import com.example.netscan.data.db.AppDatabase;
import com.example.netscan.data.db.HostEntity;
import com.example.netscan.data.db.ScanEntity;
import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.PortResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Fichier : data/ScanRepository.java
// Rôle : sauvegarde et lecture de l'historique chiffré (Étape 4).
// Écritures sur un thread dédié ; listes exposées en LiveData.
// Si la base chiffrée est inaccessible (clé Keystore perdue, fichier
// corrompu), aucune exception ne remonte : les lectures renvoient des
// listes vides et les écritures sont ignorées. L'historique ne doit
// jamais faire tomber l'application.
public class ScanRepository {

    private static final String TAG = "ScanRepository";

    public interface SaveCallback {
        void onSaved(long scanId);
    }

    public interface HostsCallback {
        void onHosts(List<HostEntity> hosts);
    }

    public interface TwoScansCallback {
        // Hôtes des deux derniers scans (listes vides si absents).
        void onTwoScans(List<HostEntity> latestHosts, List<HostEntity> previousHosts,
                        boolean hasPrevious);
    }

    private static volatile ScanRepository instance;
    private final AppDatabase db;
    private final ExecutorService writer = Executors.newSingleThreadExecutor();

    public static ScanRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (ScanRepository.class) {
                if (instance == null) {
                    instance = new ScanRepository(context);
                }
            }
        }
        return instance;
    }

    private ScanRepository(Context context) {
        db = AppDatabase.getInstance(context);
    }

    // Vrai si l'historique chiffré est exploitable (false = base indisponible).
    public boolean isAvailable() {
        return db != null;
    }

    // Jamais null : une base indisponible se lit comme un historique vide.
    public LiveData<List<ScanEntity>> observeScans() {
        if (db == null) {
            return new MutableLiveData<>(new ArrayList<ScanEntity>());
        }
        return db.scanDao().observeScans();
    }

    // Sauvegarde un scan terminé + ses hôtes, avec les agrégats affichés.
    public void saveScan(final List<Host> hosts, final String ssid,
                         final String ownIp, final String gatewayIp,
                         final long durationMs, final Set<String> knownIps,
                         final SaveCallback callback) {
        if (db == null) {
            Log.w(TAG, "Scan non enregistré : historique indisponible.");
            return;
        }
        writer.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Host> safe = hosts == null ? new ArrayList<Host>() : hosts;
                    int recognized = AlertHelper.countRecognized(safe, knownIps, ownIp, gatewayIp);
                    int openTotal = 0;
                    for (Host host : safe) {
                        openTotal += host.getOpenPorts().size();
                    }
                    long id = db.scanDao().insertScan(new ScanEntity(
                            System.currentTimeMillis(), ssid, ownIp, gatewayIp,
                            safe.size(), recognized, openTotal, durationMs));
                    List<HostEntity> entities = new ArrayList<>(safe.size());
                    for (Host host : safe) {
                        entities.add(new HostEntity(id, host.getIpAddress(), host.getHostname(),
                                host.getLatencyMs(), joinPorts(host.getOpenPorts()),
                                firstBanner(host.getOpenPorts()), host.getServiceName()));
                    }
                    db.scanDao().insertHosts(entities);
                    if (callback != null) {
                        callback.onSaved(id);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Enregistrement du scan impossible.", e);
                }
            }
        });
    }

    public void getHosts(final long scanId, final HostsCallback callback) {
        if (db == null) {
            callback.onHosts(new ArrayList<HostEntity>());
            return;
        }
        writer.execute(new Runnable() {
            @Override
            public void run() {
                callback.onHosts(queryHosts(scanId));
            }
        });
    }

    // Les deux derniers scans : pour le bandeau "nouvel appareil" (ScanComparator).
    public void getLatestTwo(final TwoScansCallback callback) {
        if (db == null) {
            callback.onTwoScans(new ArrayList<HostEntity>(), new ArrayList<HostEntity>(), false);
            return;
        }
        writer.execute(new Runnable() {
            @Override
            public void run() {
                ScanEntity latest = null;
                ScanEntity previous = null;
                try {
                    latest = db.scanDao().latestSync();
                    previous = db.scanDao().previousSync();
                } catch (Exception e) {
                    Log.e(TAG, "Lecture de l'historique impossible.", e);
                }
                List<HostEntity> latestHosts = latest == null
                        ? new ArrayList<HostEntity>() : queryHosts(latest.id);
                List<HostEntity> previousHosts = previous == null
                        ? new ArrayList<HostEntity>() : queryHosts(previous.id);
                callback.onTwoScans(latestHosts, previousHosts, latest != null && previous != null);
            }
        });
    }

    public void deleteScan(final long scanId, final Runnable done) {
        if (db == null) {
            runDone(done);
            return;
        }
        writer.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    db.scanDao().deleteHostsForScan(scanId);
                    db.scanDao().deleteScan(scanId);
                } catch (Exception e) {
                    Log.e(TAG, "Suppression du scan impossible.", e);
                }
                runDone(done);
            }
        });
    }

    public void clearAll(final Runnable done) {
        if (db == null) {
            runDone(done);
            return;
        }
        writer.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    db.scanDao().clearHosts();
                    db.scanDao().clearScans();
                } catch (Exception e) {
                    Log.e(TAG, "Effacement de l'historique impossible.", e);
                }
                runDone(done);
            }
        });
    }

    // Une requête qui échoue ne doit pas casser l'écran : liste vide.
    private List<HostEntity> queryHosts(long scanId) {
        try {
            List<HostEntity> hosts = db.scanDao().hostsForScan(scanId);
            return hosts == null ? new ArrayList<HostEntity>() : hosts;
        } catch (Exception e) {
            Log.e(TAG, "Lecture des hôtes impossible.", e);
            return new ArrayList<HostEntity>();
        }
    }

    private static void runDone(Runnable done) {
        if (done != null) {
            done.run();
        }
    }

    // Reconstruit un Host d'affichage depuis une ligne de base (réouverture).
    public static Host toHost(HostEntity entity) {
        Host host = new Host(entity.ip, entity.hostname, true, entity.latencyMs);
        host.setServiceName(entity.serviceName);
        if (entity.openPortsCsv != null && !entity.openPortsCsv.isEmpty()) {
            boolean first = true;
            for (String part : entity.openPortsCsv.split(",")) {
                try {
                    PortResult port = new PortResult(Integer.parseInt(part.trim()), true);
                    if (first && entity.banner != null) {
                        port.setBanner(entity.banner);
                        first = false;
                    }
                    host.addOpenPort(port);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return host;
    }

    public static List<String> ipsOf(List<HostEntity> hosts) {
        List<String> ips = new ArrayList<>();
        if (hosts != null) {
            for (HostEntity host : hosts) {
                ips.add(host.ip);
            }
        }
        return ips;
    }

    public static Set<String> knownIpsOf(List<com.example.netscan.data.db.KnownDeviceEntity> devices) {
        Set<String> ips = new HashSet<>();
        if (devices != null) {
            for (com.example.netscan.data.db.KnownDeviceEntity device : devices) {
                ips.add(device.ip);
            }
        }
        return ips;
    }

    private static String joinPorts(List<PortResult> ports) {
        StringBuilder csv = new StringBuilder();
        for (PortResult port : ports) {
            if (csv.length() > 0) {
                csv.append(',');
            }
            csv.append(port.getPort());
        }
        return csv.toString();
    }

    private static String firstBanner(List<PortResult> ports) {
        for (PortResult port : ports) {
            if (port.getBanner() != null) {
                return port.getBanner();
            }
        }
        return null;
    }

}
