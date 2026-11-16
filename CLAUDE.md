# MeteoQuote — guide pour Claude

App Android de météo (actuelle, 24 h, 7 jours) avec une citation adaptée au temps et un widget d'écran d'accueil.
Projet personnel d'une étudiante : explique tes choix, privilégie le code lisible aux astuces.

## Stack
- Kotlin 2.0.21, AGP 8.13, JDK 17, Gradle Kotlin DSL + version catalog (`gradle/libs.versions.toml`)
- Module unique `:app`, package `com.valerie.meteoquote`, minSdk 24 / targetSdk 36, desugaring `java.time`
- UI **hybride** : Views/XML (`res/layout/`) + Compose Material 3 pour le bloc citation (`ui/compose/`). La migration vers Compose est **progressive**, section par section (voir `docs/migration-compose.md`). Ne migre pas d'autres écrans sans qu'on te le demande.
- Hilt (kapt) pour l'injection, WorkManager + `@HiltWorker` pour le widget, Room pour le cache hors ligne (schémas exportés dans `app/schemas/`), OkHttp + `org.json` pour l'API Open-Meteo

## Architecture
- `data/` : interfaces de repositories (`WeatherRepository`, `CityRepository`, `QuoteRepository`) et leurs implémentations ; `di/RepositoryModule.kt` les fournit.
- `ui/WeatherViewModel.kt` expose un `StateFlow<WeatherUiState>` ; les composants Compose sont sans état et reçoivent données + callbacks.
- `widget/` : provider, worker et rendu du widget. Les noms du worker et des préférences ne doivent pas changer (travaux et données déjà enregistrés chez l'utilisatrice).
- Toute nouvelle dépendance passe par le version catalog.

## Commandes (Windows : `.\gradlew.bat`, sinon `./gradlew`)
- Tests unitaires JVM (Robolectric, MockK, MockWebServer) : `testDebugUnitTest`
- Build + lint : `assembleDebug lintDebug`
- Tests instrumentés : `assembleDebugAndroidTest` puis `scripts/test-emulator.ps1` (**émulateur uniquement**, jamais un appareil personnel) ou `connectedDebugAndroidTest`
- Release : `assembleRelease` (R8 + shrink resources ; vérifier `proguard-rules.pro` si une classe disparaît)
- La CI (`.github/workflows/android.yml`) lance `OfflineCacheTest` sur API 24 et 36 : garde ce test vert.

## Règles de travail
- Après une modification : lance au minimum `testDebugUnitTest` et `assembleDebug`, et dis-moi ce qui passe ou échoue.
- Ajoute ou adapte un test pour chaque correction de bug.
- Une modification de schéma Room demande une migration explicite (jamais de `fallbackToDestructiveMigration` sans accord).
- Les décisions de conception sont documentées dans `docs/` : lis le document concerné avant de toucher une zone, et mets-le à jour si le comportement change.
- Git : c'est moi qui fais les commits, après avoir relu le code. Ne lance jamais `git commit`, `git push` ni de fusion. Si je te le demande, propose-moi un message de commit en français, court et au présent, avec un préfixe de type (`feat`, `fix`, `test`, `docs`, `refactor`…), par exemple `fix(widget) : évite les mises à jour en double`.

## À ne jamais faire
- Lire, copier ou modifier les clés de signature (`*.jks`, `*.keystore`, `key.properties`, dossier `../KeyStore`) ou `local.properties`.
- Augmenter Kotlin, AGP, Hilt ou la BOM Compose « au passage » : une montée de version est une tâche à part.
