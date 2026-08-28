# CLAUDE.md

Project context for Claude Code. Read this before making changes.

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
**JDK:** 25 (Eclipse Temurin) — installed via winget, NOT the system default
(that's still 1.8). `gradle.properties` sets `org.gradle.java.home` to point
at it explicitly; the wrapper itself also needs `JAVA_HOME` set to the same
path for the same reason when invoked from a shell that doesn't already have
it exported.

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

**Rate: 2.5% per mature potato harvested.** Decided, final
(`HarvestFaucet.java`). Drops go straight into the harvester's vault balance,
not the ground — avoids losing the payout to lava/despawn immediately after
earning it.

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

## Shop UI

A craftable block, not a command-only interface — placed in the world,
right-click to open a chest-style menu (crafted from 8 gold ingots around 1 obsidian) (`PisoShopMenu`, a custom
`AbstractContainerMenu`). Ended up needing a small custom client `Screen`
class after all (`PisoShopScreen`) — the build order's original hope of
avoiding one didn't quite hold, since vanilla's own generic-container screen
turned out to be hardcoded to its own `ChestMenu` type and couldn't be
reused directly. The screen itself is tiny (just copies vanilla's
background-drawing code); the real client/server-must-agree risk the build
order was worried about never materialized.

Slots hold **named books as navigation buttons**, not real readable books,
one shared menu instance repopulating the same grid per screen:

- **Vault** — a real slot to drop potatoes in for deposit, +/- buttons to
  dial in a withdraw amount, same code paths as `/deposit`/`/withdraw`
- **Buy** — a real clickable grid of live market listings; click one to buy
  (same code path as `/market buy`)
- **Sell** — a real slot to drop the item in, plus +/- buttons to dial in a
  price (no text input needed), then a confirm button (same code path as
  `/market list`)
- **BlackMarket** — a real clickable grid: the Tier 3/4 catalog, Land Deeds,
  and Locks together (see Territory claims) — sells only, never buys

Every button/grid click ultimately calls the same shared methods the
text commands use (`MarketCommands.tryBuy`, `ShopCommands.tryBuy`, etc.) —
the commands in "In-game command surface" below and the block's menu are two
front ends to the same logic, not two separate implementations to keep in
sync by hand.

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

A **Lock**, bought from BlackMarket (not part of the Tier 1-4 cosmetic
framework — a functional security item, like the Land Deed). Consumed on
use: right-clicking a chest while standing in your own claim binds that
specific chest to the claim. Right-clicking your own locked chest with
another Lock cycles its access level for anyone who **isn't** the claim
owner — the owner always keeps full access to their own chest regardless of
lock state, since the point is protecting the contents from other people,
not from yourself:

- **Closed** — no one else can open it at all
- **Put only** — others can deposit items, can't take any out
- **Put and get** — full access for anyone the claim already trusts

This is separate from build (place/destroy) trust — a player can be trusted
to build on your land without being able to touch your chests.

## Data model

```java
Map<UUID, Long>          vault;         // vault balance only — NOT total wealth
Map<UUID, Integer>       dailyDropped;  // coins dropped today, enforces the cap
Map<UUID, DailyProgress> progress;      // order board state, reset day number
Map<UUID, Claim>         claims;        // keyed by claim id (not owner — one owner can hold several)
record ShopEntry(Item item, int qty, long price, int remaining) {}
record ClaimBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}
// Claim: owner, ClaimBox, paid-through day, Map<UUID, TrustLevel> per-player place/destroy trust
```

Note what is **not** stored: cash in hand. Items in inventories and chests are
already saved by the game. There is no single place that knows a player's total
wealth, and `/eco total` can only count the vault plus estimate the rest.

Store reset and paid-through dates as **day numbers, not timestamps** —
integer comparison instead of date math, and it survives restarts cleanly.

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
- `/pay <player> <amount>` — vault-to-vault transfer; recipient may be offline
- `/deposit [amount]` — inventory coins → vault
- `/withdraw <amount>` — vault → inventory coins, needs inventory space
- `/daily` — progress against today's drop cap

**Player — market** (asynchronous; seller and buyer need not be online together)
- `/market list <price>` — list the held stack, item moves into world storage
- `/market browse [page]` — paged listings
- `/market buy <id>` — purchase by listing ID
- `/market mine` — own active listings
- `/market cancel <id>` — retrieve an unsold listing

**Player — system shop** (sells only, never buys)
- `/shop browse [tier]` — current stock, tiers rotate weekly
- `/shop buy <id> [qty]` — purchase, deducts balance

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
- `/eco give <player> <amount>` — mint money; log every use
- `/eco take <player> <amount>`
- `/eco set <player> <amount>`
- `/eco total` — total money in circulation; the number to watch for inflation
- `/market remove <id>` — force-remove a listing
- `/claim force-unclaim` — override a claim

### Rules for all commands

- Amounts are `long`, whole units, validated positive **before** touching
  storage. Negative amounts on a transfer reverse its direction — a real
  exploit, not a hypothetical.
- Player-facing commands take no permission gate; admin commands always do.
- Every command that changes stored data calls `markDirty()`.
- Confirm destructive actions (`/market cancel`, `/unclaim`) rather than acting
  immediately.



- Currency display name
- Exact rent rates, price multiplier, deed sizes/prices — tune after the
  systems run
- Daily drop cap — deferred (see "Economy: one faucet"), not abandoned
- Whether listings ever expire and return items to the seller
- Whether rent is strictly auto-renew, or a manual `/claim pay` also exists
- Whether `/pay` should exist at all, given players can hand over coins directly