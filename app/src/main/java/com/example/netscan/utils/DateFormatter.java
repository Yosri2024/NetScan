package com.example.netscan.utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

// Fichier : utils/DateFormatter.java
// Rôle : dates lisibles des historiques (Étape 4). Pur Java.
// "Aujourd'hui, 09:12", "Hier, 20:15", "Lundi, 18:40".
public final class DateFormatter {

    private DateFormatter() {
    }

    public static String formatRelative(long timestamp) {
        Calendar now = Calendar.getInstance();
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(timestamp);
        String hour = new SimpleDateFormat("HH:mm", Locale.FRENCH).format(new Date(timestamp));
        if (sameDay(now, then)) {
            return "Aujourd'hui, " + hour;
        }
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(yesterday, then)) {
            return "Hier, " + hour;
        }
        // Moins de 7 jours : jour de la semaine ("Lundi, 18:40").
        long days = (startOfDay(now).getTimeInMillis() - startOfDay(then).getTimeInMillis())
                / (24L * 60 * 60 * 1000);
        if (days < 7) {
            String weekday = new SimpleDateFormat("EEEE", Locale.FRENCH).format(new Date(timestamp));
            return capitalize(weekday) + ", " + hour;
        }
        return new SimpleDateFormat("dd/MM/yyyy, HH:mm", Locale.FRENCH).format(new Date(timestamp));
    }

    // Pour les noms de fichiers d'export : 20261002_201500.
    public static String formatFileTimestamp(long timestamp) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date(timestamp));
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private static Calendar startOfDay(Calendar day) {
        Calendar copy = (Calendar) day.clone();
        copy.set(Calendar.HOUR_OF_DAY, 0);
        copy.set(Calendar.MINUTE, 0);
        copy.set(Calendar.SECOND, 0);
        copy.set(Calendar.MILLISECOND, 0);
        return copy;
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return text.substring(0, 1).toUpperCase(Locale.FRENCH) + text.substring(1);
    }
}
