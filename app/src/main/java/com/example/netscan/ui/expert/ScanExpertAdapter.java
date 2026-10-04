package com.example.netscan.ui.expert;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.netscan.R;
import com.example.netscan.data.ExportManager;
import com.example.netscan.data.db.ScanEntity;
import com.example.netscan.databinding.ExpertItemScanBinding;
import com.example.netscan.utils.DateFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Fichier : ui/expert/ScanExpertAdapter.java
// Rôle : lignes de l'historique Expert (Étape 4). Détail + statistiques
// (durée, hôtes, ports ouverts) + boutons d'export JSON/CSV.
public class ScanExpertAdapter extends RecyclerView.Adapter<ScanExpertAdapter.ScanViewHolder> {

    public interface OnExportClickListener {
        void onExportClicked(ScanEntity scan, ExportManager.Format format);
    }

    private final List<ScanEntity> scans = new ArrayList<>();
    private final OnExportClickListener listener;

    public ScanExpertAdapter(OnExportClickListener listener) {
        this.listener = listener;
    }

    public void setScans(List<ScanEntity> newScans) {
        scans.clear();
        if (newScans != null) {
            scans.addAll(newScans);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ScanViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ExpertItemScanBinding binding = ExpertItemScanBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ScanViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ScanViewHolder holder, int position) {
        holder.bind(scans.get(position));
    }

    @Override
    public int getItemCount() {
        return scans.size();
    }

    class ScanViewHolder extends RecyclerView.ViewHolder {

        private final ExpertItemScanBinding binding;

        ScanViewHolder(ExpertItemScanBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final ScanEntity scan) {
            binding.textScanDate.setText(DateFormatter.formatRelative(scan.timestamp));
            // « %1$d hôtes • %2$s » : nombre d'hôtes puis durée. Deux arguments,
            // comme l'exige la chaîne (un mélange d'index explicites et
            // implicites ferait planter String.format à l'exécution).
            String duree = String.format(Locale.FRENCH, "%.1f s", scan.durationMs / 1000.0);
            binding.textScanStats.setText(itemView.getContext().getString(
                    R.string.expert_history_stats_format, scan.totalHosts, duree));
            binding.buttonExportJson.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onExportClicked(scan, ExportManager.Format.JSON);
                    }
                }
            });
            binding.buttonExportCsv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onExportClicked(scan, ExportManager.Format.CSV);
                    }
                }
            });
        }
    }
}
