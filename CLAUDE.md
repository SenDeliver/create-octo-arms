# Octo Arms (mod id `tentaworks`)

Create addon for NeoForge 21.1 / MC 1.21.1. Read first: `docs/CONTEXT.md` (glossary, use its terms), `CONTRIBUTING.md` (style, frozen IDs), `tools/README.md` (test harness). Public repo: everything here is visible.

## Layout

- `src/main`: the mod. `rig/` server + shared logic, `client/` wearer side (`RigClient` drives movement, `RigVisuals` what every client renders), `mixin/` (common list vs `client` list in `tentaworks.mixins.json`; common code never loads client classes, bridge with a predicate as `RigSneak` does).
- `src/dev`: dev-only commands, TRACE/STRACE logging, `DevScript` client scripts. Not in the jar.
- `tools/`: harness. Suites = scene (RCON) + client script + timeline + `*_metrics.py`. `run_all.sh <tag>` runs all 18, about 50 min on the Mac, GUI clients pop up.

## Verify a mixin target before trusting it

Vanilla + NeoForge sources: `build/moddev/artifacts/neoforge-<ver>-sources.jar` (unzip the class into the scratchpad). Count the call sites of a redirected method: a `@Redirect` without `ordinal` hits every match.

## Reviewing a PR

1. `gh pr diff`, then `git fetch origin pull/N/head:pr-N && git checkout pr-N`. Local edits to `tools/run_all.sh` block the checkout: stash them.
2. `./gradlew build`, run the PR's suite alone first (`tools/run_split.sh ...`, see `tools/README.md`), then `tools/run_all.sh <tag>`.
3. `run_all.sh` overwrites the tracked screenshots in `tools/shots/`: `git checkout -- tools/shots` afterwards unless new shots are wanted.
4. Known timing flakes, re-run the suite alone before blaming the PR: p7 `D1 display board` (RCON read), p8 `forged zero climb` (packet bursts), p9 ride timings.
5. Comparison baseline: last `tools/logs/*-runall.txt` on `main`.
6. Merge: squash, `gh pr merge N --squash`. Maintainer pushes to `main` directly for housekeeping; contributors go through PRs.

## Harness log contract

New TRACE/STRACE fields go at the end of the line only; older parsers match by field order. Add a suite to `run_all.sh` (run line, loop list, metrics line) and a section in `tools/README.md`.

## Release

- Version: `mod_version` in `gradle.properties`. Jar: `build/libs/octo-arms-<ver>+mc1.21.1.jar` (reproducible, compare md5).
- `CHANGELOG.md`: move `[Unreleased]` entries under `## [x.y.z] - date`. Keep user-facing and short.
- Tag `vX.Y.Z` + GitHub release triggers `publish.yml` (CurseForge; Modrinth off). The owner tags only after testing in game, never tag on your own.
- Deploying to the owner's server and local client is documented outside the repo (`../../STATE.md`, private). Never put server details in this repo.
- Protocol: packets changed → bump the network registrar version, say so in CHANGELOG under Compatibility.
