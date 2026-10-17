# Refonte UX/UI — Météo & Citation

Branche : `codex/refonte-ui`. Base : version 6.7. Views/XML conservés.

## Diagnostic et composition

L'ancien écran consacrait beaucoup de place aux actions, dupliquait la localisation,
séparait trop l'icône de la température, décalait UV et AQI et alignait les prévisions
en fonction de la longueur des températures. La citation partageait sa zone d'actions
avec le feedback et manquait d'identité.

Ordre conservé : ville → météo actuelle → UV/AQI → heures → sept jours → citation
→ liens secondaires. La ville dispose d'une ligne entière, sans ellipsis forcée.
Recherche, ajout, localisation et suppression restent directement accessibles.
À très grande police, les actions passent sous la saisie.

La citation est une conclusion éditoriale : sérif, guillemet, interligne, auteur
séparé et fond sans bordure ni élévation, qui se fond dans la page. Elle partage
une nuance légère avec la météo actuelle (soleil, nuages, pluie, neige, brume, orage).
La sélection/contextualisation des citations n'a pas changé.

## Composants

- `activity_main.xml` : structure et contenu centré, largeur maximale 720 dp.
- `item_city.xml` / `item_city_dropdown.xml` : noms complets et hauteur extensible.
- `SearchActionsRow` / `CurrentWeatherRow` : adaptation à la largeur réelle du texte,
  mesurée avant disposition, sans réduction de la taille de police choisie.
- `MetricLayout` / `item_metric.xml` : panneaux de même hauteur et champs alignés,
  côte à côte ou empilés selon largeur et police.
- `item_hourly.xml` / `item_daily.xml` et `ForecastRenderer` : grille constante,
  dates françaises, minima/maxima distincts, groupes de lecture accessibles.
- `QuotePresentation` : séparation du format texte/auteur déjà fourni par le repository.
- Ressources partagées : espacements 8/16/24/32 dp, rayon 16 dp, cibles tactiles
  48 dp minimum, styles de boutons secondaires, icônes vectorielles,
  couleurs distinctes pour les boutons désactivés.

Les repositories, ViewModel, API, transports réseau, widget, manifeste, applicationId,
version, signature et configuration Play sont inchangés. Seul le texte de repli d'une
erreur technique est remplacé à l'affichage par un message compréhensible.

## Vérification reproductible

Avec Java 17 et le SDK Android configurés :

```powershell
./gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
./tools/review-ui.ps1
```

Le script de rendu utilise uniquement `emulator-5554`, installe les APK debug/test,
enregistre puis restaure taille, densité, police et autorisations de localisation.
Ne pas le lancer sur un appareil personnel. Les captures et résultats sont dans
`app/build/ui-review/` ; ils ne sont pas ajoutés aux sources.

Les tests de rendu injectent des états déterministes dans la méthode de présentation
par réflexion, exclusivement dans androidTest. Aucun mode de démonstration ni
aucune donnée fictive ne sont intégrés à l'application distribuée.

| Profil portrait | Police | Contrôles |
| --- | --- | --- |
| 320 × 640 dp | 100 % | Panneaux empilés, ville longue, saisie, texte complet |
| 411 × 891 dp | 100 % | Panneaux côte à côte, alignements des champs et des jours |
| 800 × 1280 dp | 100 % | Contenu centré, largeur de lecture limitée |
| 320 × 640 dp | 200 % | Actions sous la saisie, texte extensible, défilement intégral |

Résultat final : 44 tests unitaires réussis et 16 exécutions instrumentées réussies
(quatre tests sur chacun des quatre profils).
Les tests unitaires comprennent les tests métier existants et deux tests du format citation.
Quatre tests instrumentés par profil : identité de l'application, recherche sans
autorisation, refus réel du dialogue de localisation, matrice de rendu.
Cette dernière couvre ville et citation longues, météo changeante, AQI indisponible,
chargement, erreur réseau et récupération, dimensions tactiles, couleurs de texte
(contraste minimal 4,5:1 sur les ambiances), lignes non tronquées et grille des sept jours.

Android Lint : zéro erreur, 57 avertissements (notamment dépendances,
ressources anciennes inutilisées, internationalisation et attributs selon API).

## Revue visuelle et limites

Captures contrôlées pour les trois tailles, la police à 200 %, les prévisions,
les citations longues et leurs ambiances, l'absence d'AQI et l'erreur réseau.
Les corrections issues de la revue concernent notamment le fond des villes récentes,
l'alignement des pics UV/AQI, la saisie étroite, les barres système et les boutons désactivés.

TalkBack a été activé sur l'émulateur ; son focus visible sur la ville a été contrôlé,
ainsi que le passage au champ de saisie puis à l'ajout avec la navigation clavier.
Les descriptions d'actions et groupes de prévisions sont vérifiés dans les vues.
La restitution audio n'a pas été écoutée ; un parcours vocal exhaustif sur appareil
physique reste à faire. Les tests de rendu couvrent Android API 36 ; ils ne constituent
pas une campagne multi-versions Android.

Les états d'erreur/AQI sont contrôlés par injection de présentation, indépendamment
de la disponibilité des serveurs. Une erreur réseau réelle a également été observée
pendant la revue. Le widget et l'envoi externe du feedback n'ont pas été testés de bout
en bout ; leurs fichiers et branchements sont conservés.
