# SonarCloud (SonarQube Cloud) setup

Static analysis + coverage for the backend, run in CI on every push/PR.

> **Free tier:** SonarQube Cloud is **free for public / open-source repositories** (unlimited).
> Private repos require a paid plan — so for free analysis, make the GitHub repo public.

## What's already wired in this repo

- `backend/build.gradle` — applies the `org.sonarqube` Gradle plugin and `jacoco` (XML coverage
  report on every module), plus a `sonar { }` block with placeholder `projectKey` / `organization`.
- `.github/workflows/sonarcloud.yml` — two jobs: **backend** runs `./gradlew build sonar`, **frontend**
  runs `npm run test:coverage` then the SonarQube scan. Both use the `SONAR_TOKEN` secret.
- `frontend/sonar-project.properties` — frontend analysis config (sources, tests, lcov path).
- `frontend` has a `test:coverage` script + `@vitest/coverage-v8` producing `coverage/lcov.info`.
- `.github/workflows/ci.yml` — a plain build/test workflow (backend `./gradlew build`, frontend
  `lint`/`build`/`test`) with **no** Sonar, for fast PR feedback.

## One-time setup (you do this)

1. **Sign in** at https://sonarcloud.io with your GitHub account.
2. **Create an organization** (or bind your GitHub org) and **add/import this repository** as a
   project. Note the **Organization Key** and **Project Key** SonarCloud shows you.
3. **Fill in the keys** in `backend/build.gradle` — replace `REPLACE_ME_project-key` and
   `REPLACE_ME_organization`. (Alternatively pass them in CI:
   `./gradlew sonar -Dsonar.projectKey=... -Dsonar.organization=...`.)
4. **Generate a token:** SonarCloud → *My Account → Security → Generate Token*.
5. **Add the token to GitHub:** repo → *Settings → Secrets and variables → Actions → New repository
   secret* → name `SONAR_TOKEN`, value = the token.
6. **Turn off Automatic Analysis:** SonarCloud project → *Administration → Analysis Method* → disable
   "Automatic Analysis" (it conflicts with the CI-based Gradle analysis).
7. **Push to `main` or open a PR.** The *SonarCloud* workflow runs the build, tests, JaCoCo coverage,
   and uploads results. Open the project on sonarcloud.io to see issues, coverage, and the quality gate.

## Run the analysis locally (optional)

```bash
cd backend
SONAR_TOKEN=<your-token> ./gradlew build sonar \
  -Dsonar.projectKey=<key> -Dsonar.organization=<org>
```

## Notes

- The Gradle plugin auto-detects the 11 modules and aggregates their JaCoCo XML reports — no per-module
  config needed.
- If the build fails on Gradle 9 compatibility, bump the `org.sonarqube` plugin version in
  `backend/build.gradle` to the latest from https://plugins.gradle.org/plugin/org.sonarqube
- **Frontend** is wired as a **separate SonarCloud project** (cleanest for a JS/TS + Java monorepo).
  Create a second project on sonarcloud.io, then fill its keys into `frontend/sonar-project.properties`.
  The same `SONAR_TOKEN` secret works if both projects are in the same organization. Coverage comes from
  `npm run test:coverage` (vitest → `coverage/lcov.info`).
- So after setup you'll have **two** project keys: the backend one in `backend/build.gradle`'s `sonar { }`
  block, and the frontend one in `frontend/sonar-project.properties`.

## Quality gate & branch protection

Both Sonar jobs **wait for the quality gate** (`sonar.qualitygate.wait=true`) and fail the job if the
gate fails — so they can be used as required status checks. To enforce on `main` (this is configured in
**GitHub repo settings**, not in the repo files):

1. Repo → **Settings → Branches → Add branch protection rule** (or a ruleset) for `main`.
2. Enable **Require a pull request before merging**.
3. Enable **Require status checks to pass before merging**, then select (they appear after running once):
   - `Backend (build & test)`, `Frontend (lint, build & test)` — from `ci.yml`
   - `Backend — build, test & analyze`, `Frontend — test & analyze` — from `sonarcloud.yml`
4. Optional: **Require branches to be up to date**, **Require conversation resolution before merging**.

Tune the gate in SonarCloud under **Project → Quality Gate** (the default "Sonar way" enforces no new
bugs/vulnerabilities and coverage on new code). PRs then get the gate result as a check + inline comments.
