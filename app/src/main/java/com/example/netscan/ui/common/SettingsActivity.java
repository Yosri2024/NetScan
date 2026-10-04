package com.example.netscan.ui.common;

import android.content.DialogInterface;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.netscan.R;
import com.example.netscan.databinding.CommonActivitySettingsBinding;
import com.example.netscan.mode.AppMode;
import com.example.netscan.mode.AppTheme;
import com.example.netscan.viewmodel.SettingsViewModel;

// Fichier : ui/common/SettingsActivity.java
// Rôle : écran Réglages (Étape 5). Choix du mode, thème (mode nuit),
// effacement de l'historique (avec confirmation), rappel légal, à-propos.
public class SettingsActivity extends AppCompatActivity {

    private CommonActivitySettingsBinding binding;
    private SettingsViewModel viewModel;
    private boolean syncingUi;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = CommonActivitySettingsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Bouton retour explicite (le geste système reste valable).
        BackHelper.bind(this);

        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        viewModel.getMode().observe(this, new androidx.lifecycle.Observer<AppMode>() {
            @Override
            public void onChanged(AppMode mode) {
                syncingUi = true;
                binding.radioMode.check(mode == AppMode.EXPERT ? R.id.radio_expert : R.id.radio_simple);
                syncingUi = false;
            }
        });
        binding.radioMode.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (syncingUi) {
                    return;
                }
                viewModel.setMode(checkedId == R.id.radio_expert ? AppMode.EXPERT : AppMode.SIMPLE);
            }
        });
        // Mode nuit : système / clair / sombre.
        viewModel.getTheme().observe(this,
                new androidx.lifecycle.Observer<AppTheme>() {
                    @Override
                    public void onChanged(AppTheme theme) {
                        syncingUi = true;
                        binding.radioTheme.check(radioOf(theme));
                        syncingUi = false;
                    }
                });
        binding.radioTheme.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (syncingUi) {
                    return;
                }
                viewModel.setTheme(themeOf(checkedId));
            }
        });
        binding.buttonClearHistory.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmClear();
            }
        });
        binding.buttonLegal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LegalWarningDialog.show(SettingsActivity.this);
            }
        });
        binding.textAbout.setText(getString(R.string.settings_about_format, appVersion()));
    }

    // Une application recréée conserve son état (ViewModel) : pas de boucle.
    private int radioOf(AppTheme theme) {
        if (theme == AppTheme.LIGHT) {
            return R.id.radio_theme_light;
        }
        if (theme == AppTheme.DARK) {
            return R.id.radio_theme_dark;
        }
        return R.id.radio_theme_system;
    }

    private AppTheme themeOf(int checkedId) {
        if (checkedId == R.id.radio_theme_light) {
            return AppTheme.LIGHT;
        }
        if (checkedId == R.id.radio_theme_dark) {
            return AppTheme.DARK;
        }
        return AppTheme.SYSTEM;
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.simple_history_clear_title)
                .setMessage(R.string.simple_history_clear_message)
                .setPositiveButton(R.string.simple_history_clear_yes,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                viewModel.clearHistory(new Runnable() {
                                    @Override
                                    public void run() {
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                Toast.makeText(SettingsActivity.this,
                                                        R.string.settings_cleared,
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                                    }
                                });
                            }
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String appVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "1.0" : info.versionName;
        } catch (Exception e) {
            return "1.0";
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        binding = null;
    }
}
