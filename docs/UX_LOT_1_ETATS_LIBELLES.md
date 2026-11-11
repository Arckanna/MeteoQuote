# Lot UX 1 — États d'affichage et libellés

Date : 11 novembre 2026. Branche de cadrage : `codex/ux-etats-libelles`.

**Statut : cadrage défini ; étape 2 (fraîcheur) implémentée sur la branche, recette appareil à effectuer.** L'écran principal affiche désormais un âge court, un avertissement pour les observations anciennes et un bouton accessible « Détails des données » ouvrant les dates des conditions et du téléchargement dans le fuseau de la ville. Les dates détaillées conservent leur formulation existante. L'affichage est recalculé chaque minute au premier plan et au retour à l'écran. Les autres sections restent le contrat cible des prochaines étapes, notamment la recherche avec choix du résultat ; elles ne décrivent pas toutes l'interface déjà livrée.

## 1. Principes de lecture

- La ville, la température et les conditions restent prioritaires. Une actualisation conserve les données utilisables de cette ville et la citation.
- Séparer trois dimensions : disponibilité des données, fraîcheur des conditions et opération en cours. « Données anciennes » peut coexister avec « Actualisation en cours… » ou « Hors ligne ».
- Les messages de recherche restent près de la recherche ; ils ne remplacent jamais la météo par une erreur globale.
- Employer le vouvoiement et des actions explicites : « Actualiser », « Réessayer », « Annuler », « Rechercher », « Ma position », « Gérer mes villes ».
- Les avertissements sont exprimés par du texte, pas uniquement par une couleur. Les messages durables restent lisibles dans l'écran ; un toast seul ne suffit pas.

## 2. Règles de données conservées

Les seuils métier existants ne changent pas dans ce lot :

| Élément | Règle |
|---|---|
| Cache météo utilisable | Date de téléchargement âgée de moins de 7 jours, selon `CachePolicy`. À 7 jours exactement, ne plus présenter les mesures. |
| Conditions actuelles | `observedAt` connu, non futur et âgé de moins de 60 minutes ; aucune date de téléchargement future. C'est la règle de `WeatherFreshness.isCurrent`. |
| Conditions anciennes | Observation connue et non future, âgée d'au moins 60 minutes, avec cache encore utilisable. |
| Fraîcheur incertaine | Observation absente ou future, ou téléchargement daté du futur. Ne pas qualifier ces conditions d'actuelles. |
| Récupération récente | Moins de 30 minutes depuis téléchargement : gouverne le réseau automatique, pas la véracité du titre « Météo actuelle ». |
| Cache ancien | `isOld` à partir de 24 heures, ou date future ; cet indicateur ne remplace pas la règle de 60 minutes des observations. |
| AQI | Date de récupération indépendante ; valeur non présentée à partir de 24 heures ou sans mesure à l'heure voulue. Son échec ne bloque pas la météo. |

Une date de téléchargement absente ne fabrique pas une date d'observation. Si l'observation est valide et récente, le titre peut rester « Météo actuelle », mais la date de récupération est déclarée indisponible.

## 3. États météo et textes exacts

| ID | Situation | Titre / statut | Message | Action |
|---|---|---|---|---|
| M01 | Conditions actuelles disponibles | « Météo actuelle » | « Actualisé il y a {âge} » | « Actualiser » ; « Détails des données » |
| M02 | Conditions anciennes, cache utilisable | « Dernières conditions connues » ; statut « Données anciennes » | « Conditions relevées il y a {âge des conditions} » | « Actualiser » ; « Détails des données » |
| M03 | Dates absentes ou incohérentes | « Dernières conditions connues » ; statut « Fraîcheur inconnue » | « La date des données ne permet pas de confirmer leur fraîcheur. » | « Actualiser » ; « Détails des données » |
| M04 | Chargement initial ou changement vers une ville sans cache | « Météo » | « Chargement de la météo… » | Sélection d'une autre ville disponible ; actualisation désactivée |
| M05 | Actualisation avec données utilisables | Conserver M01, M02 ou M03 | Ajouter « Actualisation en cours… » | Actualisation désactivée jusqu'à la fin ; sélection disponible |
| M06 | Absence de réseau confirmée, cache utilisable | Conserver M01, M02 ou M03 ; statut « Hors ligne » | « Les données enregistrées restent disponibles. » | « Réessayer » ; sélection disponible |
| M07 | Absence de réseau confirmée, sans cache utilisable | « Météo indisponible » ; statut « Hors ligne » | « Aucune météo enregistrée pour cette ville. Connectez-vous pour la charger. » | « Réessayer » ; choix d'une ville enregistrée |
| M08 | Échec réseau avec cache utilisable | Conserver M01, M02 ou M03 | « Actualisation impossible. Les dernières données sont conservées. » | « Réessayer » ; « Détails des données » |
| M09 | Échec sans cache utilisable | « Météo indisponible » | Message de cause défini ci-dessous | « Réessayer » ; sélection disponible |

Pour M02, l'âge du téléchargement reste accessible dans les détails : un téléchargement de dix minutes ne doit pas masquer des conditions de deux heures. Pour M04/M07/M09, masquer les mesures et prévisions de l'ancienne ville, sans afficher de zéros fictifs ; conserver la citation disponible. L'état M04 s'arrête à la fin de la requête, même en cas d'erreur.

**« Hors ligne » est réservé à une absence de réseau confirmée.** Le code actuel expose des erreurs de connexion mais pas d'état de connectivité dédié. Tant que cette information fiable n'est pas disponible, une IOException, un timeout ou une erreur TLS utilise M08/M09, jamais M06/M07 par déduction. La décision d'ajouter cette détection appartient à l'implémentation ultérieure ; aucune nouvelle permission n'est demandée dans ce cadrage.

### Causes d'échec sans cache (M09)

| Cause | Message retenu |
|---|---|
| Connexion, y compris TLS sans absence réseau confirmée | « Impossible de joindre le service météo. Réessayez. » |
| Délai dépassé | « Le service météo met trop de temps à répondre. Réessayez. » |
| HTTP 429 | « Trop de demandes. Réessayez dans quelques instants. » |
| HTTP 404 | « Aucune donnée météo disponible pour cette ville. » |
| HTTP 5xx | « Le service météo est temporairement indisponible. Réessayez plus tard. » |
| Autre erreur HTTP | « Le service météo n'a pas pu traiter la demande. Réessayez plus tard. » |
| Réponse invalide | « Les données météo reçues sont inutilisables. Réessayez plus tard. » |
| Erreur non identifiée | « Impossible de charger la météo. Réessayez. » |

Une erreur de stockage reste distincte : sans données affichées, « Les données enregistrées sont indisponibles. Réessayez. » ; avec données déjà visibles, « La météo reste affichée, mais son enregistrement est indisponible. » Ne pas promettre un cache hors ligne après un échec d'écriture. Ne pas afficher d'URL, de trace technique ou de code HTTP dans le message principal.

## 4. Dates courtes et détails

Pour un âge non négatif, calculer les unités par troncature : moins de 60 secondes → « à l’instant » ; de 1 à 59 minutes → « il y a {n} min » ; de 1 à 23 heures → « il y a {n} h » ; à partir de 24 heures → « il y a {n} j ». Exemples complets : « Actualisé à l’instant », « Actualisé il y a 10 min », « Conditions relevées il y a 2 h ».

La notation `{âge}` des tableaux désigne la durée ; ne pas doubler « il y a » lors de l'assemblage. En code, privilégier des ressources de phrases complètes plutôt que des concaténations.

L'action visible et accessible « Détails des données » ouvre une présentation intitulée « Dates des données », avec :

- « Conditions relevées le {date} à {heure} ».
- « Données téléchargées le {date} à {heure} ».
- « Fuseau de la ville : {fuseau} ({décalage UTC}) ».
- Replis : « Heure des conditions indisponible », « Heure du téléchargement indisponible » ; pour une date future, « Date des conditions à vérifier » ou « Date du téléchargement à vérifier », avec la date concernée.
- Action de fermeture : « Fermer ».

Les durées sont calculées entre instants ; les dates détaillées suivent le fuseau de la ville. Pour TalkBack, développer les unités (« dix minutes », ou leur équivalent numérique correctement prononcé) et annoncer une transition d'état sans répéter chaque minute tout le contenu météo. Une nouvelle tentative retire l'ancien message d'échec, mais conserve l'avertissement de fraîcheur si nécessaire.

## 5. États de recherche et textes exacts

Cette table fixe aussi les états attendus pour la future recherche multirésultat. Aujourd'hui, le repository ne retourne qu'une ville et le succès la sélectionne immédiatement.

| ID | Situation | Message / libellé | Actions et conservation |
|---|---|---|---|
| R01 | Champ vide | Champ : « Rechercher une ville » ; si validation vide : « Saisissez un nom de ville. » | « Rechercher » ; aucun appel pour une saisie vide ou composée d'espaces |
| R02 | Recherche en cours | « Recherche en cours… » | « Annuler » ; saisie modifiable, météo et sélection actuelle conservées |
| R03 | Résultats disponibles | « Choisissez une ville » ; chaque résultat présente ville, région et pays | Choisir un résultat déclenche l'ajout/sélection ; « Annuler » reste disponible |
| R04 | Aucun résultat | « Aucune ville trouvée pour « {recherche} ». Vérifiez le nom ou précisez la région. » | « Rechercher » ; saisie conservée, aucun changement de ville |
| R05 | Échec de recherche, connectivité inconnue | « Impossible de rechercher une ville pour le moment. Réessayez. » | « Réessayer » ; saisie conservée |
| R06 | Absence de réseau confirmée pendant la recherche | « La recherche nécessite une connexion Internet. Vous pouvez choisir une ville enregistrée. » | « Réessayer » ; saisie et accès aux villes enregistrées conservés |
| R07 | Annulation ou résultat obsolète | Aucun message d'erreur | À l'annulation, retirer la progression et les résultats de la recherche annulée. Une réponse obsolète ne modifie aucun état, y compris la progression et les résultats d'une nouvelle recherche. Conserver la saisie et le dernier choix. |
| R08 | Choix validé | « Ville sélectionnée : {ville}. » | Fermer les résultats ; vider uniquement la saisie de cette recherche si elle n'a pas été modifiée |

R06 est soumis à la même preuve d'absence de réseau que M06/M07. Une nouvelle saisie, une autre sélection ou une demande de localisation invalide les réponses précédentes. Les erreurs tardives sont elles aussi ignorées. Annuler ne supprime ni la ville courante ni le texte saisi. Si le lieu est déjà enregistré, le sélectionner sans doublon. Les résultats ne sont pas effacés simplement parce qu'une météo s'actualise.

La recherche peut afficher R02–R06 pendant que la météo reste en M01–M03. Un échec de recherche ne doit pas produire « Météo indisponible » sur la carte météo.

## 6. Priorités de présentation

1. Identifier la ville sélectionnée et vérifier si des données de cette ville sont utilisables ; ne jamais réutiliser silencieusement celles d'un autre lieu.
2. Avec données, choisir M01/M02/M03 selon l'observation, puis ajouter l'opération M05, l'absence de réseau M06 ou l'échec M08. Un échec ne supprime pas le statut ancien/incertain.
3. Sans données, afficher M04 pendant l'attente, puis M07 ou M09 si elle échoue. Cache expiré et cache absent suivent ce même parcours, mais des dates expirées ne sont pas affichées comme des mesures disponibles.
4. Une erreur de stockage ne devient pas une erreur réseau. L'AQI affiche localement « Chargement… » ou « Indisponible », sans masquer la météo et sans réutiliser l'AQI d'une autre ville.
5. Une seule action « Actualiser » ou « Réessayer » sur la carte météo ; les actions de recherche gardent leur propre état. Aucun nouvel échec ne doit déplacer brutalement le focus ou ouvrir une boîte de dialogue automatiquement.

## 7. Cas de recette à préparer pour l'implémentation

Horloge de référence : `2026-11-11T12:00:00Z`. Les exemples sont des données de test, pas des observations réelles. Chaque cas devra contrôler le texte, les actions et la conservation des données, au retour au premier plan comme pendant l'affichage.

| Cas | Préparation | Attendu |
|---|---|---|
| Météo récente | Observation 11:45Z, téléchargement 11:50Z | M01, « Actualisé il y a 10 min » |
| Téléchargement récent, conditions anciennes | Observation 10:00Z, téléchargement 11:50Z | M02, « Données anciennes », « Conditions relevées il y a 2 h » ; le téléchargement est dans les détails |
| Limite de fraîcheur | Observation 11:00Z, puis 11:00:01Z | À 60 min exactement : M02 ; à 59 min 59 s : M01 |
| Absence de date / date future | Observation absente ou 12:05Z ; autre cas avec téléchargement futur | M03, aucune durée négative |
| Hors ligne avec cache | Cache utilisable, absence réseau confirmée | M06, mesures et citation conservées, statut de fraîcheur conservé |
| Hors ligne sans cache / cache expiré | Aucun instantané, puis instantané du 4 novembre à 12:00Z | M07, aucune mesure présentée, sélection des villes disponible |
| Connexion indéterminée | IOException sans preuve d'absence réseau | M08 ou M09 ; aucun libellé « Hors ligne » |
| Chargement initial / actualisation | Réponse suspendue sans cache, puis avec cache | M04 sans ancienne ville ; M05 avec données préservées |
| Erreur puis reprise | Timeout/429/503/invalide, avec et sans cache, puis réussite | M08/M09, message adapté ; tentative retire l'erreur, réussite restaure l'état de fraîcheur correct |
| Stockage indisponible / AQI indisponible | Échec local, puis échec AQI seul | Messages distincts ; aucune disparition de météo valide pour l'AQI |
| Recherche vide / aucun résultat | Espaces seuls, puis résultat vide pour « Xyzville » | R01 sans réseau, puis R04 avec saisie conservée |
| Recherche en erreur | Réponse en échec, puis absence réseau confirmée | R05 puis R06 ; météo inchangée |
| Réponse tardive | Recherche A suspendue, nouvelle saisie ou sélection B, puis fin de A | R07 pour A ; B et sa saisie restent intacts |
| Annulation / choix de résultat | Recherche suspendue annulée ; résultats puis choix explicite | R07 sans erreur ; R08 seulement après choix, sans ajout automatique du premier résultat |

Le cadrage initial ne modifiait pas l'application. L'étape fraîcheur ajoute des ressources Android, des tests unitaires de présentation et un test instrumenté d'ouverture des détails. Pour les étapes restantes, traduire les règles en états typés plutôt qu'en reconnaissance du texte de `notice` ; `WeatherUiState` et `SearchState` actuels ne portent pas encore toute cette distinction. La recette petit écran, police 200 %, rotation et TalkBack reste à effectuer sur appareil.

Références locales : [fraîcheur](../app/src/main/java/com/valerie/meteoquote/data/WeatherFreshness.kt), [cache](../app/src/main/java/com/valerie/meteoquote/data/CachePolicy.kt), [erreurs](../app/src/main/java/com/valerie/meteoquote/ui/WeatherFailure.kt), [états du ViewModel](../app/src/main/java/com/valerie/meteoquote/ui/WeatherViewModel.kt), [roadmap](CAHIER_DES_CHARGES_ROADMAP.md).
