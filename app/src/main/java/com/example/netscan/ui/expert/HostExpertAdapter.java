package com.example.netscan.ui.expert;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.OsInfo;
import com.example.netscan.data.model.PortResult;
import com.example.netscan.databinding.ExpertItemHostBinding;
import com.example.netscan.ui.common.OsUi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

// Fichier : ui/expert/HostExpertAdapter.java
// Rôle : liste dense des hôtes. Valeurs en monospace : IP + système estimé
// (icône et pastille), nom, latence | ports | TTL, bannière. Triée par IP
// (ordre numérique : .9 avant .10) pour rester stable pendant le scan.
// Toucher une ligne ouvre la fiche complète (indices de l'OS, bannières).
public class HostExpertAdapter extends RecyclerView.Adapter<HostExpertAdapter.HostViewHolder> {

    public interface OnHostClickListener {
        void onHostClicked(Host host, OsInfo os);
    }

    // Au-delà, la liste de ports est abrégée (« … +12 ») : un scan 1-65535
    // peut trouver beaucoup de ports ouverts.
    private static final int MAX_PORTS_SHOWN = 10;

    private final List<Host> hosts = new ArrayList<>();
    private final OnHostClickListener listener;
    private String ownIp;
    private String gatewayIp;

    public HostExpertAdapter(OnHostClickListener listener) {
        this.listener = listener;
    }

    public void setHosts(List<Host> newHosts, String ownIp, String gatewayIp) {
        this.ownIp = ownIp;
        this.gatewayIp = gatewayIp;
        hosts.clear();
        if (newHosts != null) {
            hosts.addAll(newHosts);
            Collections.sort(hosts, new Comparator<Host>() {
                @Override
                public int compare(Host first, Host second) {
                    return Long.compare(ipValue(first.getIpAddress()),
                            ipValue(second.getIpAddress()));
                }
            });
        }
        notifyDataSetChanged();
    }

    // 192.168.1.10 -> entier comparable ; texte invalide -> en dernier.
    static long ipValue(String ip) {
        if (ip == null) {
            return Long.MAX_VALUE;
        }
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return Long.MAX_VALUE;
        }
        long value = 0;
        try {
            for (String part : parts) {
                value = value * 256 + Integer.parseInt(part.trim());
            }
        } catch (NumberFormatException e) {
            return Long.MAX_VALUE;
        }
        return value;
    }

    @NonNull
    @Override
    public HostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ExpertItemHostBinding binding = ExpertItemHostBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new HostViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull HostViewHolder holder, int position) {
        holder.bind(hosts.get(position));
    }

    @Override
    public int getItemCount() {
        return hosts.size();
    }

    class HostViewHolder extends RecyclerView.ViewHolder {

        private final ExpertItemHostBinding binding;

        HostViewHolder(ExpertItemHostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Host host) {
            Context context = itemView.getContext();
            final OsInfo os = OsUi.resolve(context, host, ownIp, gatewayIp);
            OsUi.bindIcon(binding.imageOs, null, os);
            OsUi.bindChip(binding.chipOs, os);

            binding.textIp.setText(host.getIpAddress());
            binding.textHostname.setText(nameLine(host));

            StringBuilder details = new StringBuilder();
            details.append(host.isReachable() ? host.getLatencyMs() + " ms" : "injoignable");
            details.append(" | ").append(formatPorts(host.getOpenPorts()));
            if (host.getTtl() > 0) {
                details.append(" | TTL ").append(host.getTtl());
            }
            binding.textDetails.setText(details);

            String banner = firstBanner(host.getOpenPorts());
            binding.textBanner.setText(banner == null ? "" : banner);
            binding.textBanner.setVisibility(banner == null ? View.GONE : View.VISIBLE);

            binding.getRoot().setContentDescription(host.getIpAddress() + ", " + os.label());
            binding.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onHostClicked(host, os);
                    }
                }
            });
        }

        // « nom [service] » ; l'IP répétée comme nom n'apporte rien.
        private String nameLine(Host host) {
            String name = host.getHostname();
            if (name == null || name.equals(host.getIpAddress())) {
                name = "";
            }
            String service = host.getServiceName();
            if (service != null && !service.isEmpty()) {
                String first = service.split(" \\| ")[0];
                name = name.isEmpty() ? "[" + first + "]" : name + " [" + first + "]";
            }
            return name.isEmpty() ? "—" : name;
        }

        private String formatPorts(List<PortResult> openPorts) {
            if (openPorts.isEmpty()) {
                return "aucun port ouvert";
            }
            List<Integer> numbers = new ArrayList<>(openPorts.size());
            for (PortResult port : openPorts) {
                numbers.add(port.getPort());
            }
            Collections.sort(numbers);
            List<String> shown = new ArrayList<>();
            for (int i = 0; i < numbers.size() && i < MAX_PORTS_SHOWN; i++) {
                shown.add(String.valueOf(numbers.get(i)));
            }
            String text = "ports : " + TextUtils.join(", ", shown);
            if (numbers.size() > MAX_PORTS_SHOWN) {
                text += " … +" + (numbers.size() - MAX_PORTS_SHOWN);
            }
            return text;
        }

        private String firstBanner(List<PortResult> openPorts) {
            for (PortResult port : openPorts) {
                if (port.getBanner() != null) {
                    return port.getPort() + " : " + port.getBanner();
                }
            }
            return null;
        }
    }
}
