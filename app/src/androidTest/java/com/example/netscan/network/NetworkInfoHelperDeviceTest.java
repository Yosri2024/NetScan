package com.example.netscan.network;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.ConnectivityManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.netscan.data.model.NetworkDetails;

import org.junit.Test;
import org.junit.runner.RunWith;

// Fichier : androidTest/.../network/NetworkInfoHelperDeviceTest.java
// Rôle : le scan ne dépend plus du Wi-Fi. Sur un appareil RÉELEMENT
// connecté (Wi-Fi, Ethernet, USB...), la détection doit toujours produire
// une IPv4 et une plage CIDR. Sans permission Wi-Fi granted, le scan
// fonctionne quand même : seul le SSID manque.
// TEST SUR TÉLÉPHONE : ./gradlew connectedDebugAndroidTest.
@RunWith(AndroidJUnit4.class)
public class NetworkInfoHelperDeviceTest {

    @Test
    public void interface_locale_toujours_exploitable() {
        Context context = ApplicationProvider.getApplicationContext();
        ConnectivityManager cm = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assertNotNull(cm);

        NetworkDetails details = NetworkInfoHelper.getDetails(context);
        // Aucun réseau local : pas deDétection, mais aucune exception.
        assertNotNull(details);
        if (cm.getActiveNetwork() == null && details.getIpAddress() == null) {
            assertFalse(details.isUsable());
            assertNull(NetworkInfoHelper.getScanCidr(details));
            return;
        }
        // Un réseau existe : il doit être décrit et scannable.
        assertTrue(details.getTransport() != NetworkDetails.Transport.NONE);
        assertTrue(details.isUsable());
        assertNotNull(details.getIpAddress());
        assertNotNull(details.getInterfaceName());
        assertNotNull(NetworkInfoHelper.getScanCidr(details));
        assertTrue(NetworkInfoHelper.getScanCidr(details).contains("/"));
        // Hors Wi-Fi, le SSID n'a pas de sens (Ethernet, USB, VPN).
        if (!details.isWifi()) {
            assertNull(details.getSsid());
        }
    }
}