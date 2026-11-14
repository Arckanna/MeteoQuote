# Recette du lot UX 1 — 14 novembre 2026

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

Résultats et limites de la recette sur émulateur consignés ci-dessous à la fin de l'exécution.
