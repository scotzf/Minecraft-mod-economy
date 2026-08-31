# CLAUDE.md

Project context for Claude Code. Read this before making changes.

**A v2 redesign was planned 2026-08-30 — see "v2 redesign — planned
2026-08-30, not yet built" near the end of this file before assuming any
of the sections below (currency, shop catalog, interface, lockable chests,
death/revive) are still current. That section explicitly wins on conflict
until each piece is actually built.**

## What this is

A Minecraft Java Edition mod adding a player-driven market economy:
item-based currency plus a vault balance, asynchronous item listings, a
system shop, and rented territory claims.

**Mod ID:** `pisomarket`
**Package:** `com.pisomarket`
**Target version:** `26.2` (latest stable). Pinned in `gradle.properties`
alongside `loader_version` (0.19.3), `loom_version` (1.17.20), and
`fabric_api_version` (0.158.0+26.2) — check those four together when bumping.
**Loader:** Fabric (chosen for simpler API surface over NeoForge)
**JDK:** 25 (Eclipse Temurin) — install via winget
(`winget install EclipseAdoptium.Temurin.25.JDK`), NOT the system default
(commonly still an old JRE, which has no compiler). **`gradle.properties`
deliberately does NOT set `org.gradle.java.home`** — a hardcoded path there
broke on a second machine the first time this project moved computers, so
Gradle's own `java.toolchain` block in `build.gradle` finds a JDK 25 itself
instead. What a fresh shell does need is `JAVA_HOME` exported to the JDK 25
install before running `./gradlew`, since Gradle's own launcher requires
JVM 17+ and won't start on a stray JRE 1.8 `java` on PATH — see "Windows
build environment note" under "Custom weapons" below for the exact error
and fix.

## Toolchain

- **JDK (Java Development Kit)** 25 minimum — required by the Gradle JVM for
  Minecraft 26.1+
- **Fabric Loom** — Gradle plugin; prepares a local development copy of the
  game. Minecraft source is NOT public and is NOT vendored into this repo.
- **Yarn mappings** — readable names for game classes (CC0 licensed)
- **Fabric API** — required at runtime

Project skeleton comes from the official Fabric template generator, not
hand-written Gradle.

## Version-specific API notes

Minecraft 26.2's Fabric/Mojang mappings differ from what most Fabric
tutorials (and older phrasing elsewhere in this doc) assume — confirmed by
decompiling the actual game source (`./gradlew genSources`), not guessed.
Check here before writing code that touches these:

| Old/tutorial name | Actual name in 26.2 |
|---|---|
| `PersistentState` | `SavedData` (base class) + `SavedDataType<T>` (id/codec/factory record) |
| `DimensionDataStorage` | `SavedDataStorage`, via `MinecraftServer.getDataStorage()` — server-wide, not per-dimension |
| `ServerCommandSource` | `CommandSourceStack` |
| `source.hasPermission(2)` (int op-level check) | `.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))` — 26.2 replaced the whole int op-level system with a `PermissionCheck`/`PermissionSet` API. `CommandSourceStack.hasPermission(int)` no longer exists at all. The four levels are `LEVEL_MODERATORS` / `LEVEL_GAMEMASTERS` / `LEVEL_ADMINS` / `LEVEL_OWNERS`, mapping onto the old op levels 1-4 in that order; `LEVEL_ALL` is the old level 0. Confirmed by decompiling — see `EcoCommands.java` for a working example. |
| `CommandManager.literal` / `.argument` | `Commands.literal` / `.argument` |
| `HudRenderCallback` (old immediate-mode HUD hook) | `HudElementRegistry` + `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` — a deferred render-state system |
| `Level.getDayTime()` | `getOverworldClockTime()` (day/night clock, resettable by `/time set`) vs `getGameTime()` (monotonic total ticks — use this for timers) |

**Traps that cost real debugging time — read these before writing similar code:**

- **`Codec.unboundedMap` decodes into an IMMUTABLE map.** A `SavedData` that
  stores the decoded map directly works fine on a fresh world (the no-arg
  constructor makes a `HashMap`) and then throws
  `UnsupportedOperationException` on the first write *after being loaded
  from disk*. This is what made `/deposit` fail and destroy items on an
  existing save. Always `new HashMap<>(decoded)` in the decode constructor.
- **`Player.openItemGui(stack, hand)` ignores the stack you pass.** It only
  sends `ClientboundOpenBookPacket(hand)`; the client then reads
  `getItemInHand(hand)` and renders *that* item's `WRITTEN_BOOK_CONTENT`.
  To open a book UI, write the content onto the item actually in the
  player's hand (see `LandDeedItem.openBook`).
- **`AbstractContainerMenu.quickMoveStack` is abstract** — `@Inject` into it
  fails at load with "insnNode is null". Shift-click arrives at `clicked()`
  as `ContainerInput.QUICK_MOVE`; handle it there.
- **`ServerExplosion.calculateExplodedPositions` must return a MUTABLE
  list.** `interactWithBlocks` calls `Util.shuffle` on it, which does
  `list.set(...)` in place. Filtering the list with `stream().toList()`
  (immutable) crashed the server with `UnsupportedOperationException` the
  moment an explosion touched a claim. `ClaimProtection.filterProtected`
  returns `new ArrayList<>` or vanilla's own list, never `List.of()`.
- **Cancelling a placement server-side does NOT un-predict it on the
  client.** The client already drew the block and decremented the held
  stack. Returning `InteractionResult.FAIL` leaves the item looking gone
  until something else resends the inventory. Always follow a denial with
  `containerMenu.sendAllDataToRemote()` plus a `ClientboundBlockUpdatePacket`
  for both the clicked and the target position (see
  `ClaimProtection.resyncAfterDeniedPlace`).
- **Menu `clicked()` runs on the client too** (for prediction). Fields the
  server mutates (like a current-view enum) are always stale client-side,
  so branching on them there silently breaks slot interaction.

When in doubt, decompile and grep rather than trust a remembered snippet —
see "Reading failures" below for the workflow.

## Build and test

Run these from the project root. Verify changes with these before reporting
anything as working.

```bash
./gradlew build          # compile + package the jar; fastest failure signal
./gradlew runServer      # headless dedicated server — USE THIS TO TEST
./gradlew runClient      # launches the game GUI; needs a human at the screen
./gradlew clean build    # after changing dependencies or mappings
./gradlew --stop         # kill a stuck Gradle daemon
```

**Test against `runServer`, not `runClient`.** The dedicated server runs
headless, accepts commands typed into its console, and prints output that can
be read directly. `runClient` opens a game window nobody is watching in an
agent session.

Almost everything in this project is testable that way, because the build order
is deliberately commands-first. In the server console (no leading slash):

```
op <player>
balance
top
```

**Piping commands into `runServer` does NOT work** — verified 2026-08-28.
`printf 'list\nstop\n' | ./gradlew runServer` makes *every* command fail with
"An unexpected error occurred while trying to execute that command",
including plain vanilla ones, and the server doesn't even stop. So a piped
run proves the mod **loads** cleanly (registration, mixin application,
datapack parsing — all real signal) but proves nothing about whether a
command *works*. Typing into an interactive console is a separate, untried
path. Don't report a command as verified on the strength of a piped run.

First run of `runServer` fails until the EULA (End User License Agreement) is
accepted — set `eula=true` in `run/eula.txt` and re-run. This is expected, not
a bug.

**What cannot be verified headlessly:** item textures, item models, any
graphical screen, and anything requiring a real player entity in the world. For
those, report what changed and ask for a human check in `runClient` rather than
claiming it works.

### Testing everything in a real game, not the dev environment

`runClient` needs a human at the screen anyway, and it's a throwaway dev
world — there's no reason to prefer it over the real Minecraft install this
project is actually played on. On this machine that's **TLauncher**
(`%APPDATA%\.minecraft`, not the dev environment's `run/` folder), which
already has fabric-api, modmenu, and pisomarket in its `mods/` folder and a
"Fabric 26.2" profile. After any build, the jar needs copying there by hand
(no automation for this — it's a manual step every session):

```bash
cp build/libs/pisomarket-1.0.0.jar "$APPDATA/.minecraft/mods/pisomarket-1.0.0.jar"
```

**Any world with "Allow Cheats: On" set at creation** gives the singleplayer
owner full command permission automatically — no `/op` needed. That's
required for `/eco`, which is gated at `LEVEL_GAMEMASTERS`.

**`/function pisomarket:testkit`** gives one command that sets up everything
needed to test the whole mod at once, instead of typing fifteen `/give`
lines and grinding out a vault balance by hand: all 15 elemental weapons,
64 poisonous potatoes (to test the Shop block's Vault tab — see "Interface:
the Shop block is the real thing, still"), a Shop block, the three harvest
potions, and `/eco set` to 500,000 vault balance (enough to clear every
BlackMarket tier and system shop item without a second thought). Defined in
`data/pisomarket/function/testkit.mcfunction` — add to it as new testable
things get built. Needs `LEVEL_GAMEMASTERS` itself, since it calls `/eco`
internally.

A rough per-system checklist, once the kit is in hand:

- **Weapons** — equip each, check the model in hand/inventory/on the
  ground, hit a mob, confirm the right effect (ignite / slow / poison /
  heal-on-hit / bonus damage on undead only for Smite)
- **Vault** — place the Shop block, deposit the 64 potatoes, `/balance`,
  withdraw some back, `/pay` another online player
- **Market** — `/market list <price>` an item, `/market browse`,
  `/market buy <id>` from a second account, `/market mine`,
  `/market cancel <id>`
- **System shop** — `/shop browse [tier]`, `/shop buy <id>` across a few
  tiers, confirm Unbreaking/Mending never appear on enchanted stock
- **Claims** — buy a Land Deed (`/deed browse`, `/deed buy <id>`), activate
  it on open ground, open the deed book to trust/untrust a second player,
  confirm the boundary particles render (green for trusted, red for
  someone else's claim), test the chest-access levels from the deed book
- **Rent** — `/claims` to see paid-through day; forcing an actual missed
  payment needs waiting out in-game days, so this one is slow to test for
  real
- **Leaderboard** — `/top`
- **TNT** — confirm it still discards on the first tick with no explosion,
  and that the crafting recipe requires barrier blocks instead of vanilla's

Testing this on the real TLauncher install, not the dev environment, means
whatever's found here is what players will actually see.

### Reading failures

- **Compile error** — read the first error only; the rest are usually cascade.
- **Mapping errors** (`cannot find symbol` on a vanilla class or method) — the
  API changed between Minecraft versions. Check the actual mapped name in the
  decompiled source rather than trusting a snippet.
- **Mixin errors at startup** — an injection point no longer exists in this
  version. These fail at launch, not at compile time.
- Logs are in `run/logs/latest.log`.

## Developer context

Rusty Java, strong Python / Dart / Django background. When writing code here:

- Write the code rather than describing it; Denver reviews rather than types.
- Thorough inline comments explaining *why*, not just what.
- Spell out acronyms in full on first use.
- Explain errors in plain language first, technical detail second.
- For small changes, give a targeted diff: which file, what to change, what
  command to run. Never a full project re-download.
- Keep explanations short and precise.
- Wants to **see** things, not read descriptions of them — rendering models
  to images and showing a contact sheet moves things forward far faster than
  prose. Decides fast and changes direction freely; write decisions into
  this file as they're made rather than batching them up.
- Pushed back, correctly, when caution was over-applied to personal/self-use
  decisions (see the licensing note under "Custom weapons" — self-use of the
  imported art was accepted, publishing it was flagged as the separate,
  real decision).

## Currency design

**The currency is the poisonous potato item itself.** Money is a physical stack
in a player's inventory. There is no virtual balance as the primary store of
value.

**Decided, final: the vanilla poisonous potato**, not a custom lookalike.
Every system already built assumes this (`Items.POISONOUS_POTATO` in the HUD,
vault deposit/withdraw, the harvest faucet) — no separate registration, no
texture work. The known consequences are accepted, not overlooked: it's still
edible (right-clicking it eats/poisons you — spend it carefully), and every
poisonous potato already sitting in an existing world counts as pre-existing
money.

No crafting recipe. The only source is the harvest drop.

Display name is a single constant so it can be changed in one edit:

```java
public static final String CURRENCY = "Piso";  // TBD: Piso / Tarsi / Sensilyo
```

### Consequences of item-based currency

These are real and must be designed around, not discovered later:

- **Money is lost permanently** to lava, void, and death in unrecovered
  inventories. This is a passive sink — it partly replaces the listing fee.
- **Money can be stolen** from unprotected chests. That is a gameplay feature
  here, not a bug, but it means territory protection matters much more.
- **No offline transactions.** `/pay` requires the recipient online with
  inventory space, or the payment must drop items into the world.
- **Inventory space caps wealth.** A stack is 64; a double chest holds 3,456.
  Rich players need vaults. Consider whether that friction is wanted.

### The vault (required for rent)

Rent auto-renews while a player is offline, which cannot deduct from an
inventory. So a **vault balance** exists alongside the item currency:

- `/deposit` — inventory items → vault balance
- `/withdraw <amount>` — vault balance → inventory items
- Rent and market escrow draw from the vault only

Vault balance is a `long` per player UUID (Universally Unique Identifier),
whole units only, persisted via `PersistentState`. Never floating point —
fractional rounding is a duplication exploit.

Deposit and withdraw must move the exact same amount in both directions. Every
item created is subtracted from the vault in the same operation, never one
without the other.

The distinction from the earlier design: the vault is a **convenience and a
rent requirement**, not the definition of money. Cash in hand is real money and
is what most trading uses.

## Economy: one faucet, one set of sinks

**Faucet — poisonous potato drops while harvesting.**

**Payout is a physical drop, not a vault deposit.** Decided, reversing the
earlier design. The old behaviour — `vault.deposit()` plus a "A poisonous
potato slipped into your vault" chat line on every hit — is **removed**. A
successful harvest now drops the poisonous potato on the ground like any
other crop yield.

The earlier reasoning (a vault deposit can't be lost to lava or a despawn)
was true, but it made the money invisible and contradicted the core rule that
**the currency is a physical item**. Losing a payout to lava is the passive
sink the currency design already accepts.

**Multiplier rolls replace the flat payout.** A harvest can pay out 2x, 3x or
4x:

| Roll | Multiplier |
|---|---|
| 1% | 2x |
| 0.5% | 3x |
| 0.1% | 4x |

**UNRESOLVED — how these compose with the base drop chance.** The two
readings differ by roughly 5x in money supply, so this must be settled before
it is built:

- **A — multiplier applies to a successful base drop.** Base 1% to drop at
  all, then the multiplier rolls on that drop. A double is then
  0.01 x 0.01 = **1 in 10,000 harvests**, which is so rare no player will
  ever notice it.
- **B — the multiplier rolls are independent per harvest.** Expected payout
  becomes 0.049 per harvest against today's 0.01 — a **4.9x increase in the
  money supply**, which would need every shop price rechecked.

**Still player-break only.** Confirmed, unchanged: the hook stays on
`PlayerBlockBreakEvents`, so piston and villager farms mint nothing. This is
the single load-bearing reason it is safe to have no daily cap.

**No daily cap — deliberately deferred, not forgotten.** The design below
(cap as the real bound on money supply, rate only controlling feel) is still
believed correct in principle, but with only a handful of players there's no
real oversupply risk yet to justify the extra tracking. Revisit and add the
cap back once the player count grows enough for automated farms to matter.
If this line still says "no cap" and the server has since grown, that's a
sign to act on it.

Original reasoning, kept for when the cap gets added back: automated potato
farms are trivial to build and run unattended overnight, so a drop rate of
any value is a money printer without a cap — the cap would bound supply, the
rate only controls how it *feels* to harvest by hand. A daily cap would need
to count items dropped per player per day and reset on day number change,
tracked in persistent state keyed by UUID.

**TNT is disabled server-wide** (`TntRemovalMixin`). It was causing lag and
a hard server crash at claim edges. The primed entity is discarded on its
first tick, so no fuse, no explosion, no block scan. The vanilla crafting
recipe is overridden (`data/minecraft/recipe/tnt.json`) to require barrier
blocks so it can't be made either. Creepers, beds and end crystals still
explode normally and are still filtered by `ExplosionProtectionMixin`.

**Sinks:**
- System shop purchases (see catalog below)
- Territory rent (recurring, scales with claim size)

The system **sells only** — it never buys items back. No buy/sell spread
means no arbitrage loop.

## System shop catalog

**Pricing is a formula, not per-item guesswork** (`ShopCatalog.java`):

```
price = material unit value  x  recipe unit count
```

Material units: wood 1, stone 2, gold 6, iron 7, diamond 70.
Recipe units: shovel 1, sword/hoe 2, pickaxe/axe 3, boots 4, helmet 5,
leggings 7, chestplate 8.

So an iron pickaxe is 7x3 = 21 and a diamond pickaxe 70x3 = 210 — a clean
1:10 iron-to-diamond ratio. Enchanted variants add 60% of the base price
per enchantment level on top.

**Everything is ultimately priced in potato-harvests.** The faucet is 2.5%,
so **1 Piso = 40 mature potatoes harvested**. That is the real exchange
rate; a 210 diamond pickaxe costs 8,400 harvests. Small plot (81 crops)
earns ~2 per full harvest, a big automated farm ~50. Prices are therefore
calibrated for someone with a real farm — if casual players can't
participate, raise the drop rate rather than fiddling with individual
prices.

**Why stocking craftable tools does NOT break the rule below:** tools wear
out. Selling them creates *recurring* demand instead of permanently
replacing a player seller. That is exactly why **Unbreaking and Mending are
banned** from the shop's enchant list — both cancel the durability sink
that makes this safe.

Enchanted stock rules: exactly **one** enchantment per item, never
Unbreaking or Mending, and always **two levels below max** so
player-enchanted gear stays strictly better than anything the system sells.

Hard rule, unchanged: never stock anything players can produce *that does
not wear out*. Every such item stocked is a customer taken from the player
market.

**Tier 1 — tools and armour** (stone/iron/gold/diamond, plain)
**Tier 2 — single-enchant gear** (Efficiency III, Sharpness III, Protection
II, Fortune I, Power III)
**Tier 3 — consumables** (bottles o' enchanting, name tags, saddles,
fireworks, ender pearls)
**Tier 4 — prestige** (echo shard, Heart of the Sea, enchanted golden apple,
netherite template, elytra)

Cosmetics and convenience perks (chat colours, titles, `/sethome` slots) are
**not built** — they need systems that don't exist yet.

**Restocking is per-item, in in-game days** (`ShopEntry.restockDays`,
applied lazily in `PisoShopStock`). Cheap tools 1 day, diamond gear 7,
enchanted 7-14, elytra 300. Note 1 in-game day = 20 real minutes, so 300
days is roughly 100 real hours — deliberately once-per-server.

## Interface: the Shop block is the real thing, still

**Correction, 2026-08-30: this section used to claim the Shop block was
deleted in favour of commands-only. That never actually happened — checked
against the code, not assumed.** `PisoShopBlock`, `PisoShopContainer`,
`PisoShopMenu`, `PisoShopContent`, `PisoUiItems`, the client's
`PisoShopScreen`, the crafting recipe (`data/pisomarket/recipe/shop.json`),
all 16 `ui_*` textures/models/lang keys, and the Shop entry in
`PisoCreativeTabs` are all still present, still registered, and still the
live way to reach the vault. `PisoCommands.java` says so directly, in its
own comment:

> `/deposit` and `/withdraw` are deliberately NOT registered. Moving money
> between hand and vault must happen at a Shop block, so the block is a
> real place players have to go rather than decoration.

**Practical consequence for testing: `/deposit` and `/withdraw` do not
exist as commands.** To move money between hand and vault, craft a Shop
block (8 gold ingots around 1 obsidian), place it, right-click it, and use
its Vault tab. `/balance` and `/pay` do work as commands — only the
deposit/withdraw path needs the block.

The plan below is preserved for whoever picks this decision back up — it
was drafted in an earlier session but the actual deletion work was never
done, and the doc was never corrected to match until now. Do not assume
it reflects the codebase; verify against the classes listed before acting
on it.

<details>
<summary>Un-executed plan: delete the Shop block in favour of commands</summary>

The reasoning was genuine — a physical block makes the shop "a real place
players have to go" rather than decoration, but that lost to the cost of
maintaining a client `Screen`, a custom `AbstractContainerMenu`, 16 UI
button items with their own textures, models and lang keys, and the class
of bugs that only appear because `clicked()` runs on the client too.
Commands cost none of that and are the only thing testable headlessly (see
"Build and test").

The deletion was believed safe because every view already had a command
twin:

| View | Command twin |
|---|---|
| Buy (market listings grid) | `/market browse`, `/market buy <id>` |
| Sell (slot + price dial) | `/market list <price>` |
| BlackMarket (Tier 3/4, deeds) | `/shop browse <tier>`, `/deed browse`, `/deed buy` |
| Leaderboard | `/top` |
| Vault (deposit slot + withdraw dial) | `/balance`, `/deposit`, `/withdraw` |

The plan was to register `/deposit` and `/withdraw` (moving
`PisoShopMenu.depositFromInventory` and the withdraw branch of
`clickedVault` into `PisoCommands`) and then delete `PisoShopBlock`,
`PisoShopContainer`, `PisoShopMenu`, `PisoShopContent`, `PisoUiItems`, the
client's `PisoShopScreen`, the crafting recipe, blockstate, block/item
models, `textures/block/shop.png`, all 16 `ui_*` assets, and the Shop entry
in `PisoCreativeTabs`. None of that happened.

</details>

**What must NOT be deleted:** `RestrictedChestMenu` and
`RestrictedChestScreen` belong to lockable chests, not the shop. Locked
chests still need a real screen — that one is not optional, because it
replaces the vanilla chest UI.

## Territory claims

Rent-based, never permanent — ownership still requires upkeep. Acquired
differently than a plain chunk claim, though:

- **Bought as a Land Deed**, a book with a width x length x height baked in
  at purchase time (from the BlackMarket above). Not tied to Minecraft's
  16x16 chunk grid — the deed defines an exact box.
- **Activated, not claimed on the spot.** Standing in the target area and
  using the deed attempts to register that box as a claim. Fails if the box
  — or its immediate surroundings — overlaps an existing claim; no claim
  can be created on top of, or directly touching, someone else's.
- **The same book becomes the management interface** once activation
  succeeds. Opening it lets the owner add or remove trusted players, with
  independent permissions: allowed to place blocks, allowed to destroy
  blocks, or neither (revoked).

**Assumption to confirm:** the deed flow as described didn't mention rent,
so this doc keeps rent applying once a claim exists (deeds only change how
a claim is *created* and *sized*, not how it's paid for). Flag if claims
should actually become a one-time permanent purchase instead.

**Rent is implemented** (`RentCollector`, `Claim.rentPerPeriod`). Decided
numbers:

- **1% of the deed's purchase price every 4 in-game days logged in.**
  That is deliberately the integer form of "0.25% per day" — at 0.25% a
  Small claim costs 0.5/day, and rounding fractional currency is exactly
  the duplication exploit this doc warns about elsewhere. Small 2, Medium
  5, Large 10 per period.
- Land therefore costs its own purchase price again only after ~400
  in-game days of *play* (~130 real hours) — long enough to enjoy and earn
  back, which was the goal.
- **Unpaid → protection off immediately, claim released after 4 missed
  payments** (~16 in-game days of play). Blocks are never touched either
  way.

Existing non-negotiable rules, unchanged:

- **Expiry removes protection, never blocks.** Deleting builds over unpaid
  rent loses players permanently.
- **Rent freezes while offline.** Charged per *day logged in*, not per
  calendar day. Enforced by resetting each claim's billing clock on login
  without charging, so an absence is never billed retroactively.
- **Auto-renew from balance**, with a warning when the balance won't cover the
  next period.
- **Progressive pricing by area** — small deeds cheap, large deeds
  expensive. Prevents one player fencing off a continent. (Replaces the old
  "progressive per-chunk pricing" now that size is arbitrary, not
  chunk-counted.)

Size rent so a casual player hitting ~half the daily cap covers a modest claim
with money left to spend. If rent consumes all income, no money reaches the
player market.

Exact width/length/height limits, deed prices, and rent-per-area scale are
still TBD — tune after the systems run (see "Still to decide").

**Trust management has a GUI now.** A bound deed, right-clicked while
standing inside its own claim, opens a dynamically-generated written book
(`LandDeedItem.openClaimBook`) — no custom Screen/Menu needed, just
`Player.openItemGui` on a throwaway `WRITTEN_BOOK` stack, same trick as
every other clickable-text button in this project. Lists trusted players
with `[Remove]` links and every other online player with a `[Trust]` link
(default level `place`); `/trust`/`/untrust` still work directly too, same
underlying commands either way. Outside the claim, using the deed just
prints a reminder instead of opening the book. Known limit: an *offline*
trusted player shows as a raw UUID with no working Remove link — no name
reverse-lookup plumbing exists yet for that case.

**Boundary visualization**: while holding any Land Deed (bound or
unbound), `TerritoryVisualizer` draws nearby claims' box outlines as
particles only that player can see — green for claims they own or are
trusted on, red for everyone else's. No particles at all means unclaimed.
Runs on a throttled server tick (twice a second), not every tick.

### Lockable chests

**Decided, reversing the earlier per-item design: there is no Lock item.**
`ChestAccess` is a **claim-wide setting**, not a per-chest one, edited from
the Land Deed book alongside trust — nothing to craft, buy, place, or lose
track of. It applies to every container in the claim at once: chest,
trapped chest, barrel, hopper, dispenser, dropper, furnace variants,
brewing stand, and shulker box.

Four levels, cycled from the deed book (`ChestAccess.next()`):

- **Only me** — no one else can open any container in the claim
- **Trusted: put only** — trusted players can deposit, can't take anything
  out (opens a `RestrictedChestMenu`/`RestrictedChestScreen`, a real
  screen because it replaces the vanilla container UI)
- **Trusted: put and get** — full access for anyone the claim already trusts
- **Open to everyone** — no restriction at all

The claim **owner always has full access**, regardless of the setting — the
point is controlling what other people can do, never locking yourself out.
This is separate from build (place/destroy) trust — a player can be trusted
to build on your land without being able to touch your containers.
Enforced by `ChestAccessGuard`, hooked on Fabric's `BlockEvents.USE_WITHOUT_ITEM`
since chests are vanilla blocks, not this mod's own.

## Death and revive

**Dropped entirely, 2026-08-30.** A working implementation existed briefly
this session (`ReviveManager.java`/`ReviveState.java`, the v2 redesign's
§11 numbers: 120s+120s/death capped at 1 hour, spectator hold after a
normal respawn, `/revive` to pay from the vault and skip) — it compiled,
loaded cleanly, and was then deliberately removed at the user's request.
There is currently **no revive system of any kind**: dying respawns however
plain vanilla respawns, no hold, no cooldown, no `/revive` command. The
design below is kept only as history of what was tried, not a target to
rebuild toward unless it comes back up.

Dying starts a **120-second respawn cooldown**. The player may wait it out for
free, or **pay to respawn immediately**.

- **Price: 3 Piso per second of cooldown remaining**, drawn from the **vault**
  (never from cash in hand — the corpse's inventory is exactly what the player
  is trying to recover, and rent/escrow already set the precedent that
  automatic charges come from the vault).
- A full 120-second skip therefore costs **360**. That is deliberately more
  than a Small land deed (200) or a diamond pickaxe (210) — dying should hurt,
  and this is the sink that makes it hurt in currency rather than in lost time
  alone.
- Partial skips are allowed: waiting 90 seconds and then paying costs 30x3 =
  90. The price falls as the timer runs down, so the choice stays live for the
  whole two minutes instead of being a single yes/no at the moment of death.

**Repeat deaths scale the price quadratically.** The multiplier is `n²` where
`n` is the death count in the current window:

| Death | Rate | Full 120s skip |
|---|---|---|
| 1st | 3/s | 360 |
| 2nd | 12/s | 1,440 |
| 3rd | 27/s | 3,240 |

- **Capped at the 3rd death.** A 4th and any later death in the same window
  costs the same as the 3rd — the price stops climbing, it does not keep
  growing.
- **The window is a rolling hour from the first death**, not a clock hour. An
  hour after death #1, the count returns to zero.

**Why quadratic and not repeated squaring.** "Squared every time" read
literally means squaring the *rate* each death — 3, 9, 81, 6561/s, which puts
a 4th revive at 787,320, larger than the entire money supply will ever be.
Quadratic scaling delivers the same intent (repeat dying gets punishing fast)
without detonating. **Confirm this reading before it is built.**

**Implementation risk, flagged early:** vanilla respawns the player as soon as
they click the button. Holding them on the death screen for 120 seconds means
intercepting the respawn path, not just delaying a teleport. Expect this to be
the fiddly part.

## Waypoints

**BUILT 2026-08-31** (`WaypointContent`, `WaypointState`, `WarpCommand`).

Fast travel between spread-out farms, as a physical placed block rather
than a command-only convenience.

- **A SINGLE block — confirmed 2026-08-31**, reversing the two-block
  design below. The original sketch was a door-style two-block structure
  (lower + upper half, breaking either breaks both); that needs paired
  placement, paired breaking and a half-tracking blockstate, all of which
  is real complexity buying nothing mechanically. Built as one block and
  the user confirmed it. Do not "restore" the two-block version.
- **Interacting binds it**, exactly the way a bed sets spawn. The last
  waypoint a player interacted with is their active destination; binding to a
  new one silently replaces the old.
- **Public by default.** Anyone who can physically reach a waypoint can bind
  to it, including inside someone else's claim. Waypoints are infrastructure —
  the intent is that players build travel hubs others use, and that placement
  location is therefore a real decision. This is deliberately *not* gated on
  claim trust.

**`/warp` returns the player to their bound waypoint.**

- **5-minute cooldown** between uses.
- **Blocked if the player took damage in the last 5 seconds.** This is the
  combat lock — without it `/warp` is a free escape button that trivialises
  every fight and every raid. Five seconds is short enough that it never
  annoys someone travelling peacefully.
- **Free.** No Piso cost. Farming plots are spread out by nature and travel
  should not be means-tested; the cooldown alone is the limit.

**Still to decide on waypoints:**

- **How players get one — DECIDED: bought from the BlackMarket**, via
  `/shop`. Not craftable. This makes waypoints a money sink, which the economy
  is short of, and means a player must farm before they can fast-travel.
  Price not yet set.
- **Cross-dimension travel** — assumed **not allowed** for now (a Nether hub
  bound from the Overworld is a distance exploit), but this was never
  explicitly decided.
- **Destroyed bound waypoint** — assumed `/warp` fails with a clear message
  and the player is unbound, rather than teleporting into empty air.

## Custom items — deferred, not dropped

Custom gear (a sword, a bow, a cape with real art) is **wanted but parked
until the systems above work**. Three reasons it is last, all of them real:

- **The art cannot be verified in an agent session.** See "What cannot be
  verified headlessly" — textures and models need a human looking at
  `runClient`, so every iteration costs a round trip.
- **A cape is the hardest thing on the list.** Real Minecraft capes are a
  Mojang account feature a mod cannot grant; a mod cape needs a custom client
  render layer, which is the "client and server must agree exactly" work the
  build order deliberately sequenced last.
- **A custom sword or bow collides with an existing rule.** System-sold gear
  must stay strictly worse than player-enchanted gear (the reason Unbreaking
  and Mending are banned from the shop). Special weapons should be
  **sidegrades** — unusual effects, not higher numbers — or they redirect the
  reward for playing the economy away from other players and back to the shop.
  A cosmetic cape has no such problem: nobody can craft one and it competes
  with nothing.

**Tried and rejected — do not re-propose these:**

- **Crop contracts / a rotating order board**, proposed as the way to make a
  farming economy work beyond the faucet. Dropped; the faucet stays the only
  money source. This is why `DailyProgress` is not in the data model below
  even though earlier drafts of this doc had it.
- **Filipino farming-tool weapon naming** (Itak, Salakot, Bakya) for the
  custom weapons above. The direction wanted is fantasy, not farming-themed.

## Data model

```java
Map<UUID, Long>          vault;         // vault balance only — NOT total wealth
Map<UUID, Claim>         claims;        // keyed by claim id (not owner — one owner can hold several)
Map<UUID, DeathState>    deaths;        // revive cooldown + rolling-hour death count
Map<UUID, BlockPos>      boundWaypoint; // last waypoint interacted with; the /warp destination
Map<UUID, Long>          lastWarpTick;  // for the 5-minute /warp cooldown
record ShopEntry(Item item, int qty, long price, int remaining) {}
record ClaimBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}
record DeathState(long respawnReadyTick, int deathsThisWindow, long windowStartTick) {}
// Claim: owner, ClaimBox, paid-through day, Map<UUID, TrustLevel> per-player place/destroy trust

// NOT stored, deliberately: dailyDropped (no daily cap exists — see "Economy:
// one faucet") and any order-board / contract state (the rotating crop-order
// idea was considered and DROPPED; the faucet stays the only money source).
```

Note what is **not** stored: cash in hand. Items in inventories and chests are
already saved by the game. There is no single place that knows a player's total
wealth, and `/eco total` can only count the vault plus estimate the rest.

Store reset and paid-through dates as **day numbers, not timestamps** —
integer comparison instead of date math, and it survives restarts cleanly.

## Custom weapons — current state

**Art comes from the Blades of Majestica resource pack**, a third-party pack
by Eftann. It is 3D: each weapon is a Blockbench model of 30-157 cuboid
elements with a 128x128 texture, some with animated overlay strips. This is
why it looks the way it does — it is geometry, not pixel art, and no flat
16x16 or 32x32 sprite will ever match it.

**Licensing, stated plainly so it is not rediscovered later:** the pack is
All Rights Reserved and several designs are third-party IP (Crescent Rose is
RWBY, Scissor Blade is Kill la Kill, Tengen's Blade is Demon Slayer, Divine
Axe Rhitta is Black Clover). Importing it was a deliberate **self-use**
decision. **This repository is public on GitHub** — pushing the imported art
publishes it to everyone, which is a different act from using it privately.
If that matters, `git rm` the imported textures and models and keep them
local, or make the repo private.

**Acted on, 2026-08-30: `pisomarket:divineaxerhitta` (Divine Axe Rhitta,
part of the Divine line) is exactly this case.** Its model, texture, and
item-definition JSON are `.gitignore`d and were never pushed — see the
comment there. It still exists on this machine and is still registered by
`ElementalWeapons.java`, so it builds and works locally; a fresh clone of
the repo will just show it as the missing-texture placeholder until those
three files are supplied locally again. If any of the other 14 weapons turn
out to share this problem, the same treatment applies: exclude, don't
delete the registration.

**The weapon set is designed around the pack's own matched families**, not
around invented elements. This was the key lesson: the artist built coherent
sets, and picking from one family gives visual consistency for free.
Cherry-picking unrelated models across families looked wrong immediately.

| Line | Sword | Heavy | Reach | On hit |
|---|---|---|---|---|
| Molten | `moltensword` | `moltenblade` | `hearthflame` (hammer) | Ignite |
| Frost | `frostblade` | `frostaxe` | `frostscythe` | Slowness |
| Blight | `abominableblade` | `abominablegreatsaber` | `abominablescythe` | Poison |
| Soul | `souledge` | `soul_devourer` | `soul_collector` | Lifesteal |
| Divine | `divine_justice` | `divineaxerhitta` | `divine_reaper` | Smite |

Notes on that table:

- **Molten has a hammer, not a scythe, because the pack contains no fire
  scythe.** Letting the available art decide the weapon class is the whole
  point of building the set this way round.
- **Lifesteal and Smite are implemented, confirmed 2026-08-30.** Lifesteal
  heals the attacker a flat amount (`LivingEntity.heal(float)`). Smite deals
  a small burst of bonus damage, but only against
  `EntityTypeTags.SENSITIVE_TO_SMITE` — the same tag vanilla's own Smite
  enchantment checks — so it is a no-op against anything not undead.
- **No bow or crossbow art exists.** The Blades of Majestica pack has 119
  weapon models total (far more than the 36 this doc previously claimed —
  most are unrelated single fantasy swords, several tied to third-party IP,
  which is exactly why only these 5 coherent, unlicensed-feeling families
  were picked), and none of them are ranged — the table above is melee-only
  for that reason, not by design choice.
- Everything stays **iron tier**, never diamond. The effect is the reason to
  carry one, never the damage number, so player-enchanted gear still wins a
  straight fight.

**All 15 are built, as of 2026-08-30.** Frostblade went first, alone, as the
sample because it is the only strong candidate with no animated overlay, so
what renders in game is exactly what was previewed — anything wrong would be
a real bug rather than a preview artefact. Once it compiled clean the other
14 followed the same procedure in one pass (`ElementalWeapons.java`), copied
by a small one-off Python script rather than by hand — the model JSONs and
item definitions are mechanical, and hand-editing 14 of them serially was
pure risk of a typo. All are registered, in the COMBAT creative tab, and
reachable with `/give @s pisomarket:<name>` (e.g.
`pisomarket:divine_reaper`).

**Tool profile note:** Minecraft has no "scythe" tool type, so the Reach
column uses the same `.sword()` profile as the Sword column — "Reach" is
the pack's own art category for these models, not a mechanical difference
implemented here. The Heavy column uses `.axe()` for the heavier feel. See
the comment block at the top of `ElementalWeapons.java` for the exact
numbers.

**How an import works** (the procedure the script automated): copy the
model JSON from the pack, rewrite its `textures` values from `item/x` to
`pisomarket:item/x`, copy those PNGs (and any `.png.mcmeta` for animated
ones — none of these 15 are animated), write an item definition in
`assets/pisomarket/items/`, add a lang key, register the item.
**Watch for texture-name mismatches**: several of the pack's models don't
reference a same-named texture (`frostaxe.json` uses `frostscytheaxe.png`;
`soul_devourer.json` and `soul_collector.json` reference *each other's*
texture file) — always read the model's own `textures` block rather than
guessing the PNG name from the model name.

**Compiles clean, confirmed 2026-08-30.** `./gradlew build` succeeded on the
first try for Frostblade, and again after the other 14 plus Lifesteal/Smite
were added — every 26.2 API name used in `com.pisomarket.combat`
(`ToolMaterial.IRON`, `Item.Properties.sword()`/`.axe()`, `hurtEnemy`,
`igniteForSeconds`, `MobEffects.SLOWNESS`/`POISON`, `LivingEntity.heal()`,
`EntityTypeTags.SENSITIVE_TO_SMITE`) was correct as written or fixed on
sight (`hurt(DamageSource, float)` is deprecated in favour of
`hurtServer(ServerLevel, DamageSource, float)` — confirmed by decompiling
with `./gradlew genSources` and grepping the generated source, per "Reading
failures" below). A headless `runServer` load also shows `(pisomarket) Piso
Market initialized` with no mixin/registration errors for the full set.
**Not yet verified: what any of it looks like in game.** Compiling proves
the code is type-correct, not that the models/textures render right — item
rendering, model geometry and display transforms can't be checked headlessly
at all (`CLAUDE.md`'s "test with `runServer`" advice does not apply to this
weapon work). Needs a human at the screen — via `./gradlew runClient`, or by
dropping the built jar into a real `.minecraft/mods` folder (done on this
machine, TLauncher's "Fabric 26.2" profile) — to confirm all 15 render
correctly in hand, in inventory, and on the ground, and that each element
(ignite, slow, poison, lifesteal, smite) actually applies on hit.

**Windows build environment note:** on a fresh Windows machine the system
default `java` may still be an old JRE (no compiler). Gradle's own launcher
needs JVM 17+, so `JAVA_HOME` must point at a real JDK 25 install (e.g.
`winget install EclipseAdoptium.Temurin.25.JDK`) when invoking `./gradlew` —
`org.gradle.java.home` in `gradle.properties` stays deleted (see the comment
there); export `JAVA_HOME` in the shell instead. This mirrors the same
JRE-vs-JDK trap that blocked the build on Linux.

## Build order

1. Template skeleton — confirm `./gradlew runClient` launches with the mod
   loaded
2. Balance storage (`SavedData`) + `/balance` and `/pay` commands
3. Market listings — post, browse, buy; items held in world storage
4. System shop + rotating stock
5. Territory claims + rent
6. Graphical screens on top of the working commands

Steps 2–5 are pure Brigadier commands (Minecraft's command framework) — no GUI
code, no networking packets. Custom screens require client and server code that
must agree exactly, which is where rusty Java will hurt most. Commands first.

## In-game command surface

The full command surface. Everything ships as a command first; screens come
later and are built on top of these.

**Player — money**
- `/balance` — vault balance (cash in hand is visible in the inventory)
- `/donate <player> <amount>` — vault-to-vault transfer; recipient may be
  offline. **Renamed from `/pay` 2026-08-30** per the v2 redesign §1c —
  same command (`PisoCommands.donate`), new name only.
- `/deposit [amount]` and `/withdraw <amount>` — **listed here as the
  eventual v2 design, not current reality.** Today these commands do NOT
  exist; deposit/withdraw only works through the Shop block's Vault tab —
  see "Interface: the Shop block is the real thing, still" above. They'll
  become real commands once §1d of the v2 redesign (shop block removed,
  `/shop` opens the UI instead) is actually built.
- `/top` — the wealth leaderboard snapshot. **Still `/top`, not renamed** —
  this line claimed "renamed from `/top`" before this correction, but
  `LeaderboardCommands.java` registers `"top"`, nothing else. The rename to
  `/leaderboard` (plus narrowing to top 3, plus a 2-in-game-day
  auto-broadcast) is v2 redesign §8, not built yet.

**Player — market** (asynchronous; seller and buyer need not be online together)
- `/market list <price>` — list the held stack, item moves into world storage
- `/market browse [page]` — paged listings
- `/market buy <id>` — purchase by listing ID
- `/market mine` — own active listings
- `/market cancel <id>` — retrieve an unsold listing

**Player — system shop** (sells only, never buys)
- `/shop browse [tier]` — current stock, tiers rotate weekly
- `/shop buy <id> [qty]` — purchase, deducts balance

**Player — travel and death**
- `/warp` — still design-only, not built. See "Waypoints" above.
- `/revive` — **does not exist.** Built 2026-08-30, then removed the same
  day — see "Death and revive" above. Dying is currently plain vanilla
  respawn, no hold, no cooldown.

**Player — territory**
- Land Deed (bought from BlackMarket) — use it on unclaimed ground to
  activate a claim of its baked-in size; fails if the area or its
  surroundings overlap an existing claim
- `/unclaim` — release a claim you own
- `/claims` — own claims, rent due, paid-through day
- `/trust <player> <place|destroy|both>` / `/untrust <player>` — grant or
  revoke build access on own claims (also reachable by clicking inside the
  activated deed book)

**Admin** (permission level 2)
- `/eco give <player> <amount>` — mint money; logged every use
  (`EcoCommands.java`). **Implemented 2026-08-30** — this whole Admin section
  was documented from early in the project but only `/eco` actually exists;
  it was built because there was otherwise no way to fund a vault balance
  for testing except depositing real potatoes at the Shop block. Takes
  `EntityArgument.player()`, not `GameProfileArgument` like `/pay` — that
  makes `@s` work inside `pisomarket:testkit` (see "Testing everything in
  Minecraft" below) but means, unlike `/pay`, it can't target an offline
  player.
- `/eco take <player> <amount>`
- `/eco set <player> <amount>`
- `/eco total` — sum of vault balances only, logged as such; NOT total money
  in circulation, since cash in inventories/chests isn't tracked anywhere
- `/market remove <id>` — **not implemented.** Documented, never built.
- `/claim force-unclaim` — **not implemented.** Documented, never built.

### Chat formatting

Every command reply follows one colour system, so a player can scan chat and
know what they are looking at without reading. Today only 18 of 125
`Component.literal` calls carry any style at all — nearly all of them in the
leaderboard — so this is a gap, not a preference.

**There is no font size in Minecraft chat.** The only levers are the 16
named `ChatFormatting` colours plus bold, italic, underline and
strikethrough. Real size changes exist only in titles/subtitles and the
action bar, which are separate systems and not used for command replies.

| Role | Style | Applies to |
|---|---|---|
| Prefix | `GOLD` + bold, `[Piso]` | Every mod message, to separate it from vanilla |
| Success | `GREEN` | "Deposited", "Bought", "Listed" |
| Failure | `RED` | "Insufficient balance", "No inventory space" |
| Money | `YELLOW` + bold | Every currency amount, without exception |
| Names | `AQUA` | Player names, item names, claim ids |
| Body | `WHITE` | Ordinary sentence text |
| Hint | `DARK_GRAY` + italic | Footnotes, usage tips, "updates every ..." |
| Button | `AQUA` + underline | Clickable `[Confirm]` / `[Trust]` links |
| Warning | `YELLOW` | Rent unpaid, protection lapsing |

The rule that makes it feel deliberate: **money is always yellow-bold and
names are always aqua.** A player scanning a wall of chat picks out amounts
instantly.

### Rules for all commands

- Amounts are `long`, whole units, validated positive **before** touching
  storage. Negative amounts on a transfer reverse its direction — a real
  exploit, not a hypothetical.
- Player-facing commands take no permission gate; admin commands always do.
- Every command that changes stored data calls `markDirty()`.
- Confirm destructive actions (`/market cancel`, `/unclaim`) rather than acting
  immediately.



- Currency display name
- **The faucet rate contradicts itself.** This doc says 2.5% ("decided,
  final") and derives all pricing from "1 Piso = 40 mature potatoes".
  `HarvestFaucet.DROP_CHANCE` is actually **0.01** — 1%, so the real rate is
  **100 potatoes per Piso** and every shop price is effectively 2.5x more
  expensive than the reasoning above intends. The revive costs in "Death and
  revive" are quoted against the real 1%. Decide which number is right and
  make the other match.
- **The revive squaring reading** — quadratic `n²` is written up; confirm that
  is what was meant (see "Death and revive")
- Exact rent rates, price multiplier, deed sizes/prices — tune after the
  systems run
- Daily drop cap — deferred (see "Economy: one faucet"), not abandoned
- **Weapon shop prices — DECIDED 2026-08-31, but the numbers do not work.**
  Only the Tier 4 (weakest) group is sold: Abominable Blade 200,000, Frost
  Axe 150,000, Molten Sword 100,000. Everything stronger is mob-drop-only.
  The intent is sound — the shop should not sell top-tier gear. **The prices
  are the problem**, and this needs revisiting:
  - At ~5 shards/hour from mob grinding, 100,000 is ~20,000 hours. Farming
    is slower still. Neither faucet can realistically reach these numbers.
  - The only income at that scale is boss drops (Warden 10,000), so 100k is
    "ten Wardens".
  - But **one Warden already drops a Tier 1 weapon at 100%** — strictly
    better than the Tier 4 weapon 200,000 buys. So the rational play is
    always to kill Wardens and never open the shop, which makes the whole
    Tier 4 shop listing dead content.
  - Fix is one of: drop weapon prices to boss-income scale (~5k-20k), raise
    the faucet substantially, or accept Tier 4 shop weapons as deliberately
    vestigial.
- **Waypoint price — DECIDED: 5,000**, sold in the shop (`TIER_CONSUMABLE`).
  This one IS reachable: roughly half a Warden, or sustained mob grinding.
- Whether listings ever expire and return items to the seller
- Whether rent is strictly auto-renew, or a manual `/claim pay` also exists
- Whether `/pay` should exist at all, given players can hand over coins directly

## v2 redesign — planned 2026-08-30, not yet built

**A large redesign was planned in this session across 11 areas. None of it
is implemented yet — this section is the plan, written down before code so
the next session (or the next hour) doesn't have to re-derive it.** Where
this contradicts an existing section above, **this section wins** — the
older text stays only as history until each piece actually gets built and
the corresponding old section gets rewritten or deleted. Update this note's
date, or delete this whole section, once execution catches up.

### 1. Currency

**Item: the Sunstone Shard.** A hand-drawn-pixel-art amber crystal shard,
picked from a 3-concept sheet over a fang talisman and a rune shard.
Replaces the poisonous-potato-as-currency design entirely — that design's
core problem (every poisonous potato already in every world counts as
pre-existing money) goes away once currency is a new item nothing else
produces. Needs: the actual 16×16 texture (concept sheet was a rough
silhouette/palette proposal, not the final asset), item registration, and
the faucet reworked to hook every crop's harvest event, not just potato
(`HarvestFaucet.java` currently ONLY checks `Blocks.POTATOES` — wheat,
carrot, beetroot, nether wart all need their own hook, this is real
unbuilt work, not a config change).

**Drop chances, locked:**

| Crop | Chance | Notes |
|---|---|---|
| Wheat | 2.5% | |
| Potato | 2.5% | |
| Carrot | 2.5% | |
| Beetroot | 5% | |
| Nether Wart | 10% | Nether-only access is its own barrier already, priced in |

Melon/pumpkin/sugar cane/cocoa/sweet berries/glow berries are explicitly
**not** in this list yet — they're the renewable crops (harvest without
replanting, see the growth-mechanics table this session worked out) and
were flagged as needing either a lower chance or a hard cap so they don't
dominate the faucet the way an unbounded automated farm would. Still open.

**Pricing baseline, established this session:** a 9×9 (81-crop) carrot
plot with no buffs earns roughly 2 Shards per full harvest cycle
(81 × 2.5%), so **20 cycles ≈ 40 Shards** — real time per cycle is a rough
40-60 minute estimate (random-tick-driven growth, genuinely fuzzy, worth
measuring for real once this is built rather than trusting the theory).
**The shop's price floor is 20 Shards** for the cheapest item — see §3 for
how that plays out against the actual Tier 1 numbers.

**Still open:**
- Whether farm size still matters the way it did for the old single-potato
  faucet, and whether a per-crop or overall cap is needed now that more
  crops feed supply (more sources than the old design ever priced for) —
  this is where the renewable-crop question above lives
- Whether the Luck stat from the new level system (see §9) affects drop
  chance — proposed as a stat/faucet synergy, not confirmed

**Commands:** `/balance`, `/deposit <amount>`, `/withdraw <amount>`,
`/donate <player> <amount>`. **`/donate` replaces `/pay`, confirmed and
built 2026-08-30** — `PisoCommands.java` now registers `donate`, not `pay`.
`/deposit` and `/withdraw` still don't exist as commands — they're blocked
on removing the Shop block dependency, same as noted in §1d below.

**Shop block is removed. `/shop` opens the same UI instead, minus the
Vault tab** (vault access becomes deposit/withdraw commands only, so the
block dependency `PisoCommands.java` currently hard-requires goes away).
This resolves cleanly against the "Interface: the Shop block is the real
thing, still" section above — that section documents an earlier, different,
never-finished plan to delete the block outright in favor of pure commands;
this new plan keeps the GUI but changes how it opens.

### 2. Player market

UI keeps its Sell/Buy/BlackMarket tabs, now inside the `/shop`-opened menu
per §1 rather than a placed block. No other change from the current
`PisoShopMenu` design.

### 3. System shop — restructured

**Locked:** drop the wood→stone→iron→gold→diamond material ladder
entirely. New tiers:

| Tier | Contents |
|---|---|
| 1 | Tools — diamond only |
| 2 | Gear/armor — diamond only, single-enchant as before (Unbreaking/Mending still banned, still two levels below max) |
| 3 | Rare items — see table below. **Land Deeds sell here too**, once built (folds `/deed buy` into the same catalog instead of a separate BlackMarket flow — deliberately not removing the current `/deed browse`/`/deed buy` commands until this replacement actually exists, so deeds don't go unbuyable in the gap; `/deed confirm`/`/deed cancel` are a different thing — activating an already-owned deed — and aren't touched either way) |
| 4 | **New** — the 15 custom elemental weapons become purchasable here. They have no price yet; today they're `/give`/creative-only. |

**Tier 1 pricing, locked: 10 Shards per diamond in the vanilla recipe.**
Confirmed against real vanilla recipe diamond counts (hoe/sword both use
2, pickaxe/axe both use 3), replacing the old material-ladder formula
entirely — no more `price = material unit value × recipe unit count`
spanning five materials, just this one flat rate:

| Item | Diamonds | Price |
|---|---|---|
| Shovel | 1 | 10 |
| Sword | 2 | 20 |
| Hoe | 2 | 20 |
| Pickaxe | 3 | 30 |
| Axe | 3 | 30 |
| Boots | 4 | 40 |
| Helmet | 5 | 50 |
| Leggings | 7 | 70 |
| Chestplate | 8 | 80 |

Full 5-tool kit: 110. Full armor set: 240. Note the shovel (10) sits below
the §1 price floor (20) — never resolved which one bends; ask before
building if it still matters, or let the shovel be the one exception.

**Tier 2 pricing:** the old 60%-per-enchant-level markup
(`ENCHANT_PREMIUM_PER_LEVEL` in `ShopCatalog.java`) was proposed to carry
over unchanged onto the new Tier 1 base prices — **never explicitly
reconfirmed**, flag before building.

**Tier 3 pricing, locked:**

| Item | Price | Notes |
|---|---|---|
| Totem of Undying | 45 | |
| Shulker Box | 100 | |
| Netherite Ingot | 50 each | Raw material only — lets a player netherite-upgrade their OWN diamond gear at a smithing table; the shop still never sells finished netherite tools/armor |
| Elytra | 120 | |
| Harvest Potion I | 30 | Flat price, NOT the old "half of one minute's yield" formula — see below |
| Harvest Potion II | 40 | Same |
| Luck Potion | 30 | Same |
| XP potion | **deferred** | Explicitly pushed to the mob-drop/level-system discussion (§4/§9) — whether it's the existing vanilla Experience Bottle (already in the old catalog at 12) or a new drink-for-XP item is still unanswered |

Dropped from the old catalog entirely (not carried into Tier 3): Name Tag,
Saddle, Firework Rocket, Ender Pearl, Echo Shard, Heart of the Sea,
Enchanted Golden Apple, Netherite Upgrade Smithing Template, Trident (all
considered, all rejected in favor of the list above). Enchanted Golden
Apple was floated as worth keeping but never confirmed either way.

**Harvest/Luck potion buff strength, still needs a final call.** The old
buffs (`HarvestFaucet.HARVEST_BOOST_I`/`_II`, flat `+1.5`/`+4` percentage
points) were calibrated against the old single-crop 1% base and don't
scale sensibly now that base rates vary 2.5%-10% across crops. A
percentage-of-base multiplier was proposed (+50%/+150%) instead of a flat
addition, so the boost feels the same relative strength on every crop —
**this was never confirmed**, only the flat *prices* above (30/40/30) were
locked. Do not build the potion effect strength from the multiplier table
in an earlier draft of this doc without checking back — only the prices
are settled, not the numbers they buff.

### 4. Territory claims

No change. Land Deed purchase/activate/trust flow stays exactly as
documented above, except deeds now sell from the shop's Tier 3 (§3) instead
of a separate BlackMarket-only path.

### 5. Lockable chests

**No work needed — already matches what was asked for.** There is no Lock
block or item today; chest access is already the claim-wide `ChestAccess`
setting edited from the Land Deed book (see "Lockable chests" above,
corrected earlier this session). Confirmed, not a new task.

### 6. Combat — FULL REBALANCE SPEC (designed 2026-08-31, NOT YET BUILT)

**Status: the armor half is built and shipped. The weapon half is not.**
`CustomArmorContent.java` exists and matches this spec. `ElementalWeapons
.java` still carries the OLD iron-tier stats (3.0/-2.4 sword, 6.0/-3.1
axe) — every weapon number below is design only. Do not read the table
below as describing current in-game behavior.

This section supersedes the old "iron tier, never diamond, the effect is
the reason to carry one" rule from "Custom weapons — current state"
above. That rule is **reversed**: custom weapons are now deliberately
*stronger* than diamond, and the counterweight is that only the three
custom armor sets can survive them.

#### The damage math this is all built on (verified, not assumed)

Decompiled from `CombatRules`. Both steps matter and they compose:

```
tf         = 2 + toughness/4
realArmor  = clamp(armor - dmg/tf, armor*0.2, 20)
afterArmor = dmg * (1 - realArmor/25)
afterProt  = afterArmor * (1 - clamp(protPoints,0,20)/25)
```

Two findings that drove every number here:

- **Armor points cap at 20 / 80% reduction.** Vanilla diamond and
  netherite already sit at exactly 20. Giving custom armor *more armor
  points would do literally nothing.* Toughness has no cap — that's why
  the custom sets boost toughness and nothing else.
- **Protection is 1 point per level per piece, total clamped at 20.**
  So Protection V x 4 pieces = 20 = the cap exactly. **Protection VI
  would be wasted**; V is precisely the maximum useful value, which is
  why the sets use V and not something higher.

#### Shape identity — the three weapon classes

Tuned so all three land at ~46-48 DPS but feel completely different.
This is the Tier 2 baseline; tier multipliers scale it.

| Shape | Dmg | Speed | Crit | DPS | Feel |
|---|---|---|---|---|---|
| Sword | 30 | 1.6/s | — | 48.0 | fast, consistent |
| Heavy (axe) | 40 | 1.0/s | 30% @ 1.5x (60 on crit) | 46.0 | slow, spiky |
| Scythe (reach) | 44 | 1.05/s | — | 46.2 | slow, huge per hit |

Tier multipliers: **T1 Souls 0.92, T2 1.00, T3 0.88, T4 0.78.**

**Crit is a new flat percentage chance on every swing**, NOT vanilla's
fall-attack crit. Vanilla's only triggers while falling and not
sprinting, which most players never notice — a flat roll (same shape as
`Element.onHit`'s existing procs) makes the axe's identity always-live.

**Hard floor: every weapon except the Frost and Molten lines deals >= 30.**
That is exactly what 2-hits a buffed Enderman (60 HP) — the binding
constraint. Witch (40 buffed) falls out of it for free.

#### The weapon table

| Tier | Weapon | Shape | Dmg | Spd | Effect | Cleave |
|---|---|---|---|---|---|---|
| 1 | Soul Collector | Scythe | 40 | 1.05 | Lifesteal 4 HP | — |
| 1 | Soul Devourer | Heavy | 37 | 1.0 | Lifesteal 3 HP | yes |
| 2 | Divine Reaper | Scythe | 44 | 1.05 | Smite +6 | — |
| 2 | Divine Axe Rhitta | Heavy | 40 | 1.0 | **Smite +25** | — |
| 2 | Abominable Scythe | Scythe | 42 | 1.05 | Poison 6s | — |
| 2 | Abominable Greatsaber | Heavy | 38 | 1.0 | Poison 6s | yes |
| 3 | Divine Justice | Spear | 30 | 1.6 | Smite +6, charge attack | — |
| 3 | Frost Scythe | Scythe | 39 | 1.05 | Slowness 3s | — |
| 3 | Molten Blade | Heavy | 35 | 1.0 | Ignite 5s | yes |
| 3 | Frostblade | Sword | 24 | 1.6 | **Slowness 8s** | yes |
| 4 | Abominable Blade | Sword | 30 | 1.6 | Poison 4s | yes |
| 4 | Molten Sword | Sword | 23 | 1.6 | Ignite 4s | — |
| 4 | Frost Axe | Heavy | 31 | 1.0 | Slowness 3s | — |

Design notes baked into that table:

- **Divine Axe Rhitta's Smite +25** is its whole identity: 65 total
  against undead, which one-shots every common buffed undead (30 HP) and
  most tanky ones. Against anything not undead it is a plain 40 — the
  most specialised weapon in the set, deliberately.
- **Divine > Abominable within Tier 2** on raw damage (44 vs 42, 40 vs
  38), so Divine stays the PvP pick even though Abominable's poison is
  the better farming effect.
- **Frostblade is the deliberate exception**: lowest damage of any
  non-Molten weapon (24, below Abominable Blade's 30) in exchange for an
  8-second slow, more than double anyone else's. It is a control weapon,
  not a damage weapon.
- **Cleave** (hits multiple targets in an arc) goes to Soul Devourer,
  Abominable Greatsaber, Molten Blade, Frostblade, Abominable Blade.
  This is the mob-farming mechanic for everything that isn't Rhitta.
- Molten Sword and Frost Axe were never named in the boost pass and stay
  low — flag if the whole Frost/Molten lines should move together.

#### PvP balance — the actual point of all this

Damage taken per hit, computed with the real formula above:

| Armor | vs 30 dmg | vs 44 dmg | Hits to kill (20 HP, vs 44) |
|---|---|---|---|
| Vanilla netherite, no enchants | 13.2 | 24.3 | **1** |
| Vanilla netherite + Protection IV | 4.75 | 8.74 | **3** |
| Sentinel (gold) + Prot V | 4.96 | 8.03 | 3 |
| Aegis (diamond) + Prot V | 2.31 | 4.14 | **5** |
| Bulwark (netherite) + Prot V | 2.00 | 3.48 | **6** |

That is the goal met: **best-case vanilla gear dies in 3 hits, custom
armor doubles that to 5-6** — and at max level (40 HP) Bulwark reaches
~12 hits, which is a genuine duel rather than a coin flip. Sentinel
(gold) lands equal to fully-enchanted vanilla netherite, which makes it
the honest entry tier rather than a trap.

#### Mob HP — 1.5x across the board

Buffed so the weapons above have something to bite. Warden is 1.3x only;
it is already terrifying.

| Mob | Vanilla | Buffed | | Mob | Vanilla | Buffed |
|---|---|---|---|---|---|---|
| Zombie/Skeleton/Creeper | 20 | 30 | | Piglin Brute | 50 | 75 |
| Spider | 16 | 24 | | Guardian | 30 | 45 |
| Cave Spider | 12 | 18 | | Elder Guardian | 80 | 120 |
| Witch | 26 | 40 | | Ravager / Iron Golem | 100 | 150 |
| Enderman | 40 | 60 | | Wither | 300 | 450 |
| Blaze / Wither Skeleton | 20 | 30 | | Ender Dragon | 200 | 300 |
| Hoglin | 40 | 60 | | Warden | 500 | 650 |

**Mob XP scales with the HP buff** (common hostile 5 -> 8) so farming
stays worth the extra hits.

#### Player stats per level

**Assumes max level 50 — never explicitly confirmed, verify before
building.** Cycle: every 5th level grants HP, all other levels alternate
Attack and Defense.

| Stat | Trigger | Per grant | Total at 50 |
|---|---|---|---|
| Max HP | every 5th level (10x) | +2 (1 heart) | **+20 HP** -> 40 HP / 20 hearts |
| Attack | 20 of the other levels | +0.15 dmg | +3 damage |
| Defense | 20 of the other levels | +0.2 toughness | +4 toughness |

Attack is deliberately small: the weapons above are already doing the
heavy lifting, and stacking a large Attack stat on a 44-damage Divine
Reaper reintroduces the one-shot problem this whole spec exists to
avoid. Defense grants **toughness, not armor points** — same reason as
the armor sets, points are capped and already maxed.

#### Still open before this can be built

- Max level 50 confirmed?
- Divine Justice's spear charge: does normal left-click still swing as a
  weaker fallback, or is charge-and-release the only attack?
- Should the full Frost and Molten lines move together, or do Molten
  Sword (23) and Frost Axe (31) stay behind their line-mates?

### 7. Anti-grief

No change. TNT stays disabled. End crystals and beds keep exploding
normally — reasoning confirmed: both already require rare materials to
obtain, so they aren't a free griefing shortcut the way TNT was.

### 8. Leaderboard

**`/leaderboard`** — top 3 players and their balances (can build on the
existing `PisoLeaderboard`/`/top` code, this is closer to a rename plus a
narrower top-3 view than new tracking logic).

**New: auto-broadcast every 2 in-game days.** A scheduled server-wide chat
message of the same top-3 view. Low-risk to add — the day-counting pattern
already exists for shop restocks (`ShopEntry.restockDays` /
`PisoShopStock`), this reuses the same idea on a fixed 2-day interval
instead of per-item.

### 8b. Mob drops — Shards and weapons (designed 2026-08-31, NOT BUILT)

Mobs become the second faucet alongside farming, and the **only** source
of custom weapons outside the shop. Nothing in this section exists in code
yet.

#### Three safeguards that are load-bearing, not optional

These exist because without them the whole table is an infinite money
loop. Build them at the same time as the drops, not after.

1. **Player-kill only.** The drop fires only when a player lands the
   killing blow — not fall damage, not suffocation, not an iron golem,
   not a wolf. This is the exact same rule `HarvestFaucet` already uses
   for crops (`PlayerBlockBreakEvents`, player-break only), and for the
   same reason: it makes classic AFK mob-grinder farms mint nothing.
2. **Player-buildable mobs drop NOTHING.** Iron Golem and Snow Golem are
   craftable from blocks, so any drop on them is a literal
   iron-for-money printer. Zero shards, zero weapons, no exceptions.
3. **Passive mobs drop nothing.** Cows, pigs, sheep, chickens breed
   infinitely and cost nothing to kill.

#### Boss drops

| Boss | Weapon roll | Shards |
|---|---|---|
| **Warden** | **100%** — one of {Soul Devourer, Soul Collector} | 10,000 |
| **Ender Dragon** | 50% — one of {Divine Axe Rhitta, Abominable Scythe}; **plus** 5% — one of {Soul Collector, Soul Devourer} | 5,000 |
| **Wither** | 40% — one of {Divine Reaper, Abominable Greatsaber}; plus 3% Tier 1 | 3,000 |

**Repeatability warning, flagged not solved.** Wither and Ender Dragon
are both re-summonable, so those payouts are farmable in principle. The
gates are real but finite: a Wither costs ~120 wither skeleton kills
(3 skulls at ~2.5%), a dragon respawn costs 4 end crystals. The Warden
is not summonable but *can* be farmed in a deep dark by a strong enough
player. If any of these three turns out to be the dominant income source
in practice, the fix is a per-player cooldown on the boss payout rather
than nerfing the drop.

#### Rare and structure-gated mobs

| Mob | Weapon roll | Shards |
|---|---|---|
| Elder Guardian | 15% Tier 2 | 500 |
| Ravager | 10% Tier 3 | 300 |
| Evoker | 8% Tier 3 | 100 |
| Piglin Brute | 5% Tier 4 | 80 |
| Breeze | 5% Tier 3 | 60 |
| Shulker | — | 60 |
| Guardian | 3% Tier 4 | 40 |
| Vindicator / Pillager / Illusioner | — | 30 |
| **Iron Golem / Snow Golem** | **none** | **none** (safeguard 2) |

#### Uncommon mobs

| Mob | Chance | Shards on hit |
|---|---|---|
| Witch | 20% | 1-3 |
| Enderman | 20% | 1-3 |
| Blaze | 15% | 1-2 |
| Wither Skeleton | 15% | 1-2 |
| Ghast | 15% | 1-2 |
| Hoglin / Zoglin | 12% | 1-2 |
| Phantom / Piglin / Slime / Magma Cube / Cave Spider / Stray / Bogged / Husk / Drowned | 10% | 1 |

#### Common mobs

| Mob | Chance | Shards |
|---|---|---|
| Zombie / Zombie Villager / Skeleton / Creeper / Spider / Silverfish / Endermite | 5% | 1 |

#### The farming-vs-grinding tension, stated plainly

At 5% per common mob and ~100 kills/hour, mob grinding yields ~5
shards/hour. A 9x9 carrot plot yields ~2 shards per 40-60 minute cycle,
so roughly 2-3/hour. **Mob grinding is therefore ~2x more lucrative than
farming**, which inverts the original design where farming was the single
faucet. That is a deliberate consequence of adding mob drops at all, but
if farming should stay the primary path, common-mob rates want to come
down to ~2-3% rather than farming rates going up (raising farming rates
re-prices the entire shop).

### 8c. Potions — rework from farming-only to all-activity

**Current behaviour (built):** `HarvestFaucet.HARVEST_BOOST_I/_II` add a
flat +1.5 / +4 percentage points, and only ever apply to breaking a
mature potato. `HARVEST_LUCK` doubles a successful farm payout.

**New design (not built):** both potions apply to **every** Shard source
— farming *and* mob kills — and the boost becomes a **percentage of the
base rate** rather than a flat addition.

| Potion | Effect |
|---|---|
| Harvest Potion I | +50% relative Shard drop chance, all sources |
| Harvest Potion II | +150% relative Shard drop chance, all sources |
| Potion of Luck | Doubles Shard payout on a successful roll, all sources |

**Why relative and not flat:** a flat +1.5 points is huge on a 2.5% crop
(+60% relative) and nearly meaningless on a 10% nether wart (+15%
relative) or a 20% Enderman. Once rates vary from 2.5% to 20% across
sources, only a multiplier keeps the potion feeling like the same item
everywhere.

**Names are now wrong and should change** — "Harvest" no longer describes
something that buffs mob kills. Suggested: *Fortune Potion* / *Greater
Fortune Potion*, keeping *Potion of Luck*. Not renamed yet; flag if you
want different names before this gets built.

### 9. Level system — new

**Stats: Attack, Defense, Speed, Luck.** Luck doubles into §1's crop drop
chances — leveling up is meant to feel connected to the economy, not just
combat, per the brief given when this was proposed.

**XP sources, weighted farming < mobs < PvP** (locked, exact multipliers
not set — proposed default: mob kill = 3x a farming action's XP, player
kill = 3x a mob kill's XP, i.e. roughly 1 / 3 / 9; confirm or override).

**Still fully open, blocking implementation:**
- The level curve itself — XP required per level, and whether it's linear,
  quadratic, or vanilla-style (vanilla XP-to-next-level is itself
  non-linear and gets steeper after level 15 — worth deciding whether to
  reuse vanilla's curve/UI or build an entirely separate stat-XP track so
  it doesn't fight with vanilla enchanting XP spend)
- How much each stat point actually does numerically (how much attack
  damage per point, how much speed, etc.) — this is the "decide the
  scaling first" piece explicitly flagged as unresolved
- Whether stat points are auto-allocated per level or player-chosen

### 10. PvP — health display

**Built 2026-08-30, confirmed reading: persistent, always visible.**
Implemented as vanilla's own `below_name` scoreboard display slot with a
`health`-criteria objective (`data/pisomarket/function/init.mcfunction`,
run automatically via `data/minecraft/tags/function/load.json`) — no
custom client render layer needed at all, which sidesteps the exact "rusty
Java will hurt most here" risk this doc flags for client rendering
elsewhere. This is a small heart icon + number under every player's
nametag, standard vanilla behavior once the objective is set as the
display slot — matches "like a heart and number above someone's head"
closely enough that it's very likely the same technique whatever server
this was seen on used.

### 11. Revive system — built, then dropped entirely

**Built 2026-08-30, then removed the same day at the user's request.** The
implementation (`ReviveManager.java`, `ReviveState.java`, `/revive`
command — spectator hold instead of a held death screen, 120s+120s/death
capped at 1 hour, reset every 3 in-game days, pay from the vault to skip)
compiled and loaded cleanly, so this wasn't a technical failure — it was a
direction change. See "Death and revive" above for what the numbers were,
kept as history only. **There is currently no revive system at all.**
Any future death/respawn design should be treated as a fresh decision, not
a resumption of this one.