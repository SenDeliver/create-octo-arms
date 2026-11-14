#!/bin/bash
# Full regression: every suite of tools/suites.tsv (one row each; what each tests: the comments there) through run_split.sh,
# then the screenshots to tools/shots/, analyze.py per suite and the metrics below.
# TW_SHARDS=1 (default): the table rows in order, one after another, on the default shard (run/, run2/, ports 25608/25609).
# TW_SHARDS=N (2..6): suites in parallel, N workers on the shards default, B, C, ... (TW_SHARD of run_split.sh). Per dep set
# (plain, then curios, then sable) its non-quiet suites, longest weight first, each worker taking the next free one; then the
# quiet lane: every quiet suite alone, serial, on the default shard, in table order, Sable suites last. run_split output of each
# suite goes to tools/logs/<tag>-<suite>-run.txt. TW_QUIET=par: quiet suites run as the others (still per dep set).
# TW_DRY=1: print the command of each suite in schedule order (TW_SHARDS>1: the shard is a prediction from the weights), run nothing.
# A suite with a non-zero exit (run_split.sh refused to start: shard busy, ports taken) in a parallel phase stops its worker for that phase.
# TW_RUN_SPLIT=<script>: a stand-in for tools/run_split.sh (scheduler tests). Ctrl-C: no new suite starts (serial: the running suite dies, parallel: running ones finish), then exit 130 without metrics.
# TW_SHARDS>1 builds once first (gradlew classes devClasses): no source edit or commit in this checkout while it runs, the next suite's gradle would rebuild under the running shards.
# usage: [TW_SHARDS=N] [TW_QUIET=par] [TW_DRY=1] tools/run_all.sh <tag>   logs land in tools/logs/<tag>-<suite>-*, schedule in tools/logs/<tag>-sched.txt
cd "$(dirname "$0")/.."
TAG=${1:-reg}
S=tools/scen
SHARDS=${TW_SHARDS:-1}
RUN_SPLIT=${TW_RUN_SPLIT:-tools/run_split.sh}
LETTER=(x - B C D E F)
unset TW_SHARD
case $SHARDS in [1-6]) ;; *) echo "TW_SHARDS must be 1..6" >&2; exit 2;; esac

N=0
while IFS=$'\t' read -r name dep quiet weight scene client env; do
  case $name in ''|'#'*) continue;; esac
  [ "$env" = - ] && env=
  bad=
  case $dep in plain) flag=;; curios) flag=-PwithCurios;; sable) flag=-PwithSable;; *) flag=; bad="$bad dep=$dep";; esac
  case $quiet in 0|1) ;; *) bad="$bad quiet=$quiet";; esac
  case $weight in ''|*[!0-9]*) bad="$bad weight=$weight";; esac
  for f in -PwithCurios -PwithSable; do
    case "$env" in *"$f"*) [ "$f" = "$flag" ] || bad="$bad env-has$f";; *) [ "$f" = "$flag" ] && bad="$bad env-lacks$f";; esac
  done
  [ -f "$S/$scene" ] || bad="$bad scene=$scene"
  [ -f "$S/$client" ] || bad="$bad client=$client"
  j=0; while [ $j -lt $N ]; do [ "${NAMES[j]}" = "$name" ] && bad="$bad duplicate"; j=$((j+1)); done
  if [ -n "$bad" ]; then echo "tools/suites.tsv: $name:$bad" >&2; exit 2; fi
  NAMES[N]=$name; DEPS[N]=$dep; QUIETS[N]=$quiet; WEIGHTS[N]=$weight; SCENES[N]=$scene; CLIENTS[N]=$client; ENVS[N]=$env
  N=$((N+1))
done < tools/suites.tsv

sname() { if [ "$1" = - ]; then echo default; else echo "$1"; fi; }
cdir() { if [ "$1" = - ]; then echo run; else echo "runs/$1/client"; fi; }
c2dir() { if [ "$1" = - ]; then echo run2; else echo "runs/$1/client2"; fi; }
cmd() {
  local p= e=${ENVS[$1]}
  [ "$2" != - ] && p="TW_SHARD=$2 "
  echo "$p${e:+$e }$RUN_SPLIT \$S/${SCENES[$1]} \$S/${CLIENTS[$1]} tools/logs/\$TAG-${NAMES[$1]}"
}
isquiet() { [ "${QUIETS[$1]}" = 1 ] && [ "$TW_QUIET" != par ]; }
bydep() {
  local i=0
  while [ $i -lt $N ]; do
    [ "${DEPS[i]}" = "$1" ] && ! isquiet $i && echo "${WEIGHTS[i]} $i"
    i=$((i+1))
  done | sort -s -k1,1nr | awk '{print $2}'
}
lane() {
  local i=0
  while [ $i -lt $N ]; do isquiet $i && [ "${DEPS[i]}" != sable ] && echo $i; i=$((i+1)); done
  i=0
  while [ $i -lt $N ]; do isquiet $i && [ "${DEPS[i]}" = sable ] && echo $i; i=$((i+1)); done
}

worker() {
  local sh=$1 i t0 t rc
  while read -r i <&3; do
    [ -e "$ST/stop" ] && break
    mkdir "$ST/claim-$i" 2>/dev/null || continue
    t0=$(date +%s)
    echo "$(date +%H:%M:%S) start ${NAMES[i]} on $(sname $sh)"
    if [ "$SHARDS" = 1 ]; then
      eval "$(cmd $i $sh)" 3<&-
    else
      eval "$(cmd $i $sh)" > "tools/logs/$TAG-${NAMES[i]}-run.txt" 2>&1 3<&-
    fi
    rc=$?
    t=$(( $(date +%s) - t0 ))
    printf '%s\t%s\t%s\t%s\n' "${NAMES[i]}" "$sh" "$rc" "$t" >> "$ST/done"
    echo "$(date +%H:%M:%S) done  ${NAMES[i]} on $(sname $sh) rc=$rc ${t} s"
    if [ -n "$3" ] && [ $rc != 0 ]; then echo "$(date +%H:%M:%S) shard $(sname $sh) refused ${NAMES[i]}: no more suites on it this phase"; break; fi
  done 3< "$2"
}
DRYSUM=0
simulate() {
  local label=$1 w=$2 i k b end=0 sum=0 free
  shift 2
  k=1; while [ $k -le $w ]; do free[k]=0; k=$((k+1)); done
  for i in "$@"; do
    b=1; k=2
    while [ $k -le $w ]; do [ ${free[k]} -lt ${free[b]} ] && b=$k; k=$((k+1)); done
    cmd $i ${LETTER[b]}
    free[b]=$(( free[b] + WEIGHTS[i] )); sum=$(( sum + WEIGHTS[i] ))
    [ ${free[b]} -gt $end ] && end=${free[b]}
  done
  DRYSUM=$(( DRYSUM + end ))
  echo "# phase $label: $# suites, $w worker(s), $sum s of suites, predicted $end s" >&2
}
phase() {
  local label=$1 w=$2 k p pids=
  shift 2
  [ $# -gt 0 ] || return 0
  [ "$w" -gt $# ] && w=$#
  if [ -n "$TW_DRY" ]; then simulate "$label" "$w" "$@"; return 0; fi
  [ -e "$ST/stop" ] && return 0
  printf '%s\n' "$@" > "$ST/q-$label"
  echo "$(date +%H:%M:%S) phase $label: $# suites, $w worker(s)"
  if [ "$w" = 1 ]; then worker - "$ST/q-$label"; return 0; fi
  k=1
  while [ $k -le $w ]; do worker "${LETTER[k]}" "$ST/q-$label" par & pids="$pids $!"; k=$((k+1)); done
  for p in $pids; do while :; do wait $p; [ $? -gt 128 ] || break; done; done
}
schedule() {
  if [ "$SHARDS" = 1 ]; then
    phase all 1 $(i=0; while [ $i -lt $N ]; do echo $i; i=$((i+1)); done)
  else
    for d in plain curios sable; do phase $d $SHARDS $(bydep $d); done
    phase quiet 1 $(lane)
  fi
}

if [ -n "$TW_DRY" ]; then
  echo "# S=$S TAG=$TAG, $N suites, TW_SHARDS=$SHARDS${TW_QUIET:+ TW_QUIET=$TW_QUIET}" >&2
  schedule
  [ "$SHARDS" = 1 ] || echo "# predicted wall clock of the suites: $DRYSUM s" >&2
  exit 0
fi
mkdir -p tools/logs
ST=$(mktemp -d "${TMPDIR:-/tmp}/tw-runall.XXXXXX") || exit 1
: > "$ST/done"
k=1; while [ $k -le $SHARDS ]; do rm -rf "$(cdir ${LETTER[k]})/screenshots" "$(c2dir ${LETTER[k]})/screenshots"; k=$((k+1)); done
if [ "$SHARDS" != 1 ] && [ -z "$TW_RUN_SPLIT" ]; then
  ./gradlew classes devClasses > "tools/logs/$TAG-build.txt" 2>&1 || { echo "run_all: build failed, see tools/logs/$TAG-build.txt" >&2; exit 1; }
fi
trap 'touch "$ST/stop"; echo "run_all: stop asked, no new suite starts" >&2' INT TERM
schedule
trap - INT TERM
cp "$ST/done" "tools/logs/$TAG-sched.txt"
if [ -e "$ST/stop" ]; then echo "run_all $TAG: stopped, ran: $(cut -f1 "$ST/done" | tr '\n' ' ')" >&2; rm -rf "$ST"; exit 130; fi
mkdir -p tools/shots
k=1; while [ $k -le $SHARDS ]; do cp "$(cdir ${LETTER[k]})"/screenshots/*.png "$(c2dir ${LETTER[k]})"/screenshots/*.png tools/shots/ 2>/dev/null; k=$((k+1)); done
LOOK_SHARD=$(awk -F'\t' '$1 == "look" {s = $2} END {print s}' "$ST/done")
LOOK_SHOTS=$(cdir ${LOOK_SHARD:--})/screenshots
SUITES_SECONDS=$SECONDS
rm -rf "$ST"
i=0; while [ $i -lt $N ]; do r=${NAMES[i]}; echo "== $r"; python3 tools/analyze.py tools/logs/$TAG-$r | grep -v '^mark'; i=$((i+1)); done
python3 tools/spike_metrics.py tools/logs/$TAG
python3 tools/p1_metrics.py tools/logs/$TAG-p1
python3 tools/p2_metrics.py tools/logs/$TAG-p2
python3 tools/p3_metrics.py tools/logs/$TAG-p3
python3 tools/p4_metrics.py tools/logs/$TAG-p4
python3 tools/p5_metrics.py tools/logs/$TAG-p5
python3 tools/p6_metrics.py tools/logs/$TAG-p6
python3 tools/p6_metrics.py tools/logs/$TAG-p6walk --walk
python3 tools/p6fast_metrics.py tools/logs/$TAG-p6fast
python3 tools/p7_metrics.py tools/logs/$TAG-p7
python3 tools/p8_metrics.py tools/logs/$TAG-p8 tools/logs/$TAG-p8r16
python3 tools/pole_metrics.py tools/logs/$TAG-pole
python3 tools/tank_metrics.py tools/logs/$TAG-tank
python3 tools/cave_metrics.py tools/logs/$TAG-cave
python3 tools/sneak_metrics.py tools/logs/$TAG-sneak
python3 tools/sneak2_metrics.py tools/logs/$TAG-sneak2
python3 tools/flat_metrics.py tools/logs/$TAG-flat
python3 tools/ride_metrics.py tools/logs/$TAG-ride
python3 tools/spec_metrics.py tools/logs/$TAG-spec
python3 tools/dig_metrics.py tools/logs/$TAG-dig
python3 tools/sculk_metrics.py tools/logs/$TAG-sculk
python3 tools/curios_metrics.py tools/logs/$TAG-curios
python3 tools/curiosoff_metrics.py tools/logs/$TAG-curiosoff
python3 tools/faruse_metrics.py tools/logs/$TAG-faruse
python3 tools/glint_metrics.py tools/logs/$TAG-glint
python3 tools/airhud_metrics.py tools/logs/$TAG-airhud
python3 tools/ownhand_metrics.py tools/logs/$TAG-ownhand
python3 tools/fpview_metrics.py tools/logs/$TAG-fpview
python3 tools/grab_metrics.py tools/logs/$TAG-grab
python3 tools/look_metrics.py tools/logs/$TAG-look "$LOOK_SHOTS"
python3 tools/lag_metrics.py tools/logs/$TAG-lag
python3 tools/lag_metrics.py tools/logs/$TAG-lagneth
python3 tools/lag_metrics.py tools/logs/$TAG-lagoff check-off
python3 tools/slider_metrics.py tools/logs/$TAG-slider
python3 tools/p9_metrics.py tools/logs/$TAG-p9
python3 tools/rideaero_metrics.py tools/logs/$TAG-rideaero
python3 tools/shipgrab_metrics.py tools/logs/$TAG-shipgrab
FAILED=$(awk -F'\t' '$3 != 0 {printf " %s", $1}' "tools/logs/$TAG-sched.txt")
[ -n "$FAILED" ] && echo "run_all $TAG: non-zero exit, re-run alone:$FAILED"
NOTRUN=; i=0; while [ $i -lt $N ]; do grep -q "^${NAMES[i]}	" "tools/logs/$TAG-sched.txt" || NOTRUN="$NOTRUN ${NAMES[i]}"; i=$((i+1)); done
[ -n "$NOTRUN" ] && echo "run_all $TAG: not run:$NOTRUN"
echo "run_all $TAG: TW_SHARDS=$SHARDS, suites $((SUITES_SECONDS / 60)) min $((SUITES_SECONDS % 60)) s, total $((SECONDS / 60)) min $((SECONDS % 60)) s"
