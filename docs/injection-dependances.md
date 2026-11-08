# Injection de dépendances

Guide du graphe de production, relu le 8 novembre 2026. L'introduction initiale de Hilt date d'octobre ; le graphe ci-dessous inclut les évolutions Room et recherche livrées depuis.

## Graphe de production

- `MeteoQuoteApplication` initialise Hilt et fournit à WorkManager sa `HiltWorkerFactory`.
- `RepositoryModule`, installé dans `SingletonComponent`, fournit un transport HTTP, une horloge UTC et les trois repositories partagés pendant la vie du processus. Les repositories Android reçoivent exclusivement le contexte de l’application.
- `WeatherRepository` est fourni par `OfflineWeatherRepository`, avec la base singleton `WeatherDatabase`. `OpenMeteoWeatherRepository` est sa source distante et conserve le parsing, la récupération météo et l’AQI facultatif. Les lieux enregistrés et récents déterminent les clés à conserver lors de la purge.
- `CityRepository` est le contrat de recherche et de persistance des villes ; `AndroidCityRepository` conserve les préférences et le géocodage existants.
- `QuoteRepository` est fourni par `LocalQuoteRepository`, qui persiste une attribution par date locale. Les anciens index ne servent qu'à la migration initiale.
- `WeatherViewModel` utilise `@HiltViewModel` et l’injection par constructeur. Il pilote aussi la recherche et sa génération d'intention partagée avec la localisation et les choix manuels. `MainActivity`, annotée `@AndroidEntryPoint`, utilise la fabrique Hilt et affiche `searchState`. Elle reçoit encore le repository de villes, mais n'exécute plus le géocodage de recherche directement ; les récents restent gérés dans l'Activity.
- `WeatherWidgetWorker` utilise `@HiltWorker` et `@AssistedInject` : WorkManager fournit le contexte et les paramètres, Hilt fournit les repositories. L’ancienne `WeatherViewModelFactory` est supprimée.

Seul l’initialiseur WorkManager par défaut est retiré des métadonnées AndroidX Startup. Les autres initialisations AndroidX sont conservées. Le nom du worker et les noms des préférences restent identiques, notamment pour les travaux déjà enregistrés et les données existantes.

## Tests et maintenance

Les tests unitaires du ViewModel construisent directement celui-ci et injectent des mocks des interfaces : aucun démarrage Hilt n’est nécessaire pour substituer les repositories. Les tests des implémentations continuent de vérifier les données météo, le stockage et les citations. Un fake peut également implémenter simplement l’interface souhaitée et être passé au constructeur.

`DependencyInjectionTest` utilise le graphe de production sur Android : il vérifie la création du worker via la configuration de l’application et la conservation du repository de villes après recréation de l’Activity. Il ne remplace pas le test d’une actualisation réelle d’un widget installé.

Versions ajoutées : Dagger/Hilt 2.56.2, AndroidX Hilt 1.2.0, kapt correspondant à Kotlin 2.0.21. Ces versions permettent de conserver la chaîne Kotlin/AGP actuelle. Une migration KSP ou une mise à niveau globale des dépendances constitue un chantier distinct. Kapt émet un avertissement de génération des stubs en mode langage Kotlin 1.9 ; les compilations et tests doivent rester la référence de validation.

Commandes de vérification :

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
.\gradlew.bat assembleRelease
.\tools\review-ui.ps1
```

Le script Android vérifie les profils compact, standard, tablette et compact avec police à 200 %, puis restaure la configuration de l’émulateur. Les rapports sont dans `app/build/reports` et `app/build/ui-review`.

Validation du 18 octobre 2026 : 58 tests unitaires réussis ; 6 tests instrumentés réussis pour chacun des quatre profils (24 exécutions) ; test de création du worker également réussi seul après arrêt du processus, sans Activity. Builds debug et release avec R8 réussis. Lint : 0 erreur, 66 avertissements, notamment de versions de dépendances et de ressources. Le manifeste fusionné confirme le retrait du seul initialiseur WorkManager. La comparaison des trois implémentations avec la version précédente ne révèle aucune modification de leur corps, hormis les déclarations de classes et de méthodes nécessaires aux interfaces. Capture standard revue : l’écran s’ouvre et affiche les données météo.

Références : [configuration Gradle Hilt](https://dagger.dev/hilt/gradle-setup.html), [intégration HiltWorker](https://developer.android.com/reference/androidx/hilt/work/HiltWorker).
