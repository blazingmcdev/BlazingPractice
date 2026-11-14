# BlazingPractice Season 3 — World Setup and Command Guide

**Source milestone:** `0.10.5-dev`  
**Reference plugin:** ZonePracticePro `8.1.0`  
**Target:** Paper `1.21.11`, Java `25`

This is a development-source build. It has not yet been compiled against Paper or tested on a live server. Command roots retained from ZonePracticePro are not automatically behavior-compatible; see `COMMAND-COMPATIBILITY.md`.


## Wooden-axe Server Selector

In the configured Lobby, players receive one Wooden Axe named **Server Selector**. Right-click to open a 4-head GUI:
- Survival — uses the first loaded configured Survival world.
- Practice & FFA — opens the Practice/FFA chooser.
- SilixCloud Hosting — clickable link from `npcs.urls.hosting` (default `https://silixcloud.com`).
- Discord Server — clickable link from `npcs.urls.discord`.

The GUI calls the same action handlers as the existing four lobby NPCs; it does not recreate their entities or overwrite their locations/links.

To make one wooden block in the Lobby open the same GUI, look at it within six blocks and run `/bp selector block set`. Only that exact saved block responds. Use `/bp selector block info` and `/bp selector block clear` to inspect/disable it.

```text
/bp selector menu
/bp selector give
/bp selector block set
/bp selector block info
/bp selector block clear
```

On Lobby arrival, the Lobby inventory, armor and offhand are cleared and the selector is reissued. Survival inventory is saved/restored separately by the configured backend. In shared-world FFA, the previous Practice/PvP state is snapshotted, the temporary FFA inventory is cleared, and the Practice/PvP state is restored before leaving or saving.

## A. Recommended world layout: one shared PvP world

Use one Lobby world, one shared PvP world for both Practice and FFA, and your existing Survival world(s). Do **not** create separate Practice and FFA worlds for this setup.

| Role | Example world | What happens there |
|---|---|---|
| Lobby | `blazinglobby` | Existing four NPCs and mode selection; no damage |
| Shared PvP | `pvp` (or your own name) | Practice queues/duel arenas and FFA share this world, with separate spawn points and player mode state |
| Survival | `world` plus your SMP nether/end worlds | Normal survival gameplay and inventory; no BlazingPractice hotbar items |

You already created your NPCs and links, so do not recreate them. Keep your configured lobby world as it is. You only need to assign the shared PvP world and set its two mode spawns.

## B. Configure the shared PvP world

Teleport to the one world you want to use for both Practice and FFA. Replace `pvp` with your actual Multiverse world name in the teleport command:

```text
/mv tp pvp
/bp world set pvp
```

`/bp world set pvp` maps both the Practice role and FFA role to this exact world. It saves your current location as the initial Practice spawn. Confirm it:

```text
/bp world status
```

The `pvp`, `practice`, and `ffa` entries should all show the same world name.

Move to the FFA spawn point somewhere else in the **same world** and save it:

```text
/ffa setspawn
```

To move the Practice spawn, stand at its position in the same world:

```text
/bp practice setspawn
```

Do not use `/bp world set practice` and `/bp world set ffa` for this layout. Use `/bp world set pvp` once; the two other commands above only set separate locations inside it.

Your existing `/bp world set lobby` and `/blazingpractice lobby set` settings are separate and do not need to be changed. Your NPCs, Discord link, and `https://silixcloud.com` Hosting link are left untouched.

## C. Four lobby NPCs

Stand at each desired NPC position in `blazinglobby` and run one command per NPC:

```text
/bp npc create survival
/bp npc create practice
/bp npc create discord
/bp npc create hosting
```

The NPCs are persistent, invulnerable, non-moving Villager entities with named click actions:

| NPC ID | Display name | Click action |
|---|---|---|
| `survival` | SURVIVAL | Teleports to the first loaded configured Survival world spawn |
| `practice` | PRACTICE & FFA | Opens the Practice/FFA mode selector |
| `discord` | JOIN DISCORD | Sends a clickable Discord invite link |
| `hosting` | OUR HOSTING | Sends a clickable hosting link; default is `https://silixcloud.com` |

Set your real Discord invite link:

```text
/bp npc url discord https://discord.gg/YOUR_INVITE
```

The hosting URL is already set to `https://silixcloud.com`, but can be changed with:

```text
/bp npc url hosting https://silixcloud.com
```

Manage placed NPCs:

```text
/bp npc list
/bp npc remove survival
/bp npc remove practice
/bp npc remove discord
/bp npc remove hosting
```

NPC creation stores its world and position in `plugins/BlazingPractice/config.yml`. The current built-in NPCs are Villager-style NPCs, not player-skin NPCs; Citizens integration/player skins are not part of this milestone.

## D. Player experience and protection in the shared PvP world

### Practice mode

- A player entering the shared PvP world is protected by default.
- The Practice sword opens Ranked and Unranked queue controls.
- Players stay protected while selecting and waiting in a queue.
- Protection is released when a duel starts and restored when the player returns to a safe area.
- Practice queue and duel arenas must use the shared PvP world.

### FFA mode

- Joining FFA marks only that player as an FFA participant and teleports them to the FFA spawn **inside the same world**.
- The FFA participant receives the Leave FFA barrier; `/ffa leave` also works and returns them to their previous location.
- FFA participants can damage other FFA participants. They cannot damage Practice staging players or duel participants, and those players cannot damage FFA participants.
- FFA deaths respawn at the saved FFA spawn. By default, inventory is kept on death (`ffa.keep-inventory-on-death: true`).
- Being physically inside the shared PvP world does not automatically put someone into FFA mode.

### Survival and Lobby

- Survival is not the PvP world and receives no BlazingPractice hotbar items or FFA behavior.
- The Lobby remains a separate, protected world. Your existing NPCs and external links are not modified by these world-role commands.

## E. Inventory separation and Multiverse

The shared-world layout uses one `pvp` inventory profile for both modes, with separate `lobby` and `survival` profiles. It does not swap Practice and FFA inventories based on a player's mode. When **Multiverse-Inventories** is installed, BlazingPractice disables its internal swapping backend to avoid conflicts; configure groups `survival`, `lobby`, and `pvp` there. Without Multiverse-Inventories, the SQLite fallback separates those three world profiles.

Back up player data and Multiverse-Inventories data before testing. Runtime compatibility has not yet been tested on your server.

## F. Practice arenas and matchmaking

Create a WorldGuard region inside the shared PvP world, then register it to its dedicated ladder. Both spawn locations must be inside that region:

```text
/mv tp pvp
/rg define sword_01
/blazingpractice:arena create sword_01 sword sword_01
/blazingpractice:arena setspawn1 sword_01
/blazingpractice:arena setspawn2 sword_01
/blazingpractice:arena enable sword_01
/blazingpractice:arena list
```

See `docs/DEDICATED-DUEL-ARENAS.md` for the complete setup, per-ladder pool limits and command list.

Players must not be active FFA participants to join a Practice queue or start a private duel. The queues use the Practice mode of the shared PvP world.

Player commands:

```text
/practice
/practice menu
/practice ranked
/practice unranked
/ranked
/unranked
/queue ranked
/queue ranked sword
/queue unranked
/queue unranked axe
/queue join ranked sword
/queue leave
/queue list
/que ranked sword
/duel <online-player>
/duel <online-player> <ladder>
/accept [challenger]
```

## G. FFA and lobby navigation commands

```text
/ffa join [mode]
/ffa leave
/ffa status
/ffa setspawn
/blazingpractice lobby
/blazingpractice lobby set
/bp world status
/bp practice join
/bp practice setspawn
```

## H. Other currently implemented commands

```text
/party create
/party invite <player>
/party accept
/party leave
/party info
/party chat <message>
/statistics [online-player]
/bp help
/bp about
/bp status
/bp diagnostics
/bp updatecheck
```

`/bp diagnostics` and `/bp updatecheck` require their respective `blazingpractice.admin.*` permissions. World and NPC administration requires operator status, `zpp.setup`, or the matching `blazingpractice.admin.worlds` / `blazingpractice.admin.npc` permission.

## I. Arena/command parity is still in progress

The 26 ZonePracticePro root command labels and aliases remain declared. A number of their full original handlers are still pending, including ladder editing, staff mode, spectating, events, kit editor, cosmetics, tournaments, replay and complete statistics/history screens. The descriptor alone does not establish exact original command syntax.

Build with Java 25 and Maven:

```text
mvn clean package
```

Expected file: `target/BlazingPractice.jar`. The current ZIP is a source project, not an installable server JAR. Do not use it on the live server until it has been compiled and tested on a disposable Paper 1.21.11 server.


## Queue and ladder selection

See [`COMMAND-DOCUMENTATION.md`](COMMAND-DOCUMENTATION.md) for the full flow. Players choose Ranked or Unranked, then one ladder before queueing. Private Duel uses opponent selection followed by ladder selection. Direct command forms are `/ranked <ladder>`, `/unranked <ladder>`, `/queue <ranked|unranked> [ladder]`, legacy `/queue join <ranked|unranked> <ladder>`, and `/duel <online-player> [ladder]`.


### Selector configuration

```text
/bp selector menu
/bp selector give
/bp selector reload
```
Edit `plugins/BlazingPractice/selector/selector.yml` to add or remove destination heads and change their links/actions.


## Command suggestions and command-name conflicts

- Use `/blazingpractice:arena ...` for arena administration. The namespaced form explicitly selects
  BlazingPractice's arena command if another plugin also registers `/arena`.
- `/bp arena ...` is the short alternate form when `/bp` is not claimed by another plugin.
- `/duel` tab completion suggests visible online players (not the command sender), followed by
  enabled ladder IDs. `/duel <online-player>` opens the ladder GUI; `/duel <online-player> <ladder>` sends a
  direct challenge.
- `/queue` and `/que` suggest `ranked`, `unranked`, `join`, `leave`, and `list`.
  `/queue ranked` or `/queue unranked` opens the ladder GUI; adding a ladder ID joins directly.
  The older `/queue join <ranked|unranked> <ladder>` syntax remains supported.
- Other implemented BlazingPractice commands provide context-aware argument suggestions where
  applicable (enabled ladders, arena IDs, WorldGuard regions, visible online players, and valid
  subcommands).
- The final dropdown styling/color is rendered by the Minecraft client. Bukkit tab completers supply
  the suggestion values; they cannot force the client's suggestion text to a custom color. Help and
  usage messages use yellow command syntax.


## Player-friendly help (0.10.5-dev)

- `/bp help` — concise command overview.
- `/bp help player` — player commands.
- `/bp help admin` — commands allowed by the sender's administrative permissions.
- `/bp help <command>` — detailed help for a supported command.
- `/bp help features` — lists currently working and compatibility-only command roots.

TAB completion uses actual online players, pending duel invitations, enabled ladder definitions, registered arena IDs and WorldGuard region IDs in the Practice world. Minecraft client dropdown colors are client-controlled; yellow formatting is used in in-game help text.
