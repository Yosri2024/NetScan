package com.example.netscan.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Fichier : network/BannerGrabber.java
// Rôle : lit la bannière d'un port ouvert (Étape 2).
// Best-effort : null si le service ne parle pas en premier (ex. TLS).
// Les serveurs HTTP ne parlent jamais en premier : pour eux on envoie une
// requête HEAD minimale et on retient l'en-tête « Server » (IIS, nginx,
// lighttpd, Synology...), précieux pour deviner le système.
public final class BannerGrabber {

    // Suffit pour "SSH-2.0-OpenSSH", "220 FTP", etc.
    private static final int MAX_BANNER_BYTES = 512;
    private static final int MAX_HTTP_BYTES = 2048;
    private static final int MAX_HEADER_VALUE = 60;

    private BannerGrabber() {
    }

    // Ports HTTP en clair (le HTTPS n'est pas sondé : TLS ne parle pas en clair).
    public static boolean isHttpPort(int port) {
        return port == 80 || port == 8000 || port == 8008 || port == 8080 || port == 8888;
    }

    public static String grabBanner(String ip, int port, int timeoutMs) {
        if (isHttpPort(port)) {
            return grabHttpBanner(ip, port, timeoutMs);
        }
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

    // Requête HEAD / : résumé du type « HTTP 401 · Server: lighttpd · realm: Livebox ».
    public static String grabHttpBanner(String ip, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            String request = "HEAD / HTTP/1.0\r\nHost: " + ip
                    + "\r\nUser-Agent: NetScan\r\nConnection: close\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            InputStream in = socket.getInputStream();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            byte[] buffer = new byte[512];
            while (response.size() < MAX_HTTP_BYTES) {
                int read = in.read(buffer);
                if (read < 0) {
                    break;
                }
                response.write(buffer, 0, read);
                if (new String(response.toByteArray(), StandardCharsets.ISO_8859_1)
                        .contains("\r\n\r\n")) {
                    break;
                }
            }
            return parseHttpHeaders(new String(response.toByteArray(),
                    StandardCharsets.ISO_8859_1));
        } catch (IOException ignored) {
            return null;
        }
    }

    // Package-private pour les tests unitaires.
    static String parseHttpHeaders(String response) {
        if (response == null || !response.startsWith("HTTP/")) {
            return null;
        }
        String[] lines = response.split("\r?\n");
        String[] status = lines[0].split(" ", 3);
        StringBuilder summary = new StringBuilder("HTTP");
        if (status.length >= 2) {
            summary.append(' ').append(clean(status[1]));
        }
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            String value = clean(line.substring(colon + 1));
            if (value.isEmpty()) {
                continue;
            }
            if (key.equals("server")) {
                summary.append(" · Server: ").append(value);
            } else if (key.equals("www-authenticate")) {
                int realm = value.toLowerCase(java.util.Locale.ROOT).indexOf("realm=");
                if (realm >= 0) {
                    summary.append(" · realm: ").append(
                            clean(value.substring(realm + 6).replace("\"", "")));
                }
            } else if (key.equals("x-powered-by")) {
                summary.append(" · ").append(value);
            }
        }
        return summary.toString();
    }

    private static String clean(String value) {
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").trim();
        return cleaned.length() > MAX_HEADER_VALUE
                ? cleaned.substring(0, MAX_HEADER_VALUE) : cleaned;
    }
}
