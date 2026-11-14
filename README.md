# BlazingPractice Season 3 — 0.10.5-dev

**Plugin:** BlazingPractice  
**Reference:** ZonePracticePro 8.1.0  
**Platform target:** Paper 1.21.11 · Java 25 · Maven  
**Main class:** `net.blazingpractice.BlazingPractice`

BlazingPractice is a clean-room development project intended to replace the Practice/FFA experience and progressively port ZonePracticePro functionality. It does not modify, bundle or redistribute the original ZonePracticePro JAR. This is not yet a full ZonePracticePro feature clone or a release-ready server plugin.

## World layout: one shared PvP world

Use one separate Lobby world, one shared PvP world for both Practice and FFA, and your existing Survival world(s). Do **not** create separate Practice and FFA worlds for this layout.

| Role | Example world | Behavior |
|---|---|---|
| Lobby | `blazinglobby` | Existing NPCs plus a clean wooden-axe Server Selector GUI; damage protection |
| Shared PvP | `pvp` (or your own world name) | Practice queues/duel arenas and FFA all run in this same world, using separate spawn points and player mode state |
| Survival | `world`, `survival`, nether/end | Normal SMP profile; no BlazingPractice items |

Stand inside your existing shared PvP world and assign it once:

```text
/bp world set pvp
```

This sets the same world for both `practice` and `ffa` and creates one shared `pvp` inventory profile. It also saves your current position as the initial Practice spawn. Then move to your intended FFA spawn in that **same world** and run:

```text
/ffa setspawn
```

You can move the Practice spawn later with `/bp practice setspawn`. Set up dedicated duel arenas inside this same world using WorldGuard regions. First create a region with WorldEdit/WorldGuard, then register it for a specific ladder:

```text
/rg define sword_01
/blazingpractice:arena create sword_01 sword sword_01
/blazingpractice:arena setspawn1 sword_01
/blazingpractice:arena setspawn2 sword_01
/blazingpractice:arena enable sword_01
```

Repeat with `axe`, `crystal`, or any other ladder ID. See
[`docs/DEDICATED-DUEL-ARENAS.md`](docs/DEDICATED-DUEL-ARENAS.md) for all commands,
pool limits and setup steps.

Use `/bp world set lobby` in your already-created lobby world only if it has not been configured yet. Your NPCs and their Discord/hosting URLs are not changed by the shared-world setup. Run `/bp world status` to confirm that `pvp`, `practice`, and `ffa` all show the same world name.

### Mode separation inside the shared world

- **Practice mode:** player is not marked as FFA; the Practice menu offers Ranked, Unranked, Private Duel, and Party. Ranked/Unranked opens a ladder picker first. The player is protected while selecting and queued. Protection is released when a duel actually starts.
- **FFA mode:** `/ffa join`, the FFA selector item, or the lobby GUI marks that player as an FFA participant and teleports them to the FFA spawn in the same world. The barrier or `/ffa leave` returns them to their previous location.
- FFA combat is isolated from Practice staging and duel participants: players in different modes cannot damage one another in the shared PvP world.
- FFA deaths respawn at the FFA spawn. FFA participation is a player mode, not inferred merely from being inside the shared world.
- Survival remains a separate role and receives no BlazingPractice items or PvP behavior.

## Wooden-axe selector GUI

On arrival in the Lobby, BlazingPractice clears the Lobby's ordinary inventory, armor and offhand, then gives one tagged Wooden Axe named **Server Selector**. Right-clicking it opens four player-head buttons: Survival, Practice & FFA, SilixCloud Hosting and Discord. These buttons call the exact same destination actions used by the four existing NPCs, so the existing NPC locations, names and URL settings are not recreated or overwritten.

Optionally, configure one wooden lobby block to open the same GUI. Stand in the Lobby, look at a wooden block within six blocks and run `/bp selector block set`. Right-click only that saved block. Use `/bp selector block info` or `/bp selector block clear` to manage it. `/bp selector menu` opens the GUI directly; `/bp selector give` gives an additional selector axe to an admin.

Survival inventory is not meant to be deleted: the Survival profile is saved separately and restored by BlazingPractice's SQLite fallback when Multiverse-Inventories is absent, or by Multiverse-Inventories when it is installed and correctly configured. Returning to the Lobby cleans the Lobby inventory and reissues only the selector axe. In shared-world FFA, a temporary FFA inventory is used; the prior Practice/PvP snapshot is restored before returning or saving the PvP profile.

## Four lobby NPCs

Create them while standing at each target position in the lobby world:

```text
/bp npc create survival
/bp npc create practice
/bp npc create discord
/bp npc create hosting
```

- `survival`: teleport to the first loaded configured Survival world.
- `practice`: open the Practice/FFA mode selector.
- `discord`: send a clickable invite link.
- `hosting`: send a clickable link to `https://silixcloud.com`.

Configure Discord and inspect NPCs with `/bp npc url`, `/bp npc list`, and `/bp npc remove`. The built-in NPC entities are Villager-style, not player-skin NPCs.

## World inventories

The recommended layout uses one `pvp` inventory profile for the shared Practice+FFA world, plus separate `lobby` and `survival` profiles. Because Practice and FFA share a world, BlazingPractice does not attempt to swap two separate inventories between those modes. Mode kits are a separate feature and are not part of this world-setup milestone.

When Multiverse-Inventories is detected, BlazingPractice disables its internal inventory-swap fallback so both plugins do not compete. Configure Multiverse-Inventories with separate `survival`, `lobby` and `pvp` groups. Without Multiverse-Inventories, the SQLite fallback handles inventory, armor, offhand, ender chest, XP, health, hunger and potion effects between those profiles.

Back up player data and plugin data before enabling profile swapping. It has not yet been runtime-tested on a real Paper server.

## Current implementation

Implemented in source (not yet compiled/server-tested):

- Arena registry and setup commands.
- First 1v1 duel lifecycle.
- In-memory Ranked/Unranked pairing queues, isolated by both queue type and selected ladder (for example, `ranked:sword` never pairs with `unranked:axe`).
- Ladder picker seeded from 26 sanitized ZonePracticePro ladder definitions; players choose a ladder before entering any ranked or unranked queue.
- Private Duel GUI flow: select an online Practice opponent, then choose the ladder; `/duel <online-player>` opens the ladder picker and `/duel <online-player> <ladder>` sends directly. Tab completion suggests visible online players, then enabled ladder IDs.
- SQLite ELO ratings and basic match statistics/history.
- Basic in-memory party creation, invitations, membership and chat.
- One shared PvP world mapped to both Practice and FFA, with independent Practice and FFA spawn locations.
- Per-player FFA participation, Practice queue staging protection, duel-start protection release, and mode-isolated combat in the shared world.
- FFA join/leave, same-world return location, FFA respawn and configurable keep-inventory behavior.
- Lobby-only wooden-axe Server Selector, four-player-head destination GUI, shared NPC actions, and optional single wooden block trigger.
- Lobby inventory cleanup after world-profile switching, with a separate saved Survival profile; temporary FFA inventory is restored to the shared Practice/PvP profile before leaving FFA.
- Four persistent built-in lobby NPC actions reused by the wooden-axe GUI.
- All 26 ZonePracticePro root command declarations and aliases retained.

Important limitation: ladder selection currently separates queue buckets and per-ladder ELO records, but it does not yet apply the original ZonePracticePro kit inventory or mode-specific mechanics. The ZIP's ladder YAML files contain serialized plugin-specific inventory data; those loadouts have not been safely decoded/migrated. Do not treat Sword, Crystal, BedWars, etc. as fully playable custom modes yet. Still pending: original kit/loadout application and game mechanics; full original command handlers and GUI parity; Glicko-2 and placement/seasons; rating-range matchmaking; persistent party system and party-vs-party; full FFA kits, kills/deaths/streaks and map rotation; ladder/kit editor parity; staff mode; spectating; events; tournaments; replay system; BedWars gameplay; cosmetics; and full data migration/permission parity.

## Build

Requires JDK 25 and Maven 3.9+:

```bash
mvn clean package
```

Expected artifact: `target/BlazingPractice.jar`.

The authoring environment used for this milestone has JDK 21 and no Maven executable, so a full Maven compile was not possible. This ZIP is source code, not an installable JAR. Paper 1.21.11 and Multiverse runtime tests are still required before production use or Modrinth publication.

## Documentation

- `docs/COMMANDS.md` — world setup, lobby/NPC commands and current player/admin commands.
- `docs/COMMAND-DOCUMENTATION.md` — exact current queue, ladder, duel and arena usage.
- `COMMAND-COMPATIBILITY.md` — original ZonePracticePro command inventory and handler status.
- `ZONEPRACTICE-REFERENCE-PORT.md` — what was taken from the supplied config archive and what is not yet migrated.
- `BUILD-STATUS.txt` — exact verification and build state.
- `IMPLEMENTATION-STATUS.md` — implemented vs pending scope.

## Safety

Test on a disposable Paper server first. Back up the full world folder, `playerdata`, `plugins/BlazingPractice/`, and Multiverse-Inventories data. Disable/remove the original ZonePracticePro JAR during compatibility testing to avoid duplicate command names and player controls.


### Selector head click troubleshooting

The four player-head menu entries are action buttons, not merely decorative heads. Each head invokes the same `LobbyNpcService.performAction` method as its matching NPC. The GUI stores the action in item persistent metadata and, for compatibility with plugins that rewrite inventory items, also recognizes fixed server-selector slots (10 Survival, 12 Practice/FFA, 14 Hosting, 16 Discord). The uploaded `BlazingPractice.jar` inspected for this project declares `0.4.0-dev`; it is older than this selector implementation. A source ZIP is not a server plugin—build the source with JDK 25 and Maven, then install the newly built `target/BlazingPractice.jar`.

## Editable wooden-axe server selector

BlazingPractice creates `plugins/BlazingPractice/selector/selector.yml` on first startup. This separate folder contains the selector menu and its button actions. Copy an entry under `menu.buttons` to add a head, delete it or set `enabled: false` to remove it, and edit its player skin (`skin-owner`), icon (`material`), name, lore, command or URL. Run `/bp selector reload` to apply changes without restarting.

The supplied `npcs.yml` was used to map the four selector destinations: Survival, the FFA NPC's Practice world command, SylixCloud hosting links, and Discord. NPC positions and external NPC plugin settings are not imported or overwritten. The selector's matching action values live in `selector/selector.yml`; edit that file when changing a head destination.

Supported action types: `console_command`, `url`, `links`, `message`, and `practice_menu`. Console command actions run as console and support `{player}`, `{uuid}`, and `{world}`. Only trusted administrators should edit these entries.

Selector configuration reference: `docs/SELECTOR-CONFIGURATION.md`.


## Command UX update (0.10.5-dev)

Use `/blazingpractice:arena ...` for the arena manager when `/arena` conflicts with another plugin.
`/duel <online-player> [ladder]` suggests online players and enabled ladder IDs. `/queue ranked [ladder]`
and `/queue unranked [ladder]` open the ladder selector or join directly; `/queue join ...` remains
supported, and `/que` is an alias.


## Player command help and TAB completion (0.10.5-dev)

Use `/bp help` for the player command overview, `/bp help player` for player-only help, `/bp help admin` for permission-filtered administrator help, `/bp help <command>` for command-specific instructions, and `/bp help features` for an implementation-status list. Active commands provide contextual TAB completions for visible online players, enabled ladders, arena IDs, and supported options. Registered compatibility command labels are not presented as fully implemented features.
