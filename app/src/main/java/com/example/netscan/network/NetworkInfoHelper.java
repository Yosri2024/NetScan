package com.example.netscan.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;

import com.example.netscan.data.model.NetworkDetails;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

// Fichier : network/NetworkInfoHelper.java
// Rôle : décrit le réseau local courant, quel que soit le support
// (Wi-Fi, Ethernet/câble, USB, Bluetooth, VPN). Remplace WifiInfoHelper :
// un utilisateur câblé n'a ni SSID ni permission Wi-Fi, mais son interface
// Ethernet a une IPv4, une passerelle et un préfixe : le scan fonctionne
// à l'identique.
// Aucune permission n'est requise pour obtenir l'IPv4, la passerelle et le
// préfixe. SSID et niveau de signal ne sont lus qu'en Wi-Fi, et seulement si
// NEARBY_WIFI_DEVICES (13+) ou la localisation (<13) est accordée.
public final class NetworkInfoHelper {

    // Préfixe maximal sondé en mode Simple : au-delà, le scan devient
    // interminable (et sans rapport avec un usage « qui est sur mon réseau »).
    public static final int MAX_AUTO_PREFIX = 24;
    // Borne basse acceptée par SubnetCalculator (65 534 adresses maximum).
    public static final int MIN_AUTO_PREFIX = 16;

    private NetworkInfoHelper() {
    }

    // Toutes les interfaces locales IPv4 exploitables. La première est celle
    // du réseau par défaut, c'est-à-dire celui que voit l'utilisateur.
    public static List<NetworkDetails> getAll(Context context) {
        List<NetworkDetails> found = new ArrayList<>();
        ConnectivityManager cm = connectivity(context);
        if (cm == null) {
            return found;
        }
        Network active = cm.getActiveNetwork();
        NetworkDetails activeDetails = describe(context, cm, active);
        if (activeDetails != null && activeDetails.isUsable()) {
            found.add(activeDetails);
        }
        // Puis les autres interfaces : Ethernet branchée en plus du Wi-Fi, USB, VPN...
        for (Network network : cm.getAllNetworks()) {
            if (network.equals(active)) {
                continue;
            }
            NetworkDetails details = describe(context, cm, network);
            if (details != null && details.isUsable()) {
                found.add(details);
            }
        }
        return found;
    }

    // Réseau à scanner par défaut : le réseau par défaut s'il est exploitable,
    // sinon Ethernet, USB, Wi-Fi, etc.
    public static NetworkDetails getDetails(Context context) {
        NetworkDetails best = null;
        for (NetworkDetails candidate : getAll(context)) {
            if (best == null || priority(candidate) < priority(best)) {
                best = candidate;
            }
        }
        return best;
    }

    // Plage CIDR à scanner depuis les détails du réseau : préfixe réel de
    // l'interface, plafonné à /24 (mode Simple) et encadré par les bornes
    // acceptées. Null si aucune IPv4.
    public static String getScanCidr(NetworkDetails details) {
        if (details == null || details.getIpAddress() == null) {
            return null;
        }
        int prefix = details.getPrefixLength();
        if (prefix <= 0) {
            prefix = MAX_AUTO_PREFIX;
        }
        prefix = Math.max(MIN_AUTO_PREFIX, Math.min(prefix, MAX_AUTO_PREFIX));
        try {
            return SubnetCalculator.cidrFromIp(details.getIpAddress(), prefix);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Ethernet avant Wi-Fi quand aucune route par défaut ne tranche.
    private static int priority(NetworkDetails details) {
        switch (details.getTransport()) {
            case ETHERNET:
                return 0;
            case USB:
                return 1;
            case BLUETOOTH:
                return 2;
            case WIFI:
                return 3;
            case VPN:
                return 4;
            case CELLULAR:
                return 5;
            default:
                return 6;
        }
    }

    // Décrit une interface réseau, ou null si elle n'a pas d'IPv4 exploitable.
    private static NetworkDetails describe(Context context, ConnectivityManager cm, Network network) {
        if (network == null) {
            return null;
        }
        NetworkCapabilities caps;
        LinkProperties link;
        try {
            caps = cm.getNetworkCapabilities(network);
            link = cm.getLinkProperties(network);
        } catch (SecurityException | IllegalArgumentException e) {
            return null;
        }
        if (caps == null || link == null) {
            return null;
        }
        LinkAddress local = firstIpv4(link);
        if (local == null || !isLocalNetwork(caps, local)) {
            return null;
        }
        String ip = local.getAddress().getHostAddress();
        NetworkDetails.Transport transport = transportOf(caps);
        String gateway = gatewayOf(link, local);
        String dns = dnsOf(link);

        String ssid = null;
        int rssi = -1;
        String security = securityLabel(transport);
        if (transport == NetworkDetails.Transport.WIFI) {
            WifiInfo info = wifiInfo(context, caps);
            if (info != null) {
                ssid = cleanSsid(info.getSSID());
                rssi = info.getRssi();
                security = securityName(info);
            }
        }
        return new NetworkDetails(transport, link.getInterfaceName(), ssid, ip,
                local.getPrefixLength(), gateway, dns, rssi, security);
    }

    // Un réseau local est retenu s'il porte du trafic IP. Exiger
    // NET_CAPABILITY_INTERNET exclurait un LAN sans Internet (câble vers un
    // routeur hors ligne) : on accepte aussi les adresses privées, ce qui
    // écarte au passage le réseau mobile (IP publique, hors périmètre).
    private static boolean isLocalNetwork(NetworkCapabilities caps, LinkAddress local) {
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return true;
        }
        return isPrivateAddress(local.getAddress());
    }

    // 10/8, 172.16/12, 192.168/16, 169.254/16 : réseaux privés ou
    // auto-configurés. Pur Java, testable sans téléphone.
    static boolean isPrivateAddress(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] octets = address.getAddress();
        int b0 = octets[0] & 0xFF;
        int b1 = octets[1] & 0xFF;
        if (b0 == 10) {
            return true;
        }
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return true;
        }
        if (b0 == 192 && b1 == 168) {
            return true;
        }
        return b0 == 169 && b1 == 254;
    }

    private static NetworkDetails.Transport transportOf(NetworkCapabilities caps) {
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return NetworkDetails.Transport.ETHERNET;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return NetworkDetails.Transport.WIFI;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)) {
            return NetworkDetails.Transport.USB;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
            return NetworkDetails.Transport.BLUETOOTH;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return NetworkDetails.Transport.CELLULAR;
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return NetworkDetails.Transport.VPN;
        }
        return NetworkDetails.Transport.OTHER;
    }

    private static LinkAddress firstIpv4(LinkProperties link) {
        for (LinkAddress address : link.getLinkAddresses()) {
            InetAddress inet = address.getAddress();
            if (inet instanceof Inet4Address && !inet.isLoopbackAddress()) {
                return address;
            }
        }
        return null;
    }

    // Passerelle : route par défaut, sinon route du réseau local.
    private static String gatewayOf(LinkProperties link, LinkAddress local) {
        for (RouteInfo route : link.getRoutes()) {
            if (route.isDefaultRoute()) {
                String gateway = gatewayOf(route);
                if (gateway != null) {
                    return gateway;
                }
            }
        }
        for (RouteInfo route : link.getRoutes()) {
            if (isLocalRoute(route, local)) {
                String candidate = gatewayOf(route);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String gatewayOf(RouteInfo route) {
        if (route == null) {
            return null;
        }
        InetAddress gateway = route.getGateway();
        if (gateway instanceof Inet4Address && !gateway.isLoopbackAddress()) {
            return gateway.getHostAddress();
        }
        return null;
    }

    // La route appartient-elle au préfixe de notre adresse ?
    private static boolean isLocalRoute(RouteInfo route, LinkAddress local) {
        if (local == null || local.getAddress() == null || route == null) {
            return false;
        }
        InetAddress gateway = route.getGateway();
        if (gateway == null) {
            return false;
        }
        byte[] a = gateway.getAddress();
        byte[] b = local.getAddress().getAddress();
        int fullBytes = local.getPrefixLength() / 8;
        if (a.length != b.length || fullBytes <= 0 || fullBytes > a.length) {
            return false;
        }
        for (int i = 0; i < fullBytes; i++) {
            if (a[i] != b[i]) {
                return false;
            }
        }
        return true;
    }

    private static String dnsOf(LinkProperties link) {
        for (InetAddress server : link.getDnsServers()) {
            if (server instanceof Inet4Address && !server.isLoopbackAddress()) {
                return server.getHostAddress();
            }
        }
        return null;
    }

    // Infos Wi-Fi : NetworkCapabilities d'abord (API 29+), WifiManager sinon.
    // Sans permission, renvoie null : le scan reste possible, seul le SSID manque.
    private static WifiInfo wifiInfo(Context context, NetworkCapabilities caps) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && caps.getTransportInfo() instanceof WifiInfo) {
            return (WifiInfo) caps.getTransportInfo();
        }
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null || !wm.isWifiEnabled()) {
                return null;
            }
            WifiInfo info = wm.getConnectionInfo();
            if (info == null || info.getNetworkId() == -1) {
                return null;
            }
            return info;
        } catch (RuntimeException e) {
            // Pas de permission Wi-Fi : seul le SSID manque, le scan reste possible.
            return null;
        }
    }

    private static ConnectivityManager connectivity(Context context) {
        return (ConnectivityManager) context.getApplicationContext()
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    // L'API entoure le SSID de guillemets ; "<unknown ssid>" -> null.
    private static String cleanSsid(String raw) {
        if (raw == null || raw.contains("unknown ssid")) {
            return null;
        }
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        return raw;
    }

    private static String securityLabel(NetworkDetails.Transport transport) {
        switch (transport) {
            case ETHERNET:
                return "Filaire (Ethernet)";
            case USB:
                return "Connexion USB";
            case BLUETOOTH:
                return "Bluetooth";
            case VPN:
                return "Tunnel VPN";
            case CELLULAR:
                return "Cellulaire";
            default:
                return "Information indisponible";
        }
    }

    // Type de sécurité Wi-Fi via getCurrentSecurityType (API 33+).
    // Avant : "information indisponible" (repli selon la version Android).
    private static String securityName(WifiInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            switch (info.getCurrentSecurityType()) {
                case WifiInfo.SECURITY_TYPE_OPEN:
                    return "Ouvert";
                case WifiInfo.SECURITY_TYPE_WEP:
                    return "WEP";
                case WifiInfo.SECURITY_TYPE_PSK:
                    return "WPA/WPA2";
                case WifiInfo.SECURITY_TYPE_SAE:
                    return "WPA3";
                case WifiInfo.SECURITY_TYPE_EAP:
                case WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
                    return "Entreprise";
                default:
                    return "Information indisponible";
            }
        }
        return "Information indisponible";
    }
}