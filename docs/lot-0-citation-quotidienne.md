# Lot 0 — Citation quotidienne et prévisions dès demain

Implémentation du 20 octobre 2026 — branche `codex/citation-quotidienne`.

## Règles livrées

- Une attribution de citation par date civile locale du téléphone, commune à toutes les villes et aux points d’entrée application/widget.
- La première météo exploitable choisit la catégorie. Si la première demande intervient sans météo, une pensée neutre est attribuée. La météo retrouvée plus tard ne remplace pas cette attribution.
- Actualiser, changer de ville ou de catégorie météo, recréer l’écran ou relancer l’application ne change pas la citation du jour.
- Une nouvelle date produit une nouvelle attribution, sans répétition immédiate lorsqu’une alternative existe. Revenir à une date déjà attribuée réutilise son identifiant.
- Les passages de date, de fuseau et d’heure utilisent une horloge injectable. Le fuseau du téléphone est lu à chaque demande, sans être figé au démarrage du processus.
- Aucun bouton de renouvellement ni méthode `nextQuote` ne subsiste. L’API du repository n’accepte plus de paramètre permettant d’avancer manuellement.

## Stockage et migration

`QuoteCatalog` donne des identifiants explicites aux 18 citations existantes et à trois pensées neutres originales. Ces identifiants ne doivent pas être réaffectés à d’autres textes lors d’un enrichissement du catalogue. La validation éditoriale des attributions historiques et l’élargissement de la collection restent des travaux distincts.

`LocalQuoteRepository` conserve les identifiants sous des clés `daily_quote_v1_<date>` dans les préférences existantes. Les jours déjà attribués sont conservés, afin qu’un retour à une date connue ne relance pas la sélection. Ce petit historique contient uniquement date et identifiant de texte, aucune ville ni localisation.

Un verrou commun aux instances protège l’attribution dans le processus de l’application. L’édition SharedPreferences est unique et appliquée avant de rendre la main : deux demandes simultanées de l’écran et du worker obtiennent la même attribution. Le projet n’exécute pas ces composants dans des processus Android distincts. Une éventuelle architecture multiprocessus nécessiterait un stockage transactionnel partagé adapté.

Lors de la première attribution après mise à jour, l’ancien index de la catégorie est repris s’il existe et est valide après normalisation. Les préférences de villes sont conservées. Les anciennes clés d’index ne sont plus mises à jour. Les identifiants inconnus et les préférences de type invalide déclenchent une attribution valide sans crash.

Room n’est pas introduit dans ce lot. Le cache météo hors ligne relève du Lot 1.

## Cycle de vie et absence de réseau

Une météo réussie attribue la citation en utilisant son code. Un échec réseau fournit la citation déjà attribuée, ou une pensée neutre si aucune attribution n’existe pour cette date. La citation reste affichée pendant une actualisation et après une erreur.

La lecture `assignedQuote` restaure immédiatement une attribution existante au démarrage de l’écran et au chargement du widget. Cette lecture n’attribue pas de pensée neutre prématurément : lors d’une première visite du jour, la météo peut encore déterminer la catégorie.

Pendant que l’écran est visible, une vérification locale est faite au retour au premier plan puis toutes les 60 secondes. Elle ne provoque aucun appel météo. Elle s’arrête quand l’écran n’est plus STARTED. Un écran laissé ouvert à minuit renouvelle donc la citation dans la minute, sauf chargement en cours ; dans ce cas la fin du chargement attribue la citation à la date courante.

Le widget interroge le même repository lors de ses actualisations existantes. Son changement visuel de jour dépend donc du prochain rafraîchissement Android ou manuel, sans promesse d’exécution exacte à minuit. Aucune notification ni tâche nocturne supplémentaire n’est créée.

Le widget affiche désormais un extrait de la même citation, même si le texte dépasse 80 caractères ; son layout existant conserve une limite de deux lignes et une ellipse. Toucher cet extrait ouvre l’application. En cas d’erreur réseau, le widget conserve la possibilité d’afficher la citation quotidienne.

## Prévisions J+1 à J+7

La demande réseau utilise `forecast_days=8` pour recevoir aujourd’hui et les sept journées suivantes. Le parsing conserve les dates strictement postérieures à aujourd’hui et inférieures ou égales à J+7, selon le fuseau de la ville sélectionnée.

Le jour courant n’est plus une ligne de prévision. Ses séries horaires et quotidiennes restent disponibles au parsing pour calculer l’UV actuel et le pic UV d’aujourd’hui. Le fallback UV recherche explicitement la date du jour au lieu de dépendre de la première ligne retenue pour l’affichage.

La section s’intitule « Les 7 prochains jours, dès demain ». L’aperçu affiche J+1 à J+3, puis le bouton ouvre jusqu’à J+7. Si une réponse fournit moins de journées, seules les données reçues sont affichées ; aucune journée n’est inventée.

Référence : [API Open-Meteo et paramètre forecast_days](https://open-meteo.com/en/docs).

## Vérifications

Les tests unitaires couvrent :

- Stabilité quotidienne malgré météo/ville/instance de repository différentes.
- Renouvellement sur trente jours sans répétition immédiate dans une catégorie.
- Minuit local distinct de minuit UTC, fuseau système modifié pendant le processus, retour à une date connue.
- Passages d’heure d’été/hiver, année bissextile et changement d’année.
- Attribution neutre hors ligne, migration des anciennes préférences, valeurs corrompues ou de mauvais type.
- Vingt-quatre demandes concurrentes et unicité des identifiants du catalogue.
- Rafraîchissement local de la citation par le ViewModel sans appel météo, citation disponible après erreur réseau.
- Requête sur huit jours, sept dates de demain à J+7, limite de date internationale et UV du jour préservé.

Les tests Android couvrent le repository réellement partagé par Hilt entre le ViewModel et le worker, la citation conservée pendant un chargement/une erreur, l’absence de bouton et les prévisions repliables. Les états visuels quotidiens utilisent désormais J+1 comme première date.

Commandes :

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
.\gradlew.bat assembleDebugAndroidTest assembleRelease
.\tools\review-ui.ps1
```

Les configurations de distribution, applicationId, signature et version ne sont pas modifiées dans ce lot.

Résultats finaux : 73 tests unitaires réussis ; huit tests instrumentés sur chacun des quatre profils (32 exécutions réussies) ; builds debug et release R8 réussis ; lint sans erreur, avec 71 avertissements. Revue des captures : sept dates futures alignées, citation courte et longue sans bouton, texte intégral à 200 %, citation restaurée au démarrage. Les captures déterministes de prévisions utilisent le 17 octobre comme jour de référence : leur première ligne est donc le 18 octobre. Le réseau réel reste celui de l’installation.
