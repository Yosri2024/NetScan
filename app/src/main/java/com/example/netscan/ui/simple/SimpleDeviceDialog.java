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
import com.example.netscan.R;
import com.example.netscan.alerts.AdviceProvider;
import com.example.netscan.databinding.SimpleDialogDeviceBinding;

// Fichier : ui/simple/SimpleDeviceDialog.java
// Rôle : fiche d'un appareil (Étape 3). "C'est mon appareil" le marque
// comme reconnu (mémorisé, base chiffrée à l'Étape 4). "Je ne le connais
// pas" affiche les conseils sans alarmer.
public class SimpleDeviceDialog extends DialogFragment {

    public interface OnDeviceMarkedListener {
        void onDeviceMarked();
    }

    private static final String ARG_IP = "ip";
    private static final String ARG_NAME = "name";

    public static SimpleDeviceDialog newInstance(String ip, String deviceName) {
        SimpleDeviceDialog dialog = new SimpleDeviceDialog();
        Bundle args = new Bundle();
        args.putString(ARG_IP, ip);
        args.putString(ARG_NAME, deviceName);
        dialog.setArguments(args);
        return dialog;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        final SimpleDialogDeviceBinding binding = SimpleDialogDeviceBinding.inflate(
                LayoutInflater.from(requireContext()));
        String ip = getArguments() != null ? getArguments().getString(ARG_IP) : "";
        String name = getArguments() != null ? getArguments().getString(ARG_NAME) : "";
        binding.textDeviceName.setText(name);
        binding.textDeviceIp.setText(ip);

        binding.buttonMine.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String ip = getArguments().getString(ARG_IP);
                String name = getArguments().getString(ARG_NAME);
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

        return new AlertDialog.Builder(requireContext())
                .setView(binding.getRoot())
                .create();
    }
}
