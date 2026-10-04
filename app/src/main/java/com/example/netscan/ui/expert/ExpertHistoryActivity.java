package com.example.netscan.ui.expert;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.netscan.R;
import com.example.netscan.data.ExportManager;
import com.example.netscan.data.ScanRepository;
import com.example.netscan.data.db.HostEntity;
import com.example.netscan.data.db.ScanEntity;
import com.example.netscan.databinding.ExpertActivityHistoryBinding;
import com.example.netscan.viewmodel.HistoryViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.netscan.ui.common.BackHelper;

// Fichier : ui/expert/ExpertHistoryActivity.java
// Rôle : historique détaillé Expert (Étape 4). Filtre par période
// (tout / 7 jours / 30 jours), statistiques par scan, export JSON/CSV
// via le sélecteur de fichiers Android (aucune permission requise).
public class ExpertHistoryActivity extends AppCompatActivity
        implements ScanExpertAdapter.OnExportClickListener {

    private static final int REQUEST_EXPORT = 2001;
    private static final long DAY_MS = 24L * 60 * 60 * 1000;

    private ExpertActivityHistoryBinding binding;
    private HistoryViewModel viewModel;
    private ScanExpertAdapter adapter;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private List<ScanEntity> allScans = new ArrayList<>();
    private long filterDays = 0; // 0 = tout.
    private ScanEntity pendingScan;
    private ExportManager.Format pendingFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ExpertActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bouton retour explicite (le geste système reste valable).
        BackHelper.bind(this);

        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        adapter = new ScanExpertAdapter(this);
        binding.recyclerScans.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerScans.setAdapter(adapter);

        binding.buttonFilterAll.setOnClickListener(filterClicked(0));
        binding.buttonFilterWeek.setOnClickListener(filterClicked(7));
        binding.buttonFilterMonth.setOnClickListener(filterClicked(30));

        viewModel.getScans().observe(this, new androidx.lifecycle.Observer<List<ScanEntity>>() {
            @Override
            public void onChanged(List<ScanEntity> scans) {
                allScans = scans == null ? new ArrayList<ScanEntity>() : scans;
                applyFilter();
            }
        });
    }

    private View.OnClickListener filterClicked(final long days) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                filterDays = days;
                applyFilter();
            }
        };
    }

    private void applyFilter() {
        long cutoff = filterDays <= 0 ? 0 : System.currentTimeMillis() - filterDays * DAY_MS;
        List<ScanEntity> shown = new ArrayList<>();
        for (ScanEntity scan : allScans) {
            if (scan.timestamp >= cutoff) {
                shown.add(scan);
            }
        }
        adapter.setScans(shown);
        // Base chiffrée indisponible : on l'explique au lieu d'un écran vide muet.
        boolean available = viewModel.repository().isAvailable();
        binding.textEmpty.setText(available
                ? R.string.expert_history_empty : R.string.history_unavailable);
        binding.textEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // Export : ouvre le sélecteur de fichiers (l'écriture attend le résultat).
    @Override
    public void onExportClicked(ScanEntity scan, ExportManager.Format format) {
        pendingScan = scan;
        pendingFormat = format;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(format == ExportManager.Format.JSON ? "application/json" : "text/csv");
        intent.putExtra(Intent.EXTRA_TITLE,
                ExportManager.suggestFileName(scan.timestamp, format));
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_EXPORT || resultCode != RESULT_OK
                || data == null || data.getData() == null || pendingScan == null) {
            return;
        }
        final Uri uri = data.getData();
        final ScanEntity scan = pendingScan;
        final ExportManager.Format format = pendingFormat;
        viewModel.repository().getHosts(scan.id, new ScanRepository.HostsCallback() {
            @Override
            public void onHosts(final List<HostEntity> hosts) {
                final String content = format == ExportManager.Format.JSON
                        ? ExportManager.scanToJson(scan, hosts)
                        : ExportManager.scanToCsv(scan, hosts);
                io.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ExportManager.writeToUri(ExpertHistoryActivity.this, uri, content);
                            toast(R.string.expert_export_done);
                        } catch (Exception e) {
                            toast(R.string.expert_export_failed);
                        }
                    }
                });
            }
        });
    }

    private void toast(final int message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(ExpertHistoryActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
        binding = null;
    }
}
