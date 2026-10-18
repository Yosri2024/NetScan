package com.example.netscan.network;

import android.content.Context;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.PortResult;
import com.example.netscan.data.model.ScanConfig;
import com.example.netscan.utils.OsGuesser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
// Phase 1 : balayage des hôtes joignables.
// Phase 2 : identification du système (TTL + ports signatures + bannières),
//           en modes Simple ET Expert.
// Phase 3 : scan de ports + bannières (Expert seulement).
// Annulable via cancel().
public class NetworkScanner {

    // Étape en cours, pour l'affichage (« Identification des systèmes… »).
    public enum Phase {
        DISCOVERY,
        OS_DETECTION,
        PORTS
    }

    public interface Callback {
        // La progression est propre à chaque phase (done/total repart de 0).
        void onProgress(int done, int total);
        void onHostFound(Host host);
        void onFinished(List<Host> reachableHosts);
        void onError(String message);

        // Appelé hors thread UI au début de chaque phase.
        default void onPhase(Phase phase) {
        }
    }

    // Timeout court pour la sonde OS : un port ouvert répond vite, un port
    // fermé coûte au plus ce délai.
    private static final int OS_PROBE_TIMEOUT_MS = 400;
    private static final int OS_POOL_SIZE = 10;

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

    // Un NetworkScanner sert pour UN scan : le drapeau d'annulation n'est donc
    // pas remis à zéro ici, sinon un « Annuler » arrivé juste avant le début
    // du scan serait perdu.
    public void scan(ScanConfig config, Callback callback) {
        List<String> targets;
        // Les ports ne sont évalués que si le scan de ports est actif : une
        // liste personnalisée invalide ne doit pas bloquer un scan Simple.
        List<Integer> ports = Collections.emptyList();
        try {
            targets = SubnetCalculator.listHosts(config.getCidr());
            if (config.isPortScanEnabled()) {
                ports = config.getPortsToScan();
            }
        } catch (IllegalArgumentException e) {
            callback.onError(e.getMessage());
            return;
        }

        // Écoute mDNS en parallèle du balayage (enrichissement best-effort).
        NsdDiscovery nsd = new NsdDiscovery(appContext);
        nsd.start(null);

        // Phase 1 : découverte des hôtes.
        callback.onPhase(Phase.DISCOVERY);
        List<Host> reachable = discoverHosts(targets, config, callback);

        if (!cancelled.get()) {
            mergeServices(nsd, reachable);

            // Phase 2 : identification du système (Simple et Expert).
            if (config.isOsDetectionEnabled()) {
                callback.onPhase(Phase.OS_DETECTION);
                Set<Integer> alreadyScanned = new HashSet<>(ports);
                detectOs(reachable, alreadyScanned, callback);
            }
        }

        if (!cancelled.get()) {
            // Phase 3 : ports + bannières (sautée en mode Simple).
            if (config.isPortScanEnabled()) {
                callback.onPhase(Phase.PORTS);
                scanPorts(reachable, ports, config, callback);
            } else {
                for (Host host : reachable) {
                    if (cancelled.get()) {
                        break;
                    }
                    callback.onHostFound(host);
                }
            }
        }

        // Fin : derniers services mDNS, puis OS figé avec TOUS les indices.
        mergeServices(nsd, reachable);
        nsd.stop();
        for (Host host : reachable) {
            host.setOs(OsGuesser.compute(host));
        }
        callback.onFinished(reachable);
    }

    private void mergeServices(NsdDiscovery nsd, List<Host> hosts) {
        Map<String, String> services = nsd.snapshot();
        for (Host host : hosts) {
            String name = services.get(host.getIpAddress());
            if (name != null) {
                host.setServiceName(name);
            }
        }
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

    // Identification du système : un appareil par thread, les ports signatures
    // et le ping se font en parallèle dans OsProber. Les ports que le scan
    // Expert va de toute façon parcourir ne sont pas sondés deux fois.
    private void detectOs(List<Host> reachable, final Set<Integer> alreadyScanned,
                          final Callback callback) {
        if (reachable.isEmpty()) {
            return;
        }
        final int total = reachable.size();
        final AtomicInteger done = new AtomicInteger(0);
        int poolSize = Math.max(1, Math.min(OS_POOL_SIZE, total));
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        executor = pool;
        callback.onProgress(0, total);
        try {
            List<Future<?>> futures = new ArrayList<>(total);
            for (final Host host : reachable) {
                if (cancelled.get()) {
                    break;
                }
                futures.add(pool.submit(new Runnable() {
                    @Override
                    public void run() {
                        OsProber.enrichHost(host, OS_PROBE_TIMEOUT_MS, alreadyScanned, cancelled);
                        callback.onProgress(done.incrementAndGet(), total);
                    }
                }));
            }
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {
                    // Une sonde en échec : l'hôte garde ses autres indices.
                }
            }
        } finally {
            pool.shutdownNow();
            executor = null;
        }
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
        callback.onProgress(0, total);
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
                // Déjà vu (et déjà lu) lors de la phase OS : pas de seconde connexion.
                PortResult known = host.findOpenPort(port.getPort());
                if (known == null || known.getBanner() == null) {
                    // Bannière avec un timeout plafonné (ne ralentit pas le scan).
                    port.setBanner(BannerGrabber.grabBanner(
                            host.getIpAddress(), port.getPort(),
                            Math.min(config.getTimeoutMs(), 1500)));
                }
                host.mergeOpenPort(port);
            }
            callback.onProgress(done.get(), total);
            callback.onHostFound(host);
        }
    }
}
