package com.example.netscan.network;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

// Fichier : network/NsdDiscovery.java
// Rôle : découverte mDNS/NSD pour nommer les appareils ET aider à identifier
// leur système (Étape 2). Tous les appareils n'annoncent pas de service
// (voir spec, limites Android).
//
// Deux contraintes d'Android à respecter :
//  - un DiscoveryListener ne peut servir qu'à UNE découverte : il en faut un
//    par type de service (sinon « listener already in use » dès le 2e type) ;
//  - les résolutions doivent se faire l'une après l'autre (FAILURE_ALREADY_ACTIVE).
//
// Pour chaque service on garde « nom._type._tcp (attributs utiles) », ex.
// « Salon._googlecast._tcp (md=Chromecast) » : le TYPE et le modèle annoncé
// sont les meilleurs indices d'OS (AirPlay, Google Cast, imprimante...).
public class NsdDiscovery {

    // Types utiles à l'identification : Apple (_airplay, _companion-link,
    // _device-info), Android TV / Chromecast, Linux (_workstation, _ssh),
    // Windows/Samba (_smb), imprimantes (_ipp, _printer), web (_http).
    private static final String[] SERVICE_TYPES = {"_http._tcp.", "_workstation._tcp.",
            "_ssh._tcp.", "_smb._tcp.", "_device-info._tcp.", "_airplay._tcp.",
            "_companion-link._tcp.", "_googlecast._tcp.", "_androidtvremote2._tcp.",
            "_printer._tcp.", "_ipp._tcp."};
    // Attributs TXT qui donnent un modèle ou une version de système.
    private static final String[] TXT_KEYS = {"md", "model", "am", "ty", "usb_mfg",
            "usb_mdl", "product", "osvers", "rpmd", "fn"};
    // Fenêtre d'écoute : passé ce délai, arrêt automatique.
    private static final long DISCOVERY_WINDOW_MS = 12000;
    // Une résolution qui ne répond pas ne doit pas bloquer la file.
    private static final long RESOLVE_TIMEOUT_MS = 2000;
    private static final int MAX_PENDING = 64;
    private static final int MAX_ATTR_LENGTH = 40;

    public interface Listener {
        void onServiceResolved(String ipAddress, String serviceName, int port);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> ipToService = new ConcurrentHashMap<>();

    // État protégé par « lock » (callbacks NsdManager sur des threads variés).
    private final Object lock = new Object();
    private final List<NsdManager.DiscoveryListener> discoveryListeners = new ArrayList<>();
    private final Queue<NsdServiceInfo> pending = new ArrayDeque<>();
    private boolean running;
    private boolean resolving;
    private int resolveGeneration;

    public NsdDiscovery(Context context) {
        this.appContext = context.getApplicationContext();
    }

    // Démarre sur le thread principal (exigé par NsdManager).
    public void start(final Listener listener) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                startOnMainThread(listener);
            }
        });
    }

    private void startOnMainThread(final Listener listener) {
        NsdManager nsd = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            return;
        }
        synchronized (lock) {
            if (running) {
                return;
            }
            running = true;
        }
        for (String type : SERVICE_TYPES) {
            NsdManager.DiscoveryListener discovery = newDiscoveryListener(listener);
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discovery);
                synchronized (lock) {
                    discoveryListeners.add(discovery);
                }
            } catch (Exception ignored) {
                // Un type refusé n'empêche pas les autres.
            }
        }
        // Arrêt automatique après la fenêtre d'écoute.
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stop();
            }
        }, DISCOVERY_WINDOW_MS);
    }

    private NsdManager.DiscoveryListener newDiscoveryListener(final Listener listener) {
        return new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                enqueue(serviceInfo, listener);
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
            }
        };
    }

    // ------------------------------------------------------------------
    // Résolution en série
    // ------------------------------------------------------------------

    private void enqueue(NsdServiceInfo info, Listener listener) {
        synchronized (lock) {
            if (!running || pending.size() >= MAX_PENDING) {
                return;
            }
            pending.add(info);
        }
        resolveNext(listener);
    }

    private void resolveNext(final Listener listener) {
        final NsdServiceInfo next;
        final int generation;
        synchronized (lock) {
            if (!running || resolving) {
                return;
            }
            next = pending.poll();
            if (next == null) {
                return;
            }
            resolving = true;
            generation = ++resolveGeneration;
        }
        NsdManager nsd = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            finishResolve(generation, listener);
            return;
        }
        // Garde-fou : si aucun callback n'arrive, on passe au service suivant.
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finishResolve(generation, listener);
            }
        }, RESOLVE_TIMEOUT_MS);
        try {
            nsd.resolveService(next, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                    finishResolve(generation, listener);
                }

                @Override
                public void onServiceResolved(NsdServiceInfo info) {
                    handleResolved(info, listener);
                    finishResolve(generation, listener);
                }
            });
        } catch (Exception ignored) {
            // Résolution best-effort : un échec ne bloque pas le scan.
            finishResolve(generation, listener);
        }
    }

    private void finishResolve(int generation, Listener listener) {
        synchronized (lock) {
            if (generation != resolveGeneration || !resolving) {
                return; // callback tardif d'une résolution déjà terminée
            }
            resolving = false;
        }
        resolveNext(listener);
    }

    private void handleResolved(NsdServiceInfo info, Listener listener) {
        InetAddress host = info.getHost();
        if (host == null) {
            return;
        }
        String ip = host.getHostAddress();
        String label = describe(info);
        if (ip == null || label == null) {
            return;
        }
        // Plusieurs services pour une même IP : on les cumule.
        ipToService.merge(ip, label, new java.util.function.BiFunction<String, String, String>() {
            @Override
            public String apply(String old, String added) {
                return old.contains(added) ? old : old + " | " + added;
            }
        });
        if (listener != null) {
            listener.onServiceResolved(ip, info.getServiceName(), info.getPort());
        }
    }

    // « Nom._type._tcp (md=Modèle, osvers=17.2) », null si pas de nom.
    static String describe(NsdServiceInfo info) {
        String name = info.getServiceName();
        if (name == null || name.trim().isEmpty()) {
            return null;
        }
        StringBuilder label = new StringBuilder(name.trim());
        String type = info.getServiceType();
        if (type != null) {
            String cleaned = type.trim();
            while (cleaned.startsWith(".")) {
                cleaned = cleaned.substring(1);
            }
            while (cleaned.endsWith(".")) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
            if (!cleaned.isEmpty()) {
                label.append('.').append(cleaned);
            }
        }
        String attributes = usefulAttributes(info);
        if (!attributes.isEmpty()) {
            label.append(" (").append(attributes).append(')');
        }
        return label.toString();
    }

    private static String usefulAttributes(NsdServiceInfo info) {
        Map<String, byte[]> txt;
        try {
            txt = info.getAttributes();
        } catch (RuntimeException e) {
            return "";
        }
        if (txt == null || txt.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, byte[]> entry : txt.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey().toLowerCase(Locale.ROOT);
            for (String wanted : TXT_KEYS) {
                if (wanted.equals(key) && entry.getValue() != null) {
                    String value = new String(entry.getValue(), StandardCharsets.UTF_8)
                            .replaceAll("\\p{Cntrl}", " ").trim();
                    if (!value.isEmpty()) {
                        if (value.length() > MAX_ATTR_LENGTH) {
                            value = value.substring(0, MAX_ATTR_LENGTH);
                        }
                        if (out.length() > 0) {
                            out.append(", ");
                        }
                        out.append(key).append('=').append(value);
                    }
                }
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------
    // Arrêt
    // ------------------------------------------------------------------

    public void stop() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                List<NsdManager.DiscoveryListener> toStop;
                synchronized (lock) {
                    if (!running) {
                        return;
                    }
                    running = false;
                    pending.clear();
                    toStop = new ArrayList<>(discoveryListeners);
                    discoveryListeners.clear();
                }
                NsdManager nsd = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
                if (nsd == null) {
                    return;
                }
                for (NsdManager.DiscoveryListener discovery : toStop) {
                    try {
                        nsd.stopServiceDiscovery(discovery);
                    } catch (Exception ignored) {
                        // Déjà arrêté : sans conséquence.
                    }
                }
            }
        });
    }

    // Copie IP -> services pour enrichir les hôtes trouvés.
    public Map<String, String> snapshot() {
        return new HashMap<>(ipToService);
    }
}
