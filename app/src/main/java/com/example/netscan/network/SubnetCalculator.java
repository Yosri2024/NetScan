package com.example.netscan.network;

import java.util.ArrayList;
import java.util.List;

// Fichier : network/SubnetCalculator.java
// Rôle : calcule les adresses d'un sous-réseau IPv4 (Étape 2).
// Pur Java : testé par SubnetCalculatorTest, sans téléphone.
public final class SubnetCalculator {

    // Refuse au-delà de /16 (plus de 65 534 hôtes : trop long et risqué).
    public static final int MIN_PREFIX_LENGTH = 16;

    private SubnetCalculator() {
    }

    // Liste les adresses à sonder pour "192.168.1.0/24".
    // Exclut l'adresse réseau et le broadcast (/31 : 2 hôtes, /32 : 1 hôte).
    public static List<String> listHosts(String cidr) {
        if (cidr == null) {
            throw new IllegalArgumentException("CIDR nul.");
        }
        String[] parts = cidr.trim().split("/", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("CIDR attendu (ex. 192.168.1.0/24).");
        }
        int base = ipToInt(parts[0].trim());
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Préfixe invalide : " + parts[1]);
        }
        if (prefix < MIN_PREFIX_LENGTH || prefix > 32) {
            throw new IllegalArgumentException("Préfixe refusé (/" + MIN_PREFIX_LENGTH + " à /32 exigé).");
        }
        int mask = prefix == 0 ? 0 : (0xFFFFFFFF << (32 - prefix));
        int network = base & mask;
        int broadcast = network | ~mask;
        List<String> hosts = new ArrayList<>();
        if (prefix == 32) {
            hosts.add(intToIp(network));
        } else if (prefix == 31) {
            hosts.add(intToIp(network));
            hosts.add(intToIp(broadcast));
        } else {
            for (int ip = network + 1; ip < broadcast; ip++) {
                hosts.add(intToIp(ip));
            }
        }
        return hosts;
    }

    // Construit "192.168.1.0/24" à partir de l'IP du téléphone (bouton Détecter).
    public static String cidrFromIp(String ip, int prefix) {
        int addr = ipToInt(ip);
        int mask = 0xFFFFFFFF << (32 - prefix);
        return intToIp(addr & mask) + "/" + prefix;
    }

    // "192.168.1.10" -> entier 32 bits. Rejette l'IPv6 (hors périmètre).
    public static int ipToInt(String ip) {
        if (ip == null || ip.contains(":")) {
            throw new IllegalArgumentException("IPv4 attendue : " + ip);
        }
        String[] bytes = ip.trim().split("\\.", -1);
        if (bytes.length != 4) {
            throw new IllegalArgumentException("Adresse IPv4 invalide : " + ip);
        }
        int value = 0;
        for (String b : bytes) {
            int octet;
            try {
                octet = Integer.parseInt(b);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Adresse IPv4 invalide : " + ip);
            }
            if (octet < 0 || octet > 255) {
                throw new IllegalArgumentException("Adresse IPv4 invalide : " + ip);
            }
            value = (value << 8) | octet;
        }
        return value;
    }

    public static String intToIp(int value) {
        return ((value >>> 24) & 0xFF) + "."
                + ((value >>> 16) & 0xFF) + "."
                + ((value >>> 8) & 0xFF) + "."
                + (value & 0xFF);
    }
}
