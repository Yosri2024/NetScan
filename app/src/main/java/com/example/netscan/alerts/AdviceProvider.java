package com.example.netscan.alerts;

// Fichier : alerts/AdviceProvider.java
// Rôle : conseils affichés en mode Simple (Étape 3).
// Ton rassurant, vouvoiement, jamais de certitude sans preuve.
public final class AdviceProvider {

    private AdviceProvider() {
    }

    // Encart "Conseils" sous les résultats.
    public static String generalAdvice() {
        return "Si vous ne reconnaissez pas un appareil, commencez par éteindre "
                + "vos propres appareils un par un. Si l'appareil reste visible, "
                + "changez le mot de passe de votre réseau (box Wi-Fi ou Ethernet).";
    }

    // Fiche d'un appareil que l'utilisateur dit ne pas connaître.
    public static String unknownDeviceAdvice() {
        return "Cet appareil est peut-être un objet connecté oublié (montre, "
                + "enceinte, prise). Si vraiment vous ne le reconnaissez pas, "
                + "changez le mot de passe de votre réseau et redémarrez votre box.";
    }

    // Rappel affiché avec chaque diagnostic : l'identification est approximative.
    public static String accuracyReminder() {
        return "L'identification des appareils est approximative : "
                + "un nom affiché peut être inexact.";
    }
}
