# Release rules

Every release of Octo Arms follows these rules. The owner tags and publishes; AI sessions and contributors prepare.

This is the copy on `mc/1.20.1` (Forge 1.20.1). A 1.20.1 release ships the same `mod_version` as the 1.21.1 release it was cherry-picked from, with `+mc1.20.1` after it: tag `vX.Y.Z+mc1.20.1`, jar `octo-arms-X.Y.Z+mc1.20.1.jar`, built with Java 17. The fixes are cherry-picked from `1.21.1`, whose `vX.Y.Z` release of the same version comes first. `main` is frozen at 1.5.0: never tag or release from it.

## 1. Version bump, every release

`mod_version` in `gradle.properties` changes on every release, never ship two different jars under one version.

| Bump | When |
|---|---|
| **Patch** `x.y.Z` | Client-only bug fixes and small features: rendering, sounds, client config, Rig Settings screen, translations. No server code change, no packet change. |
| **Minor** `x.Y.0` | Client + server bug fixes, and bigger features: new mechanics, server config keys, Air or Reach rules, packet changes (bump the network registrar version too). |
| **Major** `X.0.0` | Big functionality rewrites: movement model, Grip model, save format, anything that changes how existing worlds or Rigs behave at large. |

In doubt between two levels, take the higher one.

## 2. Translations complete, every release

Every language file under `src/main/resources/assets/tentaworks/lang/` carries every key of the generated `en_us.json`. New user-facing text (items, GUI, config labels and tooltips, Ponder, advancements) is added to `TentaworksLang`, then `./gradlew runData`, then translated into all supported languages before the release.

Supported languages (15): cs_cz, de_de, es_es, es_mx, fr_fr, it_it, ja_jp, ko_kr, pl_pl, pt_br, tr_tr, uk_ua, vi_vn, zh_cn, zh_tw.

Check: `python3 tools/lang_check.py` prints PASS for each file and exits 0. A release with a FAIL line does not ship.

Translation rules: reuse the terms already in that file (Rig, Air, Reach, Grip, Crawl, Idle, Hang, Ship, tiers, enchantment names), keep `W`, `S`, `Octo Arms`, `Sable`, numbers and `%` as they are, 2-space indent, UTF-8 without `\u` escapes, keys sorted.

## 3. Checklist

1. `./gradlew runData`, then `python3 tools/lang_check.py`.
2. Bump `mod_version` by the table above.
3. `CHANGELOG.md`: move `[Unreleased]` entries under `## [x.y.z+mc1.20.1] - date`, user-facing and short; 1.20.1-only differences in a sentence on top of the entry. Packet change: say so under Compatibility.
4. `./gradlew build` (Java 17 toolchain), jar `build/libs/octo-arms-<ver>+mc1.20.1.jar` (reproducible, compare md5).
5. `tools/run_all.sh <tag>` green, or the flakes listed in `CLAUDE.md` re-run alone and green.
6. Owner tests the jar in game on a Forge 1.20.1 client and server. Only then the owner tags `vX.Y.Z+mc1.20.1` on `mc/1.20.1` and creates the GitHub release **without attaching the jar** (`gh release create vX.Y.Z+mc1.20.1 --target mc/1.20.1 --title "Octo Arms X.Y.Z for 1.20.1" --notes-file notes.md`): `publish.yml` builds the jar on the tag and uploads it to the release, Modrinth and CurseForge with `loaders: forge`, `game-versions: 1.20.1`, `java: 17` and no Sable dependency. A release created with the jar already attached makes the workflow skip everything (it reads that as a re-fire), and CurseForge gets nothing; repair with `gh workflow run publish.yml --ref mc/1.20.1 -f tag=vX.Y.Z+mc1.20.1 -f targets=curseforge`. Never tag on your own.

Deploying to the owner's server and local client is documented outside the repo. Never put server details here.
