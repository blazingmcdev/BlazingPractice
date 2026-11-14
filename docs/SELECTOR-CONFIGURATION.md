# Server Selector Configuration

BlazingPractice creates a separate editable selector directory on first startup:

```text
plugins/BlazingPractice/
└── selector/
    └── selector.yml
```

The selector is opened by the wooden axe by default. The config also contains the menu title/size and every menu icon/action. You do not need to edit the main `config.yml` for normal selector customization.

After changing the file, run:

```text
/bp selector reload
```

The command reloads the file and refreshes the selector hotbar for online players. Open the menu again to see the updated buttons.

## Add another head

Copy a block under `menu.buttons` and give it a unique key and unused slot. Example:

```yaml
  minigames:
    enabled: true
    slot: 19
    material: PLAYER_HEAD
    skin-owner: 'Notch'
    npc-name: 'MiniGames'
    name: '&6&lMINIGAMES'
    lore:
      - '&7Open the minigames server'
      - '&eClick to join'
    action:
      type: console_command
      command: 'mv tp {player} w:minigames --unsafe'
```

Slots start at `0`; a 27-slot menu allows `0` through `26`. The slot must be unique among enabled buttons.

## Remove a head

Either delete the full button section or set:

```yaml
    enabled: false
```

Then run `/bp selector reload`.

## Change a head's appearance

- `material`: normally `PLAYER_HEAD`; other valid item materials are also supported.
- `skin-owner`: Minecraft account name whose skin should be used. Keep it blank for a default head. The uploaded `npcs.yml` has `mirrorSkin: false` but does not identify a skin owner or texture, so those values cannot be recovered automatically from that file.
- `name`: displayed name, supporting legacy `&` colors.
- `lore`: list of description lines.
- `slot`: position in the selector inventory.

## Change actions and links

Supported action types:

### Console command

```yaml
    action:
      type: console_command
      command: 'mv tp {player} w:world --unsafe'
```

Runs as server console. Placeholders: `{player}`, `{uuid}`, `{world}`. This allows existing Multiverse commands from the supplied NPC file to be mirrored by the menu. Use only commands trusted administrators are willing to run with console permissions.

### One URL

```yaml
    action:
      type: url
      message: 'Click to open the website'
      url: 'https://example.com'
```

The player receives a clickable chat link. Only `http` and `https` URLs are accepted by the selector.

### Multiple URLs

```yaml
    action:
      type: links
      message: '&6Choose a link:'
      links:
        - label: 'Website'
          url: 'https://example.com'
        - label: 'Discord'
          url: 'https://discord.gg/example'
```

Each link appears as a separate clickable chat line.

Other actions are `message` and `practice_menu`.

## Mapping from the supplied npcs.yml

The initial four menu entries mirror the action values found in the provided file:

| Selector head | NPC name in supplied file | Action copied into selector config |
|---|---|---|
| Survival | `Survival` | `mv tp {player} w:world --unsafe` |
| Practice & FFA | `FFA` | `mv tp {player} w:practice --unsafe` |
| Hosting | `SylixCloud` | `https://sylixcloud.com` and `https://discord.gg/bSWnZ9ftmS` |
| Discord | `Discord` | `https://discord.gg` |

The external NPC plugin remains responsible for the NPC entities, locations, skins and its own YAML file. `npc-name` is a reference label in BlazingPractice's selector config; it is not an automatic live parser of another plugin's file. When you edit an action in your NPC plugin, also update the corresponding action in `plugins/BlazingPractice/selector/selector.yml` so the wooden-axe head stays in sync.

The two default Multiverse commands use the world names found in the uploaded file (`world` and `practice`). Change those names if your actual Multiverse world names are different.
