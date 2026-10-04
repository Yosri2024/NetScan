package com.example.netscan.network;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Fichier : network/BannerGrabber.java
// Rôle : lit la bannière d'un port ouvert (Étape 2).
// Best-effort : null si le service ne parle pas en premier (ex. TLS).
public final class BannerGrabber {

    // Suffit pour "SSH-2.0-OpenSSH", "220 FTP", etc.
    private static final int MAX_BANNER_BYTES = 512;

    private BannerGrabber() {
    }

    public static String grabBanner(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            InputStream in = socket.getInputStream();
            byte[] buffer = new byte[MAX_BANNER_BYTES];
            int read = in.read(buffer);
            if (read <= 0) {
                return null;
            }
            String banner = new String(buffer, 0, read, StandardCharsets.UTF_8)
                    .replaceAll("\\p{Cntrl}", " ").trim();
            return banner.isEmpty() ? null : banner;
        } catch (IOException ignored) {
            return null;
        }
    }
}
