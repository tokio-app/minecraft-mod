# AGENTS.md

## Project

A Fabric mod for Minecraft: Java Edition **26.3** that connects the world the player opens to Tokio, the Electron app at `../tokio` (`tokio-app/tokio`). Tokio's Games page runs Minecraft commands when TikTok LIVE events fire, and this mod is how they reach Java Edition. Bedrock Edition connects to Tokio by itself; Java can't take outside commands without a mod.

- **Single-player only:** worlds the player opens, and LAN worlds they host. `fabric.mod.json` says `"environment": "client"`, so the mod never loads on a dedicated server. The code runs in the integrated server.
- **Connecting:** the world's host types `/tokio connect [address]` (default `localhost:37892`), as Bedrock's `/connect`. There is no auto-connect or retry.
- **Commands:** they run as the host player, at their position, with gamemaster permission (level 2), so no cheats are needed. Their feedback goes back to Tokio, not to chat.
- `PROTOCOL.md` is the contract with Tokio. Change it, and Tokio's `MinecraftConnection`, together.

Repository `tokio-app/minecraft-mod`. Players download the jar from its GitHub releases. MIT.

Stack: Java 25, Gradle 9.8.2 (wrapper), Fabric Loom 1.19 (`net.fabricmc.fabric-loom`), Fabric Loader 0.20.1, Fabric API 0.165.0+26.3. 26.x ships unobfuscated, so there are no mappings: the code uses Mojang's own names. The WebSocket client is the JDK's `java.net.http`, and JSON is the game's Gson, so the mod has no dependency of its own.

## Commands

Gradle's daemon needs JDK 25 (`gradle/gradle-daemon-jvm.properties`), and it can't find the Microsoft build by itself here. Set `JAVA_HOME` for each command, e.g. in Git Bash:

```sh
export JAVA_HOME="/c/Program Files/Microsoft/jdk-25.0.4.101-hotspot"
./gradlew build        # compile, unit tests, server game tests; the jar in build/libs/
./gradlew test         # unit tests only
./gradlew runGameTest  # server game tests only
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
  game/                 the Minecraft side: running commands, /tokio, chat lines, the hazards
  client/               this client only: the lasers' beams drawn as beacon beams
src/main/resources/     fabric.mod.json, assets/tokio/icon.png
src/test/java/          JUnit tests (connection/ against a fake Tokio)
src/gametest/           server game tests and their fabric.mod.json (mod id tokio-test)
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
- **Hazards** (`/tokio meteor [count]`, `/tokio laser [super [duper]]`):
  - Each is a `Hazard`, a block display that runs itself in `tick()` on the server. Clients get a plain block display, particles and sounds, all vanilla, so LAN guests without the mod see them. Don't add registry content (entities, blocks, particles): a vanilla client couldn't join.
  - `shouldBeSaved()` is false, so a world closed or a chunk unloaded midway keeps no stray meteor or beam.
  - Clients with the mod draw a laser's beams, the block displays named `Laser.BEAM`, as beacon beams: `LaserRenderer` (the `client` entrypoint registers it) replaces the block display renderer and draws every other display as the game does. It also slides bright pulses down the beams and draws the thin aim beam wider. Glass plates for rings and an impact disc were tried and dropped: up close they read as floating panes.
  - Their blasts break blocks and start fires like TNT, or only hurt while `tnt_explodes` is off.
  - They need gamemaster permission on top of being the host. Tokio's commands have it; a world without cheats can't use them as free TNT.
  - A super laser brings the night through `Night`, which runs the overworld's clock to midnight when the first one starts and on to the hour it was after the last one ends, on `END_SERVER_TICK`. It lights the spot with light blocks up along the beam, never at the spot (they'd stop the blasts), and takes them away when it goes. It then bores a tunnel 64 blocks on along the beam, since blasts barely dent stone: it removes what breaks without drops, but what holds items drops it, and it stops at the world's bottom. A super duper laser first burns down along the beam through all the ground over the player (`cover`), so its timeline starts later. `SERVER_STOPPING` ends the super lasers and brings the day back before the world saves.
- **Text for the player:**
  - Chat lines are short, plain sentences: "Connected to Tokio.", "Disconnected from Tokio. Another game connected to Tokio."
  - No prefixes like `[Tokio]`, no colors beyond the game's own for errors.

## Testing

- **Unit tests** (`src/test`): the connection against Java-WebSocket standing in for Tokio. No Minecraft classes, so they're fast.
- **Server game tests** (`src/gametest`):
  - They run Tokio's command forms (`PROTOCOL.md` and Tokio's `gameCommands`) through the game's command dispatcher with mock players.
  - They need `eula = true` in `build.gradle`, which agrees to the Minecraft EULA. The user agreed to it for these tests.
  - `HazardsTest` starts `Meteor.fall` and `Laser.fire` itself: game tests have no world host, so `/tokio` isn't reachable there. Its tests pad their area so the blasts can't reach other tests, and force-load the chunks a meteor starts in, since a test loads only its own.
  - The mod is client-only, so the game test server never runs `Tokio.onInitialize`: a test ticks what it registers itself, as the super laser test ticks `Night`. The super laser tests run in their own batch, `tokio-test:noon` (`src/gametest/resources/data/tokio-test/test_environment/`), which starts at noon, since other tests set the time. They share its one night.
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
