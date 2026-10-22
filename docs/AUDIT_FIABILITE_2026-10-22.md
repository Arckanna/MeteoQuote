# Audit de fiabilité et plan d’évolution — 22 octobre 2026

## Conclusion

La priorité est de fiabiliser les données présentées et les opérations asynchrones avant d’ajouter des fonctions. Le projet dispose déjà d’un socle utile : repositories injectés avec Hilt, StateFlow, annulation des requêtes météo, transport borné avec retries, tests unitaires et tests Android, citation quotidienne persistée.

Les faiblesses principales sont la fraîcheur non réévaluée de la météo, une course entre acquisition de position et sélection manuelle, l’attente de l’AQI facultatif avant affichage, et la récupération du widget après erreur. Le hors-ligne prévu dans la roadmap est l’évolution offrant le plus de valeur après ces corrections.

L’objectif réaliste n’est pas de garantir l’absence de tout bug : chaque défaut connu doit être qualifié, priorisé, corrigé avec une preuve de non-régression, ou explicitement accepté avec une raison.

## Périmètre et preuves

- Révision examinée : `f9d663af52646e358bd877e0a6da3bfd8f5007d6`, application 7.1, versionCode 19. Arbre de travail initial propre.
- Lecture du code principal, des tests, du manifeste, de la configuration Gradle, du workflow CI et des documents de roadmap/publication.
- Audit local : ni incidents Play Console, ni retours Google Forms, ni tickets GitHub distants consultés. Leur fréquence en production reste inconnue.
- Aucun comportement applicatif modifié pendant l’audit. Les scénarios ci-dessous sont déduits du code ; ils ne sont pas présentés comme des reproductions sur appareil.
- Vérifications Gradle : résultats consignés en fin de document après exécution.
- Tests instrumentés non exécutés pendant cet audit ; TalkBack, installations/mises à jour réelles, Doze et constructeurs restent à recetter.

Les chemins et numéros de ligne ci-dessous concernent cette révision. P0 = crash généralisé, perte de données ou fonction essentielle inutilisable sans contournement ; P1 = correction prioritaire ; P2 = correction à planifier. Aucun P0 démontré dans ce périmètre.

## Registre initial des défauts

### B01 — P1 — La météo ne vieillit pas dans l’état affiché

**Preuve :** `MainActivity.kt:235` réévalue seulement la citation dans `repeatOnLifecycle`. `WeatherViewModel.kt:58` charge la météo à l’initialisation ; aucune politique d’âge ne déclenche sa réévaluation. Les listes horaires et quotidiennes restent celles du dernier téléchargement.

**Scénario à reproduire :** charger la météo, garder le processus vivant plusieurs heures ou revenir le lendemain. Sans action manuelle, les heures passées restent affichées ; après minuit, la liste censée commencer à J+1 commence au jour devenu courant. L’horodatage existe, mais aucun état « données anciennes » ne qualifie les valeurs.

**Correction :** injecter une horloge dans la présentation, conserver le fuseau du lieu, filtrer les périodes terminées et appliquer une politique d’âge au retour au premier plan et pendant une consultation longue. Ne pas renouveler la date de récupération sans réponse réseau réussie.

**Acceptation :** tests à horloge contrôlée à +2 h, après minuit dans le fuseau de la ville, et au retour au premier plan ; aucun affichage silencieux d’une prévision passée comme future. Seuil proposé de rafraîchissement : 30 minutes, à confirmer en bêta.

### B02 — P1 — Une position tardive peut écraser le choix manuel

**Preuve :** `MainActivity.kt:459` possède son propre `locationJob` qui attend `currentLocation`, puis appelle `viewModel.detectLocation`. `WeatherViewModel.kt:132` annule uniquement le job de géocodage du ViewModel. Choisir une ville n’annule pas l’acquisition encore en cours dans l’Activity.

**Scénario à reproduire :** toucher « Ma position », choisir une autre ville avant la réponse du fournisseur, puis laisser arriver la position. La sélection manuelle peut être remplacée. Le test `manualChoiceCancelsSlowReverseGeocoding` couvre la phase suivante, pas cette attente initiale.

**Correction :** un propriétaire commun pour l’opération complète, ou un identifiant d’intention invalidé à chaque choix manuel ; vérifier cet identifiant avant publication. Appliquer la même règle aux recherches de ville tardives.

**Acceptation :** test avec fournisseur de position contrôlable : une position reçue après un choix manuel ne change ni la sélection ni la liste. Tester aussi arrêt/recréation de l’écran et deux demandes successives.

### B03 — P1 — L’AQI facultatif retarde toute la météo

**Preuve :** `data/OpenMeteoWeatherRepository.kt:20` attend le corps météo, puis la requête AQI, avant de parser et retourner le résultat. Chaque `HttpTransport.get` dispose d’un budget de 30 secondes.

**Conséquence :** une météo déjà téléchargée peut rester invisible pendant jusqu’à environ 30 secondes supplémentaires si le service d’air est lent. Les budgets successifs peuvent approcher 60 secondes au total, hors traitement local. La dégradation en AQI indisponible fonctionne, mais intervient trop tard.

**Correction :** publier la météo indépendamment de l’AQI, puis enrichir l’état ; borner séparément l’attente de l’AQI. La simple parallélisation avec attente obligatoire des deux résultats ne suffit pas.

**Acceptation :** météo répondant immédiatement et AQI suspendu/503 : météo visible sans attendre la fin du budget AQI, puis état partiel explicite. Une annulation utilisateur doit toujours annuler les appels associés.

### B04 — P1 — Le widget traite les échecs comme des succès définitifs

**Preuve :** `widget/WeatherWidgetWorker.kt:66` remplace les données par une erreur et persiste ce rendu ; la méthode retourne ensuite `Result.success()` à la ligne 79. `WeatherWidgetProvider.kt:78` ne pose pas de contrainte réseau et ne programme pas de reprise dédiée à l’échec.

**Scénario à reproduire :** widget chargé, panne réseau pendant une actualisation, puis rétablissement du réseau. Le dernier rendu réussi est remplacé par l’erreur et WorkManager considère le travail terminé. Il reste l’action manuelle ou une future mise à jour système, dont la période déclarée est de 30 minutes.

**Correction :** conserver le dernier instantané valide et sa date ; différencier erreurs transitoires et permanentes ; reprise bornée avec backoff pour les premières. Afficher le cache même lorsque la partie réseau attend sa contrainte.

**Acceptation :** scénario succès → panne → rétablissement, avec conservation de la météo ancienne signalée et reprise effective. Une erreur permanente ne doit pas entraîner une boucle sans fin.

### B05 — P2 — Les clics d’actualisation du widget s’accumulent

**Preuve :** `widget/WeatherWidgetProvider.kt:42` traite chaque action manuelle ; `enqueue` utilise `APPEND_OR_REPLACE` à la ligne 91. La temporisation d’`onUpdate` ne protège pas cette branche.

**Scénario à reproduire :** plusieurs clics pendant une requête lente ajoutent plusieurs travaux successifs, chacun rechargeant météo et AQI. Le test de déduplication existant porte sur les broadcasts système.

**Correction :** mutualiser les demandes pendant un travail actif et, si nécessaire, mémoriser une seule actualisation supplémentaire. Éviter une politique qui annule et relance sans cesse au clic.

**Acceptation :** dix clics rapides, plusieurs widgets et réseau lent : nombre de travaux/appels borné ; dernier état cohérent après la fin du chargement.

### B06 — P2 — Le widget peut restaurer une citation d’une autre journée sans indication

**Preuve :** `widget/WidgetViews.kt:14` persiste un texte de citation et des valeurs formatées sans date. `restored` les relit telles quelles. `WeatherWidgetProvider.onAppWidgetOptionsChanged` restaure sans reconsulter l’attribution quotidienne. La branche d’échec du worker masque toute citation, même disponible localement.

**Correction :** partager l’attribution quotidienne via `QuoteRepository` lors du rendu ; dissocier disponibilité de la citation et succès météo ; stocker et afficher la fraîcheur météo.

**Acceptation :** à J+1 hors ligne, le widget et l’application utilisent la même attribution du jour ; un redimensionnement ne réintroduit pas celle de la veille. Tester les formats où la citation tient et ceux où elle est volontairement masquée.

### B07 — P2 — Température tronquée dans le widget

**Preuve :** `widget/WeatherWidgetWorker.kt:56` utilise `temp.toInt()`, alors que `ui/ForecastRenderer.kt:37` formate les températures entières avec arrondi.

**Exemple déterministe :** 19,9 devient 19° et −0,9 devient 0° dans le widget. La perte de précision est attendue dans un format compact, mais la troncature biaise la valeur affichée vers zéro.

**Correction et acceptation :** partager une règle de formatage explicite, avec tests des valeurs positives, négatives et proches de zéro, y compris les descriptions vocales.

### B08 — P2 — Les erreurs de localisation sont transformées en erreurs météo génériques

**Preuve :** `WeatherViewModel.detectLocation` écrit « Localisation indisponible. Recherchez une ville. » dans `state.error` avec `errorKind = null`. `MainActivity.updateUI`, dans ce cas, remplace les messages non reconnus par « Météo indisponible » et masque les prévisions.

**Portée :** chemin présent dans le code, déclenchable par une exception de géocodage autre que les IOException déjà traitées. Sa fréquence réelle n’est pas établie.

**Correction :** séparer l’erreur d’une action secondaire de l’état des données météo ; conserver la météo valide et proposer la recherche manuelle avec le bon message.

**Acceptation :** repository de géocodage levant une exception après une météo réussie : météo toujours visible, message de localisation fidèle et action adaptée.

## Limites produit et risques à traiter séparément

| ID | Constat | Qualification et suite |
|---|---|---|
| E01 | Aucun cache météo persistant partagé ; un échec vide l’écran et un redémarrage hors ligne ne retrouve pas la météo. | Limite déjà documentée, pas une régression nouvelle. Évolution prioritaire après les corrections ciblées. |
| R01 | `AndroidCityRepository.geocodeCity` demande `count=1` ; `addCity` déduplique par libellé. | Choix ambigu pour les homonymes et identité fragile. Prévoir sélection de résultats et identité stable du lieu. |
| R02 | Géocodeur pré-API 33 bloquant sur IO sans délai garanti. | Risque connu ; définir une sortie rapide vers « Ma position » sans attendre l’enrichissement du nom. |
| R03 | Chargement des récents moins robuste que celui des villes ; certains accès SharedPreferences ne protègent pas un type inattendu. | Durcissement après tests de préférences malformées ; pas de corruption réelle observée ici. |
| R04 | Une confirmation de suppression capture un index avant son exécution. | Vérifier le scénario où un résultat asynchrone modifie la liste pendant le dialogue ; supprimer par identité et revérifier au moment de confirmer. |
| R05 | Pas de synchronisation immédiate de la sélection dans l’application vers le widget trouvée. | Définir le contrat : ville partagée, délai de convergence et comportement si la ville disparaît. Couvrir par tests. |

## Couverture et maintenance

Les tests existants couvrent notamment transport HTTP, retries, parsing, dates des prévisions, citation quotidienne, annulation d’une requête météo lente, géocodage inverse tardif, présentation et widget. Cela ne démontre pas que tous les parcours asynchrones sont couverts.

Lacunes prioritaires : acquisition de position avant géocodage, reprise réseau du widget, clics manuels multiples, fraîcheur au retour écran, AQI lent, cache et migrations, suppression concurrente, erreurs secondaires non destructives.

`.github/workflows/android.yml` lance les tests unitaires et lint, construit l’APK de tests et le bundle release, mais ne lance pas les tests sur émulateur. Ajouter un job instrumenté déterministe ; séparer les tests réseau réel des tests bloquants de PR. `WidgetHostTest` accepte à juste titre un rendu final d’erreur pour vérifier le transport des RemoteViews : il ne prouve donc pas un affichage météo réussi ni la reprise après panne.

Documentation à réconcilier : README mentionnant encore « Nouvelle citation » et crossfade ; procédure `docs/RELEASE.md` centrée sur 6.7/code 16 au lieu de 7.1/code 19 ; anciens constats de roadmap à distinguer de l’état actuel. La sélection quotidienne est déjà implémentée : ne pas refaire le lot 0 sur la seule base du constat historique.

## Plan d’évolution proposé

Charges indicatives en jours-personne, tests et documentation compris ; à ajuster après reproduction des scénarios. Elles ne comprennent pas les délais de bêta ou de distribution.

| Lot | Livrable | Charge | Condition de sortie |
|---|---|---|---|
| A — Référence et suivi | Transformer B01–B08 en tickets locaux/externes au choix, fixtures déterministes, tests de reproduction ciblés, première exécution instrumentée en CI, documentation de version alignée. | 2–3 j | Chaque défaut a une preuve, une priorité et un test prévu ; CI instrumentée exploitable. |
| B — Correctifs de cohérence | B01, B02, B03, B07, B08 ; identité de l’intention utilisateur, états d’erreur séparés, fraîcheur, météo indépendante de l’AQI. | 3–5 j | Les scénarios passent ; aucun retour asynchrone ne remplace un choix plus récent. |
| C — Hors-ligne et widget fiables | Cache structuré par lieu, dates de mesure/récupération, migrations, B04–B06 et contrat de synchronisation du widget. | 5–8 j | Redémarrage en mode avion utile ; cache jamais attribué au mauvais lieu ; reprise réseau et citation cohérentes. |
| D — Recette et diffusion | Matrice Android, mise à jour avec données existantes, release R8, accessibilité, bêta et vérification des incidents disponibles. | 2–4 j | Aucun P0/P1 ouvert ; P2 restants documentés et acceptés ; résultats de recette archivés. |

Total indicatif du socle de fiabilité : **12–20 jours-personne**, avec marge de planification de 20–30 % selon les anomalies appareils. Livrer les correctifs simples indépendamment du cache ; commencer la reprise du widget dès le lot B si les incidents la rendent urgente.

Ensuite, par valeur utilisateur : choix explicite parmi les villes homonymes ; thème sombre et accessibilité ; migration Compose section par section si elle réduit réellement la maintenance ; réglage du lieu par widget. Notifications matinales et animations viennent après stabilisation. Aucun remplacement de bibliothèque n’est nécessaire pour corriger B01–B08.

Le cache Room déjà proposé dans la roadmap est une option cohérente ; en faire une source partagée par écran/widget, conserver des données structurées avec fuseau et identité de lieu, migrer sans effacer les villes. Définir les seuils de fraîcheur et la purge avant développement. La migration Compose ou Glance ne doit pas être une dépendance du correctif de fiabilité.

## Organisation durable des bugs

1. Centraliser les retours dans un registre unique. Les retours volontaires et incidents disponibles doivent rejoindre ce registre, avec un lien vers leur source ; ne pas créer automatiquement de tickets publics contenant des données personnelles.
2. Demander version/code, Android/appareil, étapes, attendu/obtenu, fréquence, réseau et état des permissions. Ajouter au modèle actuel les champs attendu/obtenu et reproductibilité.
3. Utiliser les états : à qualifier → reproduit → correction → vérification → livré ; conserver aussi « non reproduit » et « accepté » avec justification. Désigner un responsable et une version cible pour chaque P0/P1.
4. Écrire un test qui échoue avant la correction lorsque le défaut est automatisable. Pour un défaut visuel ou constructeur, joindre la recette et les captures avant/après.
5. Revue des nouveaux bugs à chaque session de maintenance ; suivi hebdomadaire du nombre de P0/P1 ouverts, délai de correction, réouvertures et instabilité des tests. Fixer les objectifs chiffrés après mesure initiale.
6. Publier par petits lots ; suivre les incidents de la version diffusée avec les outils déjà disponibles. Ne pas ajouter de collecte de localisation ou d’analytics pour réaliser cette roadmap.

## Matrice minimale de recette

| Axe | Cas indispensables |
|---|---|
| Android | API 24, 33 et 36 ; appareil physique et émulateur de test. |
| Réseau | Hors ligne initial, panne après succès, rétablissement, météo rapide/AQI lent, HTTP 429/503, réponse malformée. |
| Concurrence | Ville A lente puis B, position lente puis choix manuel, recherche puis suppression, dix clics widget. |
| Dates | Minuit téléphone/ville différents, changement de fuseau et d’heure, reprise après plusieurs heures, citation J+1 hors ligne. |
| Cycle de vie | Rotation, arrière-plan/premier plan, destruction du processus, redémarrage, mise à jour depuis une version antérieure. |
| Widget | Petit/grand, plusieurs instances, redimensionnement, panne/reprise, ville supprimée/changée, batterie restreinte. |
| Accessibilité | Police 200 %, TalkBack, textes longs, petit écran, clavier, gestes et navigation à trois boutons. |

## Vérifications réalisées

Commande : `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease`, JDK 17 configuré et SDK Android local.

Résultat : **BUILD SUCCESSFUL**, 157 tâches exécutées, en 1 min 55 s.

- **73 tests unitaires**, zéro échec, zéro erreur, zéro test ignoré, d’après les XML de `app/build/test-results/testDebugUnitTest`.
- APK debug, APK de tests Android et APK release construits ; minification R8 exécutée. La compilation de l’APK de tests ne vaut pas exécution sur appareil. Aucune signature de publication ni diffusion effectuée.
- **Lint : 0 erreur, 71 avertissements**. Répartition : 29 suggestions de versions de dépendances/outillage, 18 ressources inutilisées, 10 suggestions KTX, 6 textes à externaliser, 3 attributs réservés aux API récentes, 2 icônes monochromes manquantes, 1 overdraw, 1 recherche d’identifiant répétée et 1 alerte de provider.
- L’alerte `ExportedContentProvider` vise `InitializationProvider` dans le manifeste source ; le manifeste debug fusionné contient bien `android:exported="false"`. Elle n’établit donc pas une exposition effective dans cet artefact. Rendre l’intention explicite dans le source et vérifier aussi le manifeste release lors du nettoyage.
- Les suggestions de versions de lint ne constituent ni un audit de vulnérabilités ni une validation de migration. Traiter les mises à jour séparément, avec compatibilité et tests, sans les confondre avec les défauts utilisateur.
- Avertissements de compilation : repli Kapt vers le langage 1.9, options de processeurs non reconnues sur les tests, symboles de `libandroidx.graphics.path.so` non retirés. Builds réussis ; à suivre dans le lot de maintenance, sans conclure à un défaut d’exécution.

Rapports locaux : `app/build/reports/tests/testDebugUnitTest/index.html` et `app/build/reports/lint-results-debug.html`. Ces fichiers générés peuvent être effacés par un nettoyage Gradle ; les résultats essentiels sont conservés ici.
