# Lots A/B — Première livraison de correctifs

Suite de l’audit du 22 octobre 2026. Périmètre de cette livraison : B02 et B03, avec tests déterministes et suivi local. Les lots A et B ne sont pas déclarés terminés dans leur ensemble.

## Comportement livré

### B03 — Météo indépendante de l’AQI

Le repository propose un flux `weatherUpdates` : une première émission contient température, état du ciel, UV et prévisions ; une seconde termine l’enrichissement AQI. La carte AQI affiche « Chargement… » pendant cette attente, sans bloquer la météo ni le bouton d’actualisation.

Le budget AQI est de cinq secondes, retries compris. Échec réseau, réponse AQI invalide ou dépassement du délai : la météo reste affichée, l’air devient indisponible. Une annulation externe se propage au transport. Changer de ville ou actualiser annule le flux précédent. Les mesures AQI de la ville précédente ne sont pas réutilisées pour la nouvelle.

L’enrichissement ne renouvelle pas artificiellement l’heure de récupération de la météo. `fetchWeather` fournit désormais uniquement la météo principale : le widget, qui n’affiche pas l’AQI, évite entièrement cet appel réseau. Les consommateurs souhaitant l’AQI doivent utiliser `weatherUpdates`.

### B02 — Priorité au choix manuel

Un identifiant est créé au clic sur « Ma position », avant la permission et l’acquisition. Le même identifiant accompagne la réponse jusqu’au géocodage inverse. Sélection manuelle, ajout de ville, lancement d’une recherche, suppression, nouvelle demande de localisation et arrêt de l’écran invalident l’ancienne demande.

Une position tardive avec un identifiant périmé ne démarre pas de géocodage. Un géocodage déjà engagé est annulé et son résultat est également vérifié avant toute écriture. L’Activity conserve l’acquisition liée à son cycle de vie ; le ViewModel contrôle l’autorisation de publier le résultat. Un choix manuel invalide le résultat même si le fournisseur de position termine son acquisition ensuite.

## Tests de non-régression

- Météo émise avant une réponse AQI lente ; budget de cinq secondes vérifié avec une horloge virtuelle et annulation de la requête lente.
- Enrichissement AQI réussi après une première émission sans valeur ; prévisions préservées.
- Échec HTTP facultatif et propagation de l’annulation externe.
- Un seul appel météo pour le widget, aucun appel AQI.
- Météo visible pendant l’enrichissement ; horodatage inchangé.
- Changement de ville pendant l’AQI : ancien flux annulé, ancienne valeur jamais publiée dans la nouvelle ville.
- Échec d’enrichissement après affichage : météo conservée sans erreur globale.
- Choix manuel pendant l’acquisition : position tardive ignorée avant géocodage.
- Demande remplacée ou annulée à l’arrêt : ancienne position ignorée.
- Test existant conservé : choix manuel pendant un géocodage inverse lent.

## Registre et prochaines étapes

| Élément | Statut | Prochaine validation/action |
|---|---|---|
| B02 — Géolocalisation tardive | Implémenté, tests automatisés ajoutés | Recette réelle : permission, position lente puis choix manuel, arrière-plan, rotation. |
| B03 — AQI bloquant | Implémenté, tests automatisés ajoutés | Recette écran/widget avec réseau lent et AQI indisponible. |
| Lot A — Tests ciblés et registre | Engagé | Ajouter exécution instrumentée déterministe en CI ; réconcilier README/procédure de publication. |
| B01 — Fraîcheur | À implémenter | Horloge de présentation, fuseau du lieu, politique d’âge et tests de minuit/reprise. |
| B07 — Arrondi widget | À implémenter | Formatage partagé et tests autour de zéro. |
| B08 — Erreur de localisation | À implémenter | État d’erreur secondaire qui conserve la météo valide. |
| B04–B06 — Reprise/cache du widget | Lot C, sauf incident urgent | Cache structuré partagé, reprise bornée, déduplication et citation du jour hors ligne. |

Ne pas traiter une validation unitaire comme une recette sur appareil. La CI instrumentée, la validation Play et le reste du cache hors ligne restent des tâches distinctes.

Préparation suivante : [Lot C — cache Room et consultation hors ligne](LOT_C_CACHE_HORS_LIGNE.md). Ce plan reprend B01 pour la fraîcheur, B08 pour les erreurs secondaires et B04–B06 pour le widget, en conservant les garanties B02/B03.

## Vérification de cette livraison

Commande : `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease`.

Résultat final : **BUILD SUCCESSFUL**. **81 tests unitaires réussis** (8 nouveaux), zéro échec, zéro erreur, zéro test ignoré. APK debug, APK de tests Android et release avec R8 construits. Lint : zéro erreur, 71 avertissements, comme lors de l’audit initial. `git diff --check` réussi.

Tests instrumentés compilés mais non exécutés sur appareil pendant cette livraison. Aucune publication effectuée. Les scénarios de recette réelle ci-dessus restent nécessaires avant diffusion.
