# Optimisation widget et réseau

Guide relu le 8 novembre 2026, sur la version déclarée 7.4. Le rendu des listes et le stockage JSON/Room restent des sujets d'optimisation distincts.

## Comportement

- L’écran compare uniquement les informations affichées par le widget avant d’envoyer ACTION_RENDER. Un changement de ville, de météo, de citation, de date de téléchargement ou le franchissement du seuil d’ancienneté provoque un rendu ; le chargement et l’AQI seuls ne le provoquent pas.
- ACTION_RENDER ajoute un passage à la file locale WorkManager avec RENDER_ONLY : la dernière sélection est rendue depuis le cache, sans déclencher de réseau. La file utilise APPEND_OR_REPLACE pour conserver les demandes reçues pendant un rendu ; les lectures directes lors des redimensionnements restent bornées.
- Le réseau reste séparé par lieu : KEEP pour les demandes automatiques, REPLACE pour les demandes forcées afin de ne pas hériter d'une décision automatique de cache ou d'un délai de reprise. Le worker local attend l'enregistrement de cette demande avant de terminer.
- Un travail réseau automatique différé revérifie les 30 minutes de fraîcheur au moment de son exécution. L’actualisation manuelle transmet explicitement FORCE et remplace un travail en attente au lieu de perdre l’intention utilisateur.
- Les échecs des services météo et AQI ont une temporisation indépendante par lieu : 1, 2, 4, 8, puis 15 minutes maximum après les échecs successifs. Une vérification pendant cette période ne fait pas de nouvel appel et ne prolonge pas le délai. Le dernier échec est conservé pour signaler l’indisponibilité météo ; l’AQI reste facultatif.
- Une réussite remet le compteur du service à zéro. Les demandes forcées ignorent la temporisation. L’annulation n’est pas comptée comme un échec. Ce petit état reste en mémoire et est réinitialisé au redémarrage du processus.
- Le flux du cache continue d’être relu pendant la temporisation : les fenêtres horaires et l’ancienneté peuvent évoluer sans appel réseau.

## Vérifications

Tests unitaires ajoutés : délais et plafond, limite exacte de reprise, vérifications répétées sans prolongation, remise à zéro et séparation des lieux/services ; clé de rendu widget indépendante du chargement et de l’AQI, changement de ville/citation et passage au statut ancien.

Tests du dépôt ajoutés : relecture du cache par un travail différé, seuil des 30 minutes, demande forcée, temporisation météo, reprise après réussite et temporisation AQI indépendante. Ils utilisent l’horloge contrôlée et le transport simulé déjà présents dans la suite.

`WidgetWorkSchedulerTest` couvre aussi, avec WorkManager sous Robolectric et barrières contrôlées : A suspendu puis B sans cache ni réseau ; erreur de cache de A sans annuler B ; demandes forcées conservées malgré un rendu ordinaire ultérieur ; remplacement d'un téléchargement automatique en attente et transmission de `force=true` même si le cache devient récent. Ces tests ne constituent pas une recette sur lanceur réel.

Avant fusion, vérifier les résultats de `testDebugUnitTest`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest` et `bundleRelease` dans la CI existante. Puis tester sur téléphone :

1. Application et widget sur la même ville, cache récent : attendre plusieurs cycles ; le widget reste stable.
2. Changer la ville ou la citation du jour ; vérifier le widget et ses différentes tailles.
3. Couper le réseau avec un cache existant ; le garder affiché et vérifier l’ancienneté.
4. Pendant la temporisation, utiliser Actualiser dans l’écran et le widget : une tentative doit être possible immédiatement.
5. Revenir en ligne et actualiser ; le fonctionnement normal doit reprendre.
6. Laisser des conditions franchir une heure d’âge sans nouveau téléchargement : le statut ancien doit apparaître.

Aucun gain chiffré de batterie n’est promis : le but est de supprimer des opérations inutiles identifiées, sans modifier la source météo.
