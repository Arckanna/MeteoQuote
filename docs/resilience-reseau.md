# Résilience réseau

Branche : `codex/resilience-reseau`, basée sur la refonte UI.

Le transport conserve désormais le code HTTP dans `HttpStatusException`.
Le ViewModel transforme l'exception en message utilisateur et catégorie
`WeatherErrorKind`, sans exposer les URL ni le texte technique des exceptions.
Une IOException signifie que la communication a échoué : elle n'est pas considérée
systématiquement comme la preuve d'une absence d'Internet.

## Politique des GET

- Trois tentatives maximum : appel initial, puis attente de 500 ms et de 1 s.
- Retry des IOException de communication et HTTP 408, 429, 500, 502, 503, 504.
- Pas de retry automatique des autres HTTP, des erreurs de certificat/handshake,
  des violations de protocole ou des corps rejetés pour taille excessive.
- Le parsing JSON reste hors du retry : des données invalides ne sont pas une
  raison de multiplier les appels.
- `Retry-After` est lu en secondes ou date HTTP ; l'attente automatique ne
  précède jamais cette échéance. Si elle dépasse le budget, l'erreur HTTP est rendue.
- Budget total de 30 secondes **par requête GET**, incluant appels et attentes.
  Les limites OkHttp existantes restent en vigueur. La météo et l'AQI étant deux
  requêtes successives, ce budget ne constitue pas une limite de 30 s pour
  l'ensemble de l'actualisation.
- Les délais suspendent les coroutines. Une annulation interrompt l'attente ou
  annule l'appel OkHttp actif, sans lancer de nouvelle tentative.
- La politique s'applique au transport partagé, donc aussi à la recherche de
  villes et au widget. Aucun retry supplémentaire n'est ajouté au ViewModel.

## Interface

Messages distincts pour communication impossible, timeout, limitation 429,
données introuvables 404, indisponibilité du serveur et données invalides.
Le bouton texte « Réessayer » remplace l'icône d'actualisation pendant l'erreur.
Il utilise le même flux de chargement et disparaît lors de la nouvelle tentative.
Les métadonnées d'erreur sont effacées au chargement puis au succès.
L'AQI reste optionnel : son échec ne fait pas perdre la météo.

## Vérification

```powershell
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./tools/review-ui.ps1
```

Les tests utilisent MockWebServer pour les réponses HTTP réelles, un temps virtuel
pour les délais et l'annulation, et des états déterministes pour l'interface.
Ils couvrent la récupération après 503/500, le refus 404, la limitation 429,
le respect de Retry-After, les plafonds, le corps trop volumineux, l'annulation,
l'AQI optionnel et le retour du ViewModel à un état valide.
Le test Android vérifie aussi le clic sur « Réessayer », l'apparition du chargement
et le retrait du bouton après récupération, sur les quatre profils de la refonte.

Résultat : 58 tests unitaires réussis ; 16 exécutions de tests Android réussies
sur compact, standard, tablette et police à 200 %. Compilation debug et Lint
réussis (zéro erreur Lint, 58 avertissements). Les captures de l'état d'erreur
ont été revues sur téléphone standard et avec la grande police.
