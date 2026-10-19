# Météo & Citation — Cahier des charges et roadmap

Version de cadrage : 1.0 — 19 octobre 2026.
Statut : proposition de périmètre et de critères de recette ; les évolutions décrites ne sont pas encore implémentées.
Référence du projet consulté : application 7.1, versionCode 19.

## 1. Vision produit

Permettre une consultation courte et agréable : « Je regarde le temps qu’il fait, je découvre l’humeur du ciel, puis je consulte les prévisions si j’en ai besoin. »

La température et l’état du ciel doivent être immédiatement compréhensibles. La citation quotidienne apporte la personnalité et donne une raison de revenir. La simplicité, la lisibilité et la fiabilité priment sur le nombre de fonctions et les effets visuels.

Public visé : personnes recherchant une météo quotidienne accessible, accompagnée d’une touche éditoriale, sans compte utilisateur nécessaire.

### Objectifs observables

- Comprendre la météo actuelle et identifier la citation au premier écran ou après un léger défilement à police normale.
- Retrouver des données utiles après une consultation réussie, même sans réseau.
- Découvrir une citation quotidienne stable et renouvelée d’un jour à l’autre.
- Consulter des informations cohérentes dans l’application, le widget et, ultérieurement, les notifications.
- Conserver les fonctions utilisables à police 200 %, avec TalkBack et sur petit écran.

## 2. État de départ et contraintes

### Éléments constatés dans le projet

- MVVM, StateFlow, interfaces de repositories et injection Hilt.
- Open-Meteo via OkHttp, délais bornés, retry contrôlé et AQI facultatif.
- Interface hybride : citation Compose Material 3, sections météo et prévisions en Views/XML.
- Ordre actuel : ville → météo actuelle → UV/AQI → citation → horaires → trois/sept jours → actions secondaires.
- Widget existant, actualisé par WorkManager.
- Suppression du bouton « Nouvelle citation » dans l’interface.
- 58 tests unitaires réussis lors des vérifications précédentes, tests Android et captures sur quatre profils. Ces résultats historiques ne remplacent pas une recette de chaque future version.

L’appellation « Clean Architecture complète » n’est pas un objectif à elle seule : conserver une séparation proportionnée entre présentation, accès aux données et modèles, sans multiplier les couches sans besoin.

### Point à corriger en priorité

`LocalQuoteRepository.getQuote` conserve un index par catégorie météo. La date sert de valeur initiale lorsque cet index n’existe pas encore ; elle ne provoque pas systématiquement son renouvellement les jours suivants. Le contrat « une journée, une citation » nécessite donc une évolution métier explicite et des tests dédiés.

### Contraintes de compatibilité

- Conserver applicationId, signature et compatibilité avec les installations existantes.
- Conserver minSdk 24 tant qu’un changement n’a pas été explicitement décidé.
- Préserver les villes enregistrées, la ville sélectionnée et les préférences.
- Préserver recherche, ajout/suppression, localisation facultative, actualisation, météo, UV, AQI, prévisions, citation, widget, feedback, confidentialité et sources.
- Toute nouvelle permission doit correspondre à une fonction activée volontairement.
- Aucun compte, service distant supplémentaire, analyse d’usage ou collecte de localisation ajoutés par défaut.
- Livrer les évolutions par lots indépendants, testés, avec un retour à la version précédente préparé avant diffusion.

## 3. Priorités proposées

| Priorité | Évolution | Valeur attendue | Dépendance principale |
|---|---|---|---|
| P0 | Citation quotidienne fiable et contenu enrichi | Rend la promesse de l’application tangible | Règle de date et identité de citation |
| P0 | Cache local Room et affichage des données périmées | Rend l’application utile hors ligne | États de fraîcheur des données |
| P0 | Tests de référence et recette des mises à jour | Sécurise les migrations | États de test déterministes |
| P1 | Migration Compose par sections | Simplifie le rendu et les évolutions UX | Tests visuels en place |
| P1 | Thème cohérent, sombre et couleurs dynamiques | Améliore le confort et la personnalisation | Tokens visuels partagés |
| P1 | Widget modernisé, Glance si le prototype convient | Rend météo et citation visibles hors de l’application | Cache et citation partagés |
| P2 | Résumé matinal facultatif | Crée un rendez-vous quotidien | Données et citation fiables |
| P2 | Conseils de conditions météo marquées | Aide à interpréter les prévisions | Données fraîches, règles explicables |
| P3 | Retrofit et sérialisation typée | Améliore la maintenance si nécessaire | Parité réseau démontrée |
| P3 | Animation d’ambiance légère | Apporte une finition visuelle | Mesures de fluidité et d’accessibilité |

Les changements de bibliothèques ne constituent pas, seuls, des bénéfices utilisateur. La réussite d’une migration sera évaluée sur la maintenance, la fiabilité et les performances mesurées.

## 4. Exigences fonctionnelles

### F01 — Citation quotidienne et identité éditoriale

Proposition de règle : une citation principale par date locale du téléphone, commune à l’application, au widget et aux notifications. Son ambiance est choisie à partir de la première météo exploitable du jour ; la citation reste ensuite stable jusqu’au changement de date, même après actualisation, changement de ville ou redémarrage.

Le fond et l’accroche peuvent suivre la météo affichée. Le texte est sélectionné selon l’ambiance au moment de son attribution ; sa stabilité quotidienne est prioritaire ensuite. Cette distinction évite de promettre une nouvelle sélection à chaque changement du ciel.

Exigences :

- Identifiants stables de citation, texte, auteur facultatif, catégorie et provenance éditoriale.
- Persistance de la date d’attribution et de l’identifiant choisi, accessible aux différents points d’entrée.
- Une attribution unique même si worker, widget et écran demandent la citation simultanément.
- Renouvellement au jour suivant ; éviter la répétition immédiate lorsqu’une alternative existe.
- Aucune action de renouvellement manuel.
- Citation présente hors ligne. En l’absence de météo connue, utiliser une catégorie neutre et figer cette attribution pour la journée.
- Réévaluation de la date au retour au premier plan et dans les workers ; pas de tâche nocturne permanente uniquement pour faire tourner le texte.
- Horloge injectable et tests autour de minuit, changement d’heure, changement de fuseau et retour à une date déjà rencontrée. Réutiliser l’attribution d’une date déjà connue plutôt que relancer un tirage.
- Migration des anciennes préférences sans réintroduire de bouton de changement.

Contenu : viser une première collection de 60 à 100 citations ou pensées validées, avec une couverture équilibrée des ambiances. Ce volume reste une cible éditoriale, à ajuster au budget et aux droits d’utilisation. Vérifier les attributions ; employer des textes originaux ou utilisables avec une provenance documentée. Prévoir des formulations qui restent naturelles lorsque la météo change dans la journée.

Critères de recette : même texte et même auteur pendant une journée dans les trois points d’entrée ; nouvelle attribution au jour suivant ; aucun auteur inventé ; citation longue et auteur absent correctement affichés ; aucun réseau nécessaire pour sélectionner le texte local.

### F02 — Consultation hors ligne avec Room

Le stockage local devient la source des données présentées à l’interface. Le réseau met à jour ce stockage après validation, selon le principe offline-first décrit par Android. [Référence](https://developer.android.com/topic/architecture/data-layer/offline-first)

Données à conserver : identité du lieu, coordonnées nécessaires à la requête, fuseau fourni par l’API, météo courante, prévisions horaires et quotidiennes, UV, AQI et horodatages. Utiliser des dates et types explicites ; ne pas stocker uniquement des libellés formatés pour l’écran.

Exigences :

- Afficher immédiatement le dernier instantané disponible pour le lieu demandé, puis actualiser en arrière-plan si nécessaire.
- Distinguer date de récupération et date de mesure lorsque le fournisseur la fournit ; ne pas présenter l’heure de téléchargement comme une observation météo.
- Traiter l’AQI comme une donnée facultative possédant son propre statut et sa propre fraîcheur.
- En cas d’échec réseau, conserver les dernières données et afficher un message discret avec « Réessayer ».
- Ne jamais afficher silencieusement les données de la ville précédente pendant un changement de ville sans cache.
- Éviter les écritures incohérentes : un instantané météo complet remplace l’ancien de manière atomique ; une réponse tardive reste associée au bon lieu.
- Supprimer ou identifier comme passées les heures et journées dont la période est terminée. Ne pas déplacer artificiellement leurs dates.
- Dédupliquer les rafraîchissements pour un même lieu et borner la fréquence des appels.
- Recherche d’une nouvelle ville indisponible sans réseau : proposer les villes et récents déjà connus avec un message clair.
- Les coordonnées issues de « Ma position » ne constituent pas un historique de déplacements.

Politique initiale proposée, configurable dans le code et couverte par des tests :

| Ancienneté depuis récupération | Présentation et comportement |
|---|---|
| Jusqu’à 30 min | Dernières données considérées récentes ; actualisation manuelle possible |
| De 30 min à 24 h | Afficher les données avec leur âge et tenter une mise à jour ; signaler l’absence de réseau si nécessaire |
| Plus de 24 h | Qualifier explicitement la météo comme « Dernières données connues » ; ne plus la présenter comme actuelle |
| Plus de 7 jours | Purger le cache météo expiré ; conserver les villes et les préférences |

Ces seuils sont des choix produit à tester, pas des garanties de fraîcheur des fournisseurs. L’AQI pourra avoir un seuil distinct. Un AQI ancien ne doit jamais être affiché comme une qualité de l’air actuellement garantie.

Prévoir des états distincts : première ouverture sans cache, cache récent, cache ancien en cours de mise à jour, échec avec cache, échec sans cache, résultat partiel sans AQI. Un rafraîchissement ne doit pas vider inutilement tout l’écran.

Critères de recette : après une première consultation réussie, un redémarrage en mode avion retrouve la météo, les prévisions encore pertinentes et la citation ; un échec d’actualisation conserve ces informations ; une première ouverture sans données présente une explication et un moyen de réessayer ; la migration de base ne supprime pas les préférences existantes.

### F03 — Interface Compose et navigation

Conserver l’ordre ville → météo actuelle → UV/AQI → citation → horaires → aperçu trois/sept jours → actions secondaires.

Ordre de migration conseillé : prévisions horaires et quotidiennes, météo actuelle, UV/AQI, puis recherche/sélection et structure de l’écran. La citation étant déjà en Compose, conserver son rendu et son absence de bouton de changement.

Exigences :

- Composants recevant des états et des callbacks ; aucun accès réseau dans les composables.
- Collecte des flux liée au cycle de vie.
- Conservation de la sélection de ville, de l’état trois/sept jours et du défilement lors d’une recréation lorsque pertinent.
- Trois journées issues de l’API visibles initialement, aujourd’hui compris si c’est la première entrée ; sept au dépliage.
- Prévisions horaires compactes, régulières et défilant horizontalement.
- Sur tablette, largeur de lecture maîtrisée ; une disposition à plusieurs colonnes n’est retenue qu’après validation visuelle.
- Suppression des anciens layouts et renderers seulement après parité fonctionnelle vérifiée.

Critères de recette : toutes les actions existantes restent accessibles ; aucune perte de ville, de citation ou d’état au changement d’orientation ; pas de différence de données entre ancien et nouveau rendu pour une même fixture.

### F04 — Thème, Material You et accessibilité

Définir des tokens partagés : couleurs de surface et de texte, espacements, rayons, tailles typographiques et niveaux d’importance des actions. Les couleurs dynamiques sont disponibles à partir d’Android 12 ; les anciennes versions utilisent une palette de secours. [Référence](https://developer.android.com/develop/ui/views/theming/dynamic-colors)

Proposition : modes clair, sombre et système, avec option « Couleurs du téléphone ». Activer les couleurs dynamiques selon un choix explicite de préférence ; conserver une palette météo reconnaissable pour la citation.

Exigences :

- Les accents météo restent subtils et compatibles avec les palettes dynamiques.
- Les couleurs sémantiques UV/AQI ne sont pas remplacées arbitrairement par celles du fond d’écran.
- Niveau et valeur toujours exprimés par du texte, en complément des couleurs.
- Cibles interactives d’au moins 48 dp ; contrastes visés 4,5:1 pour texte courant et 3:1 pour grand texte.
- Police système à 200 %, noms de ville et citations longs, navigation TalkBack et ordre de lecture testés.
- Pas de réduction automatique de police pour faire entrer les textes dans une hauteur fixe.
- Tests avec plusieurs palettes dynamiques, et captures de référence avec palette contrôlée.

Critères de recette : absence de texte coupé sur les quatre profils de référence ; titre et auteur de citation distingués ; aucune information exprimée uniquement par couleur ; saisie manuelle disponible après refus de localisation.

### F05 — Widget modernisé

Objectif : rendre météo et citation utiles depuis l’écran d’accueil, indépendamment du choix de bibliothèque.

Prévoir un format compact (ville, icône, température, fraîcheur) et un format étendu ajoutant la citation et l’auteur lorsque la place le permet. Si le texte complet ne tient pas, afficher un extrait clairement identifié ; le toucher ouvre le bloc citation complet dans l’application.

Exigences :

- Source locale et attribution quotidienne communes avec l’application.
- Chargement du cache avant toute tentative réseau.
- Gestion du redimensionnement, du processus détruit, du redémarrage et de plusieurs widgets.
- Actualisation mutualisée pour éviter une requête identique par widget.
- Gestion d’un lieu supprimé et affichage explicite des données anciennes.
- Préserver autant que possible les widgets déjà placés ; tester une mise à jour depuis la version publiée avant de changer l’identité du provider.
- Choix du lieu par widget envisagé après le premier lot ; il ne doit pas être ajouté implicitement au périmètre initial.

Un prototype Glance doit valider ces exigences avant migration. Glance possède ses propres composants : le composable de l’écran ne peut pas être réutilisé directement tel quel dans le widget. Partager les modèles de présentation et les tokens appropriés. [Référence](https://developer.android.com/develop/ui/compose/glance)

### F06 — Résumé matinal facultatif

Proposer un réglage « Recevoir ma météo du matin », désactivé initialement. Choisir une heure souhaitée et une ville de référence ; fournir un aperçu et une désactivation simple.

Contenu : ville, plage de températures du jour, état du ciel et citation quotidienne. Une seule notification par date locale et par programmation. Le clic ouvre l’application sur la bonne ville.

Exigences :

- Demander la permission de notification au moment de l’activation, lorsque nécessaire sous Android 13 et suivants ; gérer le refus sans bloquer l’application. [Référence](https://developer.android.com/develop/ui/compose/notifications/notification-permission)
- Programmer des travaux uniques, éviter les doublons après redémarrage, changement de réglage ou changement d’heure.
- Utiliser le cache et une actualisation bornée ; ne pas envoyer silencieusement une météo ancienne comme si elle était récente.
- Si aucune météo suffisamment récente n’est disponible dans la fenêtre retenue, sauter le résumé de la journée plutôt qu’envoyer tardivement une notification trompeuse.
- Horaire approximatif annoncé dans le réglage ; WorkManager peut être retardé par les contraintes et les optimisations système. Ne pas promettre une livraison exactement à 7 h 00. [Référence](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work)
- Proposition de fenêtre : heure souhaitée jusqu’à deux heures plus tard ; ne pas rattraper le résumé en soirée.
- Aucun suivi continu de la position ; utiliser une ville déjà choisie.

Critères de recette : activation/refus/désactivation testés ; pas de doublon le même jour ; citation identique à l’application ; comportement défini hors ligne, sous économie d’énergie et après modification du fuseau.

### F07 — Conditions marquées et conseils

Premier lot éventuel : bannières dans l’application pour UV élevé ou autre condition calculable à partir des données disponibles. Les règles, unités, période et seuils doivent être écrits, sourcés et testés avant développement.

Exigences :

- Dire « Conditions prévues » ou « Conseil météo » ; réserver une présentation d’alerte officielle à une future source officielle adaptée.
- Ne pas déduire un phénomène complexe, par exemple une canicule officielle, d’un seul seuil de température.
- Vérifier la présence des variables nécessaires ; les données actuelles ne suffisent pas nécessairement pour caractériser des pluies extrêmes.
- Ne déclencher aucun conseil sur une donnée absente ou trop ancienne.
- Afficher un seul message prioritaire, explicable, sans masquer température ni citation.
- Aucune notification supplémentaire d’alerte dans le périmètre du résumé matinal initial.

### F08 — Animations discrètes

Retenir d’abord une animation locale de faible amplitude ou une transition courte d’ambiance. Éviter un arrière-plan animé permanent derrière les informations et les textes.

Respecter le réglage de réduction/désactivation des animations, arrêter le mouvement hors écran et au passage en arrière-plan, proposer un rendu statique complet. Mesurer le coût avant/après. Lottie est une option d’implémentation à comparer à Compose Graphics ; il ne constitue pas une dépendance obligatoire.

Critères de recette : aucune baisse mesurable et persistante de fluidité sur l’appareil de référence ; aucune requête réseau nécessaire aux assets ; lisibilité inchangée et absence de mouvement lorsque désactivé.

## 5. Réseau et architecture cible

Conserver l’interface des repositories, Hilt et les règles d’annulation. Room apporte une source locale distincte de la source réseau ; l’interface expose les données, leur âge et leur statut au ViewModel. Le widget et les workers réutilisent ces mêmes règles.

Une migration Retrofit/Serialization peut être étudiée ensuite si elle simplifie réellement les DTO, la validation et les nouveaux endpoints. Le remplacement d’OkHttp n’est pas requis : Retrofit peut s’appuyer sur le client OkHttp existant. Le cache métier Room reste distinct d’un cache HTTP.

Conditions de migration réseau :

- Un seul propriétaire du retry pour éviter la multiplication des tentatives entre client, repository et worker.
- Conserver les délais, budget global, annulation, erreurs HTTP, Retry-After et fallback AQI existants.
- Tests de parité des paramètres envoyés, fuseaux, séries horaires, champs absents/null et valeurs incohérentes.
- DTO distants séparés des modèles persistés et affichés ; validation avant écriture du cache.
- Aucun changement de fournisseur météo induit par cette migration.

Les choix de versions seront arrêtés au début de chaque lot après vérification de leur compatibilité avec Kotlin, AGP, Compose, Hilt et minSdk. Aucune mise à niveau globale n’est imposée par ce document.

## 6. Tests, performances et livraison

### Couverture attendue

| Niveau | Vérifications principales |
|---|---|
| Unitaires | Attribution quotidienne, fraîcheur et expiration, erreurs, annulation, sélection de ville, règles de conseils |
| Room | Transactions, migrations, cache multi-ville, données partielles, suppression, purge |
| Réseau simulé | Hors ligne, timeout, 404/429/503, Retry-After, réponse invalide, météo correcte/AQI absent |
| Compose UI | Recherche, changement de ville, repliage, erreurs, récupération, texte long et sémantique |
| Captures comparées | États déterministes, couleurs contrôlées, clair/sombre, compact/standard/tablette et police 200 % |
| Android réel/émulateur | Permissions, redémarrage, widget, notifications, économie d’énergie, mise à jour d’une installation existante |

Choisir un seul outil principal de captures comparées après un prototype de compatibilité, par exemple Roborazzi. Les captures actuelles restent utiles, mais des images enregistrées sans comparaison automatique ne constituent pas à elles seules un test de non-régression visuelle. Les différences attendues seront approuvées explicitement, jamais réenregistrées automatiquement pour rendre un test vert.

### Matrice minimale

- 320 × 640 dp, 411 × 891 dp, 800 × 1280 dp ; compact à police 200 %.
- Portrait et paysage ; système de navigation par gestes et barres système correctement gérés.
- API 24, une version intermédiaire pertinente et API 36 ; au moins un téléphone physique avant diffusion.
- Ville longue, températures négatives, auteur absent, citation longue, changement de météo, AQI absent, cache périmé, refus de permissions et absence de réseau.
- Changement d’heure/date/fuseau, processus tué, widget déjà installé et migration de données existantes.

### Mesures proposées

Établir un état de référence sur un appareil et un build release fixes, puis comparer sur les mêmes conditions. Objectifs initiaux à confirmer par mesure : lecture du cache sans réseau et affichage utile dans la seconde suivant l’ouverture ; aucune régression de plus de 10 % sur le temps d’ouverture médian sans justification ; pas de requêtes dupliquées pour un même rafraîchissement ; taille de cache et APK suivie à chaque lot.

La stabilité en production sera suivie avec les indicateurs Play déjà disponibles. Aucun ajout d’analytics n’est nécessaire pour cette roadmap. L’intérêt du widget et du résumé peut d’abord être évalué par une petite bêta et du feedback volontaire.

### Définition de terminé

Code revu ; tests pertinents réussis ; build release avec R8 réussi ; revue manuelle des captures et TalkBack ; recette de mise à jour ; migration testée ; sources/confidentialité actualisées si les données ou permissions changent ; notes de version rédigées ; retour à la version précédente étudié.

Le rollback d’une version utilisant un nouveau schéma Room doit être préparé : ne pas supposer qu’un ancien binaire saura lire une base plus récente. Éviter les migrations destructives silencieuses. Pour les tests, utiliser des installations isolées lorsque le numéro de version installé empêche une mise à jour ; ne pas désinstaller une version utilisateur pour contourner le problème.

## 7. Données personnelles et maintenance

- Stocker uniquement les lieux et données nécessaires aux fonctions activées ; permettre d’effacer les données locales depuis les réglages.
- Mettre à jour les exclusions de sauvegarde pour la future base Room contenant des coordonnées, en cohérence avec les préférences actuelles.
- Retirer les entrées locales devenues inutiles selon la politique de rétention ; ne pas créer d’historique de localisation.
- Éviter les coordonnées et le contenu sensible dans les logs ; messages d’erreur utilisateur compréhensibles.
- Documenter les nouveaux traitements et permissions dans la confidentialité et les déclarations de distribution applicables lors de la livraison.
- Conserver les crédits et sources météo, AQI et citations accessibles dans les actions secondaires.

## 8. Roadmap et charge indicative

Estimations en jours-personne de développement, tests et documentation compris. Elles supposent une personne familière du projet et excluent les délais de validation Play, le recrutement de testeurs et l’acquisition des droits éditoriaux. Elles seront affinées après le cadrage et les prototypes.

| Lot | Contenu livrable | Charge indicative | Condition de passage |
|---|---|---|---|
| 0 — Promesse et références | Règle quotidienne, correction de sélection, identification des textes, états de test et première référence de performances | 2–4 j | Même citation quotidienne dans les points d’entrée existants |
| 1 — Hors ligne | Room, migrations, cache multi-ville, fraîcheur et nouveaux états UI | 5–8 j | Recette complète en mode avion et sans cache |
| 2 — Expérience visuelle | Migration Compose par sections, tokens, sombre/dynamique, captures comparées | 6–10 j | Parité fonctionnelle et accessibilité sur les profils retenus |
| 3 — Widget | Prototype Glance puis widget compact/étendu, cache partagé et mise à jour compatible | 3–6 j | Widget installé avant mise à jour toujours utilisable |
| 4 — Rendez-vous matinal | Réglages, permission, programmation, déduplication et recette système | 3–6 j | Notification facultative fiable dans sa fenêtre annoncée |
| Option A | Conseils de conditions marquées après validation des variables et règles | 2–4 j | Seuils documentés, données récentes, aucune confusion avec une alerte officielle |
| Option B | Retrofit/Serialization si le bénéfice de maintenance est confirmé | 3–5 j | Parité des tests réseau |
| Option C | Animation subtile et mesure de son coût | 1–3 j | Fluidité, lisibilité et réduction des animations validées |

Socle et expérience visuelle, lots 0 à 3 : environ 16–28 jours-personne. Avec le résumé matinal : 19–34 jours-personne. Prévoir une marge de planification de 20–30 % pour les comportements propres aux appareils et aux migrations. L’enrichissement éditorial peut avancer en parallèle, avec une charge dépendant des textes et de leur validation.

Chaque lot constitue une livraison testable distincte ; ne pas publier simultanément une nouvelle base, une réécriture réseau et une refonte complète de l’interface. Une bêta intermédiaire suit au minimum les lots hors ligne, interface et notifications.

## 9. Décisions produit proposées pour le premier cadrage

1. Une citation globale par date locale du téléphone, stable malgré les changements de ville ; ambiance graphique toujours liée à la météo affichée.
2. Pas de bouton de changement ; enrichir le contenu et garantir son renouvellement quotidien.
3. Seuils de cache 30 min / 24 h / purge à 7 jours à confirmer en bêta.
4. Widget compact et étendu partageant initialement la ville de référence ; configuration par widget ultérieure.
5. Résumé matinal désactivé par défaut, horaire approximatif et fenêtre maximale de deux heures.
6. Thèmes clair/sombre/système ; couleurs du téléphone proposées en option.
7. Conseils intégrés et animation discrète en lots facultatifs ; notifications d’alertes officielles hors périmètre initial.

Ces propositions rendent le périmètre implémentable. Elles peuvent être ajustées avant le lot concerné sans lancer d’emblée toutes les évolutions.
