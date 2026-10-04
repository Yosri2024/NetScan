package com.example.netscan.security;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

// Fichier : security/PermissionHelper.java
// Rôle : permission Wi-Fi, utile au SEUL affichage du SSID (Étape 2).
// Android 13+ : NEARBY_WIFI_DEVICES. Avant : ACCESS_FINE_LOCATION.
// Elle n'est JAMAIS nécessaire au scan lui-même : sur Ethernet, USB ou VPN
// aucune permission n'est demandée, et un refus n'empêche pas de scanner —
// seul le nom du Wi-Fi reste masqué.
public final class PermissionHelper {

    public static final int REQUEST_WIFI_SCAN = 1001;

    private PermissionHelper() {
    }

    // Permissions exigées selon la version Android (voir spec).
    public static String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{Manifest.permission.NEARBY_WIFI_DEVICES};
        }
        return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
    }

    public static boolean hasPermissions(Context context) {
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(context, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void requestPermissions(Activity activity) {
        ActivityCompat.requestPermissions(activity, requiredPermissions(), REQUEST_WIFI_SCAN);
    }

    public static boolean allGranted(int[] grantResults) {
        if (grantResults == null || grantResults.length == 0) {
            return false;
        }
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    // Ouvre la fiche Réglages de l'app après un refus définitif.
    public static void openAppSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.getPackageName(), null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
