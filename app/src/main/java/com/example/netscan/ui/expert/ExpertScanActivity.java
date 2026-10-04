package com.example.netscan.ui.expert;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.netscan.R;
import com.example.netscan.data.KnownDeviceRepository;
import com.example.netscan.data.ScanRepository;
import com.example.netscan.data.db.KnownDeviceEntity;
import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.ScanConfig;
import com.example.netscan.data.model.NetworkDetails;
import com.example.netscan.databinding.ExpertActivityScanBinding;
import com.example.netscan.network.NetworkInfoHelper;
import com.example.netscan.security.InputValidator;
import com.example.netscan.security.PermissionHelper;
import com.example.netscan.ui.common.ModeSwitchHelper;
import com.example.netscan.viewmodel.ScanViewModel;

import java.util.List;

// Fichier : ui/expert/ExpertScanActivity.java
// Rôle : écran de scan Expert (Étape 2). Champs CIDR/ports/timeout/threads,
// infos réseau (Wi-Fi, Ethernet, USB...), progression, liste dense,
// annulation, refus de permission.
// Non exportée (voir manifest) ; ouverte depuis l'écran Simple en test
// jusqu'à l'arrivée de la bascule de mode (Étape 3).
public class ExpertScanActivity extends AppCompatActivity {

    private ExpertActivityScanBinding binding;
    private ScanViewModel viewModel;
    private HostExpertAdapter adapter;
    // Appareils reconnus (base chiffrée) : pour la sauvegarde historique.
    private final java.util.Set<String> knownIps = new java.util.HashSet<>();
    private boolean scanLaunched;
    private long scanStartMs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ExpertActivityScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Pastille de mode : indispensable ici, c'est le seul endroit
        // d'où l'on peut revenir au mode utilisateur.
        ModeSwitchHelper.bindPastille(this, binding.pastilleMode);

        viewModel = new ViewModelProvider(this).get(ScanViewModel.class);
        adapter = new HostExpertAdapter();
        binding.recyclerHosts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerHosts.setAdapter(adapter);

        // Liste des préréglages de ports (1-1024, 1-65535, personnalisée).
        ArrayAdapter<CharSequence> presets = ArrayAdapter.createFromResource(
                this, R.array.port_presets, android.R.layout.simple_spinner_item);
        presets.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerPorts.setAdapter(presets);

        binding.editTimeout.setText("800");
        binding.editThreads.setText("50");

        // Affiche le champ libre uniquement pour "Liste personnalisée".
        binding.spinnerPorts.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                binding.layoutCustomPorts.setVisibility(
                        position == ScanConfig.PRESET_CUSTOM ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        binding.buttonDetect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fillCidrFromNetwork();
            }
        });
        binding.buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onStartClicked();
            }
        });
        binding.buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.cancelScan();
            }
        });
        // Second bouton Annuler de la barre d'action : même comportement,
        // sinon il reste désactivé pour toujours.
        binding.buttonCancelSecondary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                viewModel.cancelScan();
            }
        });
        binding.buttonSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PermissionHelper.openAppSettings(ExpertScanActivity.this);
            }
        });
        binding.buttonHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ExpertScanActivity.this, ExpertHistoryActivity.class));
            }
        });
        binding.buttonOpenSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(ExpertScanActivity.this,
                        com.example.netscan.ui.common.SettingsActivity.class));
            }
        });

        // Barre basse, comme en mode Simple : Scan / Historique / Réglages.
        binding.bottomNav.setSelectedItemId(R.id.nav_scan);
        binding.bottomNav.setOnItemSelectedListener(
                new com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
                        int id = item.getItemId();
                        if (id == R.id.nav_scan) {
                            return true;
                        }
                        if (id == R.id.nav_history) {
                            startActivity(new Intent(ExpertScanActivity.this,
                                    ExpertHistoryActivity.class));
                            return false;
                        }
                        startActivity(new Intent(ExpertScanActivity.this,
                                com.example.netscan.ui.common.SettingsActivity.class));
                        return false;
                    }
                });

        // Appareils reconnus : tenus à jour pour la sauvegarde historique.
        KnownDeviceRepository.getInstance(this).observeAll().observe(this,
                new androidx.lifecycle.Observer<java.util.List<KnownDeviceEntity>>() {
                    @Override
                    public void onChanged(java.util.List<KnownDeviceEntity> devices) {
                        knownIps.clear();
                        if (devices != null) {
                            for (KnownDeviceEntity device : devices) {
                                knownIps.add(device.ip);
                            }
                        }
                    }
                });

        observeViewModel();
        viewModel.loadNetwork(this);
    }

    private void observeViewModel() {
        viewModel.getHosts().observe(this, new androidx.lifecycle.Observer<List<Host>>() {
            @Override
            public void onChanged(List<Host> hosts) {
                adapter.setHosts(hosts);
                updateStatus(hosts);
            }
        });
        viewModel.getProgress().observe(this, new androidx.lifecycle.Observer<Pair<Integer, Integer>>() {
            @Override
            public void onChanged(Pair<Integer, Integer> value) {
                if (value == null) {
                    return;
                }
                binding.progressBar.setMax(Math.max(1, value.second));
                binding.progressBar.setProgress(value.first);
                binding.textProgress.setText(getString(
                        R.string.expert_progress_format, value.first, value.second));
            }
        });
        viewModel.getScanning().observe(this, new androidx.lifecycle.Observer<Boolean>() {
            @Override
            public void onChanged(Boolean scanning) {
                boolean active = Boolean.TRUE.equals(scanning);
                binding.buttonStart.setEnabled(!active);
                binding.buttonCancel.setEnabled(active);
                binding.buttonCancelSecondary.setEnabled(active);
                binding.progressBar.setVisibility(active ? View.VISIBLE : View.GONE);
                if (active) {
                    binding.textStatus.setText(R.string.expert_status_scanning);
                    binding.textStatus.setVisibility(View.VISIBLE);
                    binding.buttonSettings.setVisibility(View.GONE);
                } else if (scanLaunched) {
                    // Scan terminé : sauvegarde dans l'historique chiffré (Étape 4).
                    scanLaunched = false;
                    saveFinishedScan();
                }
            }
        });
        viewModel.getError().observe(this, new androidx.lifecycle.Observer<String>() {
            @Override
            public void onChanged(String message) {
                if (message != null) {
                    binding.textStatus.setText(message);
                    binding.textStatus.setVisibility(View.VISIBLE);
                }
            }
        });
        viewModel.getNetwork().observe(this, new androidx.lifecycle.Observer<NetworkDetails>() {
            @Override
            public void onChanged(NetworkDetails details) {
                showNetwork(details);
                // Pré-remplit la plage avec le préfixe réel de l'interface
                // (modifiable) : Wi-Fi en /24 comme en Ethernet.
                if (details != null && details.isUsable()
                        && binding.editCidr.getText().toString().trim().isEmpty()) {
                    String cidr = NetworkInfoHelper.getScanCidr(details);
                    if (cidr != null) {
                        binding.editCidr.setText(cidr);
                    }
                }
            }
        });
    }

    // "Aucun hôte" vs "N hôtes" quand le scan est terminé.
    private void updateStatus(List<Host> hosts) {
        if (Boolean.TRUE.equals(viewModel.getScanning().getValue())) {
            return;
        }
        if (hosts == null || hosts.isEmpty()) {
            binding.textStatus.setText(R.string.expert_status_no_hosts);
        } else {
            binding.textStatus.setText(getString(R.string.expert_status_done_format, hosts.size()));
        }
        binding.textStatus.setVisibility(View.VISIBLE);
    }

    private void showNetwork(NetworkDetails details) {
        if (details == null || !details.isUsable()) {
            binding.textWifi.setText(R.string.expert_network_disconnected);
            return;
        }
        String unknown = getString(R.string.expert_unknown);
        String iface = details.getInterfaceName() != null
                ? details.getInterfaceName() : unknown;
        String ip = details.getIpAddress() != null ? details.getIpAddress() : unknown;
        String gateway = details.getGateway() != null ? details.getGateway() : unknown;
        binding.textWifi.setText(getString(R.string.expert_network_format,
                details.getDisplayName(), iface, ip, gateway, details.getSecurityType()));
    }

    // Remplit la plage CIDR depuis l'interface réseau courante : Wi-Fi,
    // Ethernet (câble) ou USB. Préfixe réel, plafonné à /24.
    private void fillCidrFromNetwork() {
        NetworkDetails details = NetworkInfoHelper.getDetails(this);
        String cidr = NetworkInfoHelper.getScanCidr(details);
        if (cidr == null) {
            Toast.makeText(this, R.string.expert_network_disconnected, Toast.LENGTH_SHORT).show();
            return;
        }
        binding.editCidr.setText(cidr);
    }

    private void onStartClicked() {
        if (!readAndValidateConfig()) {
            return;
        }
        // Avertissement légal obligatoire avant tout scan (Étape 5).
        if (!com.example.netscan.ui.common.LegalWarningDialog.ensureAccepted(this)) {
            return;
        }
        // La permission Wi-Fi ne sert qu'au SSID : demandée au mieux, elle ne
        // bloque pas le scan (en Ethernet, aucune permission n'est requise).
        NetworkDetails current = NetworkInfoHelper.getDetails(this);
        if (!PermissionHelper.hasPermissions(this) && current != null && current.isWifi()) {
            PermissionHelper.requestPermissions(this);
        }
        viewModel.loadNetwork(this);
        scanLaunched = true;
        scanStartMs = System.currentTimeMillis();
        viewModel.startScan(this);
    }

    // Sauvegarde le scan terminé (appelée une fois par scan).
    private void saveFinishedScan() {
        if (viewModel.getError().getValue() != null) {
            return;
        }
        NetworkDetails details = NetworkInfoHelper.getDetails(this);
        List<Host> hosts = viewModel.getHosts().getValue();
        ScanRepository.getInstance(this).saveScan(hosts,
                details != null ? details.getDisplayName() : null,
                details != null ? details.getIpAddress() : null,
                details != null ? details.getGateway() : null,
                System.currentTimeMillis() - scanStartMs,
                new java.util.HashSet<>(knownIps), null);
    }

    // Lit les champs, affiche les erreurs, stocke dans le ViewModel si tout est valide.
    private boolean readAndValidateConfig() {
        boolean ok = true;
        String cidr = binding.editCidr.getText().toString();
        String error = InputValidator.validateCidr(cidr);
        binding.editCidr.setError(error);
        ok &= error == null;

        String timeout = binding.editTimeout.getText().toString();
        error = InputValidator.validateTimeout(timeout);
        binding.editTimeout.setError(error);
        ok &= error == null;

        String threads = binding.editThreads.getText().toString();
        error = InputValidator.validateThreads(threads);
        binding.editThreads.setError(error);
        ok &= error == null;

        int preset = binding.spinnerPorts.getSelectedItemPosition();
        String custom = binding.editCustomPorts.getText().toString();
        if (preset == ScanConfig.PRESET_CUSTOM) {
            error = InputValidator.validateCustomPorts(custom);
            binding.editCustomPorts.setError(error);
            ok &= error == null;
        }
        if (!ok) {
            return false;
        }
        ScanConfig config = viewModel.getConfig();
        config.setCidr(cidr);
        config.setPortPreset(preset);
        config.setCustomPortsText(custom);
        config.setTimeoutMs(Integer.parseInt(timeout.trim()));
        config.setThreadCount(Integer.parseInt(threads.trim()));
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PermissionHelper.REQUEST_WIFI_SCAN) {
            return;
        }
        if (PermissionHelper.allGranted(grantResults)) {
            viewModel.loadNetwork(this);
        } else {
            // Refus : le scan reste possible, seul le SSID reste masqué.
            binding.textStatus.setText(R.string.expert_status_permission_denied);
            binding.textStatus.setVisibility(View.VISIBLE);
            binding.buttonSettings.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mode changé dans les réglages : cet accueil n'est plus le bon.
        ModeSwitchHelper.redirectIfModeChanged(this);
        // La pastille reflète le mode après un aller-retour avec le Simple.
        ModeSwitchHelper.bindPastille(this, binding.pastilleMode);
        if (binding != null && binding.bottomNav != null) {
            binding.bottomNav.setSelectedItemId(R.id.nav_scan);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
