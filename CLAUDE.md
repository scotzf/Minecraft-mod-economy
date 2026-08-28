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
eco give <player> 1000
eco total
```

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

**Sinks:**
- System shop purchases (see catalog below)
- Territory rent (recurring, scales with claim size)

The system **sells only** — it never buys items back. No buy/sell spread
means no arbitrage loop.

## System shop catalog

**Implementation status:** Tiers 3 and 4 are real and purchasable
(`ShopCatalog.java`, `/shop browse`, `/shop buy`, and the shop block's
BlackMarket grid). Tiers 1 and 2 are **not built** — they need systems that
don't exist yet (a chat-color/title/prefix system, particle-trail tick
handling, `/sethome`, per-player market-slot limits). Weekly rotation isn't
built either — it needs the day-tracking system the daily drop cap also
depends on, which hasn't been built. Stock right now is a fixed pool that
depletes and never refills. Prices use a starting multiplier of 5 (still
"tune after the systems run," not final).

Hard rule: never stock anything players can produce. Every such item stocked
is a customer taken from the player market.

**Tier 1 — cosmetic** (backbone; infinite supply, price can never be "wrong")
chat name colors, titles/prefixes, custom banner patterns, non-vanilla dyed
leather armor, particle trails, player-head decorations, custom join messages

**Tier 2 — convenience** (recurring revenue; price climbs per additional unit)
extra `/sethome` slots, extra market listing slots, extra chunk allowance,
personal warp points

**Tier 3 — consumables** (destroyed on use, never accumulate)
Bottles o' Enchanting, name tags, single-enchantment books, bulk firework
rockets, saddles, horse armor

**Tier 4 — prestige** (big-ticket, mops up hoarded balances)
elytra, enchanted golden apple, netherite upgrade smithing template, Heart of
the Sea, echo shard

Tiers 3 and 4 rotate weekly with limited quantities.

Price as ratios, then scale by one multiplier:
`cosmetic 1 : consumable 2 : convenience 5 : prestige 200`

## Shop UI

A craftable block, not a command-only interface — placed in the world,
right-click to open a chest-style menu (`PisoShopMenu`, a custom
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

Existing non-negotiable rules, unchanged:

- **Expiry removes protection, never blocks.** Deleting builds over unpaid
  rent loses players permanently.
- **Rent freezes while offline.** Charge per *day logged in*, not per calendar
  day. A player away two weeks must not return to an unprotected base.
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