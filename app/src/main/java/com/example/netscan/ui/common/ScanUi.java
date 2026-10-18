package com.example.netscan.ui.common;

import android.content.Context;
import android.util.Pair;
import android.widget.TextView;

import com.example.netscan.R;
import com.example.netscan.network.NetworkScanner;
import com.google.android.material.progressindicator.LinearProgressIndicator;

// Fichier : ui/common/ScanUi.java
// Rôle : affichage de la progression du scan, identique en Simple et Expert
// (libellé de l'étape en cours + barre déterminée ou indéterminée).
public final class ScanUi {

    private ScanUi() {
    }

    // « Recherche des appareils… », « Identification des systèmes… »...
    public static String phaseLabel(Context context, NetworkScanner.Phase phase) {
        if (phase == null) {
            return context.getString(R.string.simple_scanning);
        }
        switch (phase) {
            case OS_DETECTION:
                return context.getString(R.string.scan_phase_os);
            case PORTS:
                return context.getString(R.string.scan_phase_ports);
            case DISCOVERY:
            default:
                return context.getString(R.string.scan_phase_discovery);
        }
    }

    // Total inconnu (début d'étape) : barre indéterminée ; sinon « 120 / 254 ».
    public static void bindProgress(LinearProgressIndicator bar, TextView label,
                                    Pair<Integer, Integer> value) {
        if (value == null || value.second == null || value.second <= 0) {
            bar.setIndeterminate(true);
            label.setText("");
            return;
        }
        int total = value.second;
        int done = Math.min(value.first == null ? 0 : value.first, total);
        bar.setIndeterminate(false);
        bar.setMax(total);
        bar.setProgressCompat(done, true);
        label.setText(label.getContext().getString(R.string.expert_progress_format, done, total));
    }
}
