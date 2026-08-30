# /function pisomarket:testkit
# Gives the running player one of everything needed to test Piso Market
# end to end in a single command, instead of typing fifteen /give lines by
# hand. Requires permission level 2 (same gate as /eco) since it calls
# /eco give on the caller.
#
# Weapons (13 — Hearthflame and Soul Edge were removed 2026-08-30, not a
# missed update, don't re-add them here)
give @s pisomarket:moltensword
give @s pisomarket:moltenblade
give @s pisomarket:frostblade
give @s pisomarket:frostaxe
give @s pisomarket:frostscythe
give @s pisomarket:abominableblade
give @s pisomarket:abominablegreatsaber
give @s pisomarket:abominablescythe
give @s pisomarket:soul_devourer
give @s pisomarket:soul_collector
give @s pisomarket:divine_justice
give @s pisomarket:divineaxerhitta
give @s pisomarket:divine_reaper
# Custom armor — the toughness-boosted diamond/netherite/gold sets
give @s pisomarket:custom_diamond_helmet
give @s pisomarket:custom_diamond_chestplate
give @s pisomarket:custom_diamond_leggings
give @s pisomarket:custom_diamond_boots
give @s pisomarket:custom_netherite_helmet
give @s pisomarket:custom_netherite_chestplate
give @s pisomarket:custom_netherite_leggings
give @s pisomarket:custom_netherite_boots
give @s pisomarket:custom_gold_helmet
give @s pisomarket:custom_gold_chestplate
give @s pisomarket:custom_gold_leggings
give @s pisomarket:custom_gold_boots
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
