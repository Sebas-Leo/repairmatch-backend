# Make each contribution visible

Use one small issue per deliverable, one branch, and one reviewed pull request (PR). See the [shared progress dashboard](https://github.com/Sebas-Leo/repairmatch-backend/issues/1). Module owners are proposed in the README; GitHub assignments remain pending until teammates provide their usernames and accept invitations.

## Daily path

1. Search existing issues. Use **Backend task** for new work; define the problem, solution, dependencies, and observable acceptance checkboxes. Initial tasks are proposals, not approved work.
2. A maintainer checks scope, assigns the contributor and exactly one `module:*` label, removes `status:needs-review`, and adds `status:approved`. Keep approval while work progresses; use `status:in-progress`, `status:blocked`, or `status:in-review` to describe the current state (one of these at a time).
3. Start from updated `main`, create `feature/<issue>-short-description`, and make small conventional commits, for example `feat(proposals): validate proposal ownership`. Include tests and documentation with each behavior. Never commit credentials or production data.
4. Open a PR with a conventional title. Put **one standalone `Closes #123` line per completed issue**, or `Refs #123` for partial work in this repository. Replace template instructions with actual verification commands/results and sanitized evidence links.
5. Request another member's review. Wait for peer approval and passing checks, resolve feedback, then squash-merge with a conventional commit title. No direct pushes to `main` under this team agreement.
6. Check the issue and dashboard after merge. `Closes` links close completed work when merged into the default branch; `Refs` leaves work open. Do not close incomplete work to improve the dashboard.

## What the automation does (and does not do)

| Automation | Observed checks / limits |
| --- | --- |
| **PR checks / Contribution contract** | Checks title format, same-repository issue references, `status:approved` on every referenced issue, and nonempty verification text. Does **not** validate whether evidence is true, tests pass, or a peer approved. Approval-label changes alone do not rerun this check: edit the PR description or rerun it in Actions afterward. |
| **Team progress** | Refreshes on task events, PR events, pushes to `main`, or manual Actions run. Lists task owners/assignees, declared labels, acceptance checkboxes, linked PRs, merges to `main`, and last task update. Only `delivery` tasks count; `planning-only` trackers do not. |
| **PR checks / Workflow tests** | Runs mocked workflow-logic tests on PRs and pushes to `main` with read-only permissions and no secrets. Checks metadata parsing and reporting, not backend functionality. |
| **Backend CI (pending)** | No Java application or Postman collection exists yet. Maven/JUnit/database tests and automated Postman runs must be added with the application; a green contribution check is **not** a passing backend test. |

The dashboard distinguishes **closed without merged evidence**, **closed with merged evidence**, and **canceled/not planned**. A partial `Refs` merge is still only evidence to inspect, not proof the entire task works. Checklist totals include only the `### Acceptance criteria` section, not pre-flight checkboxes. Counts are records, not completion percentages, grades, hours, effort, or personal-performance rankings. Review the linked changes and tests together.

## Maintainer setup and safety

- Labels and issues are managed on GitHub. The issue form's module selection does **not** apply its module label automatically; triage must do that. Approval is a team convention, not a tamper-proof permission system.
- Set repository Actions variable `PROGRESS_ISSUE_NUMBER` to the shared dashboard issue number. That issue must have `dashboard`; the workflow refuses other targets and never commits or pushes files. Refresh manually through **Actions → Team progress → Run workflow** when needed.
- Main-branch protection is **not assumed to be enabled**. Where the repository plan supports it, configure a rule requiring a PR, one approval, resolved conversations, and the `Contribution contract` check; disallow force-push/deletion. Until verified, these are team rules rather than enforced restrictions. Add real backend check names when backend CI exists.
- Dashboard automation uses `pull_request_target` only to read metadata and update the designated issue: no checkout, no PR code execution, no credentials in PR content. Keep this boundary when changing workflows. Updates appear as GitHub Actions bot activity, not human implementation commits.
- Local workflow-logic checks: `node --test tests/workflows.test.cjs`. These mocked metadata tests are not application tests.

## Evidence each module should provide

Each owner supplies endpoints/persistence, business rules and ownership checks, automated valid/invalid-path tests, sanitized Postman requests/results, and a peer review. Agreement on API contracts and integration dependencies comes before parallel implementation; review the selection/service-creation boundary together.
