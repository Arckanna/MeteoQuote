# Index de la documentation

Relecture du 8 novembre 2026, sur le code de la branche `codex/search-viewmodel` : version déclarée 7.4, code 22. Cet index décrit le dépôt local ; il ne certifie ni la version publiée sur Play ni l'état de la CI distante. Aucun test applicatif n'a été relancé pour cette revue documentaire.

## Guides à utiliser

| Document | Utilité |
|---|---|
| [README principal](../README.md) | Présentation, installation, commandes de build et CI. |
| [Publication](RELEASE.md) | Préparer et vérifier une nouvelle livraison ; checklist à reprendre pour chaque version. |
| [Injection de dépendances](injection-dependances.md) | Graphe Hilt, Room, repositories et WorkManager. |
| [Résilience réseau](resilience-reseau.md) | Délais HTTP, retries, annulation et articulation avec le backoff automatique. |
| [Cache Room livré](LOT_C_LIVRAISON.md) | Schéma réellement implémenté, fraîcheur, conservation et migrations. |
| [Optimisation du widget et du réseau](OPTIMISATION_WIDGET_RESEAU.md) | File de rendu, demandes forcées, déduplication réseau et temporisation. |
| [Widget compact](widget-compact.md) | Dimensions et visibilité actuelles ; anciennes validations identifiées séparément. |
| [Citation quotidienne](lot-0-citation-quotidienne.md) | Attribution, stockage et règles de date ; prévisions J+1 à J+7. |
| [Citation au premier plan](citation-premier-plan.md) | Ordre de lecture, présentation et dépliage des prévisions. |
| [Migration Compose](migration-compose.md) | Frontière actuelle Views/Compose et prochaines sections envisageables. |

## Suivi des travaux

- [Roadmap](CAHIER_DES_CHARGES_ROADMAP.md) : distingue désormais les fonctions implémentées des évolutions proposées. Les estimations initiales ne sont pas un reste à faire.
- [Revue du 2 novembre](REVUE_CODE_2026-11-02.md) : recherche tardive et file de rendu corrigées ; fraîcheur de la météo utilisée pour la citation à minuit encore à corriger. Les optimisations de rendu et de décodage restent proposées.

## Documents historiques, plus nécessaires au parcours courant

Ces cinq fichiers conservent la trace des décisions et des validations passées. Ils ne doivent plus servir de spécification de l'application actuelle. Ils restent à leur emplacement pour préserver les liens existants ; aucune suppression n'est nécessaire pour alléger la lecture courante.

| Document historique | Lire à la place pour l'état actuel |
|---|---|
| [Modernisation du 17 octobre](ANDROID_MODERNIZATION.md) | README principal, injection et publication. Les anciens constats d'architecture et de versions ne sont plus actuels. |
| [Audit de fiabilité du 22 octobre](AUDIT_FIABILITE_2026-10-22.md) | Cache livré, optimisation widget/réseau et revue du 2 novembre avec son suivi. Garder l'audit pour ses scénarios de recette et risques complémentaires. |
| [Premiers correctifs A/B](LOT_AB_CORRECTIFS.md) | Cache livré et état courant de la recherche dans la roadmap. Le tableau « à implémenter » décrit la livraison d'octobre. |
| [Préparation du lot C](LOT_C_CACHE_HORS_LIGNE.md) | Cache livré : les cinq tables et contrats proposés n'ont pas été retenus tels quels. |
| [Refonte UI initiale](refonte-ui.md) | Citation au premier plan et migration Compose. L'ancien ordre des blocs et le bouton citation ne sont plus la référence. |

## Conventions de maintenance

- Un guide courant décrit le code et indique sa date de relecture. Un compte rendu conserve les résultats à leur date, sans les présenter comme une nouvelle validation.
- Ne pas reporter un nombre de tests ou une checklist cochée d'une release à la suivante.
- Versions et dépendances : vérifier [app/build.gradle.kts](../app/build.gradle.kts) et [le catalogue](../gradle/libs.versions.toml). CI : vérifier [le workflow](../.github/workflows/android.yml).
- Les captures et rapports sous `app/build/` sont générés localement et peuvent disparaître après un nettoyage. La capture de présentation versionnée est dans [art/screenshot_main.png](../art/screenshot_main.png).
