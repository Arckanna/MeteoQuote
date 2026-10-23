# Lot C — Cache Room et consultation hors ligne

## Comportement implémenté

- Room devient la source des données météo présentées par l’application et le widget. Les lieux sont identifiés par coordonnées validées, indépendamment du nom affiché.
- Le cache est émis avant le réseau. L’écran conserve ses données pendant une actualisation et après un échec, avec une indication d’âge et un moyen de réessayer. Une ville sans cache dispose de son propre état vide.
- Réévaluation à la reprise et chaque minute au premier plan : les heures passées, jours terminés et pics de la veille ne sont pas recyclés sous une nouvelle date.
- Données récentes pendant 30 minutes ; « dernières données connues » après 24 heures ; cache non présenté à partir de sept jours. Une date future est signalée comme incertaine. Purge des entrées expirées et des lieux non référencés lors des consultations réseau/flux.
- L’AQI garde son appel, son horodatage et son budget de cinq secondes indépendants. Un rafraîchissement météo du widget ne l’efface pas. Une mesure absente pour l’heure courante n’est pas remplacée par celle de l’heure précédente ; après 24 heures, l’AQI n’est plus présenté.
- Requêtes en cours partagées par lieu et service. La fermeture d’un consommateur n’annule pas le travail attendu par un autre ; le dernier départ annule l’appel.
- Widget : rendu local sans contrainte réseau, puis tâche réseau unique avec contrainte de connexion. Trois exécutions maximum pour les erreurs transitoires, backoff exponentiel à partir de 30 secondes. Les clics rejoignent une tâche réseau existante.
- La citation du jour est obtenue localement même sans météo. Les anciens rendus `widget_render_v1` ne sont plus relus. Le changement de sélection demande un nouveau rendu ; les réponses tardives ne publient pas une ancienne ville sous la nouvelle sélection.
- Les erreurs de localisation utilisent un message secondaire et ne masquent plus la météo valide. Les températures entières du widget sont arrondies au lieu d’être tronquées.

## Stockage et choix techniques

Room 2.8.5, processeur Kapt conservé pour limiter le changement de toolchain. Base `meteoquote_weather.db`, schéma v1 exporté dans `app/schemas/com.valerie.meteoquote.data.local.WeatherDatabase/1.json`. Aucun `fallbackToDestructiveMigration`.

Adaptation de la proposition initiale : deux tables d’instantanés (`weather_snapshot`, `air_snapshot`) au lieu de cinq tables de mesures. Les coordonnées, fuseau et dates de récupération météo sont des colonnes typées ; les séries complètes validées du fournisseur sont conservées en JSON, avec leurs valeurs nulles d’origine. Elles ne sont ni tronquées à la fenêtre visible ni converties en texte de présentation. La date de mesure se trouve dans le payload et est exposée par le modèle. Cette forme évite les écritures partielles entre lignes horaires et facilite la reconstruction des périodes à la lecture ; les évolutions du parseur doivent rester compatibles avec les fixtures persistées.

Chaque remplacement d’instantané est une écriture SQLite atomique. Une lecture transactionnelle associe météo et air ; leur fraîcheur reste indépendante. Une réponse météo invalide est rejetée avant écriture ; une réponse AQI invalide conserve l’ancienne série sans rajeunir sa date.

Les nouvelles requêtes demandent `timeformat=unixtime` pour désambiguïser l’heure répétée d’automne. Les filtres horaires utilisent les instants ; l’affichage suit le fuseau de la ville. Le parseur conserve la compatibilité avec les fixtures ISO existantes. Voir [contrat Open-Meteo](https://open-meteo.com/en/docs).

## Installation, conservation et migrations

Première introduction : création d’une base vide, pas de migration fictive depuis une v0. Les villes, récents, index sélectionné et citations restent dans les préférences existantes. Aucune reprise du rendu widget sans date comme mesure météo.

Les exclusions de sauvegarde/transfert des bases et préférences existantes sont conservées. Aucun suivi distant ni journal de coordonnées n’a été ajouté. Une future modification de schéma impose une migration explicite, son schéma exporté et ses tests depuis les versions publiées. Les erreurs de stockage sont distinguées des pannes réseau ; elles ne déclenchent pas de reconstruction destructive automatique.

## Tests et recette

Tests ajoutés : bornes de fraîcheur et recul d’horloge ; identité des coordonnées ; cache immédiat ; lecture après fermeture/réouverture réelle de base ; panne sans cache et après succès ; réponse invalide ; AQI indépendant et non rajeuni ; filtering à J+1 et expiration ; partage de requête et annulation d’un seul consommateur ; erreurs de stockage ; heure répétée au changement d’heure ; conservation de la météo à l’écran et changement vers un lieu sans cache.

Tests instrumentés déterministes `OfflineCacheTest` : SQLite Android après réouverture, dates météo/AQI indépendantes et rollback transactionnel. Un job CI dédié les lance sur API 24 et 36, sans dépendre du réseau météo public. Le succès local ne préjuge pas de l’exécution future du workflow distant.

Validation locale terminée le 23 octobre 2026 :

- 98 tests unitaires réussis, aucune erreur ni aucun échec.
- 12 tests instrumentés réussis sur l’émulateur API 36, dont réouverture Room, rollback, sélection manuelle, affichage et actualisation du widget.
- Builds debug, APK de tests et release avec R8 réussis.
- Lint : aucune erreur, 71 avertissements existants ; `git diff --check` sans erreur.
- Le test d’actualisation du widget vérifie désormais l’identité du nouveau travail : avec la politique `KEEP`, le nombre d’entrées historiques ne garantit pas qu’une actualisation a été lancée.

La matrice CI API 24/36 est configurée mais n’a pas été exécutée à distance. Les modifications sont locales sur `codex/cache-room-hors-ligne`, sans commit, push ni fusion dans `master` à ce stade.

## Avant diffusion publique

Recette sur appareil physique : TalkBack, police 200 %, mise à jour depuis l’APK signé publié, mode avion puis rétablissement, widget posé avant mise à jour et restrictions batterie du constructeur. Les horaires WorkManager restent approximatifs. Aucun binaire n’est publié ni signé pour distribution par cette livraison.
