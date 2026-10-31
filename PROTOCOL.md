# Protocol

How the mod and Tokio talk. Tokio's side is `MinecraftConnection` (`src/main/services/games/minecraft.ts` in `tokio-app/tokio`), which also serves Minecraft Bedrock Edition over the same socket. Change both sides together.

## Connecting

- While **Minecraft** is on in Tokio's Games page, Tokio listens for WebSockets on port **37892** (`MINECRAFT_PORT`), on every network interface.
- The player types `/tokio connect [address]` in the world. The mod opens `ws://<host>:<port>/`, default `localhost:37892`.
- The mod offers the subprotocol **`tokio.java.v1`** (`MINECRAFT_JAVA_PROTOCOL` in Tokio). That is how Tokio tells the mod from Bedrock: it selects the subprotocol and skips Bedrock's encryption handshake.
- The mod sends no `Origin` header. Tokio refuses upgrades that carry one, so web pages can't take the socket.
- There is no handshake. Once the socket opens, Tokio counts the game as connected (Java Edition) and can send commands.
- Tokio keeps one game at a time: a new connection, Java or Bedrock, replaces the old one.

## Frames

Text frames of JSON, both ways. Either side ignores frames it doesn't understand.

### Tokio → mod: run a command

```json
{
  "header": {
    "version": 1,
    "requestId": "6c0f3a5e-…",
    "messageType": "commandRequest",
    "messagePurpose": "commandRequest"
  },
  "body": {
    "version": 42,
    "commandLine": "summon minecraft:zombie ~ ~ ~ {CustomName:\"Lucia\"}",
    "origin": { "type": "player" }
  }
}
```

It's the same frame Tokio sends Bedrock. The mod reads `header.requestId` and `body.commandLine` and ignores the rest. `commandLine` has no leading slash.

### Mod → Tokio: how it went

One answer per request, in any order:

```json
{
  "header": { "requestId": "6c0f3a5e-…", "messagePurpose": "commandResponse" },
  "body": { "statusCode": 0, "statusMessage": "Summoned new Zombie" }
}
```

- `statusCode` is `0` when the command ran and `-1` when it failed.
- `statusMessage` is the game's last feedback line on success, or why it failed. It's shown in Tokio as is, so keep it short and plain.

A `commandLine` that starts with `enableencryption` comes from a Tokio that doesn't know this protocol: it is Bedrock's handshake. The mod doesn't run it, and answers it with `statusCode` `-1` and "Update Tokio to use the Java mod."

## Running commands

- Commands run in the world's server thread, in the order they come, as the world's host player, at their position (`~` and `@s` are the player).
- They run with gamemaster permission (level 2, as command blocks), whatever the world's **Allow Commands**, so no cheats are needed.
- Their feedback isn't shown in chat or sent to operators. It only goes back to Tokio.
- A command that comes while the host isn't in the world fails with "The player isn't in the world yet.", and one that comes as the world closes with "The world is closing."
- Besides the game's commands, Tokio's command steps can send the mod's own: `tokio meteor [count]` (1 to 100) and `tokio laser [super [duper]]`. They need gamemaster permission, which Tokio's commands have. They answer at once ("A meteor is falling.", "A laser is aiming at you."), and the hazard plays out over the next few seconds.
- `/time set` that fails because the clock is already at that time (26.3 refuses it, e.g. night set twice in one tick by an action on every gift) counts as run: the world is as Tokio asked. The mod tells that error by its translation key, so it works in any language.

## Limits

- Tokio keeps at most 50 commands in flight and fails a command that gets no answer in 10 seconds. The mod answers every request.
- Viewer text reaches commands cleaned by Tokio (no quotes, backslashes, brackets, braces, `§` or `@`), so it can't break out of a name.

## Closing

| Code | Who | When |
| ---- | --- | ---- |
| 1000 | mod | `/tokio disconnect`, or the world closes |
| 1001 | Tokio | **Minecraft** was turned off in Tokio ("Minecraft was turned off in Tokio.") |
| 4000 | Tokio | another game connected to Tokio ("Another game connected to Tokio.") |

The mod tells the player when the connection ends, with Tokio's reason when there is one. It never reconnects by itself: the player types `/tokio connect` again.
