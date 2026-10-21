# Widget compact — 20 octobre 2026

## Correctif du 21 octobre 2026

Branche `codex/widget-ajout-correctif`, basée sur `codex/widget-compact`. Le contrôle initial du rendu n’avait pas couvert le cycle réel du lanceur. Sur l’émulateur, les changements de composants WorkManager entraînaient des notifications de changement du provider, une reconstruction du widget sur son layout initial, puis des relances répétées du worker.

Le provider réaffiche désormais une vue complète depuis le dernier état rendu lors de ces notifications, sans relancer systématiquement le réseau. Cet état de présentation est enregistré dans les préférences déjà exclues de sauvegarde ; il ne constitue pas le cache météo Room du Lot 1. Le redimensionnement utilise également une vue complète pour préserver textes et actions après reconstruction.

L’ajout affiche immédiatement la ville et le chargement, avec les actions déjà branchées. La programmation du travail reste protégée par `goAsync` jusqu’à son enregistrement. Les demandes utilisateur utilisent le travail accéléré sur Android 12+ avec repli si le quota est épuisé ; les anciennes versions conservent le travail standard. Les identifiants fournis par le callback sont transmis au worker. Un filtre d’une minute par widget évite les relances système répétées ; l’actualisation manuelle reste disponible.

La hauteur minimale et de redimensionnement passe à 60 dp, avec padding de 4 dp. Le descriptif est masqué lorsque la hauteur ne suffit pas, et réapparaît en agrandissant. Le provider conserve une cible de trois colonnes sur une ligne.

Validation : tests unitaires existants et lint sans erreur ; ajout réel via `AppWidgetHost`, reconstruction, notifications répétées sans nouvelle chaîne de travaux, rendu à 60/80/160 dp et police 200 %, construction Hilt du worker. Builds debug et release R8 réussis. Sur le lanceur Pixel de l’émulateur, le widget existant a retrouvé sa météo et a été réduit à une ligne ; captures dans `app/build/widget-review/launcher-fixed.png` et `launcher-compact.png`. Les données du téléphone et le widget existant ont été conservés.

Recette finale : 58 tests unitaires réussis et quatre tests Android ciblés réussis. Le test d’hôte suit les travaux par identifiant de widget, attend leur exécution, vérifie que les données survivent à la remise en page et que le bouton d’actualisation programme encore une nouvelle demande. Lint : aucune erreur, 71 avertissements. Le correctif a été installé sur l’émulateur connecté ; le checkout Android Studio sur `D:` reste inchangé.

Les paragraphes suivants décrivent la première version du 20 octobre ; les dimensions et le mécanisme de mise à jour ci-dessus les remplacent.

Branche : `codex/widget-compact`.

Le widget regroupe désormais icône, ville, température et état du ciel dans une seule bande, avec une icône de 32 dp et un padding de 8 dp. La température est affichée en degrés ; son libellé accessible précise Celsius. La zone d’actualisation reste de 48 × 48 dp. Un toucher sur le corps ouvre l’application, et le nom complet du lieu reste disponible aux services d’accessibilité.

La hauteur minimale passe de 110 à 80 dp. La cible de placement est de trois cellules sur une ligne sur les lanceurs qui prennent en charge ce réglage. La largeur minimale reste de 180 dp. Les dimensions réelles dépendent de la grille du lanceur. Les widgets déjà posés conservent l’espace alloué par l’utilisateur ; un appui long permet de les redimensionner.

La citation est masquée dans le format compact. Elle apparaît lorsque la hauteur disponible atteint 136 dp à police normale ; ce seuil augmente avec la police. Les grandes polices peuvent nécessiter d’agrandir le widget pour conserver toutes les informations. Les noms et descriptions longs utilisent une ellipse ; la température reste prioritaire.

Le redimensionnement met à jour localement la visibilité de la citation via une mise à jour partielle des RemoteViews. Il ne déclenche pas de requête météo. Le worker conserve un chargement mutualisé et publie une présentation adaptée aux dimensions de chaque widget.

Validation : compilation debug, tests unitaires et lint ; test instrumenté `WidgetLayoutTest` avec les vrais RemoteViews Android en 180 × 80 dp, 280 × 160 dp et 320 × 160 dp à police 200 %. Le test contrôle les débordements, la largeur de température et la cible d’actualisation. Les trois captures ont été revues dans `app/build/widget-review/`.

Le test de rendu ne remplace pas une vérification sur chaque lanceur constructeur. La mise en page conserve le provider existant et ne demande pas de supprimer les widgets déjà installés.

Référence : [dimensionnement des widgets Android](https://developer.android.com/develop/ui/views/appwidgets/layouts).
