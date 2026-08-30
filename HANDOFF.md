# Session handoff — Piso Market

Context for a fresh Claude Code session on another machine. **Read `CLAUDE.md`
first** — it holds the durable design. This file holds what a transcript would
otherwise carry: what state things are in, what was tried and rejected, and
what is still undecided.

---

## Where the project actually stands

A Fabric mod for Minecraft 26.2, mod id `pisomarket`, package `com.pisomarket`.
Roughly 5,000 lines of Java: vault currency, player market, system shop,
territory claims with rent, lockable chests, anti-grief mixins, wealth
leaderboard. A working `pisomarket-1.0.0.jar` exists in `for-tlauncher/`.

**Update 2026-08-30 (Windows machine): it compiles.** `./gradlew build`
succeeded on the first try after installing JDK 25 — every guessed 26.2 API
name below turned out correct as written. `com/pisomarket/combat/*.class`
confirmed present in the built jar, and a headless `runServer` load shows
`(pisomarket) Piso Market initialized` with no mixin/registration errors.

- `ToolMaterial.IRON`
- `Item.Properties.sword(material, damage, speed)`
- `hurtEnemy(ItemStack, LivingEntity, LivingEntity)`
- `LivingEntity.igniteForSeconds(int)`
- `MobEffects.SLOWNESS` / `MobEffects.POISON`

**Not yet done: the `runClient` visual check.** Compiling proves the code is
type-correct, not that the model/texture look right in game. Still need a
human at the screen to confirm Frostblade renders correctly in hand, in
inventory, and on the ground, and that Slowness actually applies on hit —
see "Testing" below.

---

## Environment facts

- Work was done on **Kali Linux**, then continued on **Windows** (current
  machine).
- `build.gradle` now uses a **Java toolchain** (`JavaLanguageVersion.of(25)`).
  The old hardcoded `org.gradle.java.home` was deliberately deleted — it was
  wrong on two machines in a row. Do not reintroduce it.
- Needs a **JDK 25, not a JRE**. A JRE has no compiler; that is exactly what
  blocked the build on Linux, and this Windows machine's system default was
  also a JRE (1.8) — installed via `winget install
  EclipseAdoptium.Temurin.25.JDK`, landed at
  `C:\Program Files\Eclipse Adoptium\jdk-25.0.4.101-hotspot`.
- On Linux `./gradlew` was not executable — `sh gradlew` worked. On Windows,
  Gradle's own launcher also needs `JAVA_HOME` pointed at the JDK 25 install
  (the system default `java` is still 1.8) — `org.gradle.java.home` stays
  deleted per above, so export `JAVA_HOME` in the shell instead of hardcoding
  it in `gradle.properties`.
- The three Kali-Linux commits (Frostblade scaffolding) were carried over via
  a git bundle + zip, merged into `main` (fast-forward, no conflicts), and
  pushed to the `master` remote. The bundle and zip have been deleted since
  their content is now in git history.
- **The GitHub repo `scotzf/Minecraft-mod-economy` is public.**

---

## Testing

`CLAUDE.md` says to test with `runServer`, not `runClient`. **That advice does
not apply to the current task.** The weapons are 3D models — item rendering,
model geometry and display transforms cannot be checked headlessly at all.
This work needs `runClient` and a human looking at the screen.

Frostblade is in the **Combat** creative tab, or `/give @s
pisomarket:frostblade`.

---

## Open questions — none of these are decided

1. **The faucet multiplier composition.** The doc specifies 1% double, 0.5%
   triple, 0.1% quadruple, but not how they compose with the base drop
   chance. The two readings differ by about **5x in total money supply**.
   Written up in `CLAUDE.md` under "Economy: one faucet". Must be settled
   before it is built.
2. **The revive cost scaling.** Quadratic `n²` is written up, because literal
   repeated squaring puts a fourth revive at 787,320 — larger than the money
   supply will ever be. The user has not confirmed the quadratic reading.
3. **The faucet rate contradicts itself.** `HarvestFaucet.DROP_CHANCE` is
   `0.01`, but the doc says 2.5% "decided, final" and derives every shop price
   from "1 Piso = 40 potatoes". At the real 1% it is 100 potatoes per Piso, so
   all pricing is ~2.5x off its own reasoning. One of the two numbers is
   wrong.
4. **Waypoint price** — acquisition is decided (BlackMarket, never craftable);
   the number is not.
5. **The Lock item does not exist.** `ChestAccess`, `RestrictedChestMenu` and
   `ChestAccessGuard` are all built, but no Lock item is registered anywhere,
   it is in no shop catalog, and it has no texture. The whole lockable-chest
   feature is unreachable in game.
6. **Bows have no art.** The resource pack covers 36 base items — swords,
   axes, picks, hoes, spears, mace — and contains no bow or crossbow.
7. **Lifesteal and Smite are unimplemented.** `Element` covers ignite, slow
   and poison only.

---

## Tried and rejected — do not re-propose these

- **Crop contracts / a rotating order board.** Proposed as the way to make a
  farming economy work; the user dropped it. The faucet stays the only money
  source. A `DailyProgress` order-board field was removed from the data model.
- **Filipino farming-tool weapon naming** (Itak, Salakot, Bakya). The user
  wanted fantasy, not farming.
- **Hand-drawn pixel art at 16x16, then 32x32.** Rejected twice as "too
  basic". The gap was never skill — the reference art is 3D geometry with
  128x128 textures, so no flat sprite competes. Do not offer to draw sprites
  for these weapons again.
- **A daily drop cap.** Deliberately deferred, not forgotten.
- **The Shop block.** Removed in favour of commands only; `/deposit` and
  `/withdraw` were restored as a direct consequence.

---

## Working preferences observed

- Wants to **see** things, not read descriptions of them. Rendering the models
  to images and showing a contact sheet moved the conversation forward far
  faster than any amount of prose.
- Decides fast and changes direction freely. Write decisions into `CLAUDE.md`
  as they are made rather than batching.
- Pushed back, correctly, when caution was over-applied to personal use.
- `CLAUDE.md` "Developer context" applies: rusty Java, strong Python/Dart/
  Django. Write the code rather than describing it. Thorough comments on
  *why*. Plain language first, technical detail second.

---

## Suggested first moves

1. ~~Install JDK 25, build, fix mapping errors until it compiles.~~ Done
   2026-08-30 — builds clean, no fixes needed.
2. `runClient`, confirm Frostblade renders correctly in hand, in inventory,
   on the ground, and that Slowness applies on hit. **Next step — not yet
   done.**
3. If it looks right, import the remaining 14 weapons — the procedure is in
   `CLAUDE.md` under "Custom weapons — current state".
4. Then implement Lifesteal and Smite.

Art reference sheet (the user's own account, opens anywhere):
https://claude.ai/code/artifact/0a16cfe1-a6fc-4aba-8436-a0fb39174ded
