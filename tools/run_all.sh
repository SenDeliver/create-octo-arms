#!/bin/bash
# Full regression: spike suites main (scenarios 1-8 + water + Dev2 watcher), lifecycle + negative controls, creative;
# phase-1 suite p1 (Netherite Reach, Rig Block refill/pickup/loot, worn render, diving HUD, creative tab);
# phase-2 suite p2 (Air drain, Crawl, Setting Down let-go, mid-air catch, Air gate, warnings, forged packets with 0 Air);
# phase-3 suite p3 (Rig Settings screen + component, clamping, Standing Height, Speed, key-only mode, Crawl let-go/re-grab, creative, tooltips, long fall; ends with a kick);
# phase-4 suite p4 (enchanting table offers + real enchant, anvil books, durability + Mending, Rig Block keeps enchants, Far Reach, Thrift, Swift, forged speed);
# phase-5 suite p5 (recipe lookups after /reload, real Sequenced Assembly line + Mechanical Crafter grids, worn-out Rig, anvil/Mending repair, tooltips, smithing, JEI, creative tab);
# phase-6 suite p6 (gait spread/steppers/Held while stepping, Tentacle art shots, Rig Blocks, icons, frame cost with two Rigs, plant sounds heard by Dev2) and p6fast (speed.max 20 + Swift II: Grips stay valid for the server at 26 b/s).
# phase-7 suite p7 (goggles on filling Rig Blocks, Display Link "Rig Air" board + percent + GUI, Rig Block shots, Ponder scene frames for every component, Ponder hint).
# phase-8 suite p8 (code-review fixes: forged zero-motion climb Air, ghost blocks, through-wall / unloaded-neighbour Grips, packet bursts, speed check,
# Setting Down rise, lenient settings codec, 16 RPM goggle sync, untested items U1-U6, input cost; ends with a kick) and p8r16 (input cost at Reach 16).
# phase-9 suite p9 (v1.1 Sable Ships: parked turned ship, moving 4/15 b/s, rotating, world <-> ship, ship removed mid-grip, deck ride). Runs last and is the
# only suite built with -PwithSable (Sable runtime); every suite above runs without Sable (no-Sable regression path).
# usage: tools/run_all.sh <tag>   logs land in tools/logs/<tag>-{main,life,creative,p1,p2,p3,p4,p5,p6,p6fast,p7,p8,p8r16,p9}-*
cd "$(dirname "$0")/.."
TAG=${1:-reg}
S=tools/scen
rm -rf run/screenshots run2/screenshots
TIMELINE=$S/main-timeline.txt SCRIPT2=$S/main-watch.txt tools/run_split.sh $S/scene-main.txt $S/main-client.txt tools/logs/$TAG-main
TIMELINE=$S/life-timeline.txt tools/run_split.sh $S/scene-main.txt $S/life-client.txt tools/logs/$TAG-life
GAMEMODE=creative TIMELINE=$S/creative-timeline.txt tools/run_split.sh $S/scene-main.txt $S/creative-client.txt tools/logs/$TAG-creative
TIMELINE=$S/p1-timeline.txt tools/run_split.sh $S/scene-p1.txt $S/p1-client.txt tools/logs/$TAG-p1
TIMELINE=$S/p2-timeline.txt tools/run_split.sh $S/scene-p2.txt $S/p2-client.txt tools/logs/$TAG-p2
TIMELINE=$S/p3-timeline.txt tools/run_split.sh $S/scene-p3.txt $S/p3-client.txt tools/logs/$TAG-p3
TIMELINE=$S/p4-timeline.txt tools/run_split.sh $S/scene-p4.txt $S/p4-client.txt tools/logs/$TAG-p4
TIMELINE=$S/p5-timeline.txt tools/run_split.sh $S/scene-p5.txt $S/p5-client.txt tools/logs/$TAG-p5
TIMELINE=$S/p6-timeline.txt SCRIPT2=$S/p6-watch.txt tools/run_split.sh $S/scene-p6.txt $S/p6-client.txt tools/logs/$TAG-p6
TW_SPEED_MAX=20 TIMELINE=$S/p6fast-timeline.txt tools/run_split.sh $S/scene-p6.txt $S/p6fast-client.txt tools/logs/$TAG-p6fast
TIMELINE=$S/p7-timeline.txt tools/run_split.sh $S/scene-p7.txt $S/p7-client.txt tools/logs/$TAG-p7
TIMELINE=$S/p8-timeline.txt tools/run_split.sh $S/scene-p8.txt $S/p8-client.txt tools/logs/$TAG-p8
TW_REACH_NETHERITE=13 TIMELINE=$S/p8r16-timeline.txt tools/run_split.sh $S/scene-p8.txt $S/p8r16-client.txt tools/logs/$TAG-p8r16
GRADLE_ARGS="$GRADLE_ARGS -PwithSable" TIMELINE=$S/p9-timeline.txt tools/run_split.sh $S/scene-p9.txt $S/p9-client.txt tools/logs/$TAG-p9
mkdir -p tools/shots && cp run/screenshots/*.png run2/screenshots/*.png tools/shots/ 2>/dev/null
for r in main life creative p1 p2 p3 p4 p5 p6 p6fast p7 p8 p9; do echo "== $r"; python3 tools/analyze.py tools/logs/$TAG-$r | grep -v '^mark'; done
python3 tools/spike_metrics.py tools/logs/$TAG
python3 tools/p1_metrics.py tools/logs/$TAG-p1
python3 tools/p2_metrics.py tools/logs/$TAG-p2
python3 tools/p3_metrics.py tools/logs/$TAG-p3
python3 tools/p4_metrics.py tools/logs/$TAG-p4
python3 tools/p5_metrics.py tools/logs/$TAG-p5
python3 tools/p6_metrics.py tools/logs/$TAG-p6
python3 tools/p6fast_metrics.py tools/logs/$TAG-p6fast
python3 tools/p7_metrics.py tools/logs/$TAG-p7
python3 tools/p8_metrics.py tools/logs/$TAG-p8 tools/logs/$TAG-p8r16
python3 tools/p9_metrics.py tools/logs/$TAG-p9
