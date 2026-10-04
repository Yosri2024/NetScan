package com.example.netscan.ui.common;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.example.netscan.R;

// Fichier : ui/common/BackHelper.java
// Rôle : bouton retour explicite en haut de chaque écran. Le geste système
// du téléphone reste valable ; ce bouton déclenche le même comportement,
// par l'OnBackPressedDispatcher (donc sans API dépréciée).
public final class BackHelper {

    private BackHelper() {
    }

    // À appeler dans onCreate, après setContentView. Sans effet si l'écran
    // ne contient pas le bouton (onboarding, dialogue légal).
    public static void bind(AppCompatActivity activity) {
        View back = activity.findViewById(R.id.button_back);
        if (back == null) {
            return;
        }
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.getOnBackPressedDispatcher().onBackPressed();
            }
        });
    }
}