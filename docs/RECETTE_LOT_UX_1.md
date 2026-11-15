# Recette du lot UX 1 — 14–15 novembre 2026

Branche : `codex/ux-etats-libelles`. Cette recette porte sur la fraîcheur, la recherche multirésultat et la gestion des villes. Les résultats locaux ne certifient pas le comportement de tous les téléphones ni de tous les lanceurs Android.

## Liste à effectuer sur votre appareil

Noter le modèle, la version Android, le lanceur, la version de TalkBack et la version de l'application installée. Pour chaque échec, noter les étapes, le résultat observé et joindre une capture.

| Fait | Test | Résultat attendu |
|---|---|---|
| ☐ | Ouvrir l'application, faire défiler les prévisions puis revenir en haut. | Aucun bloc inaccessible ; « Ma position » et « Gérer mes villes » sont lisibles et activables. |
| ☐ | Saisir « Paris » et lancer la recherche. | Au maximum cinq villes avec région/pays disponibles ; la ville courante ne change pas avant le choix. |
| ☐ | Choisir un homonyme dans une autre région ou un autre pays. | Le bon lieu est ajouté et sélectionné ; la météo correspond à ce lieu. |
| ☐ | Relancer une recherche, modifier rapidement le texte puis choisir une ville enregistrée pendant l'attente. | L'ancienne réponse ne change ni la sélection ni la nouvelle saisie. Répéter sur réseau lent si les réponses arrivent trop vite. |
| ☐ | Annuler une recherche en cours, puis relancer. | Le chargement s'arrête, le texte reste, aucun ajout tardif ; la nouvelle recherche fonctionne. |
| ☐ | Rechercher un nom inexistant. | Message sans résultat près du champ, saisie conservée, météo courante toujours disponible. |
| ☐ | Avec le clavier ouvert, modifier la saisie, utiliser Retour puis tourner le téléphone pendant une recherche ou l'affichage des résultats. | Le clavier peut être fermé ; la saisie et les résultats valides survivent, sans ajout automatique ni doublon. |
| ☐ | Ouvrir « Gérer mes villes », choisir une ville, puis demander une suppression et l'annuler. | Le choix fonctionne ; Annuler ne supprime rien. |
| ☐ | Confirmer la suppression d'une ville non sélectionnée, puis de la ville sélectionnée. | Seule la ville nommée disparaît ; la sélection reste si elle existe encore, sinon une autre ville est choisie. La dernière ville reste protégée. |
| ☐ | Afficher une météo puis ouvrir « Détails des données ». | Les heures des conditions et du téléchargement sont distinctes, avec fuseau/décalage ; « Fermer » revient à l'écran. |
| ☐ | Revenir après au moins une heure sans nouvelle observation, ou utiliser un cache ancien. | « Données anciennes » et « Dernières conditions connues » restent visibles. Un téléchargement récent ne suffit pas à rendre l'observation actuelle. |
| ☐ | Charger une ville, activer le mode avion, relancer l'application et actualiser. | Les données utilisables restent présentes ; l'échec est compréhensible. Une erreur de connexion seule ne doit pas être appelée « Hors ligne » sans preuve. |
| ☐ | Toujours sans réseau, rechercher une ville et choisir une ville enregistrée jamais chargée. | La recherche conserve le texte ; une ville sans cache n'affiche pas la météo de la précédente. Aucun chargement infini. |
| ☐ | Rétablir le réseau puis utiliser « Réessayer » / la recherche. | Les erreurs disparaissent après réussite et le lieu sélectionné reste correct. |
| ☐ | Régler la police à 200 %, puis essayer recherche, résultats, gestion, confirmation et détails en portrait et paysage. | Textes sans troncature gênante, boutons atteignables par défilement, clavier et boîtes de dialogue utilisables. Restaurer ensuite votre réglage. |
| ☐ | Activer TalkBack et parcourir tous les contrôles avec des balayages, puis les activer par double toucher. | Nom et rôle compréhensibles ; ordre cohérent ; chaque résultat et bouton de suppression désigne le bon lieu ; le focus permet de sortir des dialogues. |
| ☐ | Avec TalkBack, lancer/annuler une recherche, provoquer une erreur, ouvrir les détails puis attendre une minute. | Les changements utiles sont annoncés ; pas de lecture répétée de toute la météo à chaque minute ; les deux dates sont compréhensibles à l'écoute. |
| ☐ | Ajouter le widget au lanceur ; sélectionner A puis B rapidement dans l'application et revenir à l'accueil. | Le widget finit sur B et la citation attendue. Refaire sans réseau, avec une ville sans cache : le nom reste B, sans météo de A. |
| ☐ | Actualiser le widget pendant un chargement, puis changer de ville. Redimensionner le widget et ouvrir l'application depuis celui-ci. | La demande forcée n'est pas perdue ; la dernière sélection est conservée ; boutons et texte restent utilisables. |
| ☐ | Tester « Ma position » avec autorisation accordée, refusée puis localisation désactivée. | Aucune suppression ni sélection tardive inattendue ; la recherche manuelle reste disponible. |

## Validation locale

Environnement : Windows, JDK 17, SDK Android 36, AVD `Medium_Phone_API_36.0`. Les données météo des captures sont celles reçues pendant les essais ; les tests de concurrence et de fraîcheur utilisent aussi des scénarios contrôlés.

| Vérification | Résultat |
|---|---|
| `testDebugUnitTest` | 141 tests réussis, aucun échec. Dont réponses obsolètes, sélection explicite, suppression par identité, fraîcheur et ordonnancement du widget. |
| `lintDebug` | Réussi : 0 erreur, 77 avertissements. Ce résultat ne signifie pas « aucun avertissement ». |
| `assembleDebug`, `assembleRelease`, `assembleDebugAndroidTest` | Réussis. APK release non signé ; aucune publication effectuée. |
| Recette instrumentée finale du 15 novembre | 14 tests réussis sur chacun des quatre profils : compact 320×640 dp, standard 411×891 dp, tablette 800×1280 dp, compact 320×640 dp avec police à 200 %. Soit 56 exécutions réussies, sans échec. Clavier, recréation, gestion/confirmation, détails, cache et widget inclus. |
| Recherche réelle « Paris » au clavier | Cinq homonymes avec région/pays ; sélection conservée jusqu'au choix explicite de Paris en France. |
| Rotation réelle portrait → paysage → portrait | Saisie « Paris » et résultats conservés, puis choix possible. |
| Wi-Fi et données mobiles désactivés | Météo en cache conservée, message d'échec et Réessayer ; recherche « Lyon » en erreur sans effacer la saisie. Réseau restauré ensuite. |
| Widget sans réseau | `WidgetHostTest` réussi : ajout dans un véritable hôte Android, actualisation, passage rapide A → B dans le ViewModel de l'activité, rendu final B y compris sans cache disponible initialement. |
| TalkBack | Service activé et lié, exploration tactile active vérifiée ; parcours au clavier, ouverture/fermeture du dialogue de gestion vérifiés. Réglages restaurés. Prononciation, confort d'écoute et gestes tactiles complets restent à confirmer sur téléphone. |

### Corrections issues de la recette

- Les actions principales partagent une ligne uniquement si les deux libellés tiennent entièrement ; sinon elles s'empilent. Le test compact de visibilité de la citation conserve son seuil initial.
- Le bouton des détails reprend le style des autres actions, sans capitales forcées.
- Une frappe réelle avait révélé une réinjection transitoire de l'ancien brouillon : l'invalidation et le nouveau texte sont désormais publiés atomiquement par le ViewModel. L'activité ne recopie plus chaque émission dans le champ pendant la frappe ; elle restaure le texte à la création et le vide après un choix valide. Le test Android attend la connexion du clavier avant d'injecter les caractères, puis vérifie la conservation après recréation.
- Le champ de ville demande au clavier de désactiver les suggestions automatiques. Le clavier anglais de l'émulateur substitue toutefois « Saint Martin » lors de certaines frappes synthétiques : ce comportement ne peut pas être garanti pour tous les claviers. Le test vérifie séparément la frappe réelle de « Paris » et la conservation d'un brouillon composé après recréation. Refaire les noms propres avec votre clavier fait partie de la recette physique.
- Le test de dialogue cible explicitement la fenêtre du dialogue. L'empilement à 200 % est vérifié avec les polices réelles d'Android, plutôt qu'avec les métriques simulées de Robolectric.

### Captures conservées

- [Accueil actuel](../art/screenshot_main.png).
- [Recherche avec cinq homonymes](../art/ux-lot-1/recherche.png).
- [Résultats après rotation en paysage](../art/ux-lot-1/recherche-paysage.png).
- [Petit écran et police 200 %](../art/ux-lot-1/police-200.png).
- [Erreur de recherche sans réseau et météo en cache](../art/ux-lot-1/hors-ligne.png).

Les rapports détaillés sont générés sous `app/build/reports/`, les sorties instrumentées sous `app/build/ui-review/` et les captures de manipulation sous `app/build/ux-validation/`. Ces dossiers ne sont pas versionnés et peuvent disparaître après nettoyage. Les captures ci-dessus restent dans `art/`.

### Limites et suites

L'hôte de test Android vérifie le fournisseur et les workers réels, mais ne remplace pas la recette sur le lanceur de votre téléphone, notamment après redimensionnement et restrictions de batterie. La détection dédiée du mode hors ligne reste hors de ce lot : une erreur réseau affiche un échec, sans inventer un statut de connectivité. Aucun test sur appareil physique n'a été effectué ici ; les cases de la checklist restent volontairement décochées.
