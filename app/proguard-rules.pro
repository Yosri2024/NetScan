# Fichier : NetScan/app/proguard-rules.pro
# Rôle : règles R8/ProGuard pour la release (Étape 1, complété aux étapes 4-5).

# Garder les modèles Room/Gson (packages créés aux étapes suivantes).
-keep class com.example.netscan.data.model.** { *; }
# Gson utilise la réflexion sur les champs annotés.
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
