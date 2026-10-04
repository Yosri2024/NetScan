package com.example.netscan.alerts;

// Fichier : alerts/Alert.java
// Rôle : une alerte nuancée pour le mode Simple (Étape 3).
// Jamais alarmiste : le rouge est réservé aux cas avérés (voir AlertHelper).
public class Alert {

    // OK (vert) ou WARNING (orange). Pas de niveau rouge en mode Simple.
    public enum Level {
        OK,
        WARNING
    }

    private final Level level;
    private final String title;
    private final String message;

    public Alert(Level level, String title, String message) {
        this.level = level;
        this.title = title;
        this.message = message;
    }

    public Level getLevel() {
        return level;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }
}
