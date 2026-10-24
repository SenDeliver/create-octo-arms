# Octo Arms (mod id `tentaworks`)

Create addon. This branch `1.20.1`: Forge 47.1.33 / MC 1.20.1, Create 6.0.8-289, Java 17, ModDevGradle Legacy, no Sable (Ships are 1.21.1 only, `NoShips` is the only `ShipFrames`). `1.21.1` is NeoForge 21.1 / MC 1.21.1. Read first: `docs/CONTEXT.md` (glossary, use its terms), `CONTRIBUTING.md` (style, frozen IDs), `tools/README.md` (test harness). Public repo: everything here is visible.

## Branches

- `1.21.1`: NeoForge 21.1 line, versions `x.y.z`, tags `vX.Y.Z`.
- `1.20.1`: Forge 47 line, versions `x.y.z+mc1.20.1`, tags `vX.Y.Z+mc1.20.1`. This branch.
- `main`: frozen at 1.5.0, kept only for history and links. Never commit or merge into it.
- Fixes land on `1.21.1` first, then `git cherry-pick` onto `1.20.1`. Expected conflicts: loader glue (`Tentaworks`, `TentaworksClient`, `TentaworksNetwork`, `RigEnchants`, `RigSettings`, renderers, datagen, `tentaworks.mixins.json`, build files, docs).
- PRs target the branch of the version they change. Draft PR 21 (this branch against `1.21.1`) only shows the port diff, it is never merged. Keep file names and class layout identical to `1.21.1`.

## Layout

- `src/main`: the mod. `rig/` server + shared logic, `client/` wearer side (`RigClient` drives movement, `RigVisuals` what every client renders), `mixin/` (common list vs `client` list in `tentaworks.mixins.json`; common code never loads client classes, bridge with a predicate as `RigSneak` does).
- `src/dev`: dev-only commands, TRACE/STRACE logging, `DevScript` client scripts. Not in the jar.
- `tools/`: harness. Suites = scene (RCON) + client script + timeline + `*_metrics.py`. `run_all.sh <tag>` runs all 21 (no p9, rideaero, obs*, listship on this branch), GUI clients pop up.
- Tests: `./gradlew test` = plain JUnit plus `fmlTest` (classes in `fmlTestClasses` in `build.gradle` run inside a dev server; a new FML-backed test class goes in that list).

## Verify a mixin target before trusting it

Vanilla + Forge sources: `build/moddev/artifacts/forge-1.20.1-<ver>-sources.jar` (unzip the class into the scratchpad); SRG names in `build/moddev/artifacts/namedToIntermediate.tsrg`. Create 6.0.8 has no sources jar on its Maven (only `-slim`): read the `mc1.20.1/dev` branch of Create's GitHub repo. Count the call sites of a redirected method: a `@Redirect` without `ordinal` hits every match. Create targets are `remap=false`, the Mixin AP does not check them: a wrong one fails only at class load.

Forge EventBus: `@SubscribeEvent` static handlers and their class must be `public`, else they are skipped without an error.

## Reviewing a PR

1. `gh pr diff`, then `git fetch origin pull/N/head:pr-N && git checkout pr-N`. Local edits to `tools/run_all.sh` block the checkout: stash them.
2. `./gradlew build`, run the PR's suite alone first (`tools/run_split.sh ...`, see `tools/README.md`), then `tools/run_all.sh <tag>`.
3. `run_all.sh` copies screenshots to `tools/shots/` (gitignored, never commit them).
4. Known timing flakes, re-run the suite alone before blaming the PR: p7 `D1 display board` (RCON read), p8 `forged zero climb` (packet bursts).
5. Comparison baseline: last `tools/logs/*-runall.txt` on `1.21.1` (`port1` on `1.20.1`).
6. Merge: squash into the PR's version branch, `gh pr merge N --squash`. Maintainer pushes to `1.21.1` / `1.20.1` directly for housekeeping; contributors go through PRs.

## Harness log contract

New TRACE/STRACE fields go at the end of the line only; older parsers match by field order. Add a suite to `run_all.sh` (run line, loop list, metrics line) and a section in `tools/README.md`.

## Release

Rules in `docs/RELEASE.md` (this branch: tag `vX.Y.Z+mc1.20.1`, jar `octo-arms-X.Y.Z+mc1.20.1.jar`): version bump on every release (patch = client-only, minor = client + server or bigger feature, major = rewrite), all 15 translations complete (`python3 tools/lang_check.py`), changelog, reproducible jar, harness green, owner tests in game and tags. Never tag or create a GitHub release on your own. Server and local client deploy notes live outside the repo (`../../STATE.md`, private), never put server details in this repo.
