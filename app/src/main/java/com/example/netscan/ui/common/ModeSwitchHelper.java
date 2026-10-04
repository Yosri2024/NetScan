package com.example.netscan.ui.common;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import com.example.netscan.R;
import com.example.netscan.mode.AppMode;
import com.example.netscan.mode.ModeManager;
import com.example.netscan.ui.expert.ExpertScanActivity;
import com.example.netscan.ui.simple.SimpleScanActivity;

// Fichier : ui/common/ModeSwitchHelper.java
// Rôle : pastille "Mode simple / Mode expert" (Étape 3).
// Bascule instantanée : le choix est mémorisé puis l'application repart
// directement sur l'ACCUEIL du mode choisi (pile d'écrans effacée).
// Le dernier scan et les appareils marqués survivent via NetScanApp ; un
// scan EN COURS est annulé (son activité est détruite).
public final class ModeSwitchHelper {

    private ModeSwitchHelper() {
    }

    // Affiche la pastille et la rend touchable pour basculer.
    public static void bindPastille(final Activity activity, TextView pastille) {
        boolean simple = ModeManager.isSimple(activity);
        pastille.setText(simple ? R.string.mode_simple_badge : R.string.mode_expert_badge);
        pastille.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchMode(activity);
            }
        });
    }

    public static void switchMode(Activity activity) {
        AppMode next = ModeManager.isSimple(activity) ? AppMode.EXPERT : AppMode.SIMPLE;
        goToModeHome(activity, next);
    }

    // Redirige vers l'accueil du mode demandé. Utilisé par la pastille et
    // par les pages d'accueil elles-mêmes : si le mode a été changé depuis les
    // réglages, revenir à l'accueil doit afficher le BON écran, pas l'ancien.
    public static void goToModeHome(Activity activity, AppMode mode) {
        if (mode != null) {
            ModeManager.setMode(activity, mode);
        }
        Class<?> target = (mode == AppMode.EXPERT)
                ? ExpertScanActivity.class : SimpleScanActivity.class;
        if (activity.getClass().equals(target)) {
            return; // Déjà sur le bon accueil.
        }
        Intent intent = new Intent(activity, target);
        // CLEAR_TASK : on repart de l'accueil du mode choisi, pile d'écrans
        // effacée. Sans ça, le retour système ramène sur les écrans de
        // l'ancien mode et l'app semble incohérente.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }

    // Appelé au retour sur un écran d'accueil : si le mode mémorisé n'est
    // plus celui de cet écran, on change tout de suite.
    public static void redirectIfModeChanged(Activity activity) {
        goToModeHome(activity, ModeManager.getMode(activity));
    }
}
