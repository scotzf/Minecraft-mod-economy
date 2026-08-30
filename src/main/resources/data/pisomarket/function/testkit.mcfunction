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
give @s pisomarket:divineaxerhitta
give @s pisomarket:divine_reaper
# Custom armor — Sentinel(gold)/Aegis(diamond)/Bulwark(netherite), each
# toughness-boosted (see CustomArmorContent.java) and given here fully
# pre-enchanted via inline component syntax rather than baked into the
# item itself — enchantments need server registry access, which a static
# Item.Properties default doesn't have at class-load time. Protection V
# is intentionally past vanilla's real max (IV) — see CLAUDE.md's combat
# rebalance notes for why.
give @s pisomarket:sentinel_helmet[enchantments={"minecraft:protection":5,"minecraft:respiration":3,"minecraft:aqua_affinity":1,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:sentinel_chestplate[enchantments={"minecraft:protection":5,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:sentinel_leggings[enchantments={"minecraft:protection":5,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:sentinel_boots[enchantments={"minecraft:protection":5,"minecraft:feather_falling":4,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:aegis_helmet[enchantments={"minecraft:protection":5,"minecraft:respiration":3,"minecraft:aqua_affinity":1,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:aegis_chestplate[enchantments={"minecraft:protection":5,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:aegis_leggings[enchantments={"minecraft:protection":5,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:aegis_boots[enchantments={"minecraft:protection":5,"minecraft:feather_falling":4,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:bulwark_helmet[enchantments={"minecraft:protection":5,"minecraft:respiration":3,"minecraft:aqua_affinity":1,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:bulwark_chestplate[enchantments={"minecraft:protection":5,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:bulwark_leggings[enchantments={"minecraft:protection":5,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
give @s pisomarket:bulwark_boots[enchantments={"minecraft:protection":5,"minecraft:feather_falling":4,"minecraft:thorns":3,"minecraft:unbreaking":3,"minecraft:mending":1}]
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
