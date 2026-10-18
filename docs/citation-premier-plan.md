# Citation au premier plan

## Présentation

Ordre de lecture : ville/recherche → météo actuelle → UV/AQI → citation → 24 h → aperçu des 7 jours → actions secondaires.

La citation conserve son fond dégradé discret selon la météo, son accroche, sa typographie serif et son auteur séparé. Elle n’a ni bordure ni élévation de carte. Le grand guillemet décoratif est retiré pour rapprocher le texte de l’accroche. L’action « Nouvelle citation » reste un bouton secondaire avec une petite icône, sans modifier la sélection des citations.

Les prévisions quotidiennes affichent les trois premières entrées de l’API, dans leur ordre existant (aujourd’hui compris lorsqu’il est fourni). « Voir les 7 jours » révèle les autres lignes ; « Réduire » revient à trois. Pour une réponse contenant entre quatre et six entrées, le libellé devient « Voir toutes les prévisions ». Avec trois entrées ou moins, aucune action de dépliage n’est affichée.

L’état ouvert est conservé pendant les mises à jour et dans l’état sauvegardé de l’Activity, sans préférence persistante. Le bouton expose son état aux services d’accessibilité. Chargement et erreur masquent le bouton et effacent les lignes comme auparavant.

Sur téléphone compact, les cartes UV/AQI utilisent un padding de 8 dp et restent côte à côte à partir de 280 dp utiles à police normale. Le seuil augmente avec la taille de police : les cartes s’empilent dès que la lisibilité l’exige. À partir de 360 dp de largeur d’écran, le padding de 16 dp est conservé. Aucune hauteur d’écran fixe n’est utilisée par la mise en page.

## Vérification

`UiLayoutTest` vérifie l’ordre des blocs, une apparition précoce du titre de citation à police normale, le passage 3 → 7 → 3 jours, la conservation de l’état lors d’un nouveau rendu, les réponses de deux jours, les citations courtes/longues, les alignements et l’absence de texte coupé. Les contrôles existants des ambiances météo, de l’AQI absent et des erreurs restent exécutés.

Commandes :

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
.\tools\review-ui.ps1
```

Profils de revue : 320 × 640, 411 × 891, 800 × 1280 dp et 320 × 640 dp avec police à 200 %. Les captures sont dans `app/build/ui-review`. Une grande police ou un nom de ville très long peut naturellement nécessiter davantage de défilement ; les textes ne sont ni réduits automatiquement ni tronqués pour imposer une hauteur.

Résultats du 18 octobre 2026 : compilation debug réussie, 58 tests unitaires réussis, quatre tests Android sur chacun des quatre profils (16 exécutions réussies), lint sans erreur et avec 60 avertissements. Revue des captures : citation visible au premier écran standard ; léger scroll sur le compact avec une ville sur deux lignes ; citation longue sans coupe à 200 % ; grille de sept jours alignée après dépliage.

Les repositories, API, données, sélection des citations, widget, applicationId et configuration de distribution ne sont pas modifiés.
