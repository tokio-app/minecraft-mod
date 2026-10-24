# Third-party notices

The mod is MIT ([`LICENSE`](LICENSE)). Its jar holds only this project's code, resources and license: nothing below is bundled in it.

| What | License | How it's used |
| ---- | ------- | ------------- |
| [Fabric Loader](https://github.com/FabricMC/fabric-loader) | Apache-2.0 | Runs the mod. Players install it. |
| [Fabric API](https://github.com/FabricMC/fabric) | Apache-2.0 | Commands, server events and game tests. Players install it. |
| [Gradle wrapper](https://github.com/gradle/gradle) (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) | Apache-2.0 | Builds the mod. Not in the jar. |
| [Fabric example mod](https://github.com/FabricMC/fabric-example-mod), branch `26.3` | CC0-1.0 | The build files started from it. |
| [JUnit](https://github.com/junit-team/junit-framework), [Fabric Loader JUnit](https://github.com/FabricMC/fabric-loader) | EPL-2.0, Apache-2.0 | Tests only. |
| [Java-WebSocket](https://github.com/TooTallNate/Java-WebSocket) | MIT | A stand-in for Tokio in the tests only. |

Minecraft is Mojang's. The mod contains none of its code; it calls the game's classes at runtime, under the [Minecraft EULA](https://aka.ms/MinecraftEULA) and [usage guidelines](https://www.minecraft.net/usage-guidelines). The game tests run a Minecraft server, which needs the EULA accepted (`eula = true` in `build.gradle`).
