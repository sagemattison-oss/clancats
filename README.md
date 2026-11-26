# Clan Cats (Fabric, Minecraft 1.21.1)

Become a cat and live in a clan-based wild-cat world.

## Build
Requires JDK 21. Then:

    gradle wrapper        # once, if you don't have the wrapper jar
    ./gradlew build       # jar ends up in build/libs/
    ./gradlew runClient   # test in a dev client

Or open the folder in IntelliJ IDEA and import as a Gradle project. Drop the jar in `mods/`
alongside Fabric API. Versions are in `gradle.properties` (see https://fabricmc.net/develop).

## Commands
    /cat become | revert | status
    /cat meow | purr | hiss
    /cat clan list | join <ash|brook|bramble|crest> | leave | camp | setcamp
    /cat setprey <players> <n>        (operators; handy for testing ranks)

## Gameplay
- Cat form: small body and hitbox, rendered as a real cat model, faster, higher jumps,
  hops full blocks, no fall damage from heights up to ~11 blocks, night vision in dim light.
- Hold jump against logs / leaves / fences to climb. Sneak while moving to stalk, sneak while still to sit.
- Paws can only break soft blocks; no block placing; only meat and fish can be eaten.
- Hunt rabbits, chickens, bats, fish, silverfish, endermites: restores hunger and earns prey.
- Ranks by prey caught: Kit 0, Apprentice 10, Warrior 40, Deputy 100, Leader 200
  (each rank adds claw damage and health). Leaders get nine lives.
- Clans show up in name tags and chat via per-cat scoreboard teams. Deputies/Leaders/ops set camps.

## Layout
- `ClanCatsMod`      : registers persistent data (Fabric Data Attachment API)
- `CatAbilities`     : attribute modifiers, name-tag teams, night vision
- `CatEvents`        : restrictions, hunting, ranks, nine lives
- `CatCommands`      : `/cat ...`
- `client/` + mixin  : swaps the player model for a mirrored cat entity; tree climbing

## Ideas to extend
Custom prey/mob goals (foxes, badgers), a synced fur-colour picker, camp structures with
worldgen, a keybind for meowing (needs a networking payload), clan-vs-clan territory scent marking.
