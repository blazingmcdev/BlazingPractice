# BlazingPractice Selector folder

This folder documents the in-game server selector that is opened by the configured selector item (default: a wooden axe).

## Runtime files

After the plugin starts for the first time, it creates:

```text
plugins/BlazingPractice/
└── selector/
    └── selector.yml
```

The bundled default file is at `src/main/resources/selector/selector.yml` in this source project. Edit the runtime file, then use:

```text
/bp selector reload
```

## Add, remove or change a head

- Add: copy one complete entry under `menu.buttons`, give it a unique key, and choose an unused `slot` between `0` and `menu.size - 1`.
- Remove: delete the entry or set `enabled: false`.
- Change its icon: edit `material` (use `PLAYER_HEAD` for a player head).
- Change its player skin: set `skin-owner` to a Minecraft username. Leave it blank for the default head appearance.
- Change title and lore: edit `name` and `lore`.
- Change what it does: edit the `action` block.

## Action types

`console_command` runs a command as the server console. Supported placeholders are `{player}`, `{uuid}`, and `{world}`. Restrict file editing to trusted administrators because console commands have server-level permissions.

`url` sends one clickable URL. `links` sends a message and several clickable links. Only `http` and `https` URLs are accepted. `message` sends text. `practice_menu` opens the in-game Practice/FFA selector.

## NPC mapping from the supplied npcs.yml

The four menu entries are based on the NPC names and right-click actions in the file supplied for this task:

| Menu entry | NPC name | Imported action |
|---|---|---|
| `survival` | `Survival` | `mv tp {player} w:world --unsafe` |
| `practice` | `FFA` | `mv tp {player} w:practice --unsafe` |
| `hosting` | `SylixCloud` | Website and SylixCloud Discord links |
| `discord` | `Discord` | `https://discord.gg` |

The NPC location coordinates and entity settings are deliberately not copied into BlazingPractice. Your NPC plugin continues to own the NPCs and their positions. The selector menu uses the corresponding actions, configured here. The `npc-name` field is an editable reference label; it does not move, spawn, or modify the external NPC.

The FFA/Practice command uses the world name `practice` because that is what your uploaded NPC action uses. Change the command to your real Multiverse world name if it differs. The Survival command similarly uses `world` from the uploaded file.
