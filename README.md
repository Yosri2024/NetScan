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
- **Système estimé de chaque appareil** (Windows, Android, Apple, Linux, TV, imprimante, box…) : icône, pastille colorée, niveau de confiance et indices retenus
- Détail de chaque appareil : adresse IP, latence, système estimé
- Conseils contextuels et historique des scans

### 🔧 Mode Expert
Pour une analyse plus fine :
- **Plage CIDR** personnalisée (bouton « Détecter » pour la remplir automatiquement)
- **Ports** : préréglage 1-1024, 1-65535, ou liste personnalisée (ex. `22,80,443` ou `1-100,8000-9000`)
- **Timeout** (100 à 10 000 ms, 800 ms par défaut) et **nombre de threads** (1 à 256, 50 par défaut)
- **Identification de l'OS** (interrupteur dans la configuration) : pastille par hôte, puis fiche détaillée au toucher (TTL du ping, bannières, indices un par un)
- Bannières de services (SSH, en-tête HTTP `Server`), résolution de nom, latence
- Configuration **mémorisée** (chiffrée) : un aller-retour Simple ↔ Expert, les réglages ou une rotation ne forcent plus à tout ressaisir
- Historique détaillé et **comparaison** entre deux scans (appareils apparus / disparus)
- **Export JSON ou CSV** via le sélecteur de fichiers Android

---

## ⚙️ Comment ça marche

1. **Détection du réseau** : l'application déduit le sous-réseau à partir de la connexion active.
2. **Découverte des hôtes** : `InetAddress.isReachable`, puis repli sur des connexions TCP (ports 80, 443, 53, 8080, 8443, 22, 445, 9100), car le ping ICMP est souvent filtré sans droits root.
3. **Découverte de services** : mDNS / NSD (AirPlay, Google Cast, imprimantes, SSH, SMB…). On retient le **type** de service et le modèle annoncé, pas seulement le nom.
4. **Identification du système** (modes Simple **et** Expert) : voir ci-dessous.
5. **Scan de ports** (Expert uniquement) : TCP parallélisé avec timeout configurable.
6. **Bannières** : lecture des bannières de service (SSH) et de l'en-tête HTTP `Server`.

### 🖥️ Identification du système d'exploitation

Sans droits root, Android ne permet pas un vrai fingerprinting TCP/IP : le résultat est une **estimation**, toujours accompagnée d'un niveau de confiance (élevée / moyenne / faible) et des indices retenus. Chaque indice ajoute des points à une famille d'OS ; la meilleure famille gagne.

| Indice | Exemple |
|---|---|
| **TTL du ping** (commande système `ping`, sans root) | 64 → Linux/Android/Apple, 128 → Windows, 255 → équipement réseau |
| **Ports « signatures »** | 3389 / 135 → Windows, 62078 → iOS, 9100 → imprimante, 8008 → Chromecast, 5555 → Android (ADB) |
| **Nom d'hôte** | `DESKTOP-xxxx` → Windows, `android-xxxx`, `iPhone`, `raspberrypi` |
| **Services mDNS** | `_airplay` → Apple, `_googlecast` → TV, `_ipp` → imprimante |
| **Bannières** | `OpenSSH … Debian`, `Server: Microsoft-IIS`, `dropbear` |

Les noms courts (`nas`, `cam`, `tv`…) ne sont reconnus qu'en **mot entier** : « banana » n'est plus pris pour un NAS. Un TTL seul ne donne jamais mieux qu'une estimation « possible ». Les cas de certitude sont traités à part : le téléphone qui scanne est un Android connu, et la passerelle est une box ou un routeur. L'OS est conservé dans l'historique et dans les exports JSON/CSV.

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

- **Tests unitaires** (JUnit 4) : calcul de sous-réseaux, validation des saisies, comparaison de scans, alertes, terminologie, informations réseau, **identification d'OS** (score, TTL, faux positifs, persistance), lecture des en-têtes HTTP, analyse du `ping`, noms d'appareils, configuration Expert
- **Tests instrumentés** : chiffrement (`CryptoManager`) et dépôt de scans (`ScanRepository`, dont l'OS conservé dans l'historique)

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

Le `ScanViewModel` est rattaché à l'**application** (`NetScanApp` fournit son `ViewModelStore`) et non à un écran : le scan, sa progression et ses résultats sont partagés par les modes Simple et Expert, et l'enregistrement du scan (historique + « dernier scan ») s'y fait une seule fois.

**Bibliothèques principales** : AndroidX (AppCompat, ConstraintLayout, Lifecycle), Material Components, Room, SQLCipher, DataStore, Gson. Versions centralisées dans `gradle/libs.versions.toml`.

---

## 📦 Télécharger l'APK

Les APK sont disponibles dans l'onglet **[Releases](../../releases)** du dépôt.

---

## 📄 Licence

Distribué sous licence **MIT**. Voir le fichier [LICENSE](LICENSE).
