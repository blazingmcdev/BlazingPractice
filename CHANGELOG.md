# Changelog

## 0.10.5-dev — Player-friendly help and contextual command suggestions

- Added `/bp help <topic>` with player, administrator, command-specific and implementation-status help pages.
- Added command-specific `/help` usage pages for implemented player roots: duel, accept, queue, ranked, unranked, FFA, party, statistics and ladder.
- Expanded contextual TAB completion across implemented command arguments: visible online players, pending duel challengers, enabled ladders, arena IDs, Practice-world WorldGuard region IDs, NPC IDs and supported action names.
- Completion hides administrative actions from senders without the corresponding permission where practical.
- Added usage guidance that explains what each active command does and how TAB completion helps players.
- Explicitly marks registered compatibility-only roots as not fully implemented; no fictitious argument suggestions are provided for them.
- Bukkit/Paper TabCompleter supplies suggestion strings but cannot force the Minecraft client's dropdown text color; in-game help and usage lines use yellow formatting.
- Source package only; full Java 25 Maven compilation and Paper runtime testing remain required.

## 0.10.4-dev — Command labels and tab-completion improvements

- Documented `/blazingpractice:arena` as the conflict-safe command for this plugin's arena manager; updated arena help and dedicated-arena instructions to use the namespaced command.
- Added case-insensitive tab completion for duel opponents, enabled duel ladders, pending duel acceptors, queue types/ladders, FFA modes, party invites, statistics and arena IDs/WorldGuard region IDs.
- Added `/que` as an alias for `/queue`.
- Added concise `/queue ranked [ladder]` and `/queue unranked [ladder]` forms; the legacy `/queue join <ranked|unranked> <ladder>` form remains supported.
- Clarified `/duel <online-player> [ladder]`: no ladder opens the ladder picker; a ladder ID sends a direct duel invite.
- Tab-completion values are supplied by the plugin; final suggestion dropdown colors remain controlled by the Minecraft client.
- Source changes are not compiled or runtime-tested in this environment (Java 25/Maven are unavailable here).

## Dedicated duel arenas — 2026-11-14

- Replaced the single generic `DUEL` arena assignment with dedicated arena pools keyed by ladder ID.
- Added WorldGuard region registration and validation: the region must exist in the arena world, and both player spawns must be inside it.
- Preserved opposite-side fighter placement: Player 1 uses spawn 1 and Player 2 uses spawn 2.
- Added runtime arena reservations so two matches cannot use the same arena at once; reservations are released after match end, death, quit, failed setup, or plugin shutdown.
- Ranked and unranked queues for the same ladder share that ladder's arena pool; full pools keep players waiting in their existing queue rather than borrowing a different ladder's map.
- Added per-ladder `min-arenas` and `max-arenas` configuration, including 5–8 defaults for high-demand ladders and smaller defaults for other ladders.
- Added `/bp arena` management commands, tab completion, WorldGuard soft dependency metadata, and `docs/DEDICATED-DUEL-ARENAS.md`.
- Legacy generic `DUEL` entries are not auto-assigned to a ladder; admins must explicitly register each region under its intended ladder.
- This source update has not been compiled or tested on a Paper server in this environment.

## 0.10.1-dev — 2026-11-13

- Hardened the four server-selector head click routing: the GUI reads its persistent action tag first and falls back to the four fixed server-selector slots if another plugin strips item metadata.
- All four destination heads route through the same `LobbyNpcService.performAction` dispatcher used by the corresponding NPC clicks, keeping Survival, Practice/FFA, Hosting and Discord behavior in one place.
- Selector inventory clicks are cancelled before action resolution to prevent shift-click, number-key and offhand item movement from bypassing the menu handler.
- Confirmed the previously uploaded `BlazingPractice.jar` descriptor is `0.4.0-dev`; it does not contain the later 0.10.x selector implementation. This source update is not a compiled JAR.
- Full Maven compilation and Paper runtime click tests remain outstanding.

## 0.9.0-dev — 2026-11-13

- Added 26 sanitized, editable ladder metadata templates adapted from the supplied ZonePracticePro configuration archive.
- Ranked and Unranked now require an explicit ladder selection; matchmaking queues are isolated by queue type and ladder ID, and per-ladder ELO remains separate.
- Added a Private Duel GUI flow that selects an online Practice opponent and then a ladder; `/duel <player>` opens the ladder selector.
- Added direct selection commands `/ranked <ladder>`, `/unranked <ladder>` and `/queue join <ranked|unranked> <ladder>`; ladder-less queue entry is refused.
- Added basic ladder metadata administration: list, info, enable/disable, create, clone, delete, set icon and reload.
- Fixed `/ladder set icon <ladder>` argument validation and expanded ranked/unranked/duel tab completion.
- Explicitly documented that original serialized ZonePracticePro item loadouts and special mode mechanics are not yet migrated or applied.
- No original plugin JARs, bundled plugins, or ZonePracticePro world/day/force settings were copied.
- Full Maven compilation and Paper/Multiverse runtime tests remain outstanding.

## 0.8.0-dev — 2026-11-13

- Reworked world setup so `/bp world set pvp` maps Practice and FFA to one shared Minecraft world.
- Added independent Practice and FFA spawn points within that shared world; Practice queues and duel arenas stay in the shared world.
- Changed FFA from a world-only identity to an explicit per-player mode, with same-world return locations and a Leave FFA control.
- Players in the shared PvP world are protected by default until they explicitly join FFA or enter a duel.
- Added cross-mode damage isolation: active FFA players cannot hit Practice staging players or duel participants, and vice versa.
- Uses one `pvp` inventory profile for the shared world; keeps Lobby and Survival profiles separate.
- Prevents assigning Practice and FFA to separate worlds through `/bp world set`; use `/bp world set pvp` and configure only their spawn points separately.
- Existing NPCs and their Discord/hosting links are not part of this update and are left unchanged.
- Full Maven compilation and Paper/Multiverse runtime tests remain outstanding.

## 0.5.0-dev — 2026-11-13

- Split Practice and FFA into independent hotbar items, menu flows, world classification and command paths.
- Practice menu opens Ranked and Unranked matchmaking; queue entry is limited to configured Practice worlds and the saved Practice lobby.
- Added an initial FFA service with join/leave/status/setspawn commands, dedicated FFA destination and spawn, return-location handling, respawning, and configurable keep-inventory-on-death behavior.
- Added a dedicated Leave FFA barrier in FFA worlds; Practice hub items are not injected into FFA worlds.
- Configured FFA worlds are always excluded from safe-zone protection, while Practice/lobby areas and queued players remain protected.
- Setting a Practice lobby also adds that world to the internal fallback's isolated lobby inventory group, while rejecting Survival-group worlds.
- Updated world-group defaults and setup documentation for separate Survival, lobby, Practice and FFA inventories.
- Full Maven build and Paper/Multiverse integration tests remain outstanding.

## 0.4.0-dev
- Reworked Practice lobby hotbar with a mode selector, party information, create party and help items.
- Right-click handling processes plugin-tagged items even when another listener has already cancelled the interaction.
- Practice menu offered Ranked/Unranked queue actions; the original FFA option only teleported and did not implement an independent FFA lifecycle.

## 0.3.0-dev
- Added independent BlazingPractice lobby set/teleport commands.
- Added safe-zone protection for configured lobby/Practice/FFA worlds and queued players.

## 0.2.0-dev
- Added persistent arena registry and initial 1v1 duel lifecycle.
- Added ranked/unranked matchmaking queues, transactional SQLite Elo rating updates, basic match-history rows and party utilities.

## 0.7.0-dev — 2026-11-13

- Reworked world roles to explicitly separate Lobby, Practice, FFA and Survival.
- Added `/bp world status` and `/bp world set <lobby|practice|ffa|survival>` setup commands.
- `/bp world set lobby` also saves the independent BlazingPractice lobby location; Practice and FFA setup commands save their initial spawns.
- Practice world now receives Practice-only queue controls; the Lobby offers the Practice/FFA mode selector; FFA receives only its Leave FFA control; Survival receives no selector items.
- Queue entry now requires the dedicated Practice world, and leaving that world removes the player from matchmaking.
- Added `/bp practice join` and `/bp practice setspawn`.
- Added four persistent villager-style NPCs and `/bp npc create|remove|list|url`; the Hosting NPC defaults to `https://silixcloud.com`.
- Updated the command guide, setup docs and implementation status to reflect the new world and NPC model.
- Compilation and Paper/Multiverse runtime tests remain outstanding.
- Added validation that DUEL arenas and both spawn points belong to the dedicated Practice world; private duels also require both players to be in Practice.
- Added duel teleport rollback and restores safe-zone protection if an arena teleport is blocked; failed FFA entry also restores protection.

## 0.10.0-dev — 2026-11-13

- Added a Lobby-only wooden-axe Server Selector that opens a four-player-head GUI.
- GUI destinations reuse the four existing NPC actions: Survival, Practice & FFA, SylixCloud Hosting and Discord.
- Added an optional single configured wooden Lobby block that opens the same GUI.
- Lobby inventory is rebuilt cleanly on arrival; armor/offhand are cleared while the Survival profile is saved/restored separately.
- FFA uses a temporary inventory snapshot in the shared PvP world and restores the prior Practice/PvP snapshot before return/world save.
- Added `/bp selector give|menu|block set|clear|info`.

## 0.10.3-dev — Configurable selector folder

- Added a separate runtime folder: `plugins/BlazingPractice/selector/selector.yml`.
- The wooden-axe item, menu title/size, buttons, head skins, slots, names, lore, links and click actions are data-driven.
- Add a head by adding a `menu.buttons` entry; remove it by deleting the entry or setting `enabled: false`.
- Added `/bp selector reload` and tab completion. Reload refreshes selector items for online players.
- Selector head actions and BlazingPractice-managed lobby NPC actions now use the same selector action dispatcher.
- Mapped the supplied `npcs.yml` actions: Survival Multiverse command; FFA NPC's `practice` world command; Discord invite; SylixCloud website and Discord links.
- External NPC coordinates and entity settings are not copied or modified.
- Console-command placeholders: `{player}`, `{uuid}`, `{world}`. URL actions accept only HTTP(S) links.

## 0.10.3-dev source update — FFA mode selection
- Right-clicking the FFA hotbar item now opens a dedicated `Choose an FFA Mode` inventory instead of immediately joining FFA.
- The lobby mode selector's FFA option now opens the same mode picker.
- `/ffa join` and `/ffa play` open the picker; `/ffa join <ladder>` selects a specific enabled ladder directly.
- The picker lists enabled ladder definitions and remembers the chosen ladder ID for the active FFA session.
- Leaving FFA clears the temporary selected-mode state and keeps the existing inventory restoration flow.
- Ladder-specific kit application and special ladder mechanics are not implemented by this change; this is a mode-selection UI and selection-state foundation only.
