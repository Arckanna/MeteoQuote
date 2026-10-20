# Widget compact — 20 octobre 2026

Branche : `codex/widget-compact`.

Le widget regroupe désormais icône, ville, température et état du ciel dans une seule bande, avec une icône de 32 dp et un padding de 8 dp. La température est affichée en degrés ; son libellé accessible précise Celsius. La zone d’actualisation reste de 48 × 48 dp. Un toucher sur le corps ouvre l’application, et le nom complet du lieu reste disponible aux services d’accessibilité.

La hauteur minimale passe de 110 à 80 dp. La cible de placement est de trois cellules sur une ligne sur les lanceurs qui prennent en charge ce réglage. La largeur minimale reste de 180 dp. Les dimensions réelles dépendent de la grille du lanceur. Les widgets déjà posés conservent l’espace alloué par l’utilisateur ; un appui long permet de les redimensionner.

La citation est masquée dans le format compact. Elle apparaît lorsque la hauteur disponible atteint 136 dp à police normale ; ce seuil augmente avec la police. Les grandes polices peuvent nécessiter d’agrandir le widget pour conserver toutes les informations. Les noms et descriptions longs utilisent une ellipse ; la température reste prioritaire.

Le redimensionnement met à jour localement la visibilité de la citation via une mise à jour partielle des RemoteViews. Il ne déclenche pas de requête météo. Le worker conserve un chargement mutualisé et publie une présentation adaptée aux dimensions de chaque widget.

Validation : compilation debug, tests unitaires et lint ; test instrumenté `WidgetLayoutTest` avec les vrais RemoteViews Android en 180 × 80 dp, 280 × 160 dp et 320 × 160 dp à police 200 %. Le test contrôle les débordements, la largeur de température et la cible d’actualisation. Les trois captures ont été revues dans `app/build/widget-review/`.

Le test de rendu ne remplace pas une vérification sur chaque lanceur constructeur. La mise en page conserve le provider existant et ne demande pas de supprimer les widgets déjà installés.

Référence : [dimensionnement des widgets Android](https://developer.android.com/develop/ui/views/appwidgets/layouts).
