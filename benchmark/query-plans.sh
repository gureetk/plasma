#!/bin/bash
# SPDX-License-Identifier: GPL-3.0-or-later
#
# How the device's own SQLite runs Plasma's guide window query (#127): its plan, and how long the
# query takes there, as written and with the join's order fixed, on the benchmarks' made-up
# source. For an emulator, whose `su` reads Plasma's database; the Benchmarks workflow runs it once
# the benchmarks are done. Usage: query-plans.sh <package>
package=${1:-ca.gureet.plasma.benchmark}
db=/data/data/$package/databases/plasma.db

# The window the live screens load: from 3 hours before this hour to 15 hours after it.
now=$(($(date +%s) * 1000))
hour=$((now - now % 3600000))
from=$((hour - 3 * 3600000))
to=$((hour + 15 * 3600000))

sql() { adb shell "su 0 sqlite3 $db '$1'"; }

echo "-- SQLite $(sql 'SELECT sqlite_version()'), journal mode $(sql 'PRAGMA journal_mode'), $(sql 'SELECT COUNT(*) FROM programmes') programmes"

as_written="SELECT p.* FROM programmes p JOIN sources s ON p.sourceId = s.id WHERE p.generation = s.guideGeneration AND p.startAt < $to AND p.endAt > $from ORDER BY p.epgKey, p.startAt"
in_order="SELECT p.* FROM programmes p CROSS JOIN sources s ON p.sourceId = s.id WHERE p.generation = s.guideGeneration AND p.startAt < $to AND p.endAt > $from ORDER BY p.epgKey, p.startAt"

for query in "$as_written" "$in_order"; do
    echo "-- $query"
    sql "EXPLAIN QUERY PLAN $query"
    # Three times: the first may read the disk, the others the memory's copy.
    for _ in 1 2 3; do
        adb shell "su 0 sqlite3 $db '.timer on' '$query' | tail -1"
    done
done
