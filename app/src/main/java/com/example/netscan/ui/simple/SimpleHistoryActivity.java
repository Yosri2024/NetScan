package com.example.netscan.ui.simple;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.netscan.NetScanApp;
import com.example.netscan.R;
import com.example.netscan.data.ScanRepository;
import com.example.netscan.data.db.HostEntity;
import com.example.netscan.data.db.ScanEntity;
import com.example.netscan.data.model.Host;
import com.example.netscan.databinding.SimpleActivityHistoryBinding;
import com.example.netscan.ui.common.ModeSwitchHelper;
import com.example.netscan.utils.ScanComparator;
import com.example.netscan.viewmodel.HistoryViewModel;

import java.util.ArrayList;
import java.util.List;
import com.example.netscan.ui.common.BackHelper;

// Fichier : ui/simple/SimpleHistoryActivity.java
// Rôle : Écran 3 du mode Simple, "Mes derniers scans" (Étape 4).
// Bandeau orange si un nouvel appareil est apparu depuis le scan précédent.
// Toucher une ligne rouvre ses résultats. "Effacer" avec confirmation.
public class SimpleHistoryActivity extends AppCompatActivity
        implements ScanSimpleAdapter.OnScanClickListener {

    private SimpleActivityHistoryBinding binding;
    private HistoryViewModel viewModel;
    private ScanSimpleAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = SimpleActivityHistoryBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bouton retour explicite (le geste système reste valable).
        BackHelper.bind(this);

        ModeSwitchHelper.bindPastille(this, binding.pastilleMode);

        viewModel = new ViewModelProvider(this).get(HistoryViewModel.class);
        adapter = new ScanSimpleAdapter(this);
        binding.recyclerScans.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerScans.setAdapter(adapter);

        binding.buttonClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });

        viewModel.getScans().observe(this, new androidx.lifecycle.Observer<List<ScanEntity>>() {
            @Override
            public void onChanged(List<ScanEntity> scans) {
                adapter.setScans(scans);
                boolean empty = scans == null || scans.isEmpty();
                binding.textEmpty.setText(empty && !historyAvailable()
                        ? R.string.history_unavailable : R.string.simple_history_empty);
                binding.textEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.buttonClear.setVisibility(empty ? View.GONE : View.VISIBLE);
            }
        });
        checkNewcomers();
    }

    // La base chiffrée est-elle exploitable ? Clé Keystore perdue, fichier
    // corrompu : l'écran reste lisible, l'historique est simplement vide.
    private boolean historyAvailable() {
        return viewModel.repository().isAvailable();
    }

    // Bandeau orange si du nouveau depuis le scan précédent.
    private void checkNewcomers() {
        viewModel.checkNewcomers(new ScanRepository.TwoScansCallback() {
            @Override
            public void onTwoScans(final List<HostEntity> latestHosts,
                                   final List<HostEntity> previousHosts,
                                   final boolean hasPrevious) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        boolean show = hasPrevious && ScanComparator.hasNewDevices(
                                ScanRepository.ipsOf(latestHosts),
                                ScanRepository.ipsOf(previousHosts));
                        binding.bannerNewDevice.setVisibility(show ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    // Rouvre les résultats : recharge les hôtes en objets d'affichage.
    @Override
    public void onScanClicked(final ScanEntity scan) {
        viewModel.repository().getHosts(scan.id, new ScanRepository.HostsCallback() {
            @Override
            public void onHosts(List<HostEntity> entities) {
                List<Host> hosts = new ArrayList<>(entities.size());
                for (HostEntity entity : entities) {
                    hosts.add(ScanRepository.toHost(entity));
                }
                NetScanApp app = (NetScanApp) getApplication();
                app.storeLastScan(hosts, null, scan.ownIp, scan.gatewayIp);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startActivity(new Intent(SimpleHistoryActivity.this,
                                SimpleResultsActivity.class));
                    }
                });
            }
        });
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.simple_history_clear_title)
                .setMessage(R.string.simple_history_clear_message)
                .setPositiveButton(R.string.simple_history_clear_yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                viewModel.clearHistory();
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
