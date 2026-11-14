# Dedicated WorldGuard Duel Arenas

BlazingPractice supports separate, automatically assigned arena pools for each duel ladder.
For example, a Sword duel only uses arenas registered for `sword`; an Axe duel only uses
arenas registered for `axe`.

WorldGuard regions define the arena boundaries. BlazingPractice stores two player spawns
inside each region and preserves the existing opposite-side teleport order:
- Player 1 -> `spawn1`
- Player 2 -> `spawn2`

## Requirements

For Paper 1.21.11, install WorldEdit and a WorldGuard build compatible with 1.21.11.
WorldGuard is a soft dependency: BlazingPractice can start without it, but arena creation,
validation and matchmaking are blocked until WorldGuard is enabled.

The Maven build uses WorldEdit Bukkit 7.4.6 and WorldGuard API 7.0.17 as `provided`
dependencies. Neither plugin is bundled inside the BlazingPractice JAR.

## One-time setup

1. Install WorldEdit and WorldGuard on the Paper server.
2. Restart the server and check that both plugins enabled successfully.
3. Go to the shared Practice/PvP world (the same world used for Practice and FFA).
4. Use WorldEdit to select each arena's corners, then create its WorldGuard region.
   Example: `//wand`, select the two corners, then `/rg define sword_01`.
5. Ensure each region covers the complete fight area and both player spawn positions.
6. Register the region in BlazingPractice, configure both spawns, and enable it.

Do not create duel arenas in the hub or Survival world.

## Example: Sword arena

Create the WorldGuard region first:

```text
//wand
# select the first and second corners in-game
/rg define sword_01
```

Then stand at the first player's spawn position and run:

```text
/blazingpractice:arena create sword_01 sword sword_01
/blazingpractice:arena setspawn1 sword_01
```

Stand at the opposite side and run:

```text
/blazingpractice:arena setspawn2 sword_01
/blazingpractice:arena enable sword_01
/blazingpractice:arena info sword_01
```

Repeat for `sword_02`, `sword_03`, and so on. The arena ID and WorldGuard region
name may match, but they do not have to.

## Example: Axe and Crystal

Create separate regions and register them under their own ladder IDs:

```text
/rg define axe_01
/blazingpractice:arena create axe_01 axe axe_01
/blazingpractice:arena setspawn1 axe_01
/blazingpractice:arena setspawn2 axe_01
/blazingpractice:arena enable axe_01

/rg define crystal_01
/blazingpractice:arena create crystal_01 crystal crystal_01
/blazingpractice:arena setspawn1 crystal_01
/blazingpractice:arena setspawn2 crystal_01
/blazingpractice:arena enable crystal_01
```

The spawn commands must be run while standing at the intended locations inside the
corresponding region. Configure the second spawn on the opposite side of the arena.

## Commands

| Command | Purpose |
|---|---|
| `/blazingpractice:arena create <id> <ladder> <region>` | Register a region in the current world |
| `/blazingpractice:arena setspawn1 <id>` | Set Player 1 spawn at your current location |
| `/blazingpractice:arena setspawn2 <id>` | Set Player 2 spawn at your current location |
| `/blazingpractice:arena enable <id>` | Validate the region and both spawns, then enable |
| `/blazingpractice:arena disable <id>` | Disable an arena |
| `/blazingpractice:arena delete <id>` | Delete an arena registration |
| `/blazingpractice:arena list` | Show all ladder pools and arena states |
| `/blazingpractice:arena info <id>` | Show one arena's configuration and validation |
| `/blazingpractice:arena reload` | Reload `arenas.yml` |

The short alternate form is `/bp arena ...` when no other plugin takes `/bp`. Prefer `/blazingpractice:arena` on servers where `/arena` is shared with another plugin.

Admin permission: `blazingpractice.admin.arenas` (also granted through the full
`blazingpractice.admin` permission and legacy `zpp.setup`).

## Pool limits

Edit `plugins/BlazingPractice/arenas.yml`.

- `min-arenas` is the recommended pool size shown in the list.
- `max-arenas` is the hard registration limit for that ladder.
- Increase `max-arenas` before registering more arenas than the current limit.
- Busy/free status is runtime-only; it is not saved to disk.

Default high-demand pools have a maximum of 8 arenas; other pools have maximums of 3 or 5.
You can change any number to match your player count.

## Match assignment and safety

- A match reserves one free arena from its exact ladder pool.
- Two matches cannot reserve the same arena.
- The arena is marked busy until the match ends, a player disconnects, a player dies,
  setup fails, or the plugin shuts down.
- Ranked and unranked queues for the same ladder share that ladder's arena pool.
- When every arena for a ladder is occupied, players remain in that ladder's queue.
- An arena is not eligible unless its WorldGuard region exists and both spawn locations
  are inside it and at least the configured minimum distance apart.
- The plugin never falls back to another ladder's arena.

## Important migration note

Old generic arenas created with the previous `DUEL` type are deliberately changed to
`unassigned` on load. They are not automatically treated as Sword arenas because that
could put another ladder's match into the wrong map. Re-register them with a ladder and
a WorldGuard region using the new commands.
