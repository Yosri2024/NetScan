package com.example.netscan.network;

import android.content.Context;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.PortResult;
import com.example.netscan.data.model.ScanConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// Fichier : network/NetworkScanner.java
// Rôle : orchestre le scan (Étape 2). BLOQUANT : à appeler hors thread UI.
// Phase 1 : balayage des hôtes. Phase 2 : ports + bannières sur les
// hôtes joignables. Annulable via cancel().
public class NetworkScanner {

    public interface Callback {
        void onProgress(int done, int total);
        void onHostFound(Host host);
        void onFinished(List<Host> reachableHosts);
        void onError(String message);
    }

    private final Context appContext;
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile ExecutorService executor;

    public NetworkScanner(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void cancel() {
        cancelled.set(true);
        ExecutorService current = executor;
        if (current != null) {
            current.shutdownNow();
        }
    }

    public void scan(ScanConfig config, Callback callback) {
        cancelled.set(false);
        List<String> targets;
        List<Integer> ports;
        try {
            targets = SubnetCalculator.listHosts(config.getCidr());
            ports = config.getPortsToScan();
        } catch (IllegalArgumentException e) {
            callback.onError(e.getMessage());
            return;
        }

        // Écoute mDNS en parallèle du balayage (enrichissement best-effort).
        NsdDiscovery nsd = new NsdDiscovery(appContext);
        nsd.start(null);

        // Phase 1 : découverte des hôtes.
        List<Host> reachable = discoverHosts(targets, config, callback);
        if (cancelled.get()) {
            nsd.stop();
            callback.onFinished(reachable);
            return;
        }

        // Phase 2 : ports + bannières (sautée en mode Simple : découverte seule).
        if (config.isPortScanEnabled()) {
            scanPorts(reachable, ports, config, callback);
        } else {
            for (Host host : reachable) {
                if (cancelled.get()) {
                    break;
                }
                callback.onHostFound(host);
            }
        }

        // Fusionne les noms de services mDNS découverts.
        Map<String, String> services = nsd.snapshot();
        nsd.stop();
        for (Host host : reachable) {
            String name = services.get(host.getIpAddress());
            if (name != null) {
                host.setServiceName(name);
            }
        }
        callback.onFinished(reachable);
    }

    private List<Host> discoverHosts(List<String> targets, ScanConfig config, Callback callback) {
        List<Host> reachable = new ArrayList<>();
        int poolSize = Math.max(1, Math.min(config.getThreadCount(), targets.size()));
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        executor = pool;
        CompletionService<Host> completion = new ExecutorCompletionService<>(pool);
        List<Future<Host>> futures = new ArrayList<>(targets.size());
        try {
            for (String ip : targets) {
                if (cancelled.get()) {
                    break;
                }
                final String target = ip;
                final int timeout = config.getTimeoutMs();
                futures.add(completion.submit(new Callable<Host>() {
                    @Override
                    public Host call() {
                        return HostDiscovery.discoverHost(target, timeout);
                    }
                }));
            }
            int done = 0;
            for (int i = 0; i < futures.size(); i++) {
                if (cancelled.get()) {
                    break;
                }
                try {
                    Host host = completion.take().get();
                    done++;
                    callback.onProgress(done, targets.size());
                    if (host.isReachable()) {
                        reachable.add(host);
                    }
                } catch (Exception ignored) {
                    done++;
                    callback.onProgress(done, targets.size());
                }
            }
        } finally {
            pool.shutdownNow();
            executor = null;
        }
        return reachable;
    }

    private void scanPorts(List<Host> reachable, List<Integer> ports,
                           ScanConfig config, Callback callback) {
        if (reachable.isEmpty() || ports.isEmpty()) {
            for (Host host : reachable) {
                callback.onHostFound(host);
            }
            return;
        }
        int total = reachable.size() * ports.size();
        AtomicInteger done = new AtomicInteger(0);
        int poolSize = Math.min(64, ports.size());
        for (Host host : reachable) {
            if (cancelled.get()) {
                break;
            }
            List<PortResult> open = PortScanner.scanPorts(
                    host.getIpAddress(), ports, config.getTimeoutMs(),
                    poolSize, cancelled, done);
            for (PortResult port : open) {
                if (cancelled.get()) {
                    break;
                }
                // Bannière avec un timeout plafonné (ne ralentit pas le scan).
                port.setBanner(BannerGrabber.grabBanner(
                        host.getIpAddress(), port.getPort(),
                        Math.min(config.getTimeoutMs(), 1500)));
                host.addOpenPort(port);
            }
            callback.onProgress(done.get(), total);
            callback.onHostFound(host);
        }
    }
}
