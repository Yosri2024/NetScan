package com.example.netscan.ui.common;

import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

// Fichier : ui/common/StateViewHelper.java
// Rôle : écrans d'état communs chargement / vide / erreur (Étape 3).
// Utilisé avec le layout common_view_state.xml inclus dans les écrans.
public final class StateViewHelper {

    public enum State {
        CONTENT,
        LOADING,
        EMPTY,
        ERROR
    }

    private StateViewHelper() {
    }

    // Affiche l'état demandé ; CONTENT masque le panneau et montre le contenu.
    // retryListener peut être null (bouton Réessayer masqué).
    public static void apply(View stateRoot, View content,
                             ProgressBar progress, TextView message, Button retry,
                             State state, String text,
                             View.OnClickListener retryListener) {
        if (state == State.CONTENT) {
            stateRoot.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);
            return;
        }
        content.setVisibility(View.GONE);
        stateRoot.setVisibility(View.VISIBLE);
        progress.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        message.setText(text == null ? "" : text);
        if (retryListener != null && state == State.ERROR) {
            retry.setVisibility(View.VISIBLE);
            retry.setOnClickListener(retryListener);
        } else {
            retry.setVisibility(View.GONE);
        }
    }
}
