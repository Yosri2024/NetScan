package com.example.netscan.ui.simple;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.netscan.NetScanApp;
import com.example.netscan.alerts.AdviceProvider;
import com.example.netscan.data.model.OsInfo;
import com.example.netscan.databinding.SimpleDialogDeviceBinding;
import com.example.netscan.ui.common.OsUi;

// Fichier : ui/simple/SimpleDeviceDialog.java
// Rôle : fiche d'un appareil. Montre son système estimé (pastille, confiance
// et indices retenus). « C'est mon appareil » le marque comme reconnu
// (mémorisé, base chiffrée). « Je ne reconnais pas » affiche les conseils
// sans alarmer.
public class SimpleDeviceDialog extends DialogFragment {

    public interface OnDeviceMarkedListener {
        void onDeviceMarked();
    }

    private static final String ARG_IP = "ip";
    private static final String ARG_NAME = "name";
    private static final String ARG_OS_CODE = "os_code";
    private static final String ARG_OS_DETAIL = "os_detail";
    private static final String ARG_OS_TEXT = "os_text";

    // osText : texte détaillé déjà composé (voir OsUi.describe).
    public static SimpleDeviceDialog newInstance(String ip, String deviceName,
                                                 OsInfo os, String osText) {
        SimpleDeviceDialog dialog = new SimpleDeviceDialog();
        Bundle args = new Bundle();
        args.putString(ARG_IP, ip);
        args.putString(ARG_NAME, deviceName);
        args.putString(ARG_OS_CODE, os.encode());
        args.putString(ARG_OS_DETAIL, os.detail());
        args.putString(ARG_OS_TEXT, osText);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final SimpleDialogDeviceBinding binding = SimpleDialogDeviceBinding.inflate(
                LayoutInflater.from(requireContext()));
        Bundle args = requireArguments();
        final String ip = args.getString(ARG_IP, "");
        final String name = args.getString(ARG_NAME, "");

        binding.textDeviceName.setText(name);
        binding.textDeviceIp.setText(ip);

        // Système estimé : on reconstruit l'OsInfo depuis son code compact.
        OsInfo os = OsInfo.decode(args.getString(ARG_OS_CODE));
        if (os == null) {
            os = OsInfo.unknown();
        }
        String detail = args.getString(ARG_OS_DETAIL);
        if (detail != null && os.isKnown()) {
            os = OsInfo.exact(os.family(), detail, null);
        }
        OsUi.bindIcon(binding.imageOs, binding.iconContainer, os);
        OsUi.bindChip(binding.chipOs, os);
        binding.textDeviceOs.setText(args.getString(ARG_OS_TEXT, ""));

        binding.buttonMine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Persisté CHIFFRÉ (Étape 4) + mémoire d'affichage (synchro Étape 3).
                com.example.netscan.data.KnownDeviceRepository
                        .getInstance(requireContext()).markKnown(ip, name);
                NetScanApp app = (NetScanApp) requireActivity().getApplication();
                app.markKnown(ip);
                if (getActivity() instanceof OnDeviceMarkedListener) {
                    ((OnDeviceMarkedListener) getActivity()).onDeviceMarked();
                }
                dismiss();
            }
        });
        binding.buttonUnknown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                binding.textAdvice.setText(AdviceProvider.unknownDeviceAdvice());
                binding.textAdvice.setVisibility(View.VISIBLE);
            }
        });
        binding.buttonClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        return new AlertDialog.Builder(requireContext())
                .setView(binding.getRoot())
                .create();
    }
}
