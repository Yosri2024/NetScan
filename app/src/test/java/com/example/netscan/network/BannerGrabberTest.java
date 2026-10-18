package com.example.netscan.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

// Fichier : test/.../network/BannerGrabberTest.java
// Rôle : lecture de l'en-tête HTTP « Server » (indice d'OS) sans Android.
public class BannerGrabberTest {

    @Test
    public void parse_status_et_server() {
        String banner = BannerGrabber.parseHttpHeaders(
                "HTTP/1.1 200 OK\r\nServer: Microsoft-IIS/10.0\r\nX-Powered-By: ASP.NET\r\n\r\n");
        assertEquals("HTTP 200 · Server: Microsoft-IIS/10.0 · ASP.NET", banner);
    }

    @Test
    public void parse_realm_des_box() {
        String banner = BannerGrabber.parseHttpHeaders(
                "HTTP/1.0 401 Unauthorized\r\nWWW-Authenticate: Basic realm=\"Livebox\"\r\n\r\n");
        assertEquals("HTTP 401 · realm: Livebox", banner);
    }

    @Test
    public void parse_reponse_non_http_donne_null() {
        assertNull(BannerGrabber.parseHttpHeaders("SSH-2.0-OpenSSH_9"));
        assertNull(BannerGrabber.parseHttpHeaders(null));
    }

    @Test
    public void parse_valeur_tres_longue_est_tronquee() {
        StringBuilder longValue = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longValue.append('x');
        }
        String banner = BannerGrabber.parseHttpHeaders(
                "HTTP/1.1 200 OK\r\nServer: " + longValue + "\r\n\r\n");
        assertTrue(banner.length() < 100);
    }

    @Test
    public void ports_http_reconnus() {
        assertTrue(BannerGrabber.isHttpPort(80));
        assertTrue(BannerGrabber.isHttpPort(8080));
        assertFalse(BannerGrabber.isHttpPort(22));
        assertFalse(BannerGrabber.isHttpPort(443));
    }

    @Test
    public void grabHttpBanner_lit_un_serveur_local() throws Exception {
        try (final ServerSocket server = new ServerSocket(0)) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try (Socket client = server.accept()) {
                        client.getInputStream().read(new byte[256]);
                        OutputStream out = client.getOutputStream();
                        out.write("HTTP/1.1 200 OK\r\nServer: lighttpd/1.4.59\r\n\r\n"
                                .getBytes(StandardCharsets.US_ASCII));
                        out.flush();
                    } catch (Exception ignored) {
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
            String banner = BannerGrabber.grabHttpBanner("127.0.0.1", server.getLocalPort(), 1000);
            assertEquals("HTTP 200 · Server: lighttpd/1.4.59", banner);
        }
    }
}
