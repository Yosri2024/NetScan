package com.example.netscan.network;

import com.example.netscan.data.model.Host;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

// Fichier : network/HostDiscovery.java
// Rôle : teste si une adresse répond (Étape 2).
// Stratégie : InetAddress.isReachable puis repli TCP (80, 443),
// car le ping ICMP est souvent filtré sans root.
public final class HostDiscovery {

    // Ports de repli pour les hôtes qui ignorent le ping.
    // Beaucoup d'appareils modernes (smartphones, TV, IoT, imprimantes)
    // ne répondent ni au ping ni aux ports 80/443 : on teste aussi le DNS
    // (53, souvent ouvert sur la box), HTTP alternatifs, SMB et impression.
    private static final int[] FALLBACK_TCP_PORTS = {80, 443, 53, 8080, 8443, 22, 445, 9100};

    private HostDiscovery() {
    }

    public static Host discoverHost(String ip, int timeoutMs) {
        long start = System.currentTimeMillis();
        boolean reachable = false;
        String hostname = ip;
        try {
            InetAddress address = InetAddress.getByName(ip);
            reachable = address.isReachable(timeoutMs);
            if (reachable) {
                try {
                    hostname = address.getHostName();
                } catch (Exception ignored) {
                    hostname = ip;
                }
            }
        } catch (IOException | SecurityException ignored) {
            reachable = false;
        }
        // Repli : un port TCP courant qui accepte prouve l'hôte vivant.
        // Budget court par port (~180ms) : les hôtes vivants répondent vite,
        // les hôtes morts coûtent ~1,4s au pire (8 ports), absorbé par les threads.
        if (!reachable) {
            reachable = tryTcpFallback(ip, 180);
        }
        long latency = reachable ? System.currentTimeMillis() - start : -1;
        return new Host(ip, hostname, reachable, latency);
    }

    private static boolean tryTcpFallback(String ip, int timeoutMs) {
        for (int port : FALLBACK_TCP_PORTS) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(ip, port), timeoutMs);
                return true;
            } catch (IOException ignored) {
                // Port fermé ou filtré : essaie le suivant.
            }
        }
        return false;
    }
}
