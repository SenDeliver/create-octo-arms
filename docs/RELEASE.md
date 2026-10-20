# Release rules

Every release of Octo Arms follows these rules. The owner tags and publishes; AI sessions and contributors prepare.

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
3. `CHANGELOG.md`: move `[Unreleased]` entries under `## [x.y.z] - date`, user-facing and short. Packet change: say so under Compatibility.
4. `./gradlew build`, jar `build/libs/octo-arms-<ver>+mc1.21.1.jar` (reproducible, compare md5).
5. `tools/run_all.sh <tag>` green, or the flakes listed in `CLAUDE.md` re-run alone and green.
6. Owner tests the jar in game. Only then the owner tags `vX.Y.Z` and creates the GitHub release, which runs `publish.yml` (CurseForge). Never tag on your own.

Deploying to the owner's server and local client is documented outside the repo. Never put server details here.
