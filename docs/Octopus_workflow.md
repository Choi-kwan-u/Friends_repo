### 1. Workflow and Rationale

**Chosen workflow:** Feature Branch Workflow

* With 9 team members developing different features in parallel, separate feature branches minimize conflicts.
* Keeps the `main` branch in a working state at all times.
* Enables code review on a per-PR basis.
* Simpler than Git Flow, making it suitable for a one-semester project.

### 2. Branch Strategy
* Create branches based on each feature and assign roles by allocating team members as needed.
* As features are completed, team members will consult with each other to merge and delete the branches to consolidate them into the main branch.

### 3. Commit Rules

* Each commit should contain one minimal, functional unit of change.
* Avoid including unrelated changes in the same commit.
* Commit messages should briefly and clearly describe the changes made.

### 4. Pull Requests and Review
* All changes to the main branch should be made through Pull Requests.
* Direct pushes to main are not allowed.
* When a Pull Request is opened, 1–2 team members should review the changes and merge them after approval.
* If a merge conflict occurs, the Pull Request should not be merged until the conflict is resolved.

### 5. Merge Strategy

* Use the standard **Merge** strategy for pull requests.
* Discuss and manage issues through the **Issues** tab.
* Use **Slack** for team communication and coordination.

### 6. Overall Workflow

| Step | Process | Description |
|------|---------|-------------|
| 1 | Create Feature Branch | Create a separate branch for each feature and assign team members as needed. |
| 2 | Develop Feature | Implement the assigned feature on the feature branch. |
| 3 | Commit Changes | Commit changes in small, functional units with clear commit messages. |
| 4 | Open Pull Request | Open a Pull Request to merge the feature branch into `main`. Direct pushes to `main` are not allowed. |
| 5 | Code Review | 1–2 team members review the Pull Request. |
| 6 | Resolve Conflicts | If a merge conflict occurs, resolve it before merging. Use GitHub Issues for issue tracking and Slack for communication. |
| 7 | Merge | After approval, merge the Pull Request into `main` using the standard Merge strategy. |
| 8 | Delete Feature Branch | After the feature is completed and merged, delete the feature branch after team consultation. |
