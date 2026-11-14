# BlazingPractice Season 3 — Player Command Guide (0.10.5-dev)

This guide lists commands that have working handlers in the current development source. Use TAB after an argument to see valid suggestions. Commands in the compatibility-only section are registered labels, not working gameplay features yet.

## In-game help

- `/bp help` — main command overview.
- `/bp help player` — player-only commands.
- `/bp help admin` — administrator commands allowed by your permissions.
- `/bp help <command>` — detailed help for a command, for example `/bp help duel`.
- `/bp help features` — shows which commands are active and which are still pending.
- `/bp about` — plugin version and feature summary.
- `/bp status` — current server, world and inventory-backend status.
- `/bp bedwars status` — shows that BedWars is planned, not playable in this build.
- `/practice help`, `/duel help`, `/queue help`, `/ffa help`, `/party help`, `/ladder help` — shorter command-specific pages.

## Player commands

| Command | What it does | Example |
|---|---|---|
| `/practice` | Opens the Practice menu. | `/practice` |
| `/ranked [ladder]` | Opens Ranked ladder selection, or joins a selected ladder directly. | `/ranked sword` |
| `/unranked [ladder]` | Opens Unranked selection, or joins directly. | `/unranked axe` |
| `/queue ranked [ladder]` | Alternative Ranked queue syntax. | `/queue ranked sword` |
| `/queue unranked [ladder]` | Alternative Unranked queue syntax. | `/queue unranked axe` |
| `/queue leave` | Leaves your current queue. | `/queue leave` |
| `/queue list` | Shows queue waiting counts. | `/queue list` |
| `/duel <player>` | Opens the ladder picker for an online Practice player. | `/duel Steve` |
| `/duel <player> <ladder>` | Sends a direct duel invitation for that ladder. | `/duel Steve sword` |
| `/accept [challenger]` | Accepts your pending duel invitation. | `/accept Steve` |
| `/ffa join [ladder]` | Opens the FFA picker or joins one enabled ladder. | `/ffa join sword` |
| `/ffa leave` | Leaves FFA and restores the previous Practice state when supported by the active FFA flow. | `/ffa leave` |
| `/ffa status` | Shows FFA status and online count. | `/ffa status` |
| `/party create` | Creates a party. | `/party create` |
| `/party invite <player>` | Invites an online player (party leader only). | `/party invite Steve` |
| `/party accept` | Accepts a pending party invitation. | `/party accept` |
| `/party leave` | Leaves your party. | `/party leave` |
| `/party info` | Shows your party leader and members. | `/party info` |
| `/party chat <message>` | Sends a party-only message. | `/party chat ready?` |
| `/statistics [player] [ladder]` | Shows your or an online player's ladder rating and record. | `/statistics Steve sword` |
| `/ladder list` | Lists ladder IDs and their availability. | `/ladder list` |
| `/ladder info <ladder>` | Shows one ladder's metadata. | `/ladder info sword` |

`/que` is an alias for `/queue`. `/p` is an alias for `/party`. `/prac`, `/zonepractice`, `/zoneprac` and `/zonep` are aliases for `/practice`. `/stats` and `/stat` are aliases for `/statistics`.

## Administrator commands

These require the relevant permissions.

- `/bp world status` — shows configured world roles.
- `/bp world set lobby|pvp|survival` — assigns the world you are standing in to that role. Practice and FFA share the `pvp` role.
- `/bp practice setspawn` — saves the Practice spawn in the shared PvP world.
- `/bp practice join` — teleports you into the shared Practice world.
- `/bp lobby set` — saves the configured BlazingPractice lobby at your current position; `/bp lobby` returns you there.
- `/blazingpractice:arena help` — arena registration help. The namespaced root avoids conflicts with another plugin's `/arena`.
- `/blazingpractice:arena create <id> <ladder> <worldguard-region>` — registers a Practice-world WorldGuard region for one duel ladder.
- `/blazingpractice:arena setspawn1 <id>` and `/blazingpractice:arena setspawn2 <id>` — save the two opposite player positions.
- `/blazingpractice:arena enable <id>` — validates and enables an arena.
- `/blazingpractice:arena list` and `/blazingpractice:arena info <id>` — inspect arena pools and status.
- `/bp npc create|remove|list|url` — manage plugin-owned NPC entries, subject to permissions.
- `/bp selector menu|give|reload|block` — open or administer the Lobby selector.
- `/ladder create|clone|delete|enable|disable|set|reload` — manage ladder definitions.
- `/bp diagnostics` and `/bp updatecheck` — available only with their corresponding permissions.

## Planned modules

- BedWars is planned, but BedWars gameplay, maps, teams and match handling are not implemented in this build. `/bp bedwars status` reports this clearly.

## Compatibility-only commands (not fully implemented)

The following command roots remain registered for compatibility with the earlier project command layout, but their complete features are not implemented in this development build: `/setup`, `/settings`, `/staff`, `/spectate`, `/matchinv`, `/event`, `/preview`, `/divisions`, `/editor`, `/copykit`, `/ignorequeue`, `/cosmetics`, `/customqueue`, `/matchhistory` and `/nick`.

Do not rely on these as working features until a later release explicitly marks them implemented. Their TAB completion is intentionally not populated with invented arguments.

## TAB color note

The plugin returns dynamic suggestion strings for online players, pending duel challengers, enabled ladder IDs, registered arena IDs and Practice-world WorldGuard regions. Bukkit/Paper's classic TabCompleter API cannot force the Minecraft client's suggestion dropdown text to yellow; the plugin uses yellow formatting for its in-game help and usage messages.
