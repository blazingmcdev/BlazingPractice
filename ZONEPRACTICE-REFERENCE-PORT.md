# ZonePracticePro reference-port notes

## What was supplied

- `ZonePractice-Pro-8.1.0(1).jar`: compiled plugin archive. The useful compatibility metadata inspected here is its `plugin.yml` command/alias and permission declaration inventory.
- `ZonePracticePro.zip`: configuration archive containing config YAMLs, GUI/inventory text layouts, language strings, player-kit data, divisions/groups and 26 `ladders/*.yml` files. It did not contain Java source or a Maven/Gradle source project.
- `BlazingPractice(1).jar`: uploaded BlazingPractice binary; its descriptor version predates the 0.10.0-dev source tree in this package. This package updates the source project, not that old binary.

## What was adapted

The following information from the 26 ladder YAML files was extracted into editable BlazingPractice ladder metadata files:

- ladder ID / display name
- ladder type label
- menu icon material
- enabled / ranked / unranked flags
- queue-enabled flag

The GUI now asks for the queue type first (Ranked or Unranked), then a specific ladder. The private-duel GUI asks for an online opponent and then a ladder. The commands support direct, explicit selection as well.

## What was deliberately not copied

- Any bundled plugin JARs or plugin implementation classes.
- Original proprietary Java implementation or obfuscated plugin internals.
- World/day/force command settings or configuration.
- Original serialized `inventory`, `armor`, and opaque `extra` payloads; these have not been decoded, validated or translated into a BlazingPractice kit format.
- Original full GUI/inventory configuration blobs as if they were compatible with BlazingPractice. The current BlazingPractice selector is implemented independently.

## Compatibility boundary

The original 26 command roots and aliases are retained in `plugin.yml`. This is command-name compatibility only. Not every command and subcommand has a functional handler yet. The queue picker, ladder metadata registry, basic ladder administration and private-duel selector are implemented in source. The remaining command behavior and GUI parity require separate clean-room implementations and tests.


## Supplied lobby NPC mapping (selector)

The provided `npcs.yml` contains four destination NPCs and their right-click actions. BlazingPractice mirrors those action values in its editable `selector/selector.yml` menu entries: `Survival`, `FFA` (displayed as Practice & FFA), `SylixCloud`, and `Discord`. The uploaded file contains NPC locations and action definitions but no configured skin owner names; therefore head skins are left blank and can be set with `skin-owner` in the selector config. The external NPC plugin remains responsible for NPC positions and spawning.
