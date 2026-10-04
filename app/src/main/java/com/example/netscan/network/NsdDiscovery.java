package com.example.netscan.network;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Handler;
import android.os.Looper;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Fichier : network/NsdDiscovery.java
// Rôle : découverte mDNS/NSD pour nommer les appareils (Étape 2).
// L'identification reste approximative : tous les appareils
// n'annoncent pas de service (voir spec, limites Android).
public class NsdDiscovery {

    // Types courants sur un réseau local (imprimantes, box, objets).
    private static final String[] SERVICE_TYPES = {"_http._tcp.", "_printer._tcp."};
    // Fenêtre d'écoute : passé ce délai, arrêt automatique.
    private static final long DISCOVERY_WINDOW_MS = 12000;

    public interface Listener {
        void onServiceResolved(String ipAddress, String serviceName, int port);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, String> ipToService = new ConcurrentHashMap<>();
    private NsdManager.DiscoveryListener discoveryListener;
    private boolean running;

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
        if (running) {
            return;
        }
        NsdManager nsd = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            return;
        }
        running = true;
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                resolveService(serviceInfo, listener);
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
        try {
            for (String type : SERVICE_TYPES) {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
            }
        } catch (Exception ignored) {
            running = false;
        }
        // Arrêt automatique après la fenêtre d'écoute.
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stop();
            }
        }, DISCOVERY_WINDOW_MS);
    }

    private void resolveService(NsdServiceInfo serviceInfo, final Listener listener) {
        NsdManager nsd = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
        if (nsd == null) {
            return;
        }
        try {
            nsd.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                @Override
                public void onResolveFailed(NsdServiceInfo info, int errorCode) {
                }

                @Override
                public void onServiceResolved(NsdServiceInfo info) {
                    InetAddress host = info.getHost();
                    if (host == null) {
                        return;
                    }
                    String ip = host.getHostAddress();
                    String name = info.getServiceName();
                    if (ip != null && name != null) {
                        ipToService.put(ip, name);
                        if (listener != null) {
                            listener.onServiceResolved(ip, name, info.getPort());
                        }
                    }
                }
            });
        } catch (Exception ignored) {
            // Résolution best-effort : un échec ne bloque pas le scan.
        }
    }

    public void stop() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!running) {
                    return;
                }
                running = false;
                try {
                    NsdManager nsd = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
                    if (nsd != null && discoveryListener != null) {
                        nsd.stopServiceDiscovery(discoveryListener);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    // Copie IP -> nom de service pour enrichir les hôtes trouvés.
    public Map<String, String> snapshot() {
        return new HashMap<>(ipToService);
    }
}
