package com.example.netscan.ui.common;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.example.netscan.data.SettingsRepository;
import com.example.netscan.databinding.CommonDialogLegalBinding;

// Fichier : ui/common/LegalWarningDialog.java
// Rôle : avertissement légal du premier lancement (Étape 5).
// "Ne scannez que vos propres réseaux ou ceux pour lesquels vous avez
// une autorisation." Acceptation mémorisée (chiffrée) une fois pour toutes.
public class LegalWarningDialog extends DialogFragment {

    public static void show(AppCompatActivity activity) {
        new LegalWarningDialog().show(activity.getSupportFragmentManager(), "legal");
    }

    // Vérifie avant chaque scan : affiche si jamais accepté, retourne faux.
    // L'utilisateur relance ensuite le scan d'un toucher (comportement voulu :
    // aucun scan ne démarre sans consentement explicite).
    public static boolean ensureAccepted(AppCompatActivity activity) {
        if (SettingsRepository.getInstance(activity).isLegalAccepted()) {
            return true;
        }
        show(activity);
        return false;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final CommonDialogLegalBinding binding = CommonDialogLegalBinding.inflate(
                LayoutInflater.from(requireContext()));
        binding.buttonAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SettingsRepository.getInstance(requireContext()).setLegalAccepted();
                dismiss();
            }
        });
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(binding.getRoot())
                .create();
        // Non contournable : le bouton est le seul moyen de fermer.
        dialog.setCanceledOnTouchOutside(false);
        return dialog;
    }
}
