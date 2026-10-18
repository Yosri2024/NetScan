package com.example.netscan.ui.expert;

import android.content.Intent;
import android.os.Bundle;
import android.util.Pair;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.netscan.NetScanApp;
import com.example.netscan.R;
import com.example.netscan.data.SettingsRepository;
import com.example.netscan.data.model.ExpertConfig;
import com.example.netscan.data.model.Host;
import com.example.netscan.data.model.NetworkDetails;
import com.example.netscan.data.model.OsInfo;
import com.example.netscan.data.model.ScanConfig;
import com.example.netscan.databinding.ExpertActivityScanBinding;
import com.example.netscan.network.NetworkInfoHelper;
import com.example.netscan.network.NetworkScanner;
import com.example.netscan.security.InputValidator;
import com.example.netscan.security.PermissionHelper;
import com.example.netscan.ui.common.LegalWarningDialog;
import com.example.netscan.ui.common.ModeSwitchHelper;
import com.example.netscan.ui.common.ScanUi;
import com.example.netscan.viewmodel.ScanViewModel;
import com.google.android.material.chip.ChipGroup;

import java.util.List;

// Fichier : ui/expert/ExpertScanActivity.java
// Rôle : écran de scan Expert (refonte). Champs CIDR/ports/timeout/threads,
// identification de l'OS, infos réseau, progression par étape, liste dense,
// fiche détaillée par hôte, annulation, refus de permission.
//
// Aller-retour sans tout reconfigurer :
//  - la configuration saisie est mémorisée (chiffrée) à chaque pause et
//    restaurée à l'ouverture, y compris après un passage en mode Simple ;
//  - le scan et ses résultats vivent dans le ScanViewModel partagé : ils
//    survivent à la bascule de mode, aux réglages et aux rotations.
// Non exportée (voir manifest).
public class ExpertScanActivity extends AppCompatActivity {

    private static final String KEY_EXPERT_CONFIG = "expert_config";

    private ExpertActivityScanBinding binding;
    private ScanViewModel viewModel;
    private HostExpertAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ExpertActivityScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Pastille de mode : c'est le seul endroit d'où l'on peut revenir au
        // mode utilisateur.
        ModeSwitchHelper.bindPastille(this, binding.pastilleMode);

        viewModel = ScanViewModel.get(this);
        // Un scan Simple abandonné pour passer ici n'ouvrira pas ses résultats plus tard.
        viewModel.discardOpenResultsRequest();

        adapter = new HostExpertAdapter(new HostExpertAdapter.OnHostClickListener() {
            @Override
            public void onHostClicked(Host host, OsInfo os) {
                ExpertHostDialog.show(ExpertScanActivity.this, host, os);
            }
        });
        binding.recyclerHosts.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerHosts.setAdapter(adapter);

        // Réseau d'abord : la plage détectée sert à pré-remplir le champ.
        viewModel.loadNetwork(this);
        // Première ouverture : on restaure la configuration mémorisée. Après une
        // rotation ou un redémarrage, ce sont les champs eux-mêmes qui se restaurent.
        if (savedInstanceState == null) {
            applyConfig(ExpertConfig.decode(SettingsRepository.getInstance(this)
                    .getString(KEY_EXPERT_CONFIG, null)));
        }

        binding.chipGroupPorts.setOnCheckedStateChangeListener(
                new ChipGroup.OnCheckedStateChangeListener() {
                    @Override
                    public void onCheckedChanged(@NonNull ChipGroup group,
                                                 @NonNull List<Integer> checkedIds) {
                        updateCustomPortsVisibility();
                    }
                });
        // Icône « Détecter » intégrée au champ de la plage.
        binding.layoutCidr.setEndIconOnClickListener(new View.OnClickListener() {
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
                // Retour visuel immédiat : l'arrêt effectif prend un instant.
                binding.buttonCancel.setEnabled(false);
                binding.textPhase.setText(R.string.scan_cancelling);
                viewModel.cancelScan();
            }
        });
        binding.buttonSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PermissionHelper.openAppSettings(ExpertScanActivity.this);
            }
        });

        // Barre basse, comme en mode Simple : Scan / Historique / Réglages.
        binding.bottomNav.setOnItemSelectedListener(
                new com.google.android.material.navigation.NavigationBarView.OnItemSelectedListener() {
                    @Override
                    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                        int id = item.getItemId();
                        if (id == R.id.nav_scan) {
                            return true;
                        }
                        Intent intent = new Intent(ExpertScanActivity.this,
                                id == R.id.nav_history
                                        ? ExpertHistoryActivity.class
                                        : com.example.netscan.ui.common.SettingsActivity.class);
                        // Un double-tap n'empile pas deux fois le même écran.
                        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(intent);
                        return false; // l'onglet « Scanner » reste sélectionné
                    }
                });

        observeViewModel();
        updateCustomPortsVisibility();
    }

    private void observeViewModel() {
        viewModel.getHosts().observe(this, new Observer<List<Host>>() {
            @Override
            public void onChanged(List<Host> hosts) {
                showHosts(hosts);
            }
        });
        viewModel.getProgress().observe(this, new Observer<Pair<Integer, Integer>>() {
            @Override
            public void onChanged(Pair<Integer, Integer> value) {
                ScanUi.bindProgress(binding.progressBar, binding.textProgress, value);
            }
        });
        viewModel.getPhase().observe(this, new Observer<NetworkScanner.Phase>() {
            @Override
            public void onChanged(NetworkScanner.Phase phase) {
                if (phase != null && viewModel.isScanning()) {
                    binding.textPhase.setText(ScanUi.phaseLabel(ExpertScanActivity.this, phase));
                }
            }
        });
        viewModel.getScanning().observe(this, new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean scanning) {
                boolean active = Boolean.TRUE.equals(scanning);
                // Un seul bouton à la fois : Démarrer OU Arrêter.
                binding.buttonStart.setVisibility(active ? View.GONE : View.VISIBLE);
                binding.buttonCancel.setVisibility(active ? View.VISIBLE : View.GONE);
                binding.cardProgress.setVisibility(active ? View.VISIBLE : View.GONE);
                // Pas de champs modifiés pendant le scan : il utilise un instantané.
                setFieldsEnabled(!active);
                if (active) {
                    binding.buttonCancel.setEnabled(true);
                    binding.buttonSettings.setVisibility(View.GONE);
                    binding.textStatus.setVisibility(View.GONE);
                    binding.textPhase.setText(ScanUi.phaseLabel(
                            ExpertScanActivity.this, viewModel.getPhase().getValue()));
                } else {
                    showFinishedStatus();
                }
            }
        });
        viewModel.getError().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String message) {
                if (message != null) {
                    binding.textStatus.setText(message);
                    binding.textStatus.setVisibility(View.VISIBLE);
                    // Affiché une fois : une rotation ne le rejoue pas.
                    viewModel.consumeError();
                }
            }
        });
        viewModel.getNetwork().observe(this, new Observer<NetworkDetails>() {
            @Override
            public void onChanged(NetworkDetails details) {
                showNetwork(details);
                // Pré-remplit la plage avec le préfixe réel de l'interface
                // (modifiable) : Wi-Fi en /24 comme en Ethernet.
                if (details != null && details.isUsable()
                        && binding.editCidr.getText().toString().trim().isEmpty()) {
                    String cidr = viewModel.getAutoCidr();
                    if (cidr != null) {
                        binding.editCidr.setText(cidr);
                    }
                }
                // Le système de « cet appareil » et de la passerelle en dépend.
                showHosts(viewModel.getHosts().getValue());
            }
        });
    }

    // ------------------------------------------------------------------
    // Affichage
    // ------------------------------------------------------------------

    private void showHosts(List<Host> hosts) {
        NetworkDetails details = viewModel.getNetwork().getValue();
        NetScanApp app = (NetScanApp) getApplication();
        String ownIp = details != null ? details.getIpAddress() : app.getLastOwnIp();
        String gateway = details != null ? details.getGateway() : app.getLastGatewayIp();
        adapter.setHosts(hosts, ownIp, gateway);
        boolean empty = hosts == null || hosts.isEmpty();
        binding.textResultsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.textResultsCount.setText(empty ? ""
                : getString(R.string.expert_results_count_format, hosts.size()));
    }

    // Message de fin de scan, jamais pendant un scan ni avant le premier.
    private void showFinishedStatus() {
        List<Host> hosts = viewModel.getHosts().getValue();
        boolean empty = hosts == null || hosts.isEmpty();
        if (viewModel.wasCancelled()) {
            binding.textStatus.setText(R.string.scan_cancelled);
        } else if (empty) {
            // Au premier affichage (aucun scan lancé), rien à annoncer.
            if (((NetScanApp) getApplication()).getLastScanTimestamp() <= 0) {
                return;
            }
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

    private void setFieldsEnabled(boolean enabled) {
        binding.layoutCidr.setEnabled(enabled);
        binding.editCidr.setEnabled(enabled);
        binding.layoutCustomPorts.setEnabled(enabled);
        binding.editCustomPorts.setEnabled(enabled);
        binding.layoutTimeout.setEnabled(enabled);
        binding.editTimeout.setEnabled(enabled);
        binding.layoutThreads.setEnabled(enabled);
        binding.editThreads.setEnabled(enabled);
        binding.switchOs.setEnabled(enabled);
        for (int i = 0; i < binding.chipGroupPorts.getChildCount(); i++) {
            binding.chipGroupPorts.getChildAt(i).setEnabled(enabled);
        }
    }

    private void updateCustomPortsVisibility() {
        boolean custom = selectedPreset() == ScanConfig.PRESET_CUSTOM;
        binding.layoutCustomPorts.setVisibility(custom ? View.VISIBLE : View.GONE);
    }

    // ------------------------------------------------------------------
    // Configuration : lecture, validation, mémorisation
    // ------------------------------------------------------------------

    private int selectedPreset() {
        int id = binding.chipGroupPorts.getCheckedChipId();
        if (id == R.id.chip_ports_all) {
            return ScanConfig.PRESET_ALL_PORTS;
        }
        if (id == R.id.chip_ports_custom) {
            return ScanConfig.PRESET_CUSTOM;
        }
        return ScanConfig.PRESET_COMMON_PORTS;
    }

    private void applyConfig(ExpertConfig config) {
        // Plage : celle saisie à la main si elle existe, sinon le réseau actuel.
        String cidr = config.cidrOverride;
        if (cidr.isEmpty()) {
            String auto = viewModel.getAutoCidr();
            cidr = auto == null ? "" : auto;
        }
        binding.editCidr.setText(cidr);
        binding.chipGroupPorts.check(
                config.portPreset == ScanConfig.PRESET_ALL_PORTS ? R.id.chip_ports_all
                        : config.portPreset == ScanConfig.PRESET_CUSTOM ? R.id.chip_ports_custom
                        : R.id.chip_ports_common);
        binding.editCustomPorts.setText(config.customPorts);
        binding.editTimeout.setText(config.timeoutText);
        binding.editThreads.setText(config.threadsText);
        binding.switchOs.setChecked(config.osDetection);
    }

    // Mémorise les champs TELS QUE SAISIS (même invalides) : on retrouve son
    // brouillon. La plage n'est conservée que si elle a été modifiée à la main.
    private void saveConfig() {
        if (binding == null) {
            return;
        }
        ExpertConfig config = new ExpertConfig();
        String cidr = textOf(binding.editCidr);
        String auto = viewModel.getAutoCidr();
        config.cidrOverride = (cidr.isEmpty() || cidr.equals(auto)) ? "" : cidr;
        config.portPreset = selectedPreset();
        config.customPorts = textOf(binding.editCustomPorts);
        config.timeoutText = textOf(binding.editTimeout);
        config.threadsText = textOf(binding.editThreads);
        config.osDetection = binding.switchOs.isChecked();
        SettingsRepository.getInstance(this).putString(KEY_EXPERT_CONFIG, config.encode());
    }

    private static String textOf(com.google.android.material.textfield.TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    // Lit les champs et affiche les erreurs ; null si quelque chose est invalide.
    private ScanConfig readAndValidateConfig() {
        boolean ok = true;
        String cidr = textOf(binding.editCidr);
        String error = InputValidator.validateCidr(cidr);
        binding.layoutCidr.setError(error);
        ok &= error == null;

        String timeout = textOf(binding.editTimeout);
        error = InputValidator.validateTimeout(timeout);
        binding.layoutTimeout.setError(error);
        ok &= error == null;

        String threads = textOf(binding.editThreads);
        error = InputValidator.validateThreads(threads);
        binding.layoutThreads.setError(error);
        ok &= error == null;

        int preset = selectedPreset();
        String custom = textOf(binding.editCustomPorts);
        if (preset == ScanConfig.PRESET_CUSTOM) {
            error = InputValidator.validateCustomPorts(custom);
            binding.layoutCustomPorts.setError(error);
            ok &= error == null;
        } else {
            binding.layoutCustomPorts.setError(null);
        }
        if (!ok) {
            return null;
        }
        ScanConfig config = new ScanConfig();
        config.setCidr(cidr);
        config.setPortPreset(preset);
        config.setCustomPortsText(custom);
        config.setTimeoutMs(Integer.parseInt(timeout));
        config.setThreadCount(Integer.parseInt(threads));
        config.setPortScanEnabled(true);
        config.setOsDetectionEnabled(binding.switchOs.isChecked());
        return config;
    }

    // Remplit la plage CIDR depuis l'interface réseau courante : Wi-Fi,
    // Ethernet (câble) ou USB. Préfixe réel, plafonné à /24.
    private void fillCidrFromNetwork() {
        viewModel.loadNetwork(this);
        String cidr = viewModel.getAutoCidr();
        if (cidr == null) {
            Toast.makeText(this, R.string.expert_network_disconnected, Toast.LENGTH_SHORT).show();
            return;
        }
        binding.layoutCidr.setError(null);
        binding.editCidr.setText(cidr);
    }

    private void onStartClicked() {
        ScanConfig config = readAndValidateConfig();
        if (config == null) {
            return;
        }
        // Avertissement légal obligatoire avant tout scan (Étape 5).
        if (!LegalWarningDialog.ensureAccepted(this)) {
            return;
        }
        // La permission Wi-Fi ne sert qu'au SSID : demandée au mieux, elle ne
        // bloque pas le scan (en Ethernet, aucune permission n'est requise).
        NetworkDetails current = NetworkInfoHelper.getDetails(this);
        if (!PermissionHelper.hasPermissions(this) && current != null && current.isWifi()) {
            PermissionHelper.requestPermissions(this);
        }
        saveConfig();
        viewModel.startExpertScan(this, config);
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
        updateCustomPortsVisibility();
    }

    @Override
    protected void onPause() {
        // Quitter l'écran (bascule de mode, réglages, historique) ne perd plus la saisie.
        saveConfig();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
