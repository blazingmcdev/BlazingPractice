# BlazingPractice — Shared PvP World + Four-Destination Selector

**Milestone:** `0.10.0-dev` · **Target:** Paper 1.21.11 / Java 25

This setup uses one Lobby, one shared PvP world for both Practice and FFA, and your existing Survival world(s). It does not copy or change NPC positions, Discord/hosting URLs, Multiverse world settings, world day/time/force settings or other plugin configuration.

## Recommended world layout

| Role | Existing world | Purpose |
|---|---|---|
| Lobby | Your configured Lobby | Clean server selector inventory and four-head GUI |
| PvP | One shared PvP world | Practice and FFA use separate mode states/spawns inside it |
| Survival | Your existing Survival world(s) | Normal SMP inventory and progression |

Assign roles from inside each world:

```text
/mv tp your_lobby_world
/bp world set lobby

/mv tp your_pvp_world
/bp world set pvp
/bp practice setspawn
# Move to the FFA spawn inside the SAME world:
/ffa setspawn

/mv tp your_survival_world
/bp world set survival
```

Use `/bp world status` to check the roles. The `pvp`, `practice` and `ffa` entries should identify the same world.

## Wooden-axe selector GUI

When the player enters the Lobby, BlazingPractice clears the Lobby's ordinary inventory, armor and offhand, then gives one tagged wooden axe. Right-clicking it opens a GUI with four player-head buttons:

- Survival
- Practice & FFA
- SylixCloud Hosting
- Discord Server

The buttons execute the same actions and use the same config destinations/URLs as your four existing NPCs. Existing NPC placements and links are not touched.

To make one exact wooden block in the Lobby open the same GUI, stand in the Lobby, look at a wooden block within six blocks and run:

```text
/bp selector block set
/bp selector block info
```

Right-clicking that configured block opens the GUI. Disable it with:

```text
/bp selector block clear
```

## Inventory behavior

- Survival -> Lobby: the Survival profile is saved first; the Lobby inventory is then cleaned and rebuilt with the axe. Survival gear, collected items and XP are kept in the Survival profile, not deleted.
- Lobby -> Survival: the saved Survival profile is restored by the configured inventory backend.
- Practice -> FFA: the Practice/PvP profile is snapshotted, FFA receives a cleared temporary inventory, and leaving FFA restores the Practice snapshot.
- FFA -> Lobby/Survival: the original Practice/PvP snapshot is restored before the cross-world inventory save, then the destination profile is loaded.
- Internal SQLite profile swapping is used only when Multiverse-Inventories is not installed. If Multiverse-Inventories is active, configure separate `lobby`, `pvp`, and `survival` groups there; do not run a competing second inventory backend.

## Existing NPCs are reused

The GUI's four actions are shared with your existing NPC actions:

- Survival: first loaded world in `worlds.survival`.
- Practice & FFA: opens the mode selector.
- Hosting: `npcs.urls.hosting` (default `https://silixcloud.com`).
- Discord: `npcs.urls.discord`.

Only set or change NPC URLs/positions when you actually need to. The selector implementation does not recreate your NPCs.

## Release caution

This is development source. YAML/XML, static wiring and archive checks can be run locally, but full Java 25 Maven compilation and live Paper/Multiverse-Inventories verification remain pending. Back up worlds, player data, BlazingPractice SQLite and Multiverse-Inventories data; test with a disposable account before production use.
