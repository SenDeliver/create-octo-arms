# Release rules

Every release of Octo Arms follows these rules, on every Minecraft version. The owner tags and publishes; AI sessions and contributors prepare. This file is identical on `1.21.1` and `1.20.1`. `main` is frozen at 1.5.0: never tag or release from it.

## 1. Versions and tags

| Branch | Loader | Java | Tag | Jar | Not included |
|---|---|---|---|---|---|
| `1.21.1` | NeoForge 21.1 | 21 | `vX.Y.Z+mc1.21.1` | `octo-arms-X.Y.Z+mc1.21.1.jar` | |
| `1.20.1` | Forge 47 | 17 | `vX.Y.Z+mc1.20.1` | `octo-arms-X.Y.Z+mc1.20.1.jar` | Sable Ships, in-game config screen |

- `mod_version` (`X.Y.Z` in `gradle.properties`) is the feature set. Both branches carry the same number when they carry the same changes.
- `minecraft_version`, `mod_loader` and `java_version` in `gradle.properties` describe the branch. `build.gradle`, `build.yml` and `publish.yml` read them, nothing else hard-codes them.
- Every tag carries the platform: `vX.Y.Z+mc<minecraft_version>`, made on the branch of that Minecraft version. The bare tags `v1.0.0` to `v1.5.0` are 1.21.1 history; `publish.yml` does not publish them again.
- One GitHub release per tag, titled `Octo Arms X.Y.Z for <mc>`.

## 2. Version bump, every release

`mod_version` changes on every release, never ship two different jars under one version and Minecraft version.

| Bump | When |
|---|---|
| **Patch** `x.y.Z` | Client-only bug fixes and small features: rendering, sounds, client config, Rig Settings screen, translations. No server code change, no packet change. |
| **Minor** `x.Y.0` | Client + server bug fixes, and bigger features: new mechanics, server config keys, Air or Reach rules, packet changes (bump the network registrar version too). |
| **Major** `X.0.0` | Big functionality rewrites: movement model, Grip model, save format, anything that changes how existing worlds or Rigs behave at large. |

In doubt between two levels, take the higher one.

Which branches get the version:

| Change | Version | Changelog |
|---|---|---|
| Both lines (the usual case: lands on `1.21.1`, cherry-picked to `1.20.1`) | Same `X.Y.Z` on both. Release both the same day, 1.21.1 first. | Untagged entries. |
| One line only (a bug that exists only on that loader or Minecraft version) | Patch bump on that branch only; the other branch skips the number. The next shared release takes a number above both and aligns them again. | `**<mc> only:**` entries. The file still goes to both branches. |
| Feature that cannot exist on one line (Sable Ships) | Ships in the shared version, by the bump table above. | `**1.21.1 only:**` entry. |
| Breaking change (save format, packets, config keys) | Minor or major by the table above, the same on both lines. | `### Compatibility` says what breaks and on which lines. |

Example: both at 1.5.0, a Forge-only fix ships as 1.5.1 on `1.20.1`; the next shared release is 1.5.2 (or 1.6.0) on both, and `1.21.1` never has a 1.5.1.

## 3. Changelog

- One `CHANGELOG.md` ([Keep a Changelog](https://keepachangelog.com/en/1.1.0/)), byte-identical on both branches: `git diff 1.21.1 1.20.1 -- CHANGELOG.md` is empty after every push.
- An entry for one platform starts with `**1.20.1 only:**` or `**1.21.1 only:**`; untagged entries apply to all. Platform-only notes for a release go under `### Platforms`.
- `python3 tools/release_notes.py X.Y.Z <mc>` prints the notes of one platform: other platforms' entries dropped, the tag prefix removed, empty headings dropped. It exits 1 when the section is missing or has nothing for `<mc>`.
- Hand-written notes win when present: `publishing/release-notes-X.Y.Z+mc<mc>.md`, then `publishing/release-notes-X.Y.Z.md`.

## 4. Translations complete, every release

Every language file under `src/main/resources/assets/tentaworks/lang/` carries every key of the generated `en_us.json`. New user-facing text (items, GUI, config labels and tooltips, Ponder, advancements) is added to `TentaworksLang`, then `./gradlew runData`, then translated into all supported languages before the release.

Supported languages (15): cs_cz, de_de, es_es, es_mx, fr_fr, it_it, ja_jp, ko_kr, pl_pl, pt_br, tr_tr, uk_ua, vi_vn, zh_cn, zh_tw.

Check: `python3 tools/lang_check.py` prints PASS for each file and exits 0. A release with a FAIL line does not ship.

Translation rules: reuse the terms already in that file (Rig, Air, Reach, Grip, Crawl, Idle, Hang, Ship, tiers, enchantment names), keep `W`, `S`, `Octo Arms`, `Sable`, numbers and `%` as they are, 2-space indent, UTF-8 without `\u` escapes, keys sorted.

## 5. Checklist

Shared release `X.Y.Z`:

1. Every change is on both branches and `[Unreleased]` is the same on both (`git diff 1.21.1 1.20.1 -- CHANGELOG.md` empty).
2. On `1.21.1`: `python3 tools/release_prep.py X.Y.Z`. It sets `mod_version`, moves the `[Unreleased]` entries under `## [X.Y.Z] - <today>`, updates the compare links and prints the next steps. Text edits only, no git; it refuses an empty `[Unreleased]` or a version that already exists. Review the new section: user-facing, short, platform tags right, packet changes under Compatibility.
3. `./gradlew runData`, then `python3 tools/lang_check.py`.
4. `./gradlew build`, jar `build/libs/octo-arms-X.Y.Z+mc<mc>.jar` (reproducible, compare md5).
5. `tools/run_all.sh <tag>` green, or the flakes listed in `CLAUDE.md` re-run alone and green.
6. Commit `Release X.Y.Z` and push. On `1.20.1`: `git cherry-pick` that commit, then steps 3 to 5 there, push.
7. Per platform: `python3 tools/release_notes.py X.Y.Z <mc> > notes.md`.
8. Owner tests each jar in game, client and server. Only then the owner creates the release, which makes the tag, **without attaching the jar**, 1.21.1 first:
   `gh release create vX.Y.Z+mc<mc> --target <branch> --title "Octo Arms X.Y.Z for <mc>" --notes-file notes.md`
   Never tag on your own.

One-line release: steps 2 to 8 on that branch only, then bring `CHANGELOG.md` to the other branch unchanged (`git checkout <branch> -- CHANGELOG.md`, commit), without the `mod_version` bump.

## 6. What publish.yml does

A published release (or a manual run) starts `.github/workflows/publish.yml`, the same file on both branches:

1. Parses the tag as `v<ver>+mc<mc>`, fails otherwise.
2. Checks out the tag and reads `gradle.properties`: fails unless `<ver>` is `mod_version` and `<mc>` is `minecraft_version` (the tag sits on the right branch) and the changelog section has entries for `<mc>`.
3. Builds with that branch's `java_version`. Skips everything when the release already carries the jar (an edited release fires again).
4. Notes from `publishing/release-notes-<ver>+mc<mc>.md`, else `publishing/release-notes-<ver>.md`, else `tools/release_notes.py <ver> <mc>`.
5. Uploads to the GitHub release and CurseForge (Modrinth once approved) as `Octo Arms <ver> for <mc>`, version `<ver>+mc<mc>`, with `mod_loader`, the Minecraft and Java version, and the dependencies: Create required, JEI optional, Sable optional on 1.21.1 only.

A release created with the jar already attached makes the workflow skip everything (it reads that as a re-fire), and CurseForge gets nothing. Repair:

`gh workflow run publish.yml --ref <branch> -f tag=vX.Y.Z+mc<mc> -f targets=curseforge`

Deploying to the owner's server and local client is documented outside the repo. Never put server details here.
