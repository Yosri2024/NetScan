package com.example.netscan.viewmodel;

import android.content.Context;
import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.netscan.R;
import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.ScanConfig;
import com.example.netscan.data.model.NetworkDetails;
import com.example.netscan.network.NetworkScanner;
import com.example.netscan.network.NetworkInfoHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// Fichier : viewmodel/ScanViewModel.java
// Rôle : état du scan conservé à la rotation (Étape 2).
// Le scan tourne hors thread UI ; les résultats sont publiés
// via LiveData (postValue, donc sans contrainte de thread).
public class ScanViewModel extends ViewModel {

    private final MutableLiveData<List<Host>> hosts = new MutableLiveData<>(new ArrayList<Host>());
    private final MutableLiveData<Pair<Integer, Integer>> progress = new MutableLiveData<>(new Pair<>(0, 0));
    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(false);
    private final MutableLiveData<String> error = new MutableLiveData<>(null);
    private final MutableLiveData<NetworkDetails> network = new MutableLiveData<>(null);

    private final ScanConfig config = new ScanConfig();
    // Liste synchronisée : remplie par le thread de scan, lue par l'UI.
    private final List<Host> collected = Collections.synchronizedList(new ArrayList<Host>());
    private final ExecutorService runner = Executors.newSingleThreadExecutor();
    private NetworkScanner scanner;

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

    public ScanConfig getConfig() {
        return config;
    }

    // Recharge les infos réseau (appelée à l'ouverture et avant chaque scan).
    // Agnostique du support : Wi-Fi, Ethernet, USB, VPN...
    public void loadNetwork(Context context) {
        NetworkDetails details = NetworkInfoHelper.getDetails(context);
        network.setValue(details);
        // Une plage déjà saisie par l'utilisateur n'est jamais écrasée.
        if (details != null && details.isUsable() && config.getCidr().isEmpty()) {
            String cidr = NetworkInfoHelper.getScanCidr(details);
            if (cidr != null) {
                config.setCidr(cidr);
            }
        }
    }

    public void startScan(Context context) {
        if (Boolean.TRUE.equals(scanning.getValue())) {
            return;
        }
        // Une plage laissée vide (aucun réseau détecté) ne peut pas démarrer
        // un scan : on retente une fois, puis on explicite sans planter.
        if (config.getCidr().isEmpty()) {
            loadNetwork(context);
            if (config.getCidr().isEmpty()) {
                error.setValue(context.getString(R.string.network_required));
                return;
            }
        }
        final Context appContext = context.getApplicationContext();
        collected.clear();
        hosts.setValue(new ArrayList<Host>());
        error.setValue(null);
        progress.setValue(new Pair<>(0, 0));
        scanning.setValue(true);
        scanner = new NetworkScanner(appContext);
        runner.execute(new Runnable() {
            @Override
            public void run() {
                scanner.scan(config, new NetworkScanner.Callback() {
                    @Override
                    public void onProgress(int done, int total) {
                        progress.postValue(new Pair<>(done, total));
                    }

                    @Override
                    public void onHostFound(Host host) {
                        collected.add(host);
                        hosts.postValue(new ArrayList<>(collected));
                    }

                    @Override
                    public void onFinished(List<Host> reachableHosts) {
                        scanning.postValue(false);
                    }

                    @Override
                    public void onError(String message) {
                        error.postValue(message);
                        scanning.postValue(false);
                    }
                });
            }
        });
    }

    public void cancelScan() {
        if (scanner != null) {
            scanner.cancel();
        }
    }

    @Override
    protected void onCleared() {
        cancelScan();
        runner.shutdownNow();
        super.onCleared();
    }
}
