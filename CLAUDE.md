# Octo Arms (mod id `tentaworks`)

Create addon for NeoForge 21.1 / MC 1.21.1. Read first: `docs/CONTEXT.md` (glossary, use its terms), `CONTRIBUTING.md` (style, frozen IDs), `tools/README.md` (test harness). Public repo: everything here is visible.

## Branches

- `1.21.1`: NeoForge 21.1 line, tags `vX.Y.Z+mc1.21.1` (bare `v1.0.0`..`v1.5.0` are its history). This branch.
- `1.20.1`: Forge 47 line, tags `vX.Y.Z+mc1.20.1`.
- Same `mod_version` X.Y.Z on both for the same changes; `CHANGELOG.md` and the release files are byte-identical on both.
- `main`: frozen at 1.5.0, kept only for history and links. Never commit or merge into it.
- Fixes land on `1.21.1` first, then `git cherry-pick` onto `1.20.1`. Expected conflicts: loader glue (`Tentaworks`, `TentaworksClient`, `TentaworksNetwork`, `RigEnchants`, `RigSettings`, renderers, datagen, `tentaworks.mixins.json`, build files, docs).
- PRs target the branch of the version they change.

## Layout

- `src/main`: the mod. `rig/` server + shared logic, `client/` wearer side (`RigClient` drives movement, `RigVisuals` what every client renders), `mixin/` (common list vs `client` list in `tentaworks.mixins.json`; common code never loads client classes, bridge with a predicate as `RigSneak` does).
- `src/dev`: dev-only commands, TRACE/STRACE logging, `DevScript` client scripts. Not in the jar.
- `tools/`: harness. Suites = scene (RCON) + client script + timeline + `*_metrics.py`. `run_all.sh <tag>` runs all 23, about 44 min on the Mac, GUI clients pop up.

## Verify a mixin target before trusting it

Vanilla + NeoForge sources: `build/moddev/artifacts/neoforge-<ver>-sources.jar` (unzip the class into the scratchpad). Count the call sites of a redirected method: a `@Redirect` without `ordinal` hits every match.

## Reviewing a PR

1. `gh pr diff`, then `git fetch origin pull/N/head:pr-N && git checkout pr-N`. Local edits to `tools/run_all.sh` block the checkout: stash them.
2. `./gradlew build`, run the PR's suite alone first (`tools/run_split.sh ...`, see `tools/README.md`), then `tools/run_all.sh <tag>`.
3. `run_all.sh` copies screenshots to `tools/shots/` (gitignored, never commit them).
4. Known timing flakes, re-run the suite alone before blaming the PR: p7 `D1 display board` (RCON read), p8 `forged zero climb` (packet bursts), p9 ride timings.
5. Comparison baseline: last `tools/logs/*-runall.txt` on `1.21.1` (`port1` on `1.20.1`).
6. Merge: squash into the PR's version branch, `gh pr merge N --squash`. Maintainer pushes to `1.21.1` / `1.20.1` directly for housekeeping; contributors go through PRs.

## Harness log contract

New TRACE/STRACE fields go at the end of the line only; older parsers match by field order. Add a suite to `run_all.sh` (run line, loop list, metrics line) and a section in `tools/README.md`.

## Release

Rules in `docs/RELEASE.md`: shared `mod_version` across Minecraft versions, tags `vX.Y.Z+mc<mc>`, one changelog with `**<mc> only:**` entries. Prep with `python3 tools/release_prep.py X.Y.Z` (bump + changelog cut), notes with `python3 tools/release_notes.py X.Y.Z <mc>`. Version bump on every release (patch = client-only, minor = client + server or bigger feature, major = rewrite), all 15 translations complete (`python3 tools/lang_check.py`), reproducible jar, harness green, owner tests in game and tags. Never tag or create a GitHub release on your own. Server and local client deploy notes live outside the repo (`../../STATE.md`, private), never put server details in this repo.
