# Piso Market

A survival economy mod for Minecraft that gives a server a real currency, a
player-driven marketplace, rented land, progression that actually changes
your character, and a set of endgame weapons and armour worth hunting for.

Built for **Minecraft 26.2** on **Fabric**.

---

## Requirements

| | |
|---|---|
| Minecraft | 26.2 |
| Mod loader | Fabric Loader 0.19.3 or newer |
| Fabric API | 0.158.0+26.2 |
| Java | 21 or newer |

Fabric API is required — the mod will not load without it.

---

## Installation

### The mod

1. Install the **Fabric Loader** for Minecraft 26.2 and create a profile for it.
2. Download **Fabric API** and drop it into your `mods` folder.
3. Drop `pisomarket-1.0.0.jar` into the same `mods` folder.

Your `mods` folder lives at:

| Platform | Path |
|---|---|
| Windows | `%APPDATA%\.minecraft\mods` |
| macOS | `~/Library/Application Support/minecraft/mods` |
| Linux | `~/.minecraft/mods` |

4. Launch the game using your Fabric profile.

On a **server**, place the same two jars in the server's `mods` folder. Every
player also needs the mod installed — it is not server-side only.

### The resource pack (optional)

Piso Market's custom weapons and armour ship with their own models. If you are
also using a companion texture pack for vanilla gear:

1. Put the `.zip` in your `resourcepacks` folder (same parent directory as
   `mods`).
2. In game, open **Options → Resource Packs**, move the pack to the right-hand
   column, and click **Done**.

The mod works fine without it — the pack only changes how vanilla tools and
armour look.

### Creating a world

Create your world with **Allow Cheats: ON** if you want access to admin
commands. Everything a normal player uses works without cheats.

---

## Getting started

1. **Plant a farm.** Wheat, potatoes, carrots, beetroot and nether wart all
   have a chance to pay out **Sunstone Shards** when you harvest them by hand.
   Nether wart pays best.
2. **Check your balance** with `/balance`, or watch the counter in the top
   right of your screen. Shards go straight to your balance — there is no item
   to pick up or carry home.
3. **Spend it** with `/shop`, which opens the market interface.
4. **Fight things.** Most hostile mobs have a chance to pay Shards, and the
   rarer ones can drop custom weapons and armour outright.
5. **Level up.** Your normal XP level now grants permanent stats — see below.

---

## Features

### Currency

**Sunstone Shards** are a balance, not an item. They arrive the way experience
does: harvest a crop or kill a mob and the number goes up. Nothing to carry,
nothing to drop, nothing to lose in a lava pit.

Two things to know:

- **Dying costs you a share of your balance.** If another player killed you,
  they take it.
- **`/donate` is the only way to hand Shards to someone else.**

Only crops that must be replanted pay out. Sugar cane, melons, pumpkins,
cocoa and berries regrow on their own and are deliberately excluded.

### Levelling

Your **vanilla XP level** grants permanent stats — more health, more attack
damage, and more armour toughness as you climb. Milestone levels give the
biggest jumps.

Stats follow the **highest level you have ever reached**, so spending XP at an
enchanting table never costs you anything you have earned.

`/level` shows your current bonuses.

### The market and shop

`/shop` opens the interface, with three sections:

- **Buy** — browse and purchase what other players have listed
- **Sell** — put your own items up for sale
- **Black Market** — the system shop: diamond tools and armour, enchanted
  gear, rare items and land deeds

Listings are asynchronous — the seller does not need to be online for you to
buy, and vice versa.

### Land claims

Land is bought as a **Land Deed** from the shop, then activated where you want
it. Deeds come in several sizes.

Once a claim exists you can:

- Trust other players to build, break, or both
- Control who may open chests and other containers inside it
- See your claim's boundary as coloured particles while holding the deed

Claims charge **rent**, paid automatically from your balance, and only while
you are online — time away is free. Falling behind switches protection off;
falling far behind releases the land. **Your builds are never deleted.**

`/claims` shows what you own and what you owe.

### Fast travel

**Waypoints** are placeable blocks bought from the shop. Right-click one to
bind it, then use `/warp` to return there from anywhere.

Waypoints are public by design — anyone who can reach one can bind to it, so
where you build a travel hub matters. `/warp` has a cooldown and is blocked
briefly after taking damage, so it can never be used to escape a fight.

### Combat

Twelve custom weapons across five families, each with its own on-hit effect —
ignite, slowness, poison, lifesteal, and bonus damage against the undead.
They come in three shapes:

| Shape | Feel |
|---|---|
| Sword | Fast and consistent |
| Heavy | Slow, with heavy critical hits |
| Scythe | Slow, with the biggest damage per swing |

Some weapons **cleave**, striking several enemies at once. All of them are
**unbreakable**.

These weapons hit far harder than vanilla gear, which is why three custom
armour sets exist — **Sentinel**, **Aegis** and **Bulwark**. Ordinary armour,
even fully enchanted netherite, will not keep you alive against them for long.

Weapons and armour are earned from mobs. The tougher and rarer the target, the
better the reward — the very hardest fights in the game are guaranteed to pay
out. A small selection of the weakest weapons can also be bought outright, at
a price.

Hostile mobs have more health than in vanilla to match.

### Anti-grief

- TNT is disabled server-wide and cannot be crafted
- Explosions, fire and lava cannot damage claimed land from outside
- Container access inside a claim follows that claim's settings

---

## Commands

### Everyday

| Command | What it does |
|---|---|
| `/balance` | Your current Shard balance |
| `/donate <player> <amount>` | Send Shards to someone |
| `/shop` | Open the market interface |
| `/shop browse [tier]` | List system shop stock in chat |
| `/shop buy <id> [qty]` | Buy from the system shop |
| `/level` | Your level and current stat bonuses |
| `/leaderboard` | The richest players |
| `/warp` | Return to your bound waypoint |

### Market

| Command | What it does |
|---|---|
| `/market list <price>` | List the item you are holding |
| `/market browse [page]` | Browse active listings |
| `/market buy <id>` | Buy a listing |
| `/market mine` | Your own listings |
| `/market cancel <id>` | Take a listing down |

### Land

| Command | What it does |
|---|---|
| `/claims` | Your claims, rent, and paid-through date |
| `/trust <player> <place\|destroy\|both>` | Grant build access |
| `/untrust <player>` | Revoke access |
| `/claim chest <mode>` | Set container access for the claim |
| `/unclaim` | Release a claim you own |
| `/deed browse` | Land deeds for sale |
| `/deed buy <id>` | Buy a deed |

### Admin

Requires operator permission.

| Command | What it does |
|---|---|
| `/eco give\|take\|set <player> <amount>` | Adjust a balance |
| `/eco total` | Total Shards held across all players |
| `/level set <player> <level>` | Set a player's level |

---

## Notes for server owners

- Every player needs the mod installed; it is not server-side only.
- Balances, claims, listings, levels and waypoints are stored in the world
  save, so backing up the world backs up the economy.
- Currency only enters the world through farming and mob kills, both of which
  require a player to land the final blow or break the block — automated farms
  and mob grinders produce no income.

---

## Building from source

```bash
./gradlew build
```

The jar lands in `build/libs/`. A JDK 21 or newer is required, and `JAVA_HOME`
must point at it.

```bash
./gradlew runClient    # launch a test client
./gradlew runServer    # launch a test server
```

---

## Credits

Weapon and armour models are third-party artwork and are **not distributed
with this repository**. A local copy is required for those items to render
correctly; without it they appear as missing textures. Everything else in the
mod is original.
