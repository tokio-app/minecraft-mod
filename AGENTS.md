# AGENTS.md

## Project

A Fabric mod for Minecraft: Java Edition **26.3** that connects the world the player opens to Tokio, the Electron app at `../tokio` (`tokio-app/tokio`). Tokio's Games page runs Minecraft commands when TikTok LIVE events fire, and this mod is how they reach Java Edition. Bedrock Edition connects to Tokio by itself; Java can't take outside commands without a mod.

- **Single-player only:** worlds the player opens, and LAN worlds they host. `fabric.mod.json` says `"environment": "client"`, so the mod never loads on a dedicated server. The code runs in the integrated server.
- **Connecting:** the world's host types `/tokio connect [address]` (default `localhost:37892`), as Bedrock's `/connect`. There is no auto-connect or retry.
- **Commands:** they run as the host player, at their position, with gamemaster permission (level 2), so no cheats are needed. Their feedback goes back to Tokio, not to chat.
- `PROTOCOL.md` is the contract with Tokio. Change it, and Tokio's `MinecraftConnection`, together.

Repository `tokio-app/minecraft-mod`. Players download the jar from its GitHub releases. MIT.

Stack: Java 25, Gradle 9.8.2 (wrapper), Fabric Loom 1.19 (`net.fabricmc.fabric-loom`), Fabric Loader 0.20.1, Fabric API 0.165.0+26.3. 26.x ships unobfuscated, so there are no mappings: the code uses Mojang's own names. The WebSocket client is the JDK's `java.net.http`, and JSON is the game's Gson, so the mod has no dependency of its own. The mixins (`tokio.mixins.json`) need no refmap, for the same reason.

## Commands

Gradle's daemon needs JDK 25 (`gradle/gradle-daemon-jvm.properties`), and it can't find the Microsoft build by itself here. Set `JAVA_HOME` for each command, e.g. in Git Bash:

```sh
export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.4.101-hotspot"
./gradlew build        # compile, unit tests, server game tests; the jar in build/libs/
./gradlew test         # unit tests only
./gradlew runGameTest  # server game tests only
./gradlew runClientGameTest  # client game tests, in a game window; build doesn't run them
./gradlew runClient    # the dev client with the mod, as the offline player "Player"
./gradlew genSources   # decompile Minecraft, to read its classes (Mojang's names)
```

Verify changes with `./gradlew build`. Check a change to commands or the connection in the dev client too, against Tokio (see Testing).

## Agent models

- **Planning:** Opus 5.5 at high effort. Be 90–95% confident in the plan before implementing or handing off; otherwise keep reading code or ask the user.
- **Implementation subagents:** Opus 5.5 at high effort.

## Layout

```
src/main/java/com/ialfaro/tokio/
  Tokio.java            the entrypoint (ModInitializer)
  connection/           the WebSocket to Tokio, no Minecraft classes:
                        TokioClient, Commands, CommandResult, ConnectionListener
  game/                 the Minecraft side: running commands, /tokio, chat lines, the hazards, the morphs
  client/               this client only: the lasers' beams drawn as beacon beams, morphed players drawn as their mob
  mixin/                the morphs' patches to the player (client/ for the first-person arm), to the mobs Tokio spawns,
                        and the host-only rule's: blasts, lightning, evoker fangs and projectiles
src/main/resources/     fabric.mod.json, tokio.mixins.json, assets/tokio/icon.png
src/test/java/          JUnit tests (connection/ against a fake Tokio)
src/gametest/           server and client game tests and their fabric.mod.json (mod id tokio-test)
scripts/release-notes.sh  a release's notes, from the Conventional Commits since the last tag
.github/workflows/release.yml  builds, tests and publishes the jar for a v<version> tag
```

## Architecture

- **Threads:**
  - `TokioClient` gets frames on the JDK's HTTP client threads.
  - `Commands.run` hops to the server thread (`MinecraftServer.execute`), the only place that touches the world, and completes there.
  - Answers go out one at a time: the JDK's WebSocket refuses a send while another is pending.
- **The world:**
  - The connection belongs to the world. Closing it disconnects.
  - `/tokio` is only for the singleplayer owner, so a LAN guest can't point the host's world at their own socket and run commands with the host's permission.
- **Only the host** (`game/Host.java`, the user's call): what Tokio's commands make, and the hazards, reach the world's host alone. Another player in a LAN world isn't targeted, hurt or pushed by them, and never gets the hazards. What they leave in the shared world is everyone's: broken blocks, fire, lava, traps, anvils, the mobs themselves.
  - `Host.isOther(entity)`: a player other than the singleplayer owner. False in a world without an owner, as in game tests, so nothing changes there.
  - `Host.tokios(entity)`: tagged (`Spawned`), or its owner is (`TraceableEntity`): an arrow a Tokio skeleton shot, fangs a Tokio evoker cast, a Tokio ghast's fireball. An explosion is Tokio's when its direct or indirect source is, or it's one of the mod's own (`Host.blast`, around `Hazard.blast`).
  - `ServerExplosionMixin` skips other players in Tokio's blasts (`ignoreExplosion`): no damage, no push. `ServerLevelMixin` sends the mod's own blasts' particles, sound and push to the host alone.
  - `LightningBoltMixin` (`thunderHit`), `EvokerFangsMixin` (`dealDamageTo`) and `ProjectileMixin` (`canHitEntity`, which every projectile's own override calls) spare other players from Tokio's.
  - Targets: see Mobs Tokio spawns.
- **Hazards** (`/tokio meteor [count]`, `/tokio laser [super [duper]]`):
  - Each is a `Hazard`, a block display that runs itself in `tick()` on the server, aimed at its host. Only the host's client gets it (`broadcastToPlayer`), and its particles and sounds go to the host alone (`Hazard.particles`, `Hazard.sound`). It's all vanilla still: don't add registry content (entities, blocks, particles), or a vanilla client couldn't join.
  - Its particles follow the host's Particles setting (`Host.particlesShown`, from the client information the server keeps): all, about two in three on Decreased as the game drops its own, none on Minimal. They're sent with `overrideLimiter`, so a meteor's trail shows from 512 blocks, and the client then draws every one it gets.
  - `shouldBeSaved()` is false, so a world closed or a chunk unloaded midway keeps no stray meteor or beam.
  - They aim at the ground or water under the player (`Hazard.groundUnder`), so one sent while they fly still lands on the ground. On the ground, that's their feet.
  - Clients with the mod draw a laser's beams, the block displays named `Laser.BEAM`, as beacon beams: `LaserRenderer` (the `client` entrypoint registers it) replaces the block display renderer and draws every other display as the game does. It also slides bright pulses down the beams and draws the thin aim beam wider. Glass plates for rings and an impact disc were tried and dropped: up close they read as floating panes.
  - Their blasts break blocks and start fires like TNT, or only hurt while `tnt_explodes` is off. They hurt and push the host alone (Only the host).
  - They need gamemaster permission on top of being the host. Tokio's commands have it; a world without cheats can't use them as free TNT.
  - A super laser brings the night to its host's screen through `Night`, which never changes the world's clock: other players, mob spawning and the time the world saves keep the world's hour.
    - It sends the host alone the overworld's clock, `Night.ahead()` ticks ahead (`ClientboundSetTimePacket`), and their client runs it on from there. It runs to midnight when the first super laser starts and on to the world's own hour after the last one ends, on `END_SERVER_TICK`: a whole day or so ahead, then the world's clock itself, so the days count the same.
    - A respawn, a trip to another dimension or a clock someone changed sends the host the world's clock, so the night goes again every second while it holds.
    - Each super laser holds the night every tick it ticks (`Night.hold`), not by counting lasers in and out: one left behind outside the area the world simulates (the player respawned or flew far) stops ticking without ending, and a count kept it night for good, so later super lasers brought none.
  - A super laser lights the spot with light blocks up along the beam, never at the spot (they'd stop the blasts), and takes them away when it goes. It then bores a tunnel 64 blocks on along the beam, since blasts barely dent stone: it removes what breaks without drops, but what holds items drops it, and it stops at the world's bottom. A super duper laser first burns down along the beam through all the ground over the player (`cover`), so its timeline starts later. Its lightning is the host's alone too: a client draws a bolt's flash and plays its thunder itself. `SERVER_STOPPING` ends the super lasers before the world saves, so it keeps none of their light.
- **Morphs** (`/tokio morph <mob> [seconds]`, `/tokio unmorph`, `game/Morph.java`):
  - The look is a Fabric data attachment, `Morph.LOOK` (`tokio:morph`, the mob's `EntityType`), synced to every client with the mod and never saved. `Tokio.onInitialize` touches it first: a client reports the synced attachments it knows as it joins, before anything else loads `Morph`. Vanilla clients report none, so LAN guests without the mod still join, and see a player.
  - The stats come from a mob of that kind that's made but never added to the world (`Morph.instance`, one per kind per side). Slimes and magma cubes are size 4, sulfur cubes 2, and the horses whose health the game rolls at spawn get 22. They're transient modifiers with the id `tokio:morph`, so nothing is saved:
    - the mob's value replaces the player's, for each stat the mob has;
    - speed is the mob's walking push (speed² ÷ 0.98), kept between 0.05 and 0.15; `aquatic` mobs get half a walk, flyers the player's own;
    - reach and `camera_distance` grow by height ÷ 1.8;
    - health keeps its share, both ways.
  - Flyers fly at once (`mayfly` and `flying`).
    - Creative flight lands a client that's on the ground, so a jump's push goes to the client first.
    - They fly at creative's speed. Ghasts, happy ghasts and blazes fly at half of it; phantoms, vexes and the dragon at one and a half.
    - That speed is saved with the player, so `Morph.joined` puts back one a crash left.
    - One that ends a morph in the air gets 10 seconds of Slow Falling.
  - The dragon breaks what its body flies into, as `EnderDragon.checkWalls` does for the real one.
    - It reaches one block around its body.
    - It skips `#dragon_immune` and `#dragon_transparent`, and breaks nothing while `mobGriefing` is off.
    - It leaves the ground under it unless it flies down; otherwise it would sink through the world.
  - Chickens and fireproof mobs get hidden effects (no icon) that last 3 seconds and are renewed, so a crash can't leave one behind. Mobs that breathe underwater give `oxygen_bonus`. `Morph.tick` ports `Mob.isSunBurnTick` and `burnUndead`, the water sensitivity and `WaterAnimal.handleAirSupply` to the player, and plays the mob's idle sounds at its own rhythm.
  - The mixins work on both sides: the host's client and its integrated server share the JVM and the synced look.
    - `AvatarMixin` gives a morphed player the mob's dimensions. Players move on their own client, so the size must hold there too.
    - The game never nudges a growing player out of blocks, so `Morph` does: with the game's own search for room (`fudgePositionAfterSizeChange`) up to 4×4, where the game stops for mobs too. Past that, the search merges every block shape around and held the world for minutes on a dragon beside terrain, so a bigger mob rises until it fits, at most its own height.
    - `LivingEntityMixin` lets spiders climb.
    - `PlayerMixin` swaps the hurt, death and step sounds. The steps are played by the server's mob, for everyone, the player included.
    - `client/FirstPersonHandsAndItemsRendererMixin` hides the bare arm; held items still show.
    - `EntityInvoker` and `LivingEntityInvoker` reach the mob's protected sound methods.
  - `MorphLooks` (a `client` entrypoint) draws them. On Fabric's `LevelExtractionEvents.END_EXTRACTION`, it swaps each morphed player's `AvatarRenderState` for one extracted from a client-only copy of the mob.
    - The copy stands where the player does, holds their items, and takes their animation from the avatar state.
    - Its own tick runs each client tick, once it's moved to the player. So its wings flap, the dragon growls and turns by the flight history it records, and the Warden's heart beats.
    - An exception while extracting crashes the game, so a kind that throws is logged once and drawn as a player from then on. A kind whose tick throws is logged once and kept still.
    - Don't hook `EntityRenderDispatcher.extractEntity` itself: the local player's own state must stay an `AvatarRenderState`.
  - A morph ends on `/tokio unmorph`, when its seconds are up, on death or leaving the End (`AFTER_RESPAWN`), and on `SERVER_STOPPING`, before the world saves.
- **Mobs Tokio spawns** (`game/Spawned.java`):
  - `CommandRunner` runs each command inside `Spawned.during`. `ServerLevelMixin` tells it of every entity `ServerLevel.addEntity` takes meanwhile, passengers included. Entities loaded with their chunk don't go through it, so they're never Tokio's. Fabric's `ENTITY_LOAD` fires for those too, and the game test server loads the mixins but never runs `Tokio.onInitialize`.
  - After the command, each entity still in the world gets the scoreboard tag `tokio` (`Spawned.TAG`), saved with it, so it still holds after the world reopens.
  - `MobMixin` cancels `Mob.burnUndead` for tagged mobs, so they never burn in daylight and their helmets aren't worn down. Not fire resistance: fire and lava still burn them.
  - Hostile means the game's `Enemy` marker: every `Monster` (endermen, zombified piglins, piglins and the Warden included), slimes, magma cubes, ghasts, phantoms, hoglins, shulkers and the dragon. They target the host (`setTarget`).
    - `NeutralMob` monsters (endermen, zombified piglins) also get the host as their anger target, and the timer, or their AI drops the target.
    - Brain-driven ones (piglins, brutes, hoglins, zoglins, the Breeze, the Creaking) get the `ATTACK_TARGET` and `ANGRY_AT` memories; a brain without a memory skips it. `ANGRY_AT` has no expiry, so piglins stay on the host in gold.
    - The Warden gets anger at the host as a hit gives (`increaseAngerAt`), then `setAttackTarget`.
    - `SpiderAttackGoalMixin` keeps tagged spiders attacking in daylight, where the game drops the target about 1 tick in 100.
    - The dragon's phases ignore its target, so it flies as it would.
  - Wolves, bees, iron golems, llamas, polar bears, goats and other neutral mobs aren't `Enemy`, so they're left alone.
  - No tagged mob ever goes after a player but the host. `MobMixin` makes `Mob.canAttack` false for other players, and every target goes through it: the goals' searches (`TargetingConditions`), the brains' sensors, and `setTarget` and `getTarget` (`asValidTarget`), brains' targets included. `WardenMixin` does the same for the Warden's anger (`canTargetEntity`), which picks its own.
- **Text for the player:**
  - Chat lines are short, plain sentences: "Connected to Tokio.", "Disconnected from Tokio. Another game connected to Tokio."
  - No prefixes like `[Tokio]`, no colors beyond the game's own for errors.

## Testing

- **Unit tests** (`src/test`): the connection against Java-WebSocket standing in for Tokio. No Minecraft classes, so they're fast.
- **Server game tests** (`src/gametest`):
  - They run Tokio's command forms (`PROTOCOL.md` and Tokio's `gameCommands`) through the game's command dispatcher with mock players.
  - They need `eula = true` in `build.gradle`, which agrees to the Minecraft EULA. The user agreed to it for these tests.
  - `HazardsTest` starts `Meteor.fall` and `Laser.fire` itself: game tests have no world host, so `/tokio` isn't reachable there. Its tests pad their area so the blasts can't reach other tests, and force-load the chunks a meteor starts in, since a test loads only its own. A test whose player is above its structure needs `skyAccess`: otherwise the barrier ceiling over the structure is the ground under them.
  - The mod is client-only, so the game test server never runs `Tokio.onInitialize` or loads its mixins.
    - A test ticks what it registers itself, as the super laser test ticks `Night` and `MorphTest` ticks `Morph`.
    - The test mod loads `tokio.mixins.json` itself (`"environment": "server"`).
  - The super laser tests run in their own batch, `tokio-test:noon` (`src/gametest/resources/data/tokio-test/test_environment/`), which starts at noon, since other tests set the time. They share its one night.
  - The test of `Night` alone runs in `tokio-test:noon_alone`, as any super laser beside it would hold the night. The first batch can start after its noon has run on, so that test compares the day with the hour it started at.
  - The sun tests run in `tokio-test:noon_morph`, out of the super lasers' night: the morph's, and `SpawnedTest`'s zombie that doesn't burn and spider that keeps attacking. The zombie test checks Tokio's zombie each tick for 200 ticks, beside one the test spawns that must catch fire, and clears the weather first.
  - `SpawnedTest` runs Tokio's summons through `CommandRunner` and checks the tag and each family's target; `CommandRunnerTest` has the chicken jockey's two forms, and the hazards Tokio builds from vanilla commands: the launch's clearing, the traps, the lava lake, the hole and the inventory clear. Each runs twice, so a second run over the first's blocks still succeeds.
  - `MockPlayer` has no connection, so it skips what a server player sends through one: its effects, the invulnerability check, combat and damage notices, and death. Tokio's hostile mobs attack it in every test that summons one, and it never dies. `/clear` updates the crafting grid through the connection, so its test uses the game's own `makeMockServerPlayerInLevel` and removes that player after.
  - `HostOnlyTest` checks Only the host with a host and another player beside them. Game tests have no host, so each test makes one (`setSingleplayerProfile`) for the tick it runs in, checks it all within that tick and removes its players, so no other test sees them.
    - Its players are in the world, as blasts, bolts and fangs find their victims there; mock players aren't. The game's `makeMockServerPlayerInLevel` is creative, so they're its own: survival, 200 health so a blast at their feet doesn't kill them, and no spawn invulnerability.
    - It ticks the bolt, the fangs and a meteor itself, and sets TNT off with `explode`. Each check has a control the world made, which does reach the other player.
- **Client game tests** (`src/gametest`, the `fabric-client-gametest` entrypoint), `./gradlew runClientGameTest`:
  - `MorphClientTest` turns the host of a real singleplayer world into mobs through `CommandRunner` and takes screenshots, in third person and first person. They land in `build/run/clientGameTest/screenshots/`; look at them after a change to how morphs are drawn.
  - It also checks that every kind of mob is drawn as itself (`MorphLooks.drawnAs`).
  - `HostOnlyClientTest` fires a super laser at the host: night on their client's clock while the world's stays at noon, then the world's own clock again. Then it checks a meteor shower's particles on Minimal and on All, set by saving the options, which tells the server as the options screen does. Its screenshot of the night lands beside the morphs'.
  - They open a game window, so `build` and the Release workflow don't run them.
- **Against Tokio:** run Tokio's built app with **Minecraft** on (its `drive-app` skill) and `./gradlew runClient`. Open a world, type `/tokio connect`, and use **Test** on the Games page.

## Releasing

Use the `release` skill (`.claude/skills/release/SKILL.md`), only when the user asks.

1. Change `version` in `gradle.properties`, and commit it as `chore(release): <version>`.
2. Tag it `v<version>` (lightweight) and push the commit and the tag. The user does the pushing.
3. The Release workflow fails unless the tag matches `version`. It runs `./gradlew build` (unit and game tests) on JDK 25 and publishes a GitHub release, "Tokio for Minecraft <version>", with `tokio-<version>.jar` (not the `-sources` jar).

The notes are `scripts/release-notes.sh <tag>`: the commits since the previous `v*` tag, grouped like Tokio's (breaking, features, fixes, performance, other), without merges and `chore(release)` commits. Without a tag it prints the next release's. A rerun only replaces the jar, so notes edited on GitHub are kept.

## Conventions

- Tabs, as Fabric's template (`.editorconfig`). LF line endings, CRLF for `.bat`.
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`). Never add Co-Authored-By or "Generated with" lines.
- Don't push or publish; the user does.
- Keep `PROTOCOL.md`, the README's commands and Tokio's docs (`../tokio-web`, the Games page docs) in step with the code.

## Licensing

- The mod is MIT. Its jar holds only this project's code, resources and `LICENSE`. `THIRD_PARTY_NOTICES.md` lists what the build and tests use; keep it current when dependencies change.
- Fabric Loader and Fabric API (Apache-2.0) are installed by players, not bundled.
- Ask before adding any dependency, and never a copyleft one (GPL, AGPL, LGPL, MPL).
- Don't copy code from other mods.
- Keep "Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft." and the TikTok notice in the README. Don't put "Minecraft" in the mod's name.
