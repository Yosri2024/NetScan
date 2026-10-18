package com.example.netscan.viewmodel;

import android.content.Context;
import android.os.SystemClock;
import android.util.Pair;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.netscan.NetScanApp;
import com.example.netscan.R;
import com.example.netscan.data.KnownDeviceRepository;
import com.example.netscan.data.ScanRepository;
import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.NetworkDetails;
import com.example.netscan.data.model.ScanConfig;
import com.example.netscan.network.NetworkInfoHelper;
import com.example.netscan.network.NetworkScanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Fichier : viewmodel/ScanViewModel.java
// Rôle : état du scan, PARTAGÉ par les modes Simple et Expert.
// Il est rattaché à l'application (voir NetScanApp) et non à un écran :
//  - basculer Simple <-> Expert, ouvrir les réglages ou tourner l'écran ne
//    perd plus ni le scan en cours ni ses résultats ;
//  - l'enregistrement du scan (historique + « dernier scan ») est fait ICI,
//    une seule fois, quel que soit l'écran affiché à la fin du scan ;
//  - les écrans n'ont plus d'état « scan lancé » à eux : ils ne font
//    qu'observer (plus de résultats perdus, de toasts rejoués ni de scan
//    annulé qui ouvre quand même les résultats).
public class ScanViewModel extends ViewModel {

    private final MutableLiveData<List<Host>> hosts = new MutableLiveData<>(new ArrayList<Host>());
    private final MutableLiveData<Pair<Integer, Integer>> progress = new MutableLiveData<>(new Pair<>(0, 0));
    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<NetworkDetails> network = new MutableLiveData<>(null);
    private final MutableLiveData<NetworkScanner.Phase> phase = new MutableLiveData<>(null);

    // Liste synchronisée : remplie par le thread de scan, lue par l'UI.
    private final List<Host> collected = Collections.synchronizedList(new ArrayList<Host>());
    private final ExecutorService runner = Executors.newSingleThreadExecutor();
    private NetworkScanner scanner;
    private volatile boolean cancelRequested;
    // Le scan Simple doit ouvrir l'écran de résultats une fois terminé (lu une seule fois).
    private volatile boolean openResultsRequested;

    // ViewModel unique de l'application, commun aux deux modes.
    public static ScanViewModel get(android.app.Activity activity) {
        return new ViewModelProvider((NetScanApp) activity.getApplication())
                .get(ScanViewModel.class);
    }

    public LiveData<List<Host>> getHosts() {
        return hosts;
    }

    public LiveData<Pair<Integer, Integer>> getProgress() {
        return progress;
    }

    public LiveData<Boolean> getScanning() {
        return scanning;
    }

    public LiveData<String> getError() {
        return error;
    }

    public LiveData<NetworkDetails> getNetwork() {
        return network;
    }

    // Étape en cours (null hors scan).
    public LiveData<NetworkScanner.Phase> getPhase() {
        return phase;
    }

    // Le dernier scan (ou celui en cours) a été annulé par l'utilisateur.
    public boolean wasCancelled() {
        return cancelRequested;
    }

    public boolean isScanning() {
        return Boolean.TRUE.equals(scanning.getValue());
    }

    // L'erreur a été affichée : on l'efface pour qu'une rotation ne la rejoue pas.
    public void consumeError() {
        error.setValue(null);
    }

    // Vrai une seule fois : le scan Simple vient de se terminer normalement.
    public boolean takeOpenResultsRequest() {
        boolean request = openResultsRequested;
        openResultsRequested = false;
        return request;
    }

    // Un scan Simple lancé puis abandonné (passage en Expert) ne doit pas ouvrir
    // les résultats longtemps après : l'écran Expert efface la demande.
    public void discardOpenResultsRequest() {
        openResultsRequested = false;
    }

    // Recharge les infos réseau (appelée à l'ouverture et avant chaque scan).
    // Agnostique du support : Wi-Fi, Ethernet, USB, VPN...
    public void loadNetwork(Context context) {
        network.setValue(NetworkInfoHelper.getDetails(context));
    }

    // Plage /24 (ou plus large) du réseau actuel ; null si aucun réseau exploitable.
    @Nullable
    public String getAutoCidr() {
        NetworkDetails details = network.getValue();
        if (details != null && details.isUsable()) {
            return NetworkInfoHelper.getScanCidr(details);
        }
        return null;
    }

    // Scan du mode Simple : découverte + identification du système, sans scan
    // de ports. La plage est toujours celle du réseau actuel (jamais périmée).
    public void startSimpleScan(Context context) {
        loadNetwork(context);
        ScanConfig simple = new ScanConfig();
        String cidr = getAutoCidr();
        simple.setCidr(cidr == null ? "" : cidr);
        simple.setPortScanEnabled(false);
        simple.setOsDetectionEnabled(true);
        startScan(context, simple, true);
    }

    // Scan du mode Expert avec la configuration validée par l'écran.
    public void startExpertScan(Context context, ScanConfig config) {
        loadNetwork(context);
        startScan(context, config, false);
    }

    private void startScan(Context context, ScanConfig requested, boolean openResultsWhenDone) {
        if (isScanning()) {
            return;
        }
        if (requested.getCidr().isEmpty()) {
            error.setValue(context.getString(R.string.network_required));
            return;
        }
        final Context appContext = context.getApplicationContext();
        final ScanConfig config = requested.copy();
        final NetworkDetails details = network.getValue();
        final String ssid = details == null ? null : details.getSsid();
        final String ownIp = details == null ? null : details.getIpAddress();
        final String gatewayIp = details == null ? null : details.getGateway();
        final long startedAt = SystemClock.elapsedRealtime();

        cancelRequested = false;
        openResultsRequested = openResultsWhenDone;
        collected.clear();
        hosts.setValue(new ArrayList<Host>());
        error.setValue(null);
        progress.setValue(new Pair<>(0, 0));
        phase.setValue(NetworkScanner.Phase.DISCOVERY);
        scanning.setValue(true);

        final NetworkScanner current = new NetworkScanner(appContext);
        scanner = current;
        runner.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    current.scan(config, new NetworkScanner.Callback() {
                        @Override
                        public void onProgress(int done, int total) {
                            progress.postValue(new Pair<>(done, total));
                        }

                        @Override
                        public void onPhase(NetworkScanner.Phase newPhase) {
                            progress.postValue(new Pair<>(0, 0));
                            phase.postValue(newPhase);
                        }

                        @Override
                        public void onHostFound(Host host) {
                            collected.add(host);
                            hosts.postValue(new ArrayList<>(collected));
                        }

                        @Override
                        public void onFinished(List<Host> reachableHosts) {
                            finish(appContext, config, ssid, ownIp, gatewayIp, startedAt);
                        }

                        @Override
                        public void onError(String message) {
                            fail(message);
                        }
                    });
                } catch (RuntimeException e) {
                    // Un scan ne doit jamais rester bloqué « en cours » après un bug.
                    fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                }
            }
        });
    }

    // Fin normale ou annulée (thread de scan).
    private void finish(Context appContext, ScanConfig config, String ssid, String ownIp,
                        String gatewayIp, long startedAt) {
        List<Host> finalHosts = new ArrayList<>(collected);
        // Republie les hôtes : leur OS est maintenant figé avec tous les indices.
        hosts.postValue(finalHosts);
        if (cancelRequested) {
            openResultsRequested = false; // un scan annulé n'ouvre pas les résultats
        } else {
            persist(appContext, config, finalHosts, ssid, ownIp, gatewayIp,
                    SystemClock.elapsedRealtime() - startedAt);
        }
        phase.postValue(null);
        scanning.postValue(false);
    }

    private void fail(String message) {
        openResultsRequested = false;
        error.postValue(message);
        phase.postValue(null);
        scanning.postValue(false);
    }

    // « Dernier scan » en mémoire + historique chiffré, une seule fois par scan.
    private void persist(Context appContext, ScanConfig config, List<Host> finalHosts,
                         String ssid, String ownIp, String gatewayIp, long durationMs) {
        NetScanApp app = (NetScanApp) appContext;
        app.storeLastScan(finalHosts, config, ownIp, gatewayIp);
        Set<String> known = new HashSet<>(
                KnownDeviceRepository.getInstance(appContext).getKnownIpsBlocking());
        known.addAll(app.getKnownIps());
        for (String ip : known) {
            app.markKnown(ip);
        }
        ScanRepository.getInstance(appContext).saveScan(finalHosts, ssid, ownIp, gatewayIp,
                durationMs, known, null);
    }

    public void cancelScan() {
        cancelRequested = true;
        NetworkScanner current = scanner;
        if (current != null) {
            current.cancel();
        }
    }

    @Override
    protected void onCleared() {
        cancelScan();
        runner.shutdownNow();
        super.onCleared();
    }
}
