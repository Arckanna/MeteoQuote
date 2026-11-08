# Migration progressive vers Compose

Guide relu le 8 novembre 2026. L’action « Nouvelle citation » a été retirée le 19 octobre ; l'attribution quotidienne persistée est décrite dans [les règles de citation](lot-0-citation-quotidienne.md). La citation est actuellement la seule section Compose.

## Première étape : citation

Le bloc citation est migré vers Compose Material 3, au même emplacement après UV/AQI. Les autres sections restent en Views/XML, dont les prévisions horaires et les sept jours repliables. Hilt, le ViewModel, les repositories, les API et le widget ne changent pas.

`QuoteRoute` collecte le `StateFlow<WeatherUiState>` avec `collectAsStateWithLifecycle`. `QuoteSection` reçoit cet état sans accéder aux repositories et sans callback de renouvellement. Il n'existe plus de méthode `nextQuote`. La `ComposeView` est installée une seule fois et sa composition est libérée avec le cycle de vie de l’écran.

`MeteoQuoteTheme` centralise les couleurs Material 3 de cette partie de l’interface. Le thème clair est conservé pour rester cohérent avec les Views ; le mode sombre et les couleurs dynamiques ne sont pas introduits implicitement. `WeatherMood` partage les nuances et accroches existantes entre les deux systèmes de rendu.

L’ancien XML du contenu citation et les appels `findViewById`/`renderQuote` correspondants sont retirés. L’hôte XML conserve son identifiant et sa position. Le titre possède la sémantique de titre pour les lecteurs d’écran. Les textes ne sont ni limités en lignes ni tronqués ; aucun bouton n'est présent dans cette section.

## Configuration et tests

Le plugin Compose Compiler suit Kotlin 2.0.21. La BOM Compose 2024.12.01 fixe un ensemble de dépendances compatible avec la chaîne existante, sans mise à niveau globale de Kotlin, AGP ou Hilt. Lifecycle Compose utilise la même version 2.8.7 que Lifecycle Views.

`QuoteComposeTest` remplace les assertions sur les anciens TextView : changements du StateFlow, ambiance météo, auteur absent, citation longue, conservation du texte pendant chargement/erreur et absence de bouton. Il inspecte les résultats de mise en page Compose pour détecter les débordements et produit des captures de citations courtes et longues. `UiLayoutTest` conserve les contrôles de la hiérarchie de l’écran et des prévisions.

Commandes :

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
.\gradlew.bat assembleRelease
.\tools\review-ui.ps1
```

Validation du 18 octobre 2026 : builds debug et release avec R8 réussis, 58 tests unitaires réussis et sept tests Android sur chacun des quatre profils (28 exécutions réussies). Lint : zéro erreur, 71 avertissements. Captures Compose revues sur écran standard et compact à 200 % : citation courte, citation longue, ambiance claire/pluie, bouton et absence d’auteur. Les textes restent entiers ; le bouton revient à la ligne à grande police. Les captures de démarrage de l’écran complet peuvent montrer le chargement réel du réseau ; les captures `compose-short.png` et `compose-long-rain.png` utilisent des états déterministes.

Les résultats du 18 octobre ci-dessus concernent l'étape initiale avec bouton ; ils ne valident pas de nouveau la version actuelle.

## Suite possible

Les prévisions constituent une prochaine étape indépendante : remplacer `ForecastRenderer` par des composants recevant les listes existantes, en conservant le défilement horizontal et l’état trois/sept jours. Ensuite seulement, migrer météo actuelle, UV/AQI et sélection de ville. Chaque étape doit garder les tests de comportement et faire l’objet d’une revue visuelle sur les quatre profils existants.

Références : [Compose dans les Views](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/compose-in-views), [plugin du compilateur Compose](https://kotlinlang.org/docs/compose-compiler-migration-guide.html).
