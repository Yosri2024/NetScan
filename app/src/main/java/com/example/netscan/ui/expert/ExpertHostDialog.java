package com.example.netscan.ui.expert;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.netscan.R;
import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.OsInfo;
import com.example.netscan.data.model.PortResult;
import com.example.netscan.ui.common.OsUi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Fichier : ui/expert/ExpertHostDialog.java
// Rôle : fiche complète d'un hôte (Expert) : identité, latence, ports,
// bannières, services mDNS, puis le système estimé avec sa confiance, le TTL
// et chaque indice retenu. C'est ici que l'on vérifie POURQUOI l'appli
// pense « Windows » ou « Linux ».
public final class ExpertHostDialog {

    private ExpertHostDialog() {
    }

    public static void show(AppCompatActivity activity, Host host, OsInfo os) {
        new AlertDialog.Builder(activity)
                .setTitle(host.getIpAddress())
                .setMessage(buildText(activity, host, os))
                .setPositiveButton(R.string.expert_host_close, null)
                .show();
    }

    static String buildText(AppCompatActivity context, Host host, OsInfo os) {
        String none = context.getString(R.string.expert_detail_none);
        StringBuilder text = new StringBuilder();

        String name = host.getHostname();
        line(text, context.getString(R.string.expert_detail_name),
                name == null || name.equals(host.getIpAddress()) ? none : name);
        line(text, context.getString(R.string.expert_detail_latency),
                host.isReachable() ? host.getLatencyMs() + " ms" : none);

        List<Integer> numbers = new ArrayList<>();
        for (PortResult port : host.getOpenPorts()) {
            numbers.add(port.getPort());
        }
        Collections.sort(numbers);
        line(text, context.getString(R.string.expert_detail_ports),
                numbers.isEmpty() ? none : join(numbers));

        StringBuilder banners = new StringBuilder();
        for (PortResult port : host.getOpenPorts()) {
            if (port.getBanner() != null) {
                banners.append("\n  ").append(port.getPort()).append(" : ")
                        .append(port.getBanner());
            }
        }
        line(text, context.getString(R.string.expert_detail_banners),
                banners.length() == 0 ? none : banners.toString());

        String services = host.getServiceName();
        line(text, context.getString(R.string.expert_detail_services),
                services == null || services.isEmpty()
                        ? none : services.replace(" | ", "\n  "));

        text.append('\n').append(context.getString(R.string.expert_detail_os)).append(" : ")
                .append(OsUi.describe(context, os, host.getTtl(), true));
        return text.toString();
    }

    private static void line(StringBuilder text, String label, String value) {
        text.append(label).append(" : ").append(value).append('\n');
    }

    private static String join(List<Integer> numbers) {
        StringBuilder out = new StringBuilder();
        for (int number : numbers) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(number);
        }
        return out.toString();
    }
}
