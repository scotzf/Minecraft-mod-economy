# /function pisomarket:testkit
# Gives the running player one of everything needed to test Piso Market
# end to end in a single command, instead of typing fifteen /give lines by
# hand. Requires permission level 2 (same gate as /eco) since it calls
# /eco give on the caller.
#
# Weapons
give @s pisomarket:moltensword
give @s pisomarket:moltenblade
give @s pisomarket:hearthflame
give @s pisomarket:frostblade
give @s pisomarket:frostaxe
give @s pisomarket:frostscythe
give @s pisomarket:abominableblade
give @s pisomarket:abominablegreatsaber
give @s pisomarket:abominablescythe
give @s pisomarket:souledge
give @s pisomarket:soul_devourer
give @s pisomarket:soul_collector
give @s pisomarket:divine_justice
give @s pisomarket:divineaxerhitta
give @s pisomarket:divine_reaper
# Currency — enough poisonous potatoes to test the Shop block's Vault tab
give @s poisonous_potato 64
# The Shop block itself — deposit/withdraw only works through this (see
# CLAUDE.md's "Interface: the Shop block is the real thing, still")
give @s pisomarket:shop
# Harvest boosters, normally only reachable via the shop
give @s pisomarket:harvest_potion_i
give @s pisomarket:harvest_potion_ii
give @s pisomarket:luck_potion
# Vault balance to buy BlackMarket deeds, system shop stock, and pay rent
eco set @s 500000
