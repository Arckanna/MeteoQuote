# Publier la mise à jour 6.7

## Identité et outils

- Application existante : `com.valerie.meteoquote` (inchangé).
- Version préparée : `versionCode = 16`, `versionName = 6.7`.
  **Vérifier dans Play Console tous les tracks** : 16 doit être strictement supérieur
  au plus grand code déjà téléversé. Le dépôt ne permet pas de connaître cette valeur.
- minSdk 24 : Android 7.0 conservé. compileSdk 36 permet la compilation des API
  récentes ; targetSdk 36 active les comportements Android 16. Aucun de ces deux
  derniers paramètres ne nécessite de relever minSdk.
- Gradle Wrapper 8.13, AGP 8.13.2, Kotlin 2.0.21 ; JDK 17 recommandé (CI).
  La machine d'audit utilise JDK 21.0.1 ; bytecode source/cible 17 et desugaring
  2.1.5. SDK platform 36, Build Tools 35.0.0. `local.properties` reste local.

## Commandes Windows

Depuis le worktree `feature/android-modernization` :

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

Le bundle Gradle est **non signé** : aucune clé de production n'a été lue ou modifiée.
Pour produire l'artefact de publication, ouvrir ce worktree dans Android Studio,
Build → Generate Signed Bundle / APK → Android App Bundle → module app → choisir
**le keystore et l'alias d'upload existants** → release. Ne pas choisir Create new.
Conserver les mots de passe hors Git. Vérifier le certificat d'upload contre
Play Console → Intégrité de l'application. Play App Signing peut employer une
autre clé pour les APK distribués : ne pas confondre clé d'upload et clé de signature.

## Play Console

1. Ouvrir **Météo & Citation existante**, ne pas créer une application.
2. Vérifier statut des règles, compte développeur, package enregistré et éventuelles
   demandes propres au compte. Target 36 répond à l'exigence générale des mises à jour
   au 17/10/2026, mais ne constitue pas une approbation de publication.
3. Publier une politique de confidentialité sur une URL HTTPS publique stable et
   renseigner cette URL dans Play Console. Le texte embarqué explique les flux réels ;
   faire valider le contact et les durées de conservation chez les fournisseurs.
4. Réviser Data safety : coordonnées facultatives transmises à Open-Meteo et au
   géocodeur Android ; villes recherchées et données du formulaire Google Forms.
   Vérifier aussi les déclarations SDK Google Play services. Ne pas déclarer
   « aucune donnée collectée » uniquement parce qu'aucun compte n'existe.
5. Vérifier audience, classification, absence de publicité et accès à l'application.
   Pas de compte ni paiement intégré, pas de permission de localisation en arrière-plan.
6. Créer une release de test interne, téléverser l'AAB signé et les notes 6.7.
   Conserver le mapping R8 ; vérifier que Play reçoit les informations de désobfuscation.
7. Lire les contrôles Play : API, versionCode, certificat, permissions fusionnées,
   SDK, compatibilité 16 Ko. Faire le rapport pré-lancement et tester une **mise à jour**
   depuis la version installée via Play (préférences/villes/widget préservés).
8. Après validation, promouvoir vers production avec déploiement progressif,
   surveiller Android Vitals/crashes/ANR, puis élargir le déploiement.

## Vérifications avant production

- [ ] versionCode supérieur à tous les codes Play, certificat d'upload correct.
- [ ] URL publique de confidentialité publiée ; contact et Data safety validés.
- [x] Usage non commercial Open-Meteo confirmé ou abonnement adapté ; attribution visible.
- [x] Test appareil API 24, API 35/36, rotation, tablette, découpe, clavier, navigation gestes/3 boutons.
- [ ] Refus simple et définitif, localisation désactivée, permission révoquée, absence Play services.
- [x] Mode avion, serveur lent, villes homonymes, clics rapides, réouverture et widget après redémarrage.
- [ ] TalkBack, police 200 %, contraste, textes et citations/attributions vérifiés.
- [ ] Test interne de l'AAB signé et rapport pré-lancement acceptables ; aucune alerte Play restante.

## Limites assumées

Pas de cache météo persistant : hors ligne, message d'erreur et recherche réessayable,
sans météo inventée. Les récents restent un historique ; retirer une ville de la liste
ne constitue pas un effacement de toutes les données (effacement complet via Android).
Le Geocoder pré-API 33 reste une API bloquante exécutée sur IO, sans garantie de délai
du fournisseur. Une prochaine évolution peut remplacer cet enrichissement par un
service inverse contractuellement adapté. L'interface reste française et claire ;
pas de promesse de traduction ou thème sombre complet dans cette release.
