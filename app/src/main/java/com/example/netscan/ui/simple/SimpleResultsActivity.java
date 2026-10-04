package com.example.netscan.ui.simple;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.netscan.NetScanApp;
import com.example.netscan.R;
import com.example.netscan.alerts.AdviceProvider;
import com.example.netscan.alerts.Alert;
import com.example.netscan.alerts.AlertHelper;
import com.example.netscan.data.model.Host;
import com.example.netscan.databinding.SimpleActivityResultsBinding;

import java.util.List;
import com.example.netscan.ui.common.BackHelper;

// Fichier : ui/simple/SimpleResultsActivity.java
// Rôle : Écran 2 du mode Simple (Étape 3). Bandeau résumé coloré,
// liste des appareils, fiche au toucher, encart Conseils.
public class SimpleResultsActivity extends AppCompatActivity
        implements DeviceSimpleAdapter.OnDeviceClickListener,
        SimpleDeviceDialog.OnDeviceMarkedListener {

    private SimpleActivityResultsBinding binding;
    private DeviceSimpleAdapter adapter;
    private NetScanApp app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = SimpleActivityResultsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bouton retour explicite (le geste système reste valable).
        BackHelper.bind(this);
        app = (NetScanApp) getApplication();

        binding.recyclerDevices.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceSimpleAdapter(app, this);
        binding.recyclerDevices.setAdapter(adapter);

        binding.textAdvice.setText(AdviceProvider.generalAdvice()
                + "\n\n" + AdviceProvider.accuracyReminder());

        // Les marquages persistés (base chiffrée) resynchronisent l'affichage,
        // y compris après un redémarrage (la mémoire vive est alors vide).
        com.example.netscan.data.KnownDeviceRepository.getInstance(this).observeAll().observe(this,
                new androidx.lifecycle.Observer<java.util.List<com.example.netscan.data.db.KnownDeviceEntity>>() {
                    @Override
                    public void onChanged(
                            java.util.List<com.example.netscan.data.db.KnownDeviceEntity> devices) {
                        if (devices != null) {
                            for (com.example.netscan.data.db.KnownDeviceEntity device : devices) {
                                app.markKnown(device.ip);
                            }
                            refresh();
                        }
                    }
                });

        refresh();
    }

    // Relit le dernier scan (après "C'est mon appareil", le point change).
    private void refresh() {
        List<Host> hosts = app.getLastHosts();
        adapter = new DeviceSimpleAdapter(app, this);
        adapter.setDevices(hosts);
        binding.recyclerDevices.setAdapter(adapter);
        showSummary(hosts);
        // Compteur d'appareils : sans cela la ligne affiche le texte
        // "Aucun appareil détecté" même quand la liste est pleine.
        binding.textCount.setText(getString(R.string.simple_last_scan_devices_format, hosts.size()));
        binding.textEmpty.setVisibility(hosts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showSummary(List<Host> hosts) {
        Alert alert = AlertHelper.summary(hosts, app.getKnownIps(),
                app.getLastOwnIp(), app.getLastGatewayIp());
        boolean ok = alert.getLevel() == Alert.Level.OK;
        binding.bannerSummary.setBackgroundColor(ContextCompat.getColor(this,
                ok ? R.color.status_ok : R.color.status_warning));
        // Le texte suit le fond : lisible en thème clair comme en mode nuit.
        binding.textSummary.setTextColor(ContextCompat.getColor(this, R.color.status_on));
        binding.textSummary.setText(alert.getTitle() + " — " + alert.getMessage());
    }

    @Override
    public void onDeviceClicked(Host host) {
        SimpleDeviceDialog dialog = SimpleDeviceDialog.newInstance(
                host.getIpAddress(),
                com.example.netscan.utils.DeviceNamer.nameFor(
                        host, app.getLastOwnIp(), app.getLastGatewayIp()));
        dialog.show(getSupportFragmentManager(), "device");
    }

    @Override
    public void onDeviceMarked() {
        refresh();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
