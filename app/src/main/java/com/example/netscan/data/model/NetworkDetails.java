package com.example.netscan.data.model;

import java.util.Locale;

// Fichier : data/model/NetworkDetails.java
// Rôle : informations sur le réseau local utilisé pour le scan.
// Indépendant du support : Wi-Fi, Ethernet (câble), USB, Bluetooth, VPN.
// Les champs Wi-Fi seuls (ssid, rssiDbm, securityType) restent nuls/-1 sur
// les autres supports. Aucun secret : ni MAC, ni identifiant matériel.
public class NetworkDetails {

    // Support physique du réseau. NONE = aucun réseau local exploitable.
    public enum Transport {
        WIFI("Wi-Fi"),
        ETHERNET("Ethernet"),
        USB("USB"),
        BLUETOOTH("Bluetooth"),
        CELLULAR("Cellulaire"),
        VPN("VPN"),
        OTHER("Réseau"),
        NONE("Aucun réseau");

        private final String label;

        Transport(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private final Transport transport;
    // Nom de l'interface noyau ("wlan0", "eth0"...), informatif.
    private final String interfaceName;
    // Nom du réseau Wi-Fi ; null sur les autres supports ou si la permission
    // de localisation / NEARBY_WIFI_DEVICES manque.
    private final String ssid;
    private final String ipAddress;
    // Longueur de préfixe réelle de l'interface (24 pour un /24 classique).
    private final int prefixLength;
    private final String gateway;
    private final String dns;
    // Puissance du signal Wi-Fi en dBm, -1 si inconnue (non Wi-Fi).
    private final int rssiDbm;
    private final String securityType;

    public NetworkDetails(Transport transport, String interfaceName, String ssid,
                          String ipAddress, int prefixLength, String gateway,
                          String dns, int rssiDbm, String securityType) {
        this.transport = transport == null ? Transport.NONE : transport;
        this.interfaceName = interfaceName;
        this.ssid = ssid;
        this.ipAddress = ipAddress;
        this.prefixLength = prefixLength;
        this.gateway = gateway;
        this.dns = dns;
        this.rssiDbm = rssiDbm;
        this.securityType = securityType;
    }

    public Transport getTransport() {
        return transport;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getSsid() {
        return ssid;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public String getGateway() {
        return gateway;
    }

    public String getDns() {
        return dns;
    }

    public int getRssiDbm() {
        return rssiDbm;
    }

    public String getSecurityType() {
        return securityType;
    }

    public boolean isWifi() {
        return transport == Transport.WIFI;
    }

    // Vrai si un scan est possible : une IPv4 locale a été trouvée.
    public boolean isUsable() {
        return transport != Transport.NONE && ipAddress != null;
    }

    // Nom du réseau affiché : SSID en Wi-Fi, sinon le support + l'interface.
    public String getDisplayName() {
        if (!isUsable()) {
            return transport.getLabel();
        }
        if (isWifi() && ssid != null && !ssid.isEmpty()) {
            return ssid;
        }
        if (interfaceName != null && !interfaceName.isEmpty()) {
            return transport.getLabel() + " (" + interfaceName.toLowerCase(Locale.ROOT) + ")";
        }
        return transport.getLabel();
    }

    // "Wi-Fi", "Ethernet", ... pour les messages d'interface.
    public String getTransportLabel() {
        return transport.getLabel();
    }
}