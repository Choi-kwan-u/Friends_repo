# The Diversity Hires - Team Git Workflow Plan

## 0. Terminology 

| Name in the document | Repository | Git remote name in your clone | 
|---|---|-------------------------------| 
|**Course upstream**| `oh-gnues/Invaders-SDP-24592` | `course` (team leader only)   |
| **Team upstream** | `AymericGe/The-Diversity-Hires` | `upstream`                    |
| **Personal fork** | `<teammate>/The-Diversity-Hires` | `origin`                      |


## 1. Selected Workflow and Rationale

**Chosen workflow : Forking workflow with two integration levels.**
```text
Course upstream   oh-gnues/Invaders-SDP-24592
        ▲   Leader's PR (from team branch upstream/<topic>)
Team upstream     AymericGe/The-Diversity-Hires
        ▲   PR from a feature branch
Personal forks    <teammate>/The-Diversity-Hires
```
1. Each developer works on a **feature branch** and pushes it to **their own fork** (`origin`).
2. They open a **pull request to the team upstream**. A team collaborator merges it after review.
3. When an increment is ready, the **team leader** opens a PR to the **course upstream**.

### What was required and what was our choice

| Level | Required by the course? | Our decision |
|---|---|---|
| Course upstream → team | **Yes.** No team has write access to the course repo, so every team must contribute through a fork and PRs. | — |
| Team upstream → developers | **No.** We could have added all members as collaborators and worked with shared branches in one team repository. | We **chose** personal forks for every developer. |

### Why we chose forks inside the team as well

| Situation | Our solution |
|---|---|
| 7 developers build one system; 10 teams share one course repo. | Two integration levels: team maintainers first, then course maintainers. |
| We want nobody to push to team `main` by accident. | Developers have no direct write path to team `main`; everything arrives as a PR. |
| Unfinished work should not appear in the shared repository. | Experiments stay in personal forks until they are ready for review. |
| Individual contributions are graded. | Every change is traceable to its author through their fork and PR. |
| Small tasks from our team board, weekly project sessions. | Short-lived feature branches and small PRs. |
| No versioned releases, no old versions to maintain. | No `develop`, `release` or `hotfix` branches (no Gitflow). |

### Alternative we did not choose: shared team repository

| | Forking workflow (ours) | Shared team repository |
|---|---|---|
| Write access | Only maintainers on team repo | All members are collaborators |
| Where unfinished work lives | Personal forks | Branches in the team repo |
| Risk of accidental push to `main` | Very low | Needs branch protection to prevent |
| Extra effort | Keep forks in sync, more remotes | Fewer steps, one remote |
| Visibility of teammates' work | Only once a (draft) PR is opened | All branches visible immediately |

We will report how this choice worked in practice in **Section 9 (Experience Log)**, so it can be compared with teams that chose a shared team repository.

---
## 2. The Branch Strategy

Each developer creates a fork of the team repository (AymericGe/The-Diversity-Hires). Each developer works on their fork in their personal workspace. They make their code changes. Then, the team developers submit a pull request to the team repository. The team then merges the code from the pull request. Once this is done, the team leader submits a pull request to the main repository (oh-gnues/Invaders-SDP-24592).
It is a three-tier architecture: the global repository, the team repository, and the developers' personal forks.

### Branches and their roles

| Branch | Lives in | Role |
|---|---|---|
| `main` (course) | Course upstream | Integrated game of all 10 teams. Changed only by PRs, merged by course maintainers. |
| `main` (team) | Team upstream | Our stable, reviewed, always-runnable version. **Protected: PRs only.** |
| `main` (fork) | Personal fork | Mirror of team `main`. Only updated by syncing, never commit on it. |
| `feature/<name>`, `fix/<name>`, `docs/<name>`, `refactor/<name>`, `test/<name>` | Personal fork | One task per branch, e.g. `feature/explosion-effect`. Lowercase, hyphens, short-lived. |
| `upstream/<topic>` | Team upstream | Leader's branch for a course PR, so later team merges do not change an open course PR. |

**Owner exception:** Aymeric cannot fork his own repository. He uses feature branches directly in the team upstream, with the same PR rules.

### Branch lifecycle

| Step | When | Rule |
|---|---|---|
| 1. Create | When we start a task | Always from the latest team `main`. One task per branch. |
| 2. Update | Before each work session and before asking for review | Sync with `upstream/main`. |
| 3. Merge | When the task is done | Only through an approved PR into team `main`, using **Squash and merge**. |
| 4. Delete | Right after merge or rejection | On our fork and locally. |

```bash
git switch -c feature/explosion-effect upstream/main
# after the PR shows "Merged" (squash means -d may refuse):
git branch -D feature/explosion-effect
```

---

## 3. Committing Rules

**One commit, one logical change.**

- One bug fix, one feature step or one refactor — never mixed.
- The game still compiles and runs after every commit.
- No build output or IDE files (`out/`, `*.class`, `.idea/`) — use `.gitignore`.
- Commit early and often; the squash merge tidies it up later.
- Use your own Git identity, since individual contributions are graded.

### Format: Conventional Commits

```text
<type>(<scope>): <summary>

<body: what changed and why>
```

- **Types:** `feat` · `fix` · `refactor` · `docs` · `test` · `chore` · `style`
- **Scopes:** `vfx` · `ui` · `game` · `build` · `docs`
- Imperative mood ("add", not "added") · summary about 50 characters (hard limit 72) · body wrapped at 72.

**Examples**

```text
feat(vfx): add explosion effect when enemy dies
fix(vfx): stop screen shake after game over
refactor(vfx): extract particle drawing class
docs: add team Git workflow plan
```

---

## 4. Pull Request and Code Review Rules

**When to open a PR**


- One PR per task: fork branch → team `main`.
- Task done: game runs, diff self-checked, branch up to date.
- Bigger task? Open a **Draft PR** early, so teammates working in the same area can see the work before it is finished.
- Title in Conventional Commit format (it becomes the squash commit title).
- Description uses the template in Section 5 and contains `Closes #<issue>`.


**Review and approval conditions before merging**
- At least **one approval** from a teammate other than the author is required before a PR can be merged. No self-approval.
- Reviewers check: the code works as intended, is reasonably readable, and follows the conventions in this document.
- Reviewers leave comments within 24 hours of a PR being opened where possible; the author addresses comments before merge.
- Any CI checks (build/tests, once set up) must pass before merging.

**Direct pushes to `main`**
- **Not allowed.** `main` is a protected branch — every change, including small fixes like typos, must go through a PR.
- Enforced by GitHub branch protection: PR required, 1 approval, no force push, no deletion.
- Nobody pushes to the course upstream.

### Two review levels

| | Team PR | Course PR |
|---|---|---|
| From → to | `fork:feature/…` → team `main` | `team:upstream/<topic>` → course `main` |
| Opened by | Any developer | Team leader only |
| When | A task is finished | The team agrees an increment is ready |
| Before opening | Rebase on `upstream/main` | Sync with course `main`, run the game |
| Approval | At least 1 teammate | Class agreement: approvals and cross-team review |
| Merged by | Team collaborator (Squash and merge) | Course maintainers (method per class agreement) |

Cross-team review goes both ways — we also review other teams' PRs when asked.

---

## 5. The Merge Strategy


### Squash by default, each method has one job

| Method | When we use it |
|---|---|
| **Squash and merge** | Default for every PR into team `main`: one clean commit per task, titled by the PR. |
| **Rebase (local)** | Updating your own, unshared feature branch: `git rebase upstream/main`, then `git push --force-with-lease`. Shared branch? Merge instead. |
| **Merge (ff / merge commit)** | Syncing only: fork `main` from team `main` (`--ff-only`); team `main` from course `main` (leader). |
| **Course repo PRs** | Method chosen by the course maintainers / class agreement. |

**Why squash?** Feature branches pile up WIP commits. Squashing keeps `main` at one reviewed commit per task — easy to read and easy to undo with `git revert`. The full history stays on the closed PR.

### Squash commit message (required)

Because a squash collapses the whole PR into **one** commit, that commit must explain the work on its own, so that readers of `main` (including the course staff) do not need to open every PR. When clicking **Squash and merge**, the merger replaces GitHub's default body with:

```text
feat(vfx): add explosion effect when enemy dies (#12)

What: particle burst and short flash when an enemy is destroyed.
Why:  task #8 "visual feedback for hits".
Files: ExplosionEffect.java (new), Enemy.java (call on death).
Tested: ran the game, destroyed 20+ enemies, no frame drops.
Author: @<github-user> · Reviewed by: @<github-user>
Closes #8
```

The **PR description** uses the same structure (What / Why / Files / Tested), plus a GIF of the effect for visual changes.

---

## 6. Merge Conflicts

### Who resolves them

| Conflict | Responsible | How |
|---|---|---|
| Feature branch ↔ team `main` | PR author | Update from `upstream/main`, resolve locally, run the game, push. Reviewers never guess. |
| …touching a teammate's code | Author + that teammate | Resolve together over Slack; the leader decides if you disagree. |
| Team upstream ↔ course `main` | Team leader + affected authors | Sync, resolve, test; talk to other teams in the team-leaders Slack channel. |

### Prevention

- Small PRs and short-lived branches.
- Sync before work and before review.
- Leader syncs course `main` every session.
- Announce edits to shared core classes.

---


## 7. Overall Development Workflow

### From task to integration

| # | Step | What happens |
|---|---|---|
| 1 | Pick a task | From the team board. |
| 2 | Sync main | Fetch team upstream, fast-forward fork `main`. |
| 3 | New branch | `feature/<name>` from `upstream/main`. |
| 4 | Code & commit | Small, conventional commits. |
| 5 | Push to fork | Rebase, then push to `origin`. |
| 6 | Open PR | Fork branch → team `main` (draft if still in progress). |
| 7 | Review | 1 approval, no conflicts. *Changes requested → back to step 4.* |
| 8 | Squash merge | By a team collaborator, with the squash message from Section 5. |
| 9 | Clean up | Delete branch, sync `main`. *Next task → step 1.* |
| 10 | Course PR | Leader syncs, opens PR from `upstream/<topic>`. |
| 11 | Course review | Cross-team review → course `main`. |

### The commands behind each step

**One-time setup**

```bash
# fork the team upstream on GitHub, then:
git clone <your fork URL>
cd The-Diversity-Hires
git remote add upstream https://github.com/AymericGe/The-Diversity-Hires.git
git remote -v
# origin   = your fork
# upstream = team upstream

# team leader only:
git remote add course https://github.com/oh-gnues/Invaders-SDP-24592.git
```

**Every task**

```bash
git switch main
git fetch upstream
git merge --ff-only upstream/main
git push origin main

git switch -c feature/<name>
# …small commits…

git fetch upstream
git rebase upstream/main
git push -u origin feature/<name>
# re-push after a rebase: git push --force-with-lease
```

---
## Additional Team Rules

- No force-pushing to `main`, ever.
- Broken builds on `main` are treated as top priority — whoever caused it fixes it immediately or reverts the merge.
- Any change to this workflow document must itself go through a PR and be agreed on by the whole team.
