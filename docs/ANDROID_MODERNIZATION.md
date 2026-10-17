# Audit Météo & Citation — 17 octobre 2026

Révision initiale : 62173ae, identique au HEAD GitHub vérifié. Worktree propre,
branche `feature/android-modernization` créée avant modification. Le checkout de
travail est celui de Codex, pas le checkout principal sur D:.

## Diagnostic et architecture existante

Un module Android `app`, Kotlin et Views/XML. La factory construit trois
repositories et un AndroidViewModel qui expose un StateFlow. CityRepository
stocke les villes en SharedPreferences, recherche via Open-Meteo, puis tente trois
géocodeurs inverses. WeatherRepository charge météo/24 h/7 jours/UV et AQI.
QuoteRepository choisit une des trois citations par catégorie météo et persiste
son index. MainActivity affiche les données, les couleurs et les récents, gère
permissions, localisation et recherche. Un AppWidgetProvider recharge la première
ville. Le code de l'Activity mélange une ancienne implémentation autonome et la
nouvelle architecture MVVM : ce checkout ne constitue pas une release compilable.

## Plan avant modification

1. Compilation et API 36 : `MainActivity.kt`, catalog, `app/build.gradle.kts`.
   Supprimer uniquement les fragments obsolètes, conserver interface et fonctions.
   Vérifier assembleDebug. minSdk 24 conservé, applicationId inchangé.
2. Données fiables : repositories, modèles, ViewModel. Réseau borné et annulable,
   parsing testable, fuseau de la ville, absence AQI explicite, persistance robuste.
3. Lifecycle et UX : Activity, localisation, widget, Manifest, ressources.
   Permissions à la demande, recherche indépendante, insets, actualisation,
   widget exécuté par un travail Android durable, confidentialité.
4. Tests et publication : tests JVM/instrumentés, CI, R8, documentation.
   Exécuter tests/lint/bundleRelease puis revue du diff et des limitations.

## Problèmes initiaux

| Gravité | Fichier / partie | Problème et impact | Correction décidée |
|---|---|---|---|
| 🔴 | MainActivity, imports/modèles/refresh | imports ambigus, fonctions imbriquées, symboles absents (`cities`, `ioScope`, `found`), modèles dupliqués : compilation impossible | terminer le branchement MVVM |
| 🔴 | app/build.gradle.kts | target 35 : mise à jour Play non conforme aujourd'hui ; ConstraintLayout utilisé sans dépendance directe | compile/target 36, catalog réellement utilisé |
| 🔴 | CityRepository.loadCities | JSON invalide au démarrage : crash | tolérance aux préférences corrompues |
| 🔴 | QuoteRepository.getQuote | index restauré hors limites : crash | normaliser l'index |
| 🟠 | WeatherRepository / CityRepository | openStream sans timeouts ni politique HTTP, travail bloquant, annulations avalées | transport commun avec délais et annulation |
| 🟠 | WeatherRepository horaires | horloge du téléphone comparée à celle de la ville | utiliser le fuseau de la réponse |
| 🟠 | WeatherRepository AQI | erreur/null convertis en zéro donc air « bon » | valeur absente et affichage indisponible |
| 🟠 | WeatherViewModel.refreshWeather | requêtes concurrentes : réponse ancienne écrase nouvelle ville | annuler la requête précédente |
| 🟠 | MainActivity spinner/recherche | synchronisation seulement sur taille, ajout doublon non sélectionné | état unique, sélection explicite |
| 🟠 | CityRepository.reverseGeocode | endpoint reverse Open-Meteo non documenté, Nominatim sans limitation globale, coordonnées exposées à plusieurs fournisseurs et logs | Geocoder Android avec fallback coordonnées |
| 🟠 | MainActivity localisation | permission au lancement, haute précision inutile, position ancienne acceptée sans limite, token non annulé | coarse à la demande, requête bornée liée au lifecycle |
| 🟠 | WidgetProvider | coroutine globale après retour du receiver, une requête par widget, receiver exporté | travail unique WorkManager et receiver privé |
| 🟠 | Manifest/backup XML | règles non raccordées, sauvegarde des coordonnées implicite | exclusions explicites cloud/transfert |
| 🟠 | tests ViewModel/Gradle | Main dispatcher absent, ApplicationProvider sans runner, org.json production doublonne Android, dépendances instrumentation absentes | corriger environnement tests et scopes |
| 🟠 | CI/ProGuard | uniquement APK debug ; dontwarn coroutines masque les problèmes | tests/lint/release, suppression règle trop large |
| 🟡 | UI/insets | padding remplacé, découpes/IME non traités ; contraste UV jaune/blanc faible ; cibles trop petites | insets complets et contraste/cibles |
| 🟡 | util/quotes | WMO 77 omis, citations attribuées non vérifiées | neige 77 ; validation éditoriale avant publication |
| 🟡 | ressources/README | textes en dur, thèmes divergents, anciennes icônes et README obsolète | nettoyage progressif, documentation réelle |

## Choix proportionnés

Conserver Views/XML, StateFlow, factory explicite, SharedPreferences et java.time
avec desugaring. Pas de Compose, Hilt, Room ni couche use-case. Garder les modèles
uniques dans data/model. Déplacer le transport réseau et le parsing hors écran.
OkHttp apporte ici annulation effective, timeout total, connexions partagées et
tests HTTP ; Retrofit n'est pas nécessaire pour trois GET JSON.

## Exigences vérifiées et sources

- [Google Play target API](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en) : depuis le 31 août 2026, mises à jour mobiles API 36 minimum ; API 35 concerne le maintien de disponibilité d'une app existante, pas sa nouvelle mise à jour.
- [Android 16](https://developer.android.com/about/versions/16/behavior-changes-16) : edge-to-edge obligatoire, grands écrans adaptables ; vérifier retour système, rotation et clavier. Aucun verrouillage d'orientation à ajouter.
- [AGP 8.13](https://developer.android.com/build/releases/agp-8-13-0-release-notes) : AGP 8.13.2/Gradle 8.13, JDK minimum 17, API jusqu'à 36.1. Pas de migration majeure AGP nécessaire.
- [Localisation](https://developer.android.com/develop/sensors-and-location/location/permissions/runtime) : accepter localisation approximative et refus ; aucune localisation de fond nécessaire.
- [Sécurité réseau](https://developer.android.com/privacy-and-security/security-config) : HTTPS, certificats système, aucun trust-all ni HTTP autorisé.
- [Pages 16 Ko](https://developer.android.com/guide/practices/page-sizes) : vérifier l'artefact et ses SDK natifs, pas uniquement le code Kotlin.
- [App Bundle](https://developer.android.com/guide/app-bundle) et [signature](https://developer.android.com/studio/publish/app-signing) : préparer AAB release, identité et signature d'upload existantes ; ne pas créer de nouvelle application.
- [Données utilisateur](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en-GB), [Data safety](https://support.google.com/googleplay/android-developer/answer/10787469?hl=en-GB) : politique accessible dans l'app et URL publique dans Play Console, déclarations cohérentes avec coordonnées transmises, fournisseurs et formulaire de retour.
- [Vérification développeur](https://developer.android.com/developer-verification) : vérifier compte/package dans Play Console ; le dépôt ne prouve pas le statut du compte.
- [Géocodage Open-Meteo](https://open-meteo.com/en/docs/geocoding-api) : search documenté, reverse absent de cette documentation.
- [Conditions Open-Meteo](https://open-meteo.com/en/terms) : API gratuite non commerciale, limites 600/minute, 5000/heure, 10000/jour, attribution CC-BY ; vérifier le contrat si monétisation.

## Fonctionnalités

Maintenant : préserver météo/citations/prévisions/UV/AQI/widget/villes/récents/retour,
ajouter actualisation accessible, erreurs fiables et information confidentialité.
Prochaine version : cache hors ligne horodaté, choix entre villes homonymes, thème
sombre conçu et testé, meilleure grille tablette, enrichissement éditorial citations.
Disproportionné : compte utilisateur, backend propriétaire, suivi GPS permanent,
carte radar lourde, migration Compose complète, architecture multi-module.

## Audit des dépendances et décision

Toutes les dépendances directes sont désormais dans `gradle/libs.versions.toml`.
Le choix est une base stable testée API 36, pas une mise à niveau aveugle vers chaque
dernière majeure. Des versions plus récentes existent : elles ne sont pas nécessaires
pour publier et devront être mises à niveau ensemble avec Kotlin/AGP si leur metadata
l'exige. Ne pas confondre « compilé et testé » avec « dernière version disponible ».

| Dépendance | Initial → retenu | Usage/décision |
|---|---|---|
| AGP | 8.13.2 conservé | compatible API 36/Gradle 8.13 ; éviter migration AGP 9 inutile |
| Kotlin | 2.0.21 conservé | compatible metadata Core 1.17 ; migration majeure reportée |
| Core KTX | 1.13.1 → 1.17.0 | insets, couleurs, permissions ; catalog existant enfin appliqué |
| AppCompat | 1.7.0 → 1.7.1 | Activity/dialogues ; correction stable du catalog |
| Material | 1.12.0 → 1.13.0 | thèmes et boutons XML |
| Activity | activity-ktx 1.9.2 → activity 1.10.1 | Activity Result ; extensions intégrées, pas Compose |
| ConstraintLayout | absent → 2.2.1 | réellement employé dans activity_main.xml |
| Play services Location | 21.3.0 conservé | position actuelle bornée ; version retrouvée dans notes Google |
| Coroutines Android/test | 1.8.1 conservées ensemble | lifecycle, StateFlow, réseau et tests |
| Lifecycle ViewModel/runtime | 2.8.7 conservé | réellement utilisé ; même version explicite |
| Lifecycle LiveData | 2.8.7 supprimé en direct | aucun usage LiveData |
| org.json | 20240303 production → tests seulement | Android fournit org.json en production |
| desugar_jdk_libs | 2.1.5 conservé | java.time sur API 24/25 |
| JUnit | 4.13.2 conservé | assertions métier ; test 2+2 supprimé |
| MockK | 1.13.10 conservé | doubles des repositories/préférences |
| arch core-testing | 2.2.0 supprimé | aucune règle LiveData utilisée |
| AndroidX test core | 1.6.1 conservé | ApplicationProvider avec Robolectric |
| AndroidX JUnit/Espresso | catalog 1.3.0/3.7.0 activé | instrumentation auparavant non configurée |
| Robolectric | 4.14.1 ajouté | ressources et Application dans tests JVM, SDK 28 ciblé pour ces tests |
| OkHttp/MockWebServer | 4.12.0 ajoutés | transport borné, annulation et vrais tests HTTP locaux |
| WorkManager | 2.10.1 ajouté | travail widget durable, sans service foreground ni notification |

Versions plus récentes constatées dans les sources au jour de l'audit :
[Core 1.19.1](https://developer.android.com/jetpack/androidx/releases/core),
[AppCompat 1.8.0](https://developer.android.com/jetpack/androidx/releases/appcompat),
[Activity 1.13.0](https://developer.android.com/jetpack/androidx/releases/activity),
[Lifecycle 2.11.0](https://developer.android.com/jetpack/androidx/releases/lifecycle),
[ConstraintLayout 2.2.2](https://developer.android.com/jetpack/androidx/releases/constraintlayout),
[WorkManager 2.12.0](https://developer.android.com/jetpack/androidx/releases/work),
[Material 1.14.0](https://github.com/material-components/material-components-android/releases/tag/1.14.0).
Les outils de test conservés ne sont pas présentés comme les plus récents ; ils sont
isolés de l'APK release. Aucun remplacement de framework imposé.

## Sécurité : résultats

Aucune clé API, clé privée, keystore, authentification codée en dur ou endpoint HTTP
de production trouvé dans les fichiers suivis audités. Les `http://schemas.android.com`
sont des espaces de noms XML, pas des connexions réseau. Aucun WebView, stockage
externe, composant de compte, SDK publicité/analytics ou Intent entrant de données.
Les Intent sortants utilisent une URL HTTPS fixe ou les paramètres du package ;
PendingIntent du widget explicites et immuables. Activity launcher exportée nécessaire ;
receiver widget désormais non exporté. Suppression des logs de corps de géocodage.
Le contrôle n'est pas une analyse exhaustive de tout l'historique Git ni une preuve
d'absence de vulnérabilité transitive.

## Architecture livrée

Activity : événements utilisateur et affichage ; ForecastRenderer : lignes de
prévisions ; CurrentLocation : adaptation suspendue de la requête Google Play
services ; ViewModel : état/ville/annulation météo ; repositories : données et
persistance ; HttpTransport : HTTP partagé ; Worker : mise à jour de tous les widgets.
Les couleurs restent calculées avec le contexte dans le ViewModel, compromis assumé
pour cette petite application. Recherche et récents restent dans l'Activity et peuvent
être déplacés vers un petit état dédié lors d'une prochaine itération ; aucun framework
d'injection nécessaire. Les modèles dupliqués et l'ancien flux réseau de l'Activity
ont été supprimés, sans retirer les fonctions utilisateur.

## Revue finale et validation

- 42 tests JVM : parsing/fuseau/heure courante/JSON invalide/tableaux incohérents,
  AQI absent, HTTP 429/500, timeout/annulation, préférences, citations, WMO,
  ViewModel, sélection concurrente, géocodeur absent et choix manuel prioritaire.
- 2 tests instrumentés réussis sur émulateur Android 16/API 36 : identité package,
  lancement/recréation et accès recherche sans permission de localisation.
  L'installation Gradle accordait automatiquement la permission : le script dédié
  installe sans `-g`, révoque explicitement puis lance l'instrumentation.
- Requête réelle Open-Meteo Toulouse : HTTP 200, timezone Europe/Paris,
  168 valeurs horaires, 7 jours. Aucun appel avec une position personnelle.
- assembleDebug, testDebugUnitTest, lintDebug et bundleRelease exécutés.
  R8 et resource shrinking activés, pas de dontwarn global coroutines.
- Manifest release inspecté : package inchangé, version 16/6.7, min 24/target 36,
  localisation coarse uniquement, pas de permission foreground service restante.
  WorkManager apporte WAKE_LOCK, ACCESS_NETWORK_STATE et RECEIVE_BOOT_COMPLETED ;
  ses composants exportés système sont protégés par BIND_JOB_SERVICE/DUMP.
- AAB inspecté : aucune bibliothèque native .so. Bundle non signé : signature
  d'upload et acceptation Play restent à vérifier par la propriétaire.
- Capture réelle API 36 revue : météo chargée et insets respectés ; contraste des
  barres système et des icônes prévisions corrigé à la suite de cette revue.
- Lint : zéro erreur, avertissements résiduels de maintenance (versions disponibles,
  textes français en dur, ressources non utilisées, recommandations KTX/icône monochrome).
  Ils ne sont pas masqués. Les icônes historiques inutilisées sont éliminées de
  la release par resource shrinking ; leur nettoyage source reste possible.

Lint signale également, via les métadonnées des dépôts, Location 21.4.0, Kotlin
2.4.21, Coroutines 1.11.0, JSON 20260814, MockK 1.14.11, Robolectric 4.17,
OkHttp/MockWebServer 5.5.0, AndroidX Test Core 1.7.0 et AGP 9.4.1.
La matrice retenue reste volontairement celle effectivement compilée/testée ;
ce travail ne prétend pas avoir adopté toutes ces dernières versions.

Non validés automatiquement : installation par-dessus la version Play signée,
authenticité éditoriale des citations, compte Play, politique publique, Data safety,
matrice complète API 24/tablette/TalkBack et widget sur lanceurs de fabricants.
Voir la checklist de `RELEASE.md` avant production.
