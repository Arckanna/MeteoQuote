# Revue du code et améliorations — 2 novembre 2026

Référence examinée : `b74fe29`, application 7.4. Revue statique du parcours principal, repositories, cache Room, transport HTTP, citation, widget et configuration CI. Aucun code applicatif modifié. Les scénarios ci-dessous sont déduits du code ; ils n'ont pas été reproduits sur appareil pendant cette revue.

## Constats à corriger

### 1. [P2] Une recherche tardive remplace un choix plus récent

Emplacement : `MainActivity.kt:208–221`.

Lancer une recherche lente, puis sélectionner une ville existante dans le menu ou les récents : à son retour, `viewModel.addCity(found)` sélectionne systématiquement le résultat de la recherche. Seul le bouton d'ajout est désactivé pendant l'appel ; les autres choix restent accessibles. Le jeton protégeant la géolocalisation ne protège pas cette recherche.

Correction proposée : porter la recherche dans le ViewModel et partager une génération d'intention entre recherche, localisation et sélection manuelle. Un résultat devenu obsolète ne doit pas changer la sélection ni effacer une nouvelle saisie. Test : différer la réponse de recherche, sélectionner une autre ville, puis terminer la recherche et vérifier que le dernier choix demeure.

### 2. [P2] Une demande de rendu du widget peut être perdue

Emplacement : `widget/WeatherWidgetProvider.kt:99–100`, avec `WeatherWidgetWorker.kt:29–36`.

Toutes les demandes locales utilisent le même travail unique `weather-widget` avec `KEEP`. Si un worker a déjà capturé la ville A et qu'un changement vers B arrive pendant son exécution, la nouvelle demande est ignorée. Le worker A refuse ensuite de publier grâce à `stillSelected()`, mais aucun rendu B n'est garanti. Si B n'a pas de cache et que son chargement échoue, le ViewModel ne diffuse pas de mise à jour finale ; l'ancien widget peut donc rester affiché jusqu'à un prochain déclenchement. De même, un clic forcé peut être absorbé par un rendu non forcé en cours.

Correction proposée : garantir une exécution finale prenant en compte la dernière sélection et conserver toute demande forcée en attente. Séparer cette garantie de rendu de la déduplication réseau par lieu. Test avec barrières contrôlées : suspendre le worker A après lecture de la sélection, demander B, terminer A, puis vérifier un rendu B même sans réseau ni cache.

### 3. [P2] Le renouvellement quotidien peut utiliser une météo ancienne

Emplacement : `ui/WeatherViewModel.kt:237–242`.

`refreshDailyQuote()` transmet le code météo dès qu'une température existe et que `error` est absent, sans vérifier `isOld`. Après minuit, avec un cache de plus de 24 heures, cette méthode peut attribuer une citation selon l'ancienne météo. À l'inverse, la collecte météo et le widget excluent explicitement ce code lorsque les données sont anciennes. L'attribution étant persistée pour la journée, la correction ultérieure de l'état ne la remplace pas.

Correction proposée : appliquer une règle de fraîcheur commune aux trois points d'entrée, évaluée avec l'horloge lors de l'attribution. Test avec horloge contrôlée : franchir minuit avec un cache ancien et vérifier une attribution neutre identique dans l'application et le widget.

## Optimisations proposées

1. **Éviter les reconstructions inutiles des prévisions.** `ForecastRenderer.renderHourly()` et `drawDaily()` suppriment puis ré-influent toutes les vues ; `MainActivity.updateUI()` les rappelle pour chaque émission, y compris les changements de citation, de progression et d'AQI. Comparer les listes et les paramètres de présentation avant le rendu, ou réutiliser les vues. Vérifier aussi le maintien du focus TalkBack. Mesurer les allocations et le temps de rendu avant/après sur le même appareil et build.
2. **Séparer passage du temps et rafraîchissement réseau.** Chaque minute visible, `refreshVisibleWeather()` relance le flux, la purge, les lectures et le décodage, même si le cache est frais. Conserver l'actualisation des heures et de l'âge affiché, mais éviter de redémarrer toute l'observation pour cela. Le TTL de 30 minutes limite déjà les appels réseau ; il ne faut pas présenter cette boucle comme un appel réseau systématique.
3. **Réduire les lectures/décodages redondants.** Le `combine` de `OfflineWeatherRepository` ignore les enregistrements observés et relit un snapshot à chaque progression. Étudier une observation transactionnelle unique et un décodage réutilisable par version du payload. Préserver absolument la cohérence météo/AQI et recalculer la fenêtre horaire avec l'heure courante.
4. **Borner l'historique des citations.** `LocalQuoteRepository` ajoute une clé quotidienne sans purge. Définir une rétention explicite et préserver les règles de non-répétition et de changement de date. Priorité faible : aucune volumétrie problématique mesurée.

## Feuille de route proposée

Estimations indicatives, développement et tests compris ; à affiner après reproduction. Chaque lot est livrable séparément.

| Ordre | Lot | Résultat attendu | Charge indicative |
|---|---|---|---|
| 1 | Fiabilité | Corriger les trois constats et ajouter les tests de concurrence/date associés | 2–4 jours |
| 2 | Fluidité | Rendus conditionnels, moins de décodages ; mesures avant/après sur build release | 2–3 jours |
| 3 | Maintenance | Recherche et récents hors de l'Activity, chaînes dans les ressources, règles de fraîcheur communes, Clock injectée dans le widget | 2–4 jours |
| 4 | Validation | Étendre la CI Android au-delà du seul `OfflineCacheTest` : ville manuelle, citation, injection et interface ; recette widget et permissions sur appareil | 2–4 jours |
| 5 | Évolutions produit | Choix parmi plusieurs villes homonymes, thème sombre cohérent, puis configuration de ville par widget | À cadrer par fonctionnalité |

Mettre à jour le statut de `CAHIER_DES_CHARGES_ROADMAP.md` : il décrit encore certains éléments désormais implémentés comme futurs, notamment le cache Room. Conserver la migration Compose progressive existante. Une réécriture globale du réseau ou de l'interface n'est pas nécessaire pour les gains identifiés.

## Points solides à préserver

- Interfaces de repositories, injection Hilt et tests ciblés déjà présents.
- Annulation propagée, budget réseau borné, gestion de Retry-After et taille de réponse plafonnée.
- Validation avant remplacement du cache, transactions Room et date de récupération conservée.
- AQI optionnel, indépendant de la disponibilité météo ; absence distinguée d'une bonne qualité.
- Protection contre les retours tardifs de géolocalisation et les anciennes réponses météo.

## Validation de cette revue

`testDebugUnitTest lintDebug` : BUILD SUCCESSFUL ; 107 tests, aucun échec, aucune erreur ni test ignoré. Lint termine sans erreur bloquante ; ses avertissements restent à traiter. Exécution avec JDK Microsoft 17 et SDK Android local, après configuration de `ANDROID_HOME`. Pas de recette sur émulateur/appareil, de mesure de performances ou de build release dans cette revue. Les tests existants réussis ne couvrent pas nécessairement les scénarios de concurrence relevés ci-dessus.
