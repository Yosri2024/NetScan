package com.example.netscan.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.netscan.data.ScanRepository;
import com.example.netscan.data.SettingsRepository;
import com.example.netscan.mode.AppMode;
import com.example.netscan.mode.AppTheme;
import com.example.netscan.mode.ModeManager;
import com.example.netscan.mode.ThemeManager;

// Fichier : viewmodel/SettingsViewModel.java
// Rôle : écran Réglages (Étape 5). Le mode et le thème passent par
// ModeManager / ThemeManager (DataStore chiffré) ; l'historique s'efface
// via le dépôt.
public class SettingsViewModel extends AndroidViewModel {

    private final MutableLiveData<AppMode> mode = new MutableLiveData<>(AppMode.SIMPLE);
    private final MutableLiveData<AppTheme> theme = new MutableLiveData<>(AppTheme.SYSTEM);

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        // Lecture après préchargement : valeur fiable même au premier accès.
        SettingsRepository.getInstance(application).awaitReady(new Runnable() {
            @Override
            public void run() {
                mode.postValue(ModeManager.getMode(getApplication()));
                theme.postValue(ThemeManager.getTheme(getApplication()));
            }
        });
    }

    public LiveData<AppMode> getMode() {
        return mode;
    }

    public void setMode(AppMode newMode) {
        ModeManager.setMode(getApplication(), newMode);
        mode.setValue(newMode);
    }

    public LiveData<AppTheme> getTheme() {
        return theme;
    }

    // Applique le thème choisi : persisté puis appliqué immédiatement.
    public void setTheme(AppTheme newTheme) {
        ThemeManager.setTheme(getApplication(), newTheme);
        theme.setValue(newTheme);
        ThemeManager.apply(newTheme);
    }

    public void clearHistory(final Runnable done) {
        ScanRepository.getInstance(getApplication()).clearAll(new Runnable() {
            @Override
            public void run() {
                if (done != null) {
                    done.run();
                }
            }
        });
    }
}
