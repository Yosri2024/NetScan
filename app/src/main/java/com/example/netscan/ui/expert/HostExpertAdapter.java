package com.example.netscan.ui.expert;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.PortResult;
import com.example.netscan.databinding.ExpertItemHostBinding;

import java.util.ArrayList;
import java.util.List;

// Fichier : ui/expert/HostExpertAdapter.java
// Rôle : liste dense des hôtes (Étape 2). Valeurs en monospace :
// IP | ports ouverts | latence | bannière.
public class HostExpertAdapter extends RecyclerView.Adapter<HostExpertAdapter.HostViewHolder> {

    private final List<Host> hosts = new ArrayList<>();

    public void setHosts(List<Host> newHosts) {
        hosts.clear();
        if (newHosts != null) {
            hosts.addAll(newHosts);
        }
        notifyDataSetChanged();
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

    static class HostViewHolder extends RecyclerView.ViewHolder {

        private final ExpertItemHostBinding binding;

        HostViewHolder(ExpertItemHostBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(Host host) {
            binding.textIp.setText(host.getIpAddress());
            String name = host.getHostname();
            if (host.getServiceName() != null) {
                name += " [" + host.getServiceName() + "]";
            }
            binding.textHostname.setText(name);
            String latency = host.isReachable()
                    ? host.getLatencyMs() + " ms"
                    : "injoignable";
            String ports = formatPorts(host.getOpenPorts());
            binding.textDetails.setText(latency + " | " + ports);
            String banner = firstBanner(host.getOpenPorts());
            binding.textBanner.setText(banner == null ? "" : banner);
        }

        private String formatPorts(List<PortResult> openPorts) {
            if (openPorts.isEmpty()) {
                return "aucun port ouvert";
            }
            List<String> numbers = new ArrayList<>(openPorts.size());
            for (PortResult port : openPorts) {
                numbers.add(String.valueOf(port.getPort()));
            }
            return "ports : " + TextUtils.join(", ", numbers);
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
