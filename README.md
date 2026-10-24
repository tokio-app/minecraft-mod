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

| Command                     | What it does                                          |
| --------------------------- | ----------------------------------------------------- |
| `/tokio`                    | Says whether the world is connected to Tokio          |
| `/tokio connect [address]`  | Connects to Tokio, at `localhost:37892` by default    |
| `/tokio disconnect`         | Disconnects from Tokio                                |

Only the world's host can use them.

## Build

With JDK 25 as `JAVA_HOME`:

```sh
./gradlew build
```

The mod is `build/libs/tokio-<version>.jar`. `AGENTS.md` has the rest, and `PROTOCOL.md` how the mod and Tokio talk.

## License

MIT ([`LICENSE`](LICENSE)). See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft. Tokio is not affiliated with TikTok or ByteDance.
