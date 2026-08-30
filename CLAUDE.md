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

## Interface: commands only

**Decided, reversing the earlier design: there is no Shop block.** Every
front end in this mod is a Brigadier command. The block, its menu, its custom
client screen and the whole named-book navigation grid are **removed**.

The earlier reasoning — that a physical block makes the shop "a real place
players have to go" rather than decoration — is recorded here because it was
genuine, but it lost to the cost of maintaining a client `Screen`, a custom
`AbstractContainerMenu`, 16 UI button items with their own textures, models
and lang keys, and the class of bugs that only appear because `clicked()`
runs on the client too. Commands cost none of that and are the only thing
testable headlessly (see "Build and test").

**This deletion is safe because every view already had a command twin.** The
build order's commands-first rule is what makes the block removable at all:

| Removed view | Surviving command |
|---|---|
| Buy (market listings grid) | `/market browse`, `/market buy <id>` |
| Sell (slot + price dial) | `/market list <price>` |
| BlackMarket (Tier 3/4, deeds) | `/shop browse <tier>`, `/deed browse`, `/deed buy` |
| Leaderboard | `/top` |
| Vault (deposit slot + withdraw dial) | `/balance`, `/deposit`, `/withdraw` |

**`/deposit` and `/withdraw` are restored.** They were deliberately left
unregistered so that moving money between hand and vault could only happen at
the Shop block. With no block, that reasoning is void and they become the
only way to reach the vault at all. Their logic already exists — it lives in
`PisoShopMenu.depositFromInventory` and the withdraw branch of
`clickedVault`, which were always a separate implementation that never called
a command. That logic moves into `PisoCommands`; the menu is deleted.

**What gets deleted with the block:**

- `PisoShopBlock`, `PisoShopContainer`, `PisoShopMenu`, `PisoShopContent`,
  `PisoUiItems`, and the client's `PisoShopScreen`
- The crafting recipe (`data/pisomarket/recipe/shop.json`), the blockstate,
  the block and item models, and `textures/block/shop.png`
- All 16 `ui_*` textures, models, item definitions and lang keys
- The Shop entry in `PisoCreativeTabs`

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

**Known gap: the Lock item does not exist yet.** `ChestAccess`,
`RestrictedChestMenu`/`RestrictedChestScreen`, and `ChestAccessGuard` are all
built and enforce access levels correctly on a chest that already has one,
but nothing registers an actual `Lock` item — it is in no shop catalog and
has no texture, so there is currently no way to bind a chest in the first
place. Whole feature is unreachable in game until that registration exists.

## Death and revive

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

Fast travel between spread-out farms, as a **physical two-block structure**
rather than a command-only convenience.

- **Two blocks tall, placed from a single item** — lower and upper half, built
  like a vanilla door. Breaking either half breaks both and returns the one
  item.
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
- **Lifesteal and Smite are not implemented** — `Element` currently covers
  ignite, slow and poison only.
- **No bow or crossbow art exists.** The Blades of Majestica pack covers 36
  base items (swords, axes, picks, hoes, spears, mace) and contains no
  ranged weapon models — the table above is melee-only for that reason, not
  by design choice.
- Everything stays **iron tier**, never diamond. The effect is the reason to
  carry one, never the damage number, so player-enchanted gear still wins a
  straight fight.

**Only one is built so far: `pisomarket:frostblade`.** It was chosen as the
sample because it is the only strong candidate with no animated overlay, so
what renders in game is exactly what was previewed — anything wrong is a real
bug rather than a preview artefact. It is registered, in the COMBAT creative
tab, and reachable with `/give @s pisomarket:frostblade`.

**How an import works** (repeat per weapon): copy the model JSON from the
pack, rewrite its `textures` values from `item/x` to `pisomarket:item/x`,
copy those PNGs (and any `.png.mcmeta` for animated ones), write an item
definition in `assets/pisomarket/items/`, add a lang key, register the item.

**Compiles clean, confirmed 2026-08-30.** `./gradlew build` succeeded on the
first try — every 26.2 API name used in `com.pisomarket.combat`
(`ToolMaterial.IRON`, `Item.Properties.sword()`, `hurtEnemy`,
`igniteForSeconds`, `MobEffects.SLOWNESS`) was correct as written, no symbol
fixes needed. A headless `runServer` load also shows `(pisomarket) Piso
Market initialized` with no mixin/registration errors. **Not yet verified:
what it looks like in game.** Compiling proves the code is type-correct, not
that the model/texture render right — item rendering, model geometry and
display transforms can't be checked headlessly at all (`CLAUDE.md`'s
"test with `runServer`" advice does not apply to this weapon work). Needs a
human at the screen — via `./gradlew runClient`, or by dropping the built
jar into a real `.minecraft/mods` folder — to confirm Frostblade renders
correctly in hand, in inventory, and on the ground, and that Slowness
actually applies on hit.

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
- `/pay <player> <amount>` — vault-to-vault transfer; recipient may be offline
- `/deposit [amount]` — inventory coins → vault
- `/withdraw <amount>` — vault → inventory coins, needs inventory space
- `/leaderboard` — the wealth leaderboard snapshot (renamed from `/top`)

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
- `/warp` — return to the last waypoint interacted with; 5-minute cooldown,
  refused if damaged in the last 5 seconds
- Respawning early is a prompt on the death screen, not a command — see
  "Death and revive"

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
- **The Lock item does not exist yet.** "Lockable chests" describes it as a
  BlackMarket purchase, and `ChestAccess`, `RestrictedChestMenu` and
  `ChestAccessGuard` are all built — but no Lock item is registered, it is in
  no shop catalog, and it has no texture. The feature is unreachable in game.
- **Waypoint price** — not set (acquisition is decided: BlackMarket)
- Whether listings ever expire and return items to the seller
- Whether rent is strictly auto-renew, or a manual `/claim pay` also exists
- Whether `/pay` should exist at all, given players can hand over coins directly