package com.example.netscan.ui.simple;

import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.netscan.NetScanApp;
import com.example.netscan.R;
import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.NetworkDetails;
import com.example.netscan.data.model.ScanConfig;
import com.example.netscan.databinding.SimpleActivityScanBinding;
import com.example.netscan.network.NetworkInfoHelper;
import com.example.netscan.security.PermissionHelper;
import com.example.netscan.ui.common.ModeSwitchHelper;
import com.example.netscan.viewmodel.ScanViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Fichier : ui/simple/SimpleScanActivity.java
// Rôle : Écran 1 du mode Simple (Étape 3). UN gros bouton "Scanner mon
// réseau", état du réseau (Wi-Fi ou Ethernet), dernier scan, navigation
// basse. Le scan Simple = découverte des appareils sans scan de ports
// (rapide), sur n'importe quel réseau local.
public class SimpleScanActivity extends AppCompatActivity {

    private SimpleActivityScanBinding binding;
    private ScanViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = SimpleActivityScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Pastille "Mode simple" : touche pour basculer en Expert.
        ModeSwitchHelper.bindPastille(this, binding.pastilleMode);

        viewModel = new ViewModelProvider(this).get(ScanViewModel.class);

        binding.buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onScanClicked();
            }
        });
        binding.buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.cancelScan();
            }
        });
        binding.bottomNav.setOnItemSelectedListener(
                new com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        int id = item.getItemId();
                        if (id == R.id.nav_scan) {
                            return true;
                        }
                        // Historique (Étape 4) : écran dédié.
                        if (id == R.id.nav_history) {
                            startActivity(new Intent(SimpleScanActivity.this,
                                    SimpleHistoryActivity.class));
                            return false;
                        }
                        // Réglages (Étape 5) : écran dédié.
                        startActivity(new Intent(SimpleScanActivity.this,
                                com.example.netscan.ui.common.SettingsActivity.class));
                        return false;
                    }
                });

        observeViewModel();
        viewModel.loadNetwork(this);
        showLastScan();
    }

    private void observeViewModel() {
        viewModel.getNetwork().observe(this, new androidx.lifecycle.Observer<NetworkDetails>() {
            @Override
            public void onChanged(NetworkDetails details) {
                if (details != null && details.isUsable()) {
                    binding.textSsid.setText(details.getDisplayName());
                    binding.textWifiState.setText(R.string.simple_network_connected);
                } else {
                    binding.textSsid.setText(R.string.simple_network_unknown);
                    binding.textWifiState.setText(R.string.network_disconnected);
                }
            }
        });
        viewModel.getScanning().observe(this, new androidx.lifecycle.Observer<Boolean>() {
            @Override
            public void onChanged(Boolean scanning) {
                boolean active = Boolean.TRUE.equals(scanning);
                binding.buttonScan.setEnabled(!active);
                binding.buttonCancel.setVisibility(active ? View.VISIBLE : View.GONE);
                binding.scanProgress.setVisibility(active ? View.VISIBLE : View.GONE);
                binding.textScanning.setVisibility(active ? View.VISIBLE : View.GONE);
                // Fin d'un scan lancé ici : ouvre les Résultats (sauf erreur).
                if (!active && scanLaunched) {
                    scanLaunched = false;
                    if (viewModel.getError().getValue() == null) {
                        openResults();
                    }
                }
            }
        });
        viewModel.getProgress().observe(this, new androidx.lifecycle.Observer<Pair<Integer, Integer>>() {
            @Override
            public void onChanged(Pair<Integer, Integer> value) {
                if (value == null) {
                    return;
                }
                binding.scanProgress.setMax(Math.max(1, value.second));
                binding.scanProgress.setProgress(value.first);
            }
        });
        // Scan terminé : mémorise et ouvre l'écran Résultats (voir observer ci-dessus).
        viewModel.getError().observe(this, new androidx.lifecycle.Observer<String>() {
            @Override
            public void onChanged(String message) {
                if (message != null) {
                    Toast.makeText(SimpleScanActivity.this, message, Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private boolean scanLaunched;
    // Début du scan : pour la statistique de durée (historique, Étape 4).
    private long scanStartMs;

    private void onScanClicked() {
        // Avertissement légal obligatoire avant tout scan (Étape 5).
        if (!com.example.netscan.ui.common.LegalWarningDialog.ensureAccepted(this)) {
            return;
        }
        // La permission Wi-Fi ne sert qu'à afficher le SSID : elle est demandée
        // au mieux et ne bloque jamais le scan (indispensable en Ethernet,
        // où aucune permission n'est requise).
        NetworkDetails current = NetworkInfoHelper.getDetails(this);
        if (!PermissionHelper.hasPermissions(this) && current != null && current.isWifi()) {
            PermissionHelper.requestPermissions(this);
        }
        startSimpleScan();
    }

    // Config automatique : préfixe réel de l'interface courante (Wi-Fi,
    // Ethernet, USB...), découverte seule. Le CIDR vient de l'interface
    // réseau, pas du Wi-Fi : un câble branché scanne comme le Wi-Fi.
    private void startSimpleScan() {
        viewModel.loadNetwork(this);
        NetworkDetails details = NetworkInfoHelper.getDetails(this);
        String cidr = NetworkInfoHelper.getScanCidr(details);
        if (cidr == null) {
            Toast.makeText(this, R.string.network_required, Toast.LENGTH_LONG).show();
            return;
        }
        ScanConfig config = viewModel.getConfig();
        config.setCidr(cidr);
        config.setPortScanEnabled(false);
        config.setTimeoutMs(800);
        config.setThreadCount(50);
        scanLaunched = true;
        scanStartMs = System.currentTimeMillis();
        viewModel.startScan(this);
    }

    // Mémorise le scan pour l'écran Résultats (survit au changement de mode)
    // + sauvegarde dans l'historique chiffré (Étape 4).
    private void openResults() {
        NetScanApp app = (NetScanApp) getApplication();
        NetworkDetails details = NetworkInfoHelper.getDetails(this);
        String ownIp = details != null ? details.getIpAddress() : null;
        String gateway = details != null ? details.getGateway() : app.getLastGatewayIp();
        String ssid = details != null ? details.getDisplayName() : null;
        List<Host> hosts = viewModel.getHosts().getValue();
        app.storeLastScan(hosts, viewModel.getConfig(), ownIp, gateway);
        long duration = System.currentTimeMillis() - scanStartMs;
        com.example.netscan.data.ScanRepository.getInstance(this).saveScan(
                hosts, ssid, ownIp, gateway, duration, app.getKnownIps(), null);
        startActivity(new Intent(this, SimpleResultsActivity.class));
        showLastScan();
    }

    private void showLastScan() {
        NetScanApp app = (NetScanApp) getApplication();
        long when = app.getLastScanTimestamp();
        if (when <= 0) {
            binding.textLastScan.setText(R.string.simple_no_last_scan);
            return;
        }
        // « Le %1$s à %2$s » : jour puis heure, deux arguments distincts.
        String day = new SimpleDateFormat("dd/MM", Locale.FRENCH).format(new Date(when));
        String time = new SimpleDateFormat("HH:mm", Locale.FRENCH).format(new Date(when));
        binding.textLastScan.setText(getString(R.string.simple_last_scan_format, day, time));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PermissionHelper.REQUEST_WIFI_SCAN) {
            return;
        }
        // Le scan n'attend pas la permission : seul le nom du Wi-Fi en dépend.
        if (!PermissionHelper.allGranted(grantResults)) {
            Toast.makeText(this, R.string.expert_status_permission_denied, Toast.LENGTH_LONG).show();
        }
        viewModel.loadNetwork(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Le mode a pu être changé dans les réglages : on affiche alors
        // l'accueil de ce mode-là, pas celui-ci.
        ModeSwitchHelper.redirectIfModeChanged(this);
        // La pastille reflète le mode même après un retour depuis l'Expert.
        ModeSwitchHelper.bindPastille(this, binding.pastilleMode);
        // Le réseau a pu changer (câble branché, Wi-Fi coupé, VPN...).
        viewModel.loadNetwork(this);
        showLastScan();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
