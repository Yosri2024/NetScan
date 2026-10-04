# 📡 NetScan

> Scanner de réseau local pour Android — simple, sécurisé et 100 % hors ligne.

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84)
![Java](https://img.shields.io/badge/Java-17-blue)
![Min SDK](https://img.shields.io/badge/minSdk-24-informational)
![Target SDK](https://img.shields.io/badge/targetSdk-36-informational)
![License](https://img.shields.io/badge/License-MIT-yellow.svg)

**NetScan** détecte les appareils connectés à votre réseau local (Wi-Fi, Ethernet, USB tethering, VPN…) et affiche leurs ports ouverts. Pas de compte, pas de cloud, pas de publicité : tout reste sur votre téléphone.

> ⚠️ **Usage responsable** : ne scannez que les réseaux qui vous appartiennent ou pour lesquels vous avez une autorisation explicite.

---

## 📱 Deux modes

### 🟢 Mode Simple (par défaut)
Pensé pour un usage quotidien :
- Un bouton : **« Scanner mon réseau »**
- Résultats lisibles avec codes couleur (vert = appareil connu, orange = à vérifier)
- Détail de chaque appareil : adresse IP, ports ouverts, latence
- Conseils contextuels et historique des scans

### 🔧 Mode Expert
Pour une analyse plus fine :
- **Plage CIDR** personnalisée (bouton « Détecter » pour la remplir automatiquement)
- **Ports** : préréglage 1-1024, 1-65535, ou liste personnalisée (ex. `22,80,443` ou `1-100,8000-9000`)
- **Timeout** (100 à 10 000 ms, 800 ms par défaut) et **nombre de threads** (1 à 256, 50 par défaut)
- Bannières de services, résolution de nom, latence
- Historique détaillé et **comparaison** entre deux scans (appareils apparus / disparus)
- **Export JSON ou CSV** via le sélecteur de fichiers Android

---

## ⚙️ Comment ça marche

1. **Détection du réseau** : l'application déduit le sous-réseau à partir de la connexion active.
2. **Découverte des hôtes** : `InetAddress.isReachable`, puis repli sur des connexions TCP (ports 80, 443, 53, 8080, 8443, 22, 445, 9100), car le ping ICMP est souvent filtré sans droits root.
3. **Découverte de services** : mDNS / NSD pour identifier certains appareils par leur nom.
4. **Scan de ports** (optionnel en mode Simple) : TCP parallélisé avec timeout configurable.
5. **Bannières** : lecture des bannières de service quand elles sont disponibles.

Limites : à partir d'Android 10, l'accès aux adresses MAC et à `/proc/net/arp` est restreint. Les noms d'appareils reposent donc sur le DNS et mDNS, et peuvent être approximatifs. Les plages plus larges que `/16` sont refusées.

---

## 🔐 Sécurité et confidentialité

- **Aucune donnée n'est envoyée** : pas de compte, pas d'analytics, pas de télémétrie
- **Trafic en clair interdit** (`network_security_config.xml`)
- **Historique chiffré** : Room + SQLCipher, passphrase protégée par l'Android Keystore
- **Préférences chiffrées** : DataStore + AES-GCM
- **Validation stricte** des saisies (CIDR, ports, timeout, threads)
- `allowBackup=false`, une seule activité exportée, **R8 / ProGuard activé** en release
- Permissions minimales. La localisation (Android < 13) ou `NEARBY_WIFI_DEVICES` (Android 13+) ne servent **qu'à lire le nom du réseau Wi-Fi (SSID)** : le scan fonctionne sans elles.

Un écran d'avertissement légal est affiché au premier lancement.

---

## 🛠️ Prérequis

| Outil | Version |
|-------|---------|
| Android Studio | Version récente compatible AGP 9.1 |
| JDK | 17 |
| Android SDK | API 36 |
| Appareil / émulateur | Android 7.0+ (API 24) |

## 🚀 Installation

```bash
git clone https://github.com/Yosri2024/NetScan.git
cd NetScan
```

1. Ouvrez le dossier dans **Android Studio** (`File > Open`).
2. Attendez la synchronisation Gradle.
3. Lancez l'application sur un appareil ou un émulateur.

En ligne de commande :

```bash
./gradlew assembleDebug        # APK de debug
./gradlew assembleRelease      # APK release (à signer avec votre keystore)
./gradlew test                 # tests unitaires
./gradlew connectedAndroidTest # tests instrumentés (appareil requis)
```

L'APK de debug se trouve dans `app/build/outputs/apk/debug/`.

---

## 🧪 Tests

- **Tests unitaires** (JUnit 4) : calcul de sous-réseaux, validation des saisies, comparaison de scans, alertes, terminologie, informations réseau
- **Tests instrumentés** : chiffrement (`CryptoManager`) et dépôt de scans (`ScanRepository`)

---

## 🧱 Architecture

Architecture **MVVM** en Java, interface en XML avec ViewBinding.

```
app/src/main/java/com/example/netscan/
├── alerts/      # Alertes et conseils
├── data/        # Repositories, export, base Room (db/), modèles (model/)
├── mode/        # Modes Simple / Expert et thème (clair / sombre / système)
├── network/     # Découverte, scan de ports, bannières, NSD, calcul CIDR
├── security/    # Keystore, chiffrement, clé de base, validation des entrées
├── ui/          # Écrans : common/, simple/, expert/
├── utils/       # Formats de date, nommage d'appareils, comparaison de scans
└── viewmodel/   # ScanViewModel, HistoryViewModel, SettingsViewModel
```

**Bibliothèques principales** : AndroidX (AppCompat, ConstraintLayout, Lifecycle), Material Components, Room, SQLCipher, DataStore, Gson. Versions centralisées dans `gradle/libs.versions.toml`.

---

## 📦 Télécharger l'APK

Les APK sont disponibles dans l'onglet **[Releases](../../releases)** du dépôt.

---

## 📄 Licence

Distribué sous licence **MIT**. Voir le fichier [LICENSE](LICENSE).
