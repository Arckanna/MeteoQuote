# Lot C — Préparation du cache Room et de la consultation hors ligne

Date : 22 octobre 2026. Statut : spécification de référence ; voir [implémentation et validation du lot C](LOT_C_LIVRAISON.md) pour le périmètre livré et les adaptations de conception.

Référence locale : commit `2668e11`, branche `codex/fiabilite-aqi-localisation`. Cette préparation ne présume pas que la fusion dans master a été effectuée. Au démarrage du développement, vérifier la base de branche et conserver les correctifs B02/B03.

## Résultat attendu

Après une consultation réussie, l’utilisateur retrouve la météo du même lieu après redémarrage en mode avion. Une actualisation échouée conserve les dernières données avec leur âge. Le widget lit la même source et conserve une citation quotidienne utilisable sans réseau. Une ville sans cache n’affiche jamais la météo de la ville précédente.

Périmètre : cache météo multi-lieu, fraîcheur, erreurs non destructives, reprise du widget, déduplication et conservation des préférences. Les notifications, la migration Compose/Glance, une nouvelle recherche de villes et un lieu différent par widget restent hors de ce lot.

## État du projet et implications

| Point actuel | Conséquence pour le lot C |
|---|---|
| `WeatherResult` contient des valeurs et dates locales, sans identité de lieu, fuseau, date de mesure ou récupération. | Introduire un instantané métier avec métadonnées avant de persister. |
| Le parseur ne garde que 24 heures et sept jours déjà filtrés. | Persister les séries validées reçues, puis choisir la fenêtre visible au moment de la lecture. Ne pas fabriquer de prévisions absentes. |
| `weatherUpdates` publie la météo puis l’AQI ; le widget utilise uniquement `fetchWeather`. | Conserver deux rafraîchissements indépendants ; le widget n’a toujours pas besoin d’AQI. |
| `MainActivity.updateUI` efface les prévisions pendant chargement/erreur. | Distinguer chargement initial et actualisation avec données. |
| `updatedAt` est créé dans le ViewModel. | Utiliser la date persistée de récupération, sans la remplacer lors d’une lecture du cache. |
| Le widget persiste seulement un rendu dans `widget_render_v1`. | Ce rendu sans date ne peut pas être converti en instantané fiable ; Room devient la source des données. |
| Villes, sélection, récents et citations sont dans SharedPreferences. | Les conserver dans ce lot ; aucun déplacement global de préférences nécessaire. |
| Sauvegardes et transfert excluent déjà les bases de données. | Maintenir ces exclusions et vérifier le manifeste et les règles de l’artefact final. |

## Décisions de conception

### 1. Room comme source partagée

Prévoir un `OfflineWeatherRepository`, injecté à la place de l’implémentation réseau actuelle, et une source distante séparée. Les lectures de données viennent de Room ; le réseau valide puis écrit les instantanés. Les erreurs et indicateurs de chargement sont des états d’opération, pas des données météo persistées. Cette organisation suit le principe de source locale de référence décrit par [Android, architecture hors ligne](https://developer.android.com/topic/architecture/data-layer/offline-first).

Contrats cibles, à introduire avec adaptation des consommateurs dans la même livraison :

```kotlin
fun observeWeather(place: PlaceKey): Flow<CachedWeather?>
suspend fun readWeather(place: PlaceKey): CachedWeather?
suspend fun refreshWeather(place: PlaceKey, force: Boolean): RefreshOutcome
suspend fun refreshAirQuality(place: PlaceKey, force: Boolean): RefreshOutcome
```

`RefreshOutcome` distingue succès, données suffisamment récentes, erreur transitoire, erreur permanente et erreur de stockage. L’annulation reste une `CancellationException`, jamais un résultat métier. `CachedWeather` porte les dates de récupération et les mesures ; l’âge est recalculé avec une horloge injectable à l’affichage.

L’observation Room reste indépendante du travail réseau. Un rafraîchissement météo ne doit pas attendre l’AQI. Sa transaction terminée suffit pour rendre la météo visible ; l’AQI a son propre budget de cinq secondes, son statut et son horodatage. Si l’AQI échoue, une ancienne mesure peut être conservée en base, mais elle reste explicitement datée et n’est pas déclarée fraîche.

### 2. Identité du lieu

Clé de cache déterministe fondée sur les coordonnées réellement demandées, jamais sur le libellé. Pour la première version : représentation normalisée des deux `Double` validés et finis, avec −0 normalisé en 0, sans arrondi géographique implicite. Même coordonnées = même cache ; mêmes noms avec coordonnées différentes = caches différents. Renommer un lieu ne change pas la clé.

Les listes de villes et la sélection restent inchangées en SharedPreferences. Une nouvelle position géographique utilise une nouvelle clé ; ne pas réattribuer l’ancien instantané au nouveau point. Quand une position est remplacée, nettoyer les entrées qui ne sont plus référencées plutôt que constituer un historique de déplacements. Les récents existants restent des références à prendre en compte.

### 3. Schéma Room v1 proposé

Base `meteoquote_weather.db`, version 1, schéma JSON exporté et versionné dans `app/schemas/`.

| Table | Clé et contenu |
|---|---|
| `weather_snapshot` | `placeKey` primaire ; coordonnées, `zoneId`, `fetchedAt` UTC, date de mesure courante si fournie, température et code, identifiant de génération. |
| `weather_hour` | `(placeKey, position)` primaire ; horodatage fourni, température/code/UV nullables selon disponibilité ; ordre d’origine conservé. |
| `weather_day` | `(placeKey, localDate)` primaire ; min/max/code/UV maximal disponibles. |
| `air_snapshot` | `placeKey` primaire ; fuseau et `fetchedAt` AQI propres, identifiant de génération. |
| `air_hour` | `(placeKey, position)` primaire ; horodatage et AQI nullable. |

Les lignes horaires/quotidiennes référencent leur instantané parent avec suppression en cascade. Lecture agrégée et remplacement complet météo dans des transactions ; transaction AQI séparée. Un échec AQI ne remplace pas une météo valide. Une réponse partielle invalide ne doit pas supprimer les anciennes lignes avant validation.

Ne pas persister de texte formaté, de couleur Android, de `isLoading` ou les sentinelles −1 : utiliser des champs nullables pour les mesures manquantes. Les adaptateurs de présentation peuvent temporairement traduire null en sentinelles pour les composants existants.

Dates de récupération : `Instant` encodé en millisecondes UTC ; jours : `LocalDate` ; fuseau IANA sauvegardé. Avant de figer le schéma horaire, vérifier le contrat temporel du fournisseur, notamment l’heure répétée au changement d’heure. Préférer des instants non ambigus si l’API les fournit ; sinon conserver la valeur locale et l’ordre d’origine sans inventer un décalage. Cette vérification fait partie de C0 et doit précéder la génération du schéma v1.

### 4. Fraîcheur et consultation

Seuils initiaux proposés, configurables dans une politique testable ; ils expriment un choix produit, pas une garantie du fournisseur.

| Âge depuis récupération | Affichage | Réseau automatique |
|---|---|---|
| Moins de 30 min | Données disponibles, date affichée. | Pas d’appel météo automatique redondant. |
| De 30 min à moins de 24 h | Données conservées ; mise à jour en cours ou dernier échec signalé. | Tentative à l’ouverture/reprise si pas déjà en cours. |
| De 24 h à moins de 7 jours | « Dernières données connues », avec date ; ne pas présenter la température comme actuelle. | Tentative bornée. |
| À partir de 7 jours | Cache météo expiré non présenté ; villes et citation conservées. | Nouvelle consultation si possible ; purge locale. |
| Date de récupération dans le futur | Fraîcheur inconnue, pas de statut récent. | Réévaluation réseau ; tester l’horloge reculée. |

Pour l’AQI : à partir de 30 minutes, afficher son âge et son caractère ancien ; à partir de 24 heures, masquer la valeur et le niveau courant, même si la météo reste visible. L’absence d’une mesure pour la période voulue vaut « Indisponible », jamais zéro.

Filtrer les heures antérieures à l’heure courante et les jours antérieurs ou égaux à la date locale du lieu ; garder au maximum 24 heures et J+1 à J+7. Accepter une liste raccourcie lorsque le cache ne couvre plus toute la fenêtre. Les pics UV/AQI doivent être recalculés pour la date du lieu : ne pas montrer le pic d’hier sous l’étiquette « aujourd’hui ».

Au retour au premier plan et au changement de période pendant l’affichage, recalculer la présentation sans requête systématique. L’horloge injectable couvre aussi B01. Après un échec, appliquer un délai minimal d’une minute avant une nouvelle tentative automatique dans la même session ; le bouton manuel reste disponible et rejoint un appel déjà actif.

### 5. États d’écran

| Situation | Rendu attendu |
|---|---|
| Aucun cache, première requête | Chargement initial ; citation locale présente. |
| Cache utilisable, rafraîchissement | Météo immédiatement visible, indicateur discret ; aucune remise à blanc. |
| Échec avec cache | Données datées conservées, message de mise à jour échouée et Réessayer. |
| Échec sans cache | Message explicite « Aucune météo enregistrée pour cette ville », Réessayer, choix d’une ville connue. |
| Changement vers un lieu sans cache | État vide propre à ce lieu ; aucune température de l’ancien lieu. |
| Météo disponible, AQI en attente/échec | Météo utilisable ; seule la carte AQI évolue. |

Faire porter `placeKey`, `snapshot`, `isInitialLoading`, `isRefreshing`, `refreshFailure` et le statut AQI à la présentation. Éviter de déduire la présence de données de l’absence d’erreur. Les erreurs de localisation/recherche restent secondaires et ne détruisent pas une météo valide (B08).

### 6. Concurrence et widget

Dans le repository singleton, mutualiser les travaux en cours par `(placeKey, type météo/AQI)`. Une simple file de mutex ne suffit pas si chaque appel relance ensuite la même requête : les demandeurs doivent rejoindre le même résultat. Supprimer les références terminées du registre. Associer chaque écriture à sa clé et à sa génération ; un enrichissement ne réécrit jamais le bloc météo.

Annulation d’un écran : arrêter son observation et détacher sa demande ; annuler l’appel réseau si aucun autre consommateur ne l’attend. Un widget attendant la même ville ne doit pas perdre son appel parce que l’écran sélectionne une autre ville. Les garanties B02/B03 restent couvertes par tests.

Widget : lire Room et afficher le cache avant le réseau, garder les données et leur date en cas d’échec, obtenir la citation du jour auprès de `QuoteRepository` indépendamment de la météo. Le rendu `widget_render_v1` n’est plus une autorité et ne doit pas ressusciter un texte de la veille ; définir son retrait après le premier rendu compatible.

Séparer le travail local de rendu, sans contrainte réseau, du travail de rafraîchissement avec réseau disponible. Reprises transitoires bornées (proposition : trois exécutions maximum avec backoff à partir de 30 secondes) ; arrêter sur erreur permanente. Le déclenchement périodique reste soumis au système et ne promet pas une heure exacte. Mutualiser les clics pendant un travail actif, avec au plus une nouvelle demande nécessaire si la sélection change. Vérifier l’identité du lieu sélectionné avant chaque publication du worker.

## Intégration et conservation des données

- Première introduction : créer une base vide ; préserver intégralement `cities_json`, sélection, récents et attribution quotidienne. Le rendu widget existant ne contient pas assez de métadonnées pour être importé comme une mesure fiable.
- Exporter le schéma v1, puis exiger un chemin de migration testé à chaque changement. Ne pas activer de migration destructive générale comme solution de secours.
- Conserver les exclusions de sauvegarde déjà présentes pour les bases. Ne pas ajouter de logs de coordonnées, ni de télémétrie pour le cache.
- Purger au démarrage et après une écriture, hors thread principal : entrées expirées et lieux devenus non référencés. Éviter une tâche nocturne dédiée. Suppression d’une ville encore présente dans les récents : conserver la sémantique existante et ne pas promettre un effacement global.
- Si Room ne peut pas être lu/écrit, exposer une erreur de stockage distincte ; ne pas annoncer la consultation hors ligne comme garantie. Tester disque plein/échec de transaction avec un faux DAO. Conserver l’état déjà affiché, sans effacer les préférences ni recréer silencieusement la base.
- Repli de livraison : suspendre le déploiement en cas d’incident et préparer un correctif avec versionCode supérieur. Un ancien binaire ne doit pas être supposé capable de lire un futur schéma. Version 1 : aucune donnée utilisateur historique n’est déplacée vers Room.

## Choix de dépendances

Le catalogue actuel utilise Kotlin 2.0.21, AGP 8.13.2, Kapt et minSdk 24. Prévoir `room-runtime`, `room-compiler` et `room-testing`, une version unique épinglée et l’export du schéma. La documentation consultée affiche Room 2.8.5 stable et recommande KSP pour Kotlin ; le choix exact du processeur et sa compatibilité avec cette toolchain doivent être validés par compilation en C0. Ne pas coupler le lot à une migration générale de Hilt/Kapt. [Notes officielles Room](https://developer.android.com/jetpack/androidx/releases/room).

Le choix de schéma ci-dessus est propre au projet ; la génération de schémas et les migrations s’appuieront sur les outils Room. La résolution des dépendances et les tests de compatibilité ne sont pas encore exécutés dans cette préparation.

## Découpage exécutable

| Étape | Travail et fichiers visés | Critère de sortie |
|---|---|---|
| C0 — Contrats et temps | `data/model`, source distante, fixtures ; confirmer timestamps/fuseaux, définir `PlaceKey`, instantané et politique de fraîcheur ; essai de dépendances dans Gradle. | Tests des bornes 30 min/24 h/7 j, heure répétée, minuit, clés et homonymes ; build de compatibilité réussi. |
| C1 — Stockage Room | `data/local/WeatherDatabase`, entités/DAO/mappers, `app/schemas`, module Hilt. | Écriture/lecture après fermeture et réouverture réelle de base ; rollback transactionnel ; aucune perte des préférences. |
| C2 — Repository hors ligne | Repository commun, coordination des demandes et erreurs ; adapter `WeatherRepository` et source Open-Meteo. | Cache émis avant réseau ; pas de mélange de villes ; AQI indépendant ; nombre d’appels borné écran + widget. |
| C3 — Écran | `WeatherViewModel`, `MainActivity`, carte AQI/citation et ressources. | Mode avion après redémarrage ; données conservées sur échec ; âge juste et dates filtrées ; erreurs secondaires séparées. |
| C4 — Widget | Worker/provider/`WidgetViews`, horodatage accessible et rendu local. | Panne/reprise, citation du jour, changement de ville en cours, clics multiples et redimensionnement. |
| C5 — Recette et livraison | Tests Android/CI, procédure de publication et documentation. | Debug/release R8/lint verts, recette de mise à jour, API 24 et 36, police 200 % et TalkBack. |

Ordre : C0 → C1 → C2 → C3 → C4 → C5. C1 peut être livré inactif derrière l’interface existante ; ne basculer les consommateurs qu’avec les tests de C2/C3. Charge initiale estimée : 5–8 jours pour stockage/repository/écran/widget, puis 2–4 jours de recette et ajustements ; compatibilité Gradle ou incidents appareil peuvent augmenter cette estimation.

## Matrice de tests avant activation

1. Consultation réussie, fermeture de la base et recréation des repositories, réseau coupé : même lieu, mêmes mesures et date de récupération ; citation du jour disponible.
2. Ville A en cache, sélection B sans cache : aucune donnée de A visible sous B ; résultat A tardif uniquement stocké sous A.
3. Succès météo/AQI, puis actualisation météo seule via widget : AQI ni effacé ni artificiellement rajeuni.
4. AQI lent, HTTP 503, JSON AQI invalide : météo rendue et persistée sans attendre ; ancien AQI daté ou indisponible.
5. Réponse météo malformée ou transaction interrompue : dernier instantané complet toujours lisible.
6. Bornes exactes de fraîcheur, horloge reculée, fuseau différent du téléphone, passage à J+1 et changement d’heure : dates/pics filtrés correctement.
7. Deux widgets et un écran, clics rapides : rafraîchissement météo commun ; annulation d’un consommateur sans perte pour l’autre.
8. Échec avec cache, reprise réseau et limite de retries : dernière donnée conservée ; aucune boucle infinie ni chaîne de travaux accumulés.
9. Mise à jour depuis 7.1 : villes, sélection, récents, citation et widgets installés préservés ; base v1 créée sans import de faux horodatages.
10. Schéma v1 exporté et testé ; à partir de v2, test de migration depuis chaque version publiée. Aucun faux test de migration v0→v1 requis : il s’agit ici d’une création de base.

Les tests utilisent fixtures et horloge contrôlée ; le réseau public ne conditionne pas les tests bloquants de PR. Compléter les 81 tests existants, dont les garanties de choix manuel et de décorrélation AQI. L’exécution instrumentée en CI, encore ouverte dans le lot A, doit être ajoutée avant activation finale du cache.

## Validation de cette préparation

Lecture du code, des contrats, des sauvegardes et du catalogue de dépendances ; consultation de la documentation Android officielle. Cette livraison est documentaire : aucune dépendance ajoutée, aucun changement de comportement, aucune migration de données, aucun nouveau résultat de build revendiqué. Le premier travail de développement est C0, suivi de la base Room C1.
