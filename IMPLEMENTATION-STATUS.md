# BlazingPractice Season 3 — implementation status (0.10.5-dev)

This remains an actively developed clean-room implementation. It is **not feature-equivalent to ZonePracticePro 8.1.0** and is not a production release.

## Implemented in source (compilation and Paper runtime verification pending)

- Paper plugin entry point, modular source layout, SQLite bootstrap, GitHub release checker and world-profile storage foundation.
- Original ZonePracticePro command roots/aliases retained in plugin metadata; retention is not full behavioral parity.
- One shared PvP world for Practice and FFA, with distinct spawn locations and per-player FFA state.
- Practice queue GUI: Ranked/Unranked -> select a specific ladder -> join only that ladder's queue.
- Private duel GUI: choose online opponent -> choose ladder -> send invitation.
- Dedicated per-ladder duel arena pools backed by WorldGuard regions, runtime reservations, opposite-side spawns and separate ranked/unranked matchmaking queues.
- Basic duel lifecycle, in-memory matchmaking queues, ELO storage, party foundation and FFA inventory snapshot foundation.
- Lobby wooden-axe selector item and optional exact configured wooden-block trigger.
- Configurable selector folder: `plugins/BlazingPractice/selector/selector.yml`.
- Selector menu size/title, item material/slot/name/lore, arbitrary destination button count, button slot/material/name/lore, optional player-head skin owner and click actions are editable in YAML.
- Add a head by adding a `menu.buttons` block; remove it by deleting the block or setting `enabled: false`; apply edits with `/bp selector reload`.
- Supported selector action types: console_command, url, links, message and practice_menu.
- Selector GUI heads and BlazingPractice-managed NPCs use the same configured destination-action dispatcher.
- Four default selector mappings were derived from the supplied `npcs.yml`: Survival command, FFA NPC's Practice-world command, Discord invite, SylixCloud website and hosting Discord invite.
- External NPC coordinates, creator IDs and entity settings are not copied into BlazingPractice and are not modified by the selector.
- Lobby inventory is rebuilt with the configured selector item; Survival is intended to be stored/restored separately through the configured inventory backend.

## Dedicated duel arena behavior

- Arena registrations live in `plugins/BlazingPractice/arenas.yml`.
- Each entry has one ladder ID, one WorldGuard region ID, two spawn points and an enabled flag.
- `min-arenas` is the recommended pool size; `max-arenas` is the registration cap.
- Arena busy state is held in memory only. A match reserves its exact ladder's free arena and releases it when the match finishes.
- WorldGuard and WorldEdit must be installed on the server. The plugin has a soft dependency and starts with an explicit warning if WorldGuard is missing; arena creation and match validation then refuse to proceed.
- Existing generic `DUEL` arena records are deliberately marked `unassigned`, rather than guessed as Sword or another ladder.
- Setup and commands: `docs/DEDICATED-DUEL-ARENAS.md`.

## Important limitations

- The `npc-name` property in selector.yml is a reference label, not an automatic connection to or live parser for an external NPC plugin's file. The selector action values were mapped from the supplied npcs.yml. Change selector.yml when changing those actions.
- The supplied npcs.yml does not contain usable head skin owner names. Set `skin-owner` to a Minecraft account name for each PLAYER_HEAD, or leave it blank.
- Commands configured as `console_command` run as console. They must be edited only by trusted administrators. The default Survival and FFA actions reference Multiverse world names `world` and `practice`; change them to the actual names on the server if needed.
- Many original ZonePracticePro command subtrees are placeholders or partially ported; full command/GUI/kit compatibility is not complete.
- Original serialized ladder inventory/armor values have not yet been migrated into functional kits; several special modes are still incomplete.
- FFA custom kits, kill/death statistics, killstreaks and map rotation remain unfinished.
- Inventory backend interactions with the server's exact Multiverse-Inventories version have not been live-tested.

## Validation and release status

- YAML parse checks: passed for plugin.yml, config.yml and selector/selector.yml.
- pom.xml XML parse: passed.
- Selector action/config static checks: passed.
- ZIP integrity: passed.
- Java 25 Maven compile: **not verified** (environment has Java 21 and no Maven executable).
- WorldGuard API compilation and Paper 1.21.11 server integration: **not verified**.
- Paper 1.21.11 integration test: **not run**.
- External NPC plugin integration: **not run**.
- Multiverse-Core / Multiverse-Inventories interoperability test: **not run**.
- Modrinth/production readiness: **not ready**.

Build with JDK 25 and Maven: `mvn clean package`. Expected output: `target/BlazingPractice.jar`.


## 0.10.5-dev command UX

The active command handlers now have expanded contextual TAB completion and player-oriented help pages. Compatibility-only labels remain declared but their feature bodies are not complete; help explicitly reports this. Final command-tree behavior, permission filtering and Paper 1.21.11 suggestions require in-server validation after a Java 25 Maven build.
