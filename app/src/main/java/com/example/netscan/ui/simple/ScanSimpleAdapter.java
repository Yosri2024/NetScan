package com.example.netscan.ui.simple;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.netscan.R;
import com.example.netscan.data.db.ScanEntity;
import com.example.netscan.databinding.SimpleItemScanBinding;
import com.example.netscan.utils.DateFormatter;
import com.example.netscan.utils.TerminologyMapper;

import java.util.ArrayList;
import java.util.List;

// Fichier : ui/simple/ScanSimpleAdapter.java
// Rôle : lignes de l'historique Simple (Étape 4). Une ligne par scan :
// point vert/orange, date lisible, résumé en langage courant.
public class ScanSimpleAdapter extends RecyclerView.Adapter<ScanSimpleAdapter.ScanViewHolder> {

    public interface OnScanClickListener {
        void onScanClicked(ScanEntity scan);
    }

    private final List<ScanEntity> scans = new ArrayList<>();
    private final OnScanClickListener listener;

    public ScanSimpleAdapter(OnScanClickListener listener) {
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
        SimpleItemScanBinding binding = SimpleItemScanBinding.inflate(
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

        private final SimpleItemScanBinding binding;

        ScanViewHolder(SimpleItemScanBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(final ScanEntity scan) {
            boolean allKnown = scan.recognizedCount >= scan.totalHosts;
            int dotColor = ContextCompat.getColor(itemView.getContext(), allKnown
                    ? R.color.status_ok : R.color.status_warning);
            binding.dotStatus.getBackground().setColorFilter(dotColor,
                    android.graphics.PorterDuff.Mode.SRC_IN);
            binding.textScanDate.setText(DateFormatter.formatRelative(scan.timestamp));
            binding.textScanSummary.setText(
                    TerminologyMapper.scanSummary(scan.totalHosts, scan.recognizedCount));
            binding.getRoot().setContentDescription(
                    binding.textScanDate.getText() + ", " + binding.textScanSummary.getText());
            binding.getRoot().setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (listener != null) {
                        listener.onScanClicked(scan);
                    }
                }
            });
        }
    }
}
