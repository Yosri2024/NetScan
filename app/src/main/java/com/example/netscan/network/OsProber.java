package com.example.netscan.network;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.PortResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Fichier : network/OsProber.java
// Rôle : collecte les indices d'identification d'OS d'un appareil, pour les
// modes Simple ET Expert (l'analyse elle-même est faite par OsGuesser).
//  1. une mini-sonde de ports « signatures » (courte, en parallèle) ;
//  2. le TTL d'un ping (64 = Linux/Android/Apple, 128 = Windows, 255 = réseau),
//     lu via la commande système « ping », accessible sans root ;
//  3. bannières SSH et en-tête HTTP « Server » des ports trouvés.
// Pur Java : testé par OsProberTest (sonde locale, analyse du ping).
public final class OsProber {

    // Ports signatures, chacun pointe vers une famille d'OS (voir OsGuesser) :
    // 22 SSH (Linux/Unix), 53 DNS (box), 80/443/8080/8443 web, 135+139+445+5357
    // + 3389 (Windows), 548 AFP + 7000 AirPlay + 62078 sync (Apple), 515/631/9100
    // (imprimantes), 554 RTSP (caméras), 5001 (NAS Synology), 5555 ADB (Android),
    // 8001/8002 (TV Samsung), 8008/8009 (Google Cast).
    static final List<Integer> OS_PROBE_PORTS = Arrays.asList(
            22, 53, 80, 135, 139, 443, 445, 515, 548, 554, 631, 3389, 5001, 5357, 5555,
            7000, 8001, 8002, 8008, 8009, 8080, 8443, 9100, 62078);

    // Pool de sondes par appareil.
    private static final int PORT_POOL_SIZE = 16;
    // Budget du ping : -W 1 (1 s d'attente) + marge de lancement du processus.
    private static final long PING_BUDGET_MS = 2500;
    private static final Pattern TTL_PATTERN =
            Pattern.compile("(?i)\\bttl[=: ]\\s*(\\d{1,3})");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$");

    private OsProber() {
    }

    // Variante simple : sonde tous les ports signatures.
    public static void enrichHost(Host host, int timeoutMs, AtomicBoolean cancelled) {
        enrichHost(host, timeoutMs, null, cancelled);
    }

    // Complète l'hôte : ports signatures ouverts (hors skipPorts, déjà couverts
    // par le scan de ports de l'Expert), bannières, puis TTL. Ne fait rien si annulé.
    public static void enrichHost(Host host, int timeoutMs, Set<Integer> skipPorts,
                                  AtomicBoolean cancelled) {
        if (host == null || isCancelled(cancelled)) {
            return;
        }
        final String ip = host.getIpAddress();

        // Le ping tourne pendant la sonde de ports : on gagne ~1 s par appareil.
        ExecutorService pingPool = Executors.newSingleThreadExecutor();
        Future<Integer> ttlFuture = pingPool.submit(new Callable<Integer>() {
            @Override
            public Integer call() {
                return measureTtl(ip);
            }
        });
        try {
            List<Integer> toProbe = new ArrayList<>(OS_PROBE_PORTS);
            if (skipPorts != null) {
                toProbe.removeAll(skipPorts);
            }
            List<PortResult> open = probePorts(ip, toProbe, timeoutMs, cancelled);
            for (PortResult port : open) {
                if (isCancelled(cancelled)) {
                    break;
                }
                int number = port.getPort();
                if (number == 22 || BannerGrabber.isHttpPort(number)) {
                    // SSH parle en premier ; HTTP répond à une requête HEAD.
                    port.setBanner(BannerGrabber.grabBanner(ip, number,
                            Math.min(Math.max(timeoutMs, 300), 1000)));
                }
                host.mergeOpenPort(port);
            }
            try {
                int ttl = ttlFuture.get(PING_BUDGET_MS, TimeUnit.MILLISECONDS);
                if (ttl > 0) {
                    host.setTtl(ttl);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ignored) {
                // Pas de TTL : l'analyse se fera sur les autres indices.
            }
        } finally {
            pingPool.shutdownNow();
        }
    }

    private static boolean isCancelled(AtomicBoolean cancelled) {
        return cancelled != null && cancelled.get();
    }

    // ------------------------------------------------------------------
    // TTL via la commande « ping » du système
    // ------------------------------------------------------------------

    // Lance « ping -c 1 -W 1 <ip> » et lit le TTL de la réponse ; -1 si le
    // ping est indisponible, bloqué par le pare-feu de l'appareil, ou trop lent.
    static int measureTtl(String ip) {
        if (ip == null || !IPV4_PATTERN.matcher(ip).matches()) {
            return -1; // jamais de texte libre dans une commande
        }
        final Process process;
        try {
            process = new ProcessBuilder("ping", "-c", "1", "-W", "1", ip)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException | SecurityException e) {
            return -1;
        }
        ExecutorService reader = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> output = reader.submit(new Callable<Integer>() {
                @Override
                public Integer call() throws IOException {
                    BufferedReader in = new BufferedReader(new InputStreamReader(
                            process.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    while ((line = in.readLine()) != null) {
                        int ttl = parseTtl(line);
                        if (ttl > 0) {
                            return ttl;
                        }
                    }
                    return -1;
                }
            });
            return output.get(PING_BUDGET_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        } catch (ExecutionException | TimeoutException e) {
            return -1;
        } finally {
            process.destroy(); // débloque aussi la lecture si le ping traîne
            reader.shutdownNow();
        }
    }

    // « 64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=2.3 ms » -> 64 ; -1 sinon.
    static int parseTtl(String text) {
        if (text == null) {
            return -1;
        }
        Matcher matcher = TTL_PATTERN.matcher(text);
        if (!matcher.find()) {
            return -1;
        }
        try {
            int ttl = Integer.parseInt(matcher.group(1));
            return ttl >= 1 && ttl <= 255 ? ttl : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------
    // Sonde de ports
    // ------------------------------------------------------------------

    // Sonde TCP connect d'une liste de ports, retourne les ouverts.
    // Testable sans Android (OsProberTest monte un ServerSocket local).
    static List<PortResult> probePorts(String ip, List<Integer> ports,
                                       int timeoutMs, AtomicBoolean cancelled) {
        List<PortResult> open = new ArrayList<>();
        if (ports == null || ports.isEmpty()) {
            return open;
        }
        int poolSize = Math.min(ports.size(), PORT_POOL_SIZE);
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        CompletionService<PortResult> completion = new ExecutorCompletionService<>(pool);
        List<Future<PortResult>> futures = new ArrayList<>(ports.size());
        try {
            for (int port : ports) {
                if (isCancelled(cancelled)) {
                    break;
                }
                final int target = port;
                futures.add(completion.submit(new Callable<PortResult>() {
                    @Override
                    public PortResult call() {
                        return probe(ip, target, timeoutMs);
                    }
                }));
            }
            for (int i = 0; i < futures.size(); i++) {
                try {
                    PortResult result = completion.take().get();
                    if (result.isOpen()) {
                        open.add(result);
                    }
                } catch (Exception ignored) {
                    // Un port en échec = fermé/filtré, on continue.
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return open;
    }

    private static PortResult probe(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return new PortResult(port, true);
        } catch (IOException ignored) {
            return new PortResult(port, false);
        }
    }
}
