# Frenchies — Flux de travail Git

## 1. Flux de travail et justification Git sélectionnés

Nous utiliserons un **workflow par forks avec GitHub Flow**, organisé autour des cinq tâches que nous avons décidé du projet. Chaque tâche aura une branche d’intégration dédiée. Les membres qui y sont affectés développeront chacun dans leur propre branche, puis ouvriront une PR vers la branche de tâche correspondante. Quand la tâche sera vérifiée, une PR permettra de l’intégrer dans `main`.

Cette organisation permet aux neuf membres de travailler sur les fonctionnalités du menu principal en parallèle, tout en séparant les tâches et en faisant réviser les changements avant leur intégration. Le dépôt d’équipe Frenchies est notre ligne d’intégration. Les contributions éventuelles au dépôt du cours suivront la procédure fixée par ses responsables.

## 2. Stratégie de branche

`main` est la branche stable commune. Les branches de tâche sont créées depuis `main`. Chaque membre crée ensuite sa branche individuelle depuis la branche correspondant à sa tâche.

| Tâche | Membres affectés | Branche de tâche |
|---|---|---|
| Navigation dans le menu principal | Alexis Bihour et Samuel Kutchukian | `task/1.1-main-menu-navigation` |
| Retours audio du menu | Arthur Nevant | `task/1.2-menu-audio-feedback` |
| Sélecteur niveau / difficulté | Chiara Bichon et Loane Gosselin | `task/1.3-level-difficulty-selector` |
| Accès aux paramètres | Grégoire Nogier et Eloi Gaillard | `task/1.4-settings-access` |
| Accès à la boutique et au hangar | Lyanh Renkin et Evangeline Vuchot | `task/1.5-shop-hangar-access` |

Les branches individuelles suivent le format `feature/<tâche>-<pseudo-github>`, par exemple `feature/1.1-navigation-Alex0xB` ou `feature/1.5-shop-hangar-renlahh`. Pour une correction, le préfixe `fix/` peut être utilisé.

Chaque tâche a donc une branche d’intégration et une branche individuelle par membre : deux branches individuelles pour une tâche en binôme, une seule pour la tâche réalisée par Arthur. Au total, cela représente cinq branches de tâche et neuf branches individuelles, en plus de `main`.

Les branches individuelles sont fusionnées dans leur branche de tâche par PR. Une fois la tâche vérifiée, la branche de tâche est fusionnée dans `main` par PR. Après les fusions, les branches terminées sont supprimées. Personne ne développe directement sur `main`.

## 3. Règles de commit

Chaque commit représente un changement cohérent et limité. Les tests ou la documentation directement liés peuvent être inclus avec ce changement, mais les modifications sans rapport ne doivent pas être regroupées.

Avant de committer, l’auteur vérifie les fichiers modifiés et n’ajoute que ceux qui concernent sa tâche. Les fichiers temporaires, sorties de compilation, journaux et données locales de sauvegarde ou de score ne sont pas commités.

Format des messages :

`<type>(<scope>): <description courte en anglais>`

Types autorisés : `feat`, `fix`, `test`, `docs`, `refactor`, `chore`.

Exemples :

- `feat(menu): add keyboard navigation`
- `fix(audio): respect configured menu volume`
- `test(settings): cover settings access`
- `docs(workflow): describe task branches`

Les commits déjà intégrés dans une branche partagée ne sont pas réécrits.

## 4. Pull requests et règles de révision de code

Chaque membre ouvre une PR de sa branche individuelle vers la branche de tâche correspondante. La PR décrit le changement, la tâche concernée et les vérifications effectuées. Une PR encore en cours peut être marquée comme brouillon.

Lorsque la tâche est prête, un membre de l’équipe ouvre une PR de la branche de tâche vers `main`. Sa description précise le comportement livré, les vérifications réalisées et les limites connues.

Avant la fusion :

- un membre autre que l’auteur examine les changements ;
- les commentaires de révision et les conflits éventuels sont résolus ;
- les vérifications pertinentes pour le changement sont réalisées et indiquées dans la PR.

Dans un binôme, les membres se relisent mutuellement pour les PR individuelles. Pour une PR de branche de tâche vers `main`, au moins un membre qui n’a pas réalisé cette tâche effectue la révision. Pour la tâche audio réalisée en solo par Arthur, un autre membre de l’équipe doit aussi réviser sa PR.

Les changements Java sont compilés et lancés selon les instructions du dépôt. L’auteur vérifie également le parcours de jeu concerné. Pour une PR de documentation, le contenu, les liens et le rendu Markdown sont vérifiés.

Les poussées directes vers `main` ne sont pas autorisées. Les changements sont intégrés par PR afin que l’équipe garde une trace des révisions et des décisions.

## 5. Stratégie de fusion

La méthode par défaut est le **Squash merge**, utilisé pour fusionner les branches individuelles dans les branches de tâche, puis les branches de tâche dans `main`. Chaque PR produit ainsi un commit regroupé et compréhensible dans sa branche cible, tout en conservant la discussion et les révisions dans la PR.

Le **rebase** peut servir à mettre à jour une branche individuelle avec les changements récents de sa branche de tâche. Il ne sert pas à réécrire l’historique de `main` ou d’une branche de tâche partagée.

Le **merge commit** n’est pas utilisé par défaut. Si l’équipe décide qu’il est important de préserver plusieurs commits liés dans l’historique, elle doit se mettre d’accord avant la fusion et le préciser dans la PR.

L’auteur de la PR est responsable de résoudre les conflits sur sa branche. Si un conflit touche le travail ou le module d’un autre membre, il demande son avis avant de choisir une résolution. Il relance ensuite les vérifications affectées. Le réviseur examine la résolution avant la fusion. Le travail d’un autre membre ne doit pas être supprimé sans concertation.

## 6. Flux de travail global de développement

1. L’équipe confirme le périmètre de la tâche et ses critères d’acceptation.
2. La branche de tâche correspondante est créée depuis `main`.
3. Chaque membre affecté crée sa branche individuelle depuis cette branche de tâche.
4. Les membres réalisent leur travail dans des commits cohérents.
5. Chacun ouvre une PR vers la branche de tâche et fait réviser ses changements.
6. L’équipe intègre les contributions dans la branche de tâche et vérifie la fonctionnalité dans le jeu.
7. Une PR de la branche de tâche vers `main` est ouverte et révisée par un membre hors de cette tâche.
8. Après approbation et vérification, l’intégrateur effectue le Squash merge dans `main`.
9. Les branches individuelles et la branche de tâche terminée sont supprimées.

```mermaid
flowchart TD
    A[Confirmer la tâche et son périmètre] --> B[Créer la branche de tâche depuis main]
    B --> C[Chaque membre crée sa branche individuelle]
    C --> D[Développer et committer]
    D --> E[PR individuelle vers la branche de tâche]
    E --> F[Révision par un autre membre]
    F --> G{Commentaires et conflits résolus ?}
    G -- Non --> D
    G -- Oui --> H[Squash merge dans la branche de tâche]
    H --> I[Vérifier la fonctionnalité dans le jeu]
    I --> J[PR de la branche de tâche vers main]
    J --> K[Révision par un membre hors de la tâche]
    K --> L{PR approuvée et vérifiée ?}
    L -- Non --> I
    L -- Oui --> M[Squash merge dans main]
    M --> N[Supprimer les branches terminées]
