package com.example.netscan.ui.simple;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.netscan.NetScanApp;
import com.example.netscan.R;
import com.example.netscan.alerts.AlertHelper;
import com.example.netscan.data.model.Host;
import com.example.netscan.databinding.SimpleItemDeviceBinding;
import com.example.netscan.utils.DeviceNamer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

// Fichier : ui/simple/DeviceSimpleAdapter.java
// Rôle : liste des appareils Simple (Étape 3). Chaque ligne : point de
// couleur (vert reconnu, orange à vérifier) + nom clair + sous-texte.
// La couleur n'est jamais seule : un texte l'accompagne (daltonisme).
public class DeviceSimpleAdapter extends RecyclerView.Adapter<DeviceSimpleAdapter.DeviceViewHolder> {

    public interface OnDeviceClickListener {
        void onDeviceClicked(Host host);
    }

    private final List<Host> devices = new ArrayList<>();
    private final Set<String> knownIps;
    private final String ownIp;
    private final String gatewayIp;
    private final OnDeviceClickListener listener;

    public DeviceSimpleAdapter(NetScanApp app, OnDeviceClickListener listener) {
        this.knownIps = app.getKnownIps();
        this.ownIp = app.getLastOwnIp();
        this.gatewayIp = app.getLastGatewayIp();
        this.listener = listener;
    }

    // Ordre d'affichage voulu : les appareils inconnus en premier, puis la
    // box, puis ceux que l'utilisateur a reconnus. À rang égal, tri par IP
    // pour que la liste reste stable d'un scan à l'autre.
    public void setDevices(List<Host> newDevices) {
        devices.clear();
        if (newDevices != null) {
            devices.addAll(newDevices);
            Collections.sort(devices, new Comparator<Host>() {
                @Override
                public int compare(Host first, Host second) {
                    int rankFirst = AlertHelper.rankOf(first, knownIps, ownIp, gatewayIp);
                    int rankSecond = AlertHelper.rankOf(second, knownIps, ownIp, gatewayIp);
                    if (rankFirst != rankSecond) {
                        return rankFirst - rankSecond;
                    }
                    return first.getIpAddress().compareTo(second.getIpAddress());
                }
            });
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        SimpleItemDeviceBinding binding = SimpleItemDeviceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new DeviceViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        holder.bind(devices.get(position));
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    class DeviceViewHolder extends RecyclerView.ViewHolder {

        private final SimpleItemDeviceBinding binding;

        DeviceViewHolder(SimpleItemDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final Host host) {
            boolean recognized = AlertHelper.isRecognized(host, knownIps, ownIp, gatewayIp);
            int dotColor = ContextCompat.getColor(itemView.getContext(), recognized
                    ? R.color.status_ok : R.color.status_warning);
            binding.dotStatus.getBackground().setColorFilter(dotColor,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            binding.textDeviceName.setText(DeviceNamer.nameFor(host, ownIp, gatewayIp));
            binding.textDeviceSub.setText(DeviceNamer.subtextFor(host, recognized, ownIp));
            // Accessibilité : le statut est lu à voix haute (TalkBack).
            binding.getRoot().setContentDescription(
                    binding.textDeviceName.getText() + ", " + binding.textDeviceSub.getText());
            binding.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onDeviceClicked(host);
                    }
                }
            });
        }
    }
}
