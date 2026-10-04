package com.example.netscan.data;

import android.content.Context;
import android.net.Uri;

import com.example.netscan.data.db.HostEntity;
import com.example.netscan.data.db.ScanEntity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Fichier : data/ExportManager.java
// Rôle : export JSON/CSV d'un scan via le sélecteur de fichiers (Étape 4,
// mode Expert uniquement). Écriture dans le flux SAF fourni par l'activité.
public final class ExportManager {

    public enum Format {
        JSON,
        CSV
    }

    private ExportManager() {
    }

    // Nom suggéré : netscan_20261002_201500.json
    public static String suggestFileName(long timestamp, Format format) {
        String base = "netscan_" + com.example.netscan.utils.DateFormatter.formatFileTimestamp(timestamp);
        return format == Format.JSON ? base + ".json" : base + ".csv";
    }

    public static String scanToJson(ScanEntity scan, List<HostEntity> hosts) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        ExportPayload payload = new ExportPayload();
        payload.timestamp = scan.timestamp;
        payload.ssid = scan.ssid;
        payload.ownIp = scan.ownIp;
        payload.gatewayIp = scan.gatewayIp;
        payload.durationMs = scan.durationMs;
        payload.hosts = new ArrayList<>();
        if (hosts != null) {
            for (HostEntity host : hosts) {
                ExportHost export = new ExportHost();
                export.ip = host.ip;
                export.hostname = host.hostname;
                export.latencyMs = host.latencyMs;
                export.openPorts = host.openPortsCsv;
                export.banner = host.banner;
                export.serviceName = host.serviceName;
                payload.hosts.add(export);
            }
        }
        return gson.toJson(payload);
    }

    public static String scanToCsv(ScanEntity scan, List<HostEntity> hosts) {
        StringBuilder csv = new StringBuilder();
        csv.append("scan_timestamp,ssid,own_ip,gateway_ip,duration_ms\n");
        csv.append(scan.timestamp).append(',')
                .append(escape(scan.ssid)).append(',')
                .append(escape(scan.ownIp)).append(',')
                .append(escape(scan.gatewayIp)).append(',')
                .append(scan.durationMs).append('\n');
        csv.append("ip,hostname,latency_ms,open_ports,banner,service\n");
        if (hosts != null) {
            for (HostEntity host : hosts) {
                csv.append(escape(host.ip)).append(',')
                        .append(escape(host.hostname)).append(',')
                        .append(host.latencyMs).append(',')
                        .append(escape(host.openPortsCsv)).append(',')
                        .append(escape(host.banner)).append(',')
                        .append(escape(host.serviceName)).append('\n');
            }
        }
        return csv.toString();
    }

    // Écriture dans le document choisi par l'utilisateur (fond : hors thread UI).
    public static void writeToUri(Context context, Uri uri, String content) throws IOException {
        OutputStream out = context.getContentResolver().openOutputStream(uri, "wt");
        if (out == null) {
            throw new IOException("Document inaccessible.");
        }
        OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        try {
            writer.write(content);
        } finally {
            try {
                writer.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean quoted = value.contains(",") || value.contains("\"") || value.contains("\n");
        String escaped = value.replace("\"", "\"\"");
        return quoted ? "\"" + escaped + "\"" : escaped;
    }

    // Structure JSON exportée (champs stables pour réimport éventuel).
    private static class ExportPayload {
        long timestamp;
        String ssid;
        String ownIp;
        String gatewayIp;
        long durationMs;
        List<ExportHost> hosts;
    }

    private static class ExportHost {
        String ip;
        String hostname;
        long latencyMs;
        String openPorts;
        String banner;
        String serviceName;
    }
}
