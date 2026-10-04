package com.example.netscan.network;

import com.example.netscan.data.model.PortResult;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// Fichier : network/PortScanner.java
// Rôle : scan TCP connect d'une liste de ports sur un hôte (Étape 2).
// Ports scannés en parallèle (pool dédié) : le mode séquentiel
// prendrait des heures sur 1-65535.
public final class PortScanner {

    private PortScanner() {
    }

    // Retourne uniquement les ports OUVERTS. progress compte les ports traités.
    public static List<PortResult> scanPorts(String ip, List<Integer> ports, int timeoutMs,
                                             int poolSize, AtomicBoolean cancelled,
                                             AtomicInteger progress) {
        List<PortResult> open = new ArrayList<>();
        if (ports == null || ports.isEmpty()) {
            return open;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, poolSize));
        CompletionService<PortResult> completion = new ExecutorCompletionService<>(pool);
        List<Future<PortResult>> futures = new ArrayList<>(ports.size());
        try {
            for (int port : ports) {
                if (cancelled.get()) {
                    break;
                }
                final int target = port;
                futures.add(completion.submit(new Callable<PortResult>() {
                    @Override
                    public PortResult call() {
                        if (cancelled.get()) {
                            return new PortResult(target, false);
                        }
                        return probe(ip, target, timeoutMs);
                    }
                }));
            }
            for (int i = 0; i < futures.size(); i++) {
                try {
                    PortResult result = completion.take().get();
                    if (progress != null) {
                        progress.incrementAndGet();
                    }
                    if (result.isOpen()) {
                        open.add(result);
                    }
                } catch (Exception ignored) {
                    if (progress != null) {
                        progress.incrementAndGet();
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
        return open;
    }

    // Une connexion TCP acceptée = port ouvert.
    private static PortResult probe(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            return new PortResult(port, true);
        } catch (IOException ignored) {
            return new PortResult(port, false);
        }
    }
}
