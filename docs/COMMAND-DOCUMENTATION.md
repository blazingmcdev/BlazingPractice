# BlazingPractice 0.10.5-dev — Server Selector, Queue and Ladder Commands

This guide describes the development source implementation. It is not a claim of complete ZonePracticePro compatibility.

## Lobby: wooden-axe server selector

In the configured main Lobby, BlazingPractice clears the lobby's ordinary inventory/armor/offhand and gives the player one tagged **Wooden Axe — Server Selector**. Right-click it to open a 27-slot GUI with four player-head buttons. The buttons execute the same actions as the four existing BlazingPractice NPCs, so the GUI and NPCs use the same configured destinations and URLs:

1. **Survival** — teleports to the first configured Survival world that is loaded.
2. **Practice & FFA** — opens the existing Practice/FFA mode chooser. Practice continues to the Ranked/Unranked/Private Duel menu; FFA joins the shared PvP world at the FFA spawn.
3. **SylixCloud Hosting** — sends a clickable link using `npcs.urls.hosting` (default `https://silixcloud.com`).
4. **Discord Server** — sends a clickable link using `npcs.urls.discord`.

The existing NPC locations, names and URL configuration are not recreated or overwritten by this selector GUI. Both NPCs and the wooden-axe GUI invoke the same `LobbyNpcService.performAction` destination actions.

### Optional wooden lobby block

You can also configure one specific wooden block in the Lobby to open the same four-button GUI. This does not make every wooden block clickable.

```text
/bp selector block set
/bp selector block info
/bp selector block clear
```

Stand in the Lobby and look directly at the wooden block within six blocks before running `block set`. Supported blocks include planks, logs, wood blocks and stems. Right-click only the saved block coordinate to open the selector.

Other selector commands:

```text
/bp selector menu
/bp selector give
```

`give` is an admin command and gives the tagged wooden axe. It is normally provided automatically in the configured Lobby.

## Inventory protection / isolation

- On entering the Lobby, the current world's profile is saved by the active inventory backend, then the Lobby inventory is rebuilt cleanly with the Server Selector. Armor and offhand are cleared too; the Ender Chest is not erased by this Lobby clean-up.
- Survival inventories, armor, offhand, Ender Chest, XP, health, hunger and potion effects are isolated under the Survival profile by BlazingPractice's SQLite fallback when Multiverse-Inventories is not installed.
- If Multiverse-Inventories is installed, BlazingPractice deliberately does not run a second profile-swapping system; the Survival/Lobby/PvP groups must be configured in Multiverse-Inventories.
- FFA inside the shared PvP world uses a temporary FFA inventory. The player's pre-FFA Practice/PvP snapshot is restored before returning or leaving the world, so FFA inventory is not written over the Practice profile. On logout/shutdown the stored Practice snapshot is used when saving the shared PvP profile.
- No server Survival items are intentionally copied into the Lobby selector inventory. Keep backups and test with a non-owner account before production use.

This protects against ordinary world/mode inventory leakage, but it is not a substitute for a verified live test with the exact Multiverse-Inventories version and other inventory plugins installed on your server.

## Practice player GUI flow

1. In the Lobby, right-click the Server Selector axe (or the configured wooden block).
2. Click **Practice & FFA**.
3. Click **Practice** to enter the shared PvP world.
4. Choose one of:
   - **Ranked Queue** — opens the Ranked ladder selector.
   - **Unranked Queue** — opens the Unranked ladder selector.
   - **Private Duel** — choose an online Practice opponent, then choose the ladder.
   - **Party** — opens party information and command guidance.
5. Selecting a Ranked/Unranked ladder joins only that exact queue.

FFA is a separate player mode inside the same shared PvP world:

```text
/ffa join
/ffa leave
/ffa setspawn
```

## Direct queue commands

```text
/ranked
/ranked sword
/unranked
/unranked axe
/queue ranked crystal
/queue unranked sumo
/queue join ranked crystal
/queue leave
/queue list
```

`/ranked` and `/unranked` without a ladder open the ladder selector. A ladder is required for direct queue entry.

## Private duels

```text
/duel Steve
/duel Steve sword
/accept
/accept Steve
```

## Selector and world administration

```text
/bp world status
/bp world set lobby
/bp world set pvp
/bp world set survival
/bp practice setspawn
/ffa setspawn
/bp npc create survival|practice|discord|hosting
/bp npc list
/bp npc url discord <https-url>
/bp npc url hosting <https-url>
```

For the shared-world layout, `/bp world set pvp` maps Practice and FFA to one world. Set Practice and FFA spawn locations separately inside that world.

## Imported ladder catalog

`archer`, `axe`, `battlerush`, `bedwars`, `boxing`, `bridges`, `builduhc`, `cart`, `creeper`, `crystal`, `diamondpotion`, `fireball`, `gapple`, `mace`, `mlgrush`, `netheritepotion`, `pearlfight`, `sg`, `skywars`, `soup`, `spearelytra`, `spearmace`, `spleef`, `sumo`, `sword`, `tntsumo`.

## Important limitations

ZonePracticePro's serialized kit inventory/armor data has not yet been decoded and migrated. A selected ladder currently separates matchmaking and rating buckets; it does not yet implement every original kit's game mechanics. Full command-handler compatibility and live Paper/Multiverse testing are still pending. This package is source code, not a compiled server JAR.

## Wooden-axe server selector

The editable menu configuration is created at `plugins/BlazingPractice/selector/selector.yml`. The wooden axe is configured under `selector-item`; menu buttons are listed under `menu.buttons`. Copy a button entry to add a head, delete an entry or set `enabled: false` to remove it, and edit `skin-owner`, `material`, `name`, `lore`, or `action` to customize it. Use `/bp selector reload` after editing.

Supported actions are `console_command`, `url`, `links`, `message`, and `practice_menu`. Console commands support `{player}`, `{uuid}`, and `{world}`. Only trusted administrators should edit console commands.

Commands:

```text
/bp selector menu
/bp selector give
/bp selector reload
/bp selector block set
/bp selector block clear
/bp selector block info
```


## Player-friendly help and completion (0.10.5-dev)

Players can use `/bp help`, `/bp help player`, `/bp help <command>` and `/bp help features`. Administrator help is permission-filtered. Implemented command roots provide contextual TAB suggestions from live server data. Reserved compatibility roots are identified as incomplete instead of showing invented subcommands.
