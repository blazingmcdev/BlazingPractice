# ZonePracticePro 8.1.0 Command Compatibility

Reference inspected: `ZonePractice-Pro-8.1.0(1).jar` descriptor plus the supplied `ZonePracticePro.zip` configuration archive. The ZIP contained no source code or Java command handlers.

BlazingPractice keeps all 26 original root labels and aliases:

| Command | Aliases |
|---|---|
| `/arena` | — |
| `/ladder` | — |
| `/setup` | — |
| `/duel` | — |
| `/accept` | — |
| `/settings` | — |
| `/practice` | `/prac`, `/zonepractice`, `/zoneprac`, `/zonep` |
| `/staff` | `/staffmode` |
| `/spectate` | `/spec` |
| `/party` | `/p` |
| `/matchinv` | — |
| `/event` | `/e` |
| `/statistics` | `/stat`, `/stats` |
| `/preview` | — |
| `/divisions` | — |
| `/unranked` | — |
| `/ranked` | — |
| `/queue` | — |
| `/editor` | `/kiteditor` |
| `/ffa` | — |
| `/copykit` | — |
| `/ignorequeue` | — |
| `/cosmetics` | `/cosmetc`, `/csmetic` |
| `/customqueue` | `/cqueue`, `/customkitqueue` |
| `/matchhistory` | `/mh`, `/matchhist` |
| `/nick` | `/nickname` |

## Current status

Command registration and aliases are retained. Functional BlazingPractice handlers currently exist for initial arena setup, duel/accept, Ranked/Unranked queues, ladder-specific queue selection, queue status/leave, ladder metadata administration, basic party operations, FFA operations, basic online statistics, Practice lobby navigation and `/bp` world/NPC administration. A Private Duel GUI now selects an online opponent and then a ladder.

The remaining original command roots do not yet reproduce every ZonePracticePro handler or its exact argument grammar. `/setup`, `/settings`, `/staff`, `/spectate`, `/matchinv`, `/event`, `/preview`, `/divisions`, `/editor`, `/copykit`, `/ignorequeue`, `/cosmetics`, `/customqueue`, `/matchhistory` and `/nick` still need complete handlers. Several advanced `/arena`, `/ladder`, `/practice`, `/party` and `/ffa` subcommands are also incomplete. Do not treat a declared command or permission as proof of behavioral compatibility.

The original `zpp.*` permission declarations and inheritance are retained in `plugin.yml`, together with new `blazingpractice.*` permission nodes. Handler-level parity with the original plugin's permission checks is not complete.

## Items explicitly out of scope

No separate `/world`, `/day`, `/5000` or `/force` gameplay command is added by BlazingPractice. Multiverse world creation/management remains under Multiverse commands such as `/mv create` and `/mv tp`. BlazingPractice only assigns existing worlds to its Lobby, Practice, FFA and Survival roles.
