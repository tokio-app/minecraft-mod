# Tokio for Minecraft

A Fabric mod for Minecraft: Java Edition 26.3 that connects your world to [Tokio](https://tokio.ialfaro.com), so your TikTok LIVE can spawn mobs, give you items and run commands while you play. Tokio's **Games** page decides what each gift, follow or chat message does.

It works in single-player worlds, and in LAN worlds you host. Cheats aren't needed.

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft 26.3, and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download `tokio-<version>.jar` from the [latest release](https://github.com/tokio-app/minecraft-mod/releases/latest), and put it in your `mods` folder.

## Connect

1. In Tokio, open **Games** and turn on **Minecraft**.
2. Open a world.
3. Type the command Tokio shows in the chat, like `/tokio connect 192.168.1.20:37892`. On the same PC as Tokio, `/tokio connect` is enough.

Type it again each time you open the world.

| Command                        | What it does                                                   |
| ------------------------------ | -------------------------------------------------------------- |
| `/tokio`                       | Says whether the world is connected to Tokio                   |
| `/tokio connect [address]`     | Connects to Tokio, at `localhost:37892` by default             |
| `/tokio disconnect`            | Disconnects from Tokio                                         |
| `/tokio meteor [count]`        | Drops 1 to 100 meteors on you, 1 by default                    |
| `/tokio laser [super [duper]]` | Fires a laser at you from the sky: plain, super or super duper |

Only the world's host can use them. The meteor and the laser also need cheats on.

## Meteors and lasers

Tokio can send them for you, cheats or not: on a Java action in the **Games** page, add a **Command** step with `tokio meteor 5`, `tokio laser`, `tokio laser super` or `tokio laser super duper`.

- **Meteor:** a burning rock falls from high in the sky to the ground under you, flying or not, and you have 2 seconds to get out of the way. It leaves a crater with a lump of magma and blackstone in it. With a count, the rest land around you, one after another.
- **Laser:** a red beam follows the ground under you for 2 seconds, then stops and blinks. A second later it fires and blasts a hole where it stands.
- **Super laser:** night falls as it aims. Once it stops, the spot lights up and lightning strikes around you. Then it blasts a wide crater and bores a tunnel about 60 blocks down along the beam, through anything but bedrock. The day comes back when it's over.
- **Super duper laser:** a super laser that first burns down through all the ground over you, so it gets you underground too.

Their explosions break blocks and start fires, like TNT. With the `tnt_explodes` game rule off, they only hurt. Guests in your LAN world see them without the mod, though only players with it see the lasers as glowing beams.

## Build

With JDK 25 as `JAVA_HOME`:

```sh
./gradlew build
```

The mod is `build/libs/tokio-<version>.jar`. `AGENTS.md` has the rest, and `PROTOCOL.md` how the mod and Tokio talk.

## License

MIT ([`LICENSE`](LICENSE)). See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. Tokio is not affiliated with TikTok or ByteDance.
