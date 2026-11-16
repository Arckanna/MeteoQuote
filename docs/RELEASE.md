# Préparer une publication de Météo & Citation

Guide relu le 8 novembre 2026 à partir du dépôt local. Ce document ne certifie pas une publication ni une validation Play. Reprendre la checklist pour chaque artefact à diffuser.

## Identité et outils

- Application existante : `com.valerie.meteoquote` (inchangé).
- Version déclarée dans [app/build.gradle.kts](../app/build.gradle.kts) : `versionCode = 22`, `versionName = 7.4`.
  **Vérifier dans Play Console tous les canaux** avant une nouvelle livraison : ne pas supposer que le code 22 est encore disponible. Le dépôt ne permet pas de connaître le plus grand code déjà téléversé.
- minSdk 24 : Android 7.0 conservé. compileSdk 36 permet la compilation des API
  récentes ; targetSdk 36 active les comportements Android 16. Aucun de ces deux
  derniers paramètres ne nécessite de relever minSdk.
- Gradle Wrapper 8.13, AGP 8.13.2, Kotlin 2.0.21 ; JDK 17 recommandé (CI).
  Bytecode source/cible 17 et desugaring 2.1.5. SDK platform 36, Build Tools 35.0.0 installés en CI. Configurer `JAVA_HOME` et `ANDROID_HOME`, ou `sdk.dir` dans `local.properties`, qui reste local.

## Commandes Windows

Depuis la racine du checkout contenant la version à publier :

```powershell
.\gradlew.bat --version
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest bundleRelease --console=plain
.\scripts\test-emulator.ps1 -Serial emulator-5554
```

La dernière commande nécessite un émulateur **de test** démarré. Le script révoque
explicitement la permission que Gradle accorde automatiquement lors de `connectedDebugAndroidTest`.
Ne pas exécuter les tests sur l'installation personnelle
publiée : l'identifiant est volontairement le même.

Rapports : `app/build/reports/tests/testDebugUnitTest/`,
`app/build/reports/lint-results-debug.html`, `app/build/reports/androidTests/connected/`.
AAB : `app/build/outputs/bundle/release/app-release.aab`.
Mapping R8 : `app/build/outputs/mapping/release/mapping.txt`.

La configuration Gradle actuelle ne définit pas de signature de production : le bundle release généré est **non signé**.
Pour produire l'artefact de publication, ouvrir ce checkout dans Android Studio,
Build → Generate Signed Bundle / APK → Android App Bundle → module app → choisir
**le keystore et l'alias d'upload existants** → release. Ne pas choisir Create new.
Conserver les mots de passe hors Git. Vérifier le certificat d'upload contre
Play Console → Intégrité de l'application. Play App Signing peut employer une
autre clé pour les APK distribués : ne pas confondre clé d'upload et clé de signature.

## Play Console

1. Ouvrir **Météo & Citation existante**, ne pas créer une application.
2. Vérifier statut des règles, compte développeur, package enregistré et éventuelles
   demandes propres au compte. Vérifier les exigences en vigueur dans la console au moment de la livraison ; la cible 36 déclarée dans le dépôt ne constitue pas une approbation de publication.
3. Publier une politique de confidentialité sur une URL HTTPS publique stable et
   renseigner cette URL dans Play Console. Le texte embarqué explique les flux réels ;
   faire valider le contact et les durées de conservation chez les fournisseurs.
4. Réviser Data safety : coordonnées facultatives transmises à Open-Meteo et au
   géocodeur Android ; villes recherchées et données du formulaire Google Forms.
   Vérifier aussi les déclarations SDK Google Play services. Ne pas déclarer
   « aucune donnée collectée » uniquement parce qu'aucun compte n'existe.
5. Vérifier audience, classification, absence de publicité et accès à l'application.
   Pas de compte ni paiement intégré, pas de permission de localisation en arrière-plan.
6. Créer une release de test interne, téléverser l'AAB signé et les notes correspondant à la version réellement construite.
   Conserver le mapping R8 ; vérifier que Play reçoit les informations de désobfuscation.
7. Lire les contrôles Play : API, versionCode, certificat, permissions fusionnées,
   SDK, compatibilité 16 Ko. Faire le rapport pré-lancement et tester une **mise à jour**
   depuis la version installée via Play (préférences/villes/widget préservés).
8. Après validation, promouvoir vers production avec déploiement progressif,
   surveiller Android Vitals/crashes/ANR, puis élargir le déploiement.

## Vérifications avant production

- [ ] versionCode supérieur à tous les codes Play, certificat d'upload correct.
- [ ] URL publique de confidentialité publiée ; contact et Data safety validés.
- [ ] Usage et contrat Open-Meteo vérifiés pour cette diffusion ; attribution visible.
- [ ] Test appareil API 24, API 35/36, rotation, tablette, découpe, clavier, navigation gestes/3 boutons.
- [ ] Refus simple et définitif, localisation désactivée, permission révoquée, absence Play services.
- [ ] Mode avion, serveur lent, villes homonymes, clics rapides, réouverture et widget après redémarrage.
- [ ] Cache Room et préférences conservés lors de la mise à jour ; schéma et migration vérifiés si le stockage change.
- [ ] Recherche lente puis autre sélection ; widget A en cours puis B sans cache ; demande forcée après une demande automatique.
- [ ] TalkBack, police 200 %, contraste, textes et citations/attributions vérifiés.
- [ ] Test interne de l'AAB signé et rapport pré-lancement acceptables ; aucune alerte Play restante.

## Limites assumées

Le cache Room permet une consultation hors ligne après un premier chargement, pour le même lieu et tant que les données restent utilisables. Sans cache, ou après son expiration à sept jours, aucune météo n'est inventée. L'AQI possède sa propre fraîcheur. Voir [le cache livré](LOT_C_LIVRAISON.md).

Les récents restent un historique ; retirer une ville de la liste
ne constitue pas un effacement de toutes les données (effacement complet via Android).
Le Geocoder pré-API 33 reste une API bloquante exécutée sur IO, sans garantie de délai
du fournisseur. Une prochaine évolution peut remplacer cet enrichissement par un
service inverse contractuellement adapté. L'interface reste française et claire ;
pas de promesse de traduction ou thème sombre complet dans cette release.

La CI exécute les tests unitaires, lint et les builds debug/tests/release ; son job instrumenté ne lance que `OfflineCacheTest` sur API 24 et 36. Les tests de concurrence du widget sont des tests Robolectric/WorkManager : ils ne remplacent pas la recette des lanceurs et restrictions batterie. Les résultats historiques des autres documents ne cochent pas cette checklist.

Le défaut de fraîcheur à l'attribution quotidienne reste ouvert dans [la revue du code](REVUE_CODE_2026-11-02.md) ; le qualifier avant diffusion. Ne pas présenter la revue documentaire comme sa correction.
