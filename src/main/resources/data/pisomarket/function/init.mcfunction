# Runs on every datapack load (world start, or /reload) via
# data/minecraft/tags/function/load.json.
#
# Sets up the PvP health display from CLAUDE.md's "v2 redesign" §10: a
# heart + number below every nearby player's nametag, always visible. This
# is entirely vanilla's own "below_name" scoreboard display slot with a
# "health" criteria objective — no custom client rendering needed at all.
#
# "scoreboard objectives add" on an objective that already exists (every
# server start after the first) fails with a harmless one-line error in
# the log — expected, not a bug. There's no clean "if exists" branch in
# vanilla mcfunction to avoid it.
scoreboard objectives add piso_health health
scoreboard objectives setdisplay below_name piso_health
