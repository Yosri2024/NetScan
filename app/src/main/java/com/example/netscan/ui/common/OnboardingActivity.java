package com.example.netscan.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;

import com.example.netscan.R;
import com.example.netscan.data.SettingsRepository;
import com.example.netscan.databinding.CommonActivityOnboardingBinding;
import com.example.netscan.mode.AppMode;
import com.example.netscan.mode.ModeManager;
import com.example.netscan.ui.expert.ExpertScanActivity;
import com.example.netscan.ui.simple.SimpleScanActivity;

// Fichier : ui/common/OnboardingActivity.java
// Rôle : premier lancement (Étape 5). Présentation, avertissement légal
// (acceptation obligatoire), choix du mode. Puis routage vers l'accueil
// du mode mémorisé. Le routage attend le préchargement DataStore (aucun
// flash, aucun choix perdu).
public class OnboardingActivity extends AppCompatActivity {

    private CommonActivityOnboardingBinding binding;
    private SettingsRepository repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = CommonActivityOnboardingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        repo = SettingsRepository.getInstance(this);
        // Contenu masqué le temps du préchargement (routage fiable).
        repo.awaitReady(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        routeOrShow();
                    }
                });
            }
        });
    }

    private void routeOrShow() {
        if (binding == null) {
            return;
        }
        if (repo.isOnboardingDone()) {
            goHome();
            return;
        }
        binding.loadingProgress.setVisibility(View.GONE);
        binding.contentOnboarding.setVisibility(View.VISIBLE);
        binding.buttonStart.setEnabled(false);
        binding.checkboxLegal.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
                        binding.buttonStart.setEnabled(checked);
                    }
                });
        binding.buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishOnboarding();
            }
        });
    }

    private void finishOnboarding() {
        int checked = binding.radioMode.getCheckedRadioButtonId();
        AppMode mode = (checked == R.id.radio_expert) ? AppMode.EXPERT : AppMode.SIMPLE;
        ModeManager.setMode(this, mode);
        repo.setLegalAccepted();
        repo.setOnboardingDone();
        goHome();
    }

    private void goHome() {
        Class<?> home = ModeManager.isSimple(this)
                ? SimpleScanActivity.class : ExpertScanActivity.class;
        Intent intent = new Intent(this, home);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
