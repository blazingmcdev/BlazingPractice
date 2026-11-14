# BlazingPractice Season 3 Roadmap

## Phase 1 — Foundation (current ZIP)
- [x] Maven/Paper 1.21.11/Java 25 project skeleton.
- [x] Plugin entry point, config and modular package layout.
- [x] Register the original 26 command roots and declared aliases.
- [x] Preserve the original `zpp.*` permission declaration tree.
- [x] Initial SQLite profile schema and async runtime access.
- [x] World-group inventory separation fallback.
- [x] Avoid conflicts with Multiverse-Inventories when installed.
- [x] Selector sword restricted to configured Practice/FFA worlds.
- [x] GitHub Releases update-checker foundation.
- [ ] Compile on JDK 25 with Maven.
- [ ] Test on a real Paper 1.21.11 server with Multiverse-Core.
- [ ] Test with Multiverse-Inventories enabled and confirm internal fallback is disabled.
- [ ] Validate inventory/ender-chest/XP/health/hunger migration with test UUIDs.

## Phase 2 — Legacy compatibility analysis + core Practice
- [ ] Inspect/decompile original command handlers and map every argument form.
- [ ] Map original permission checks and tab completion per handler.
- [ ] Implement arenas and ladders.
- [ ] Implement duel requests, accept/deny, rematch, match lifecycle and safe cleanup.
- [ ] Implement kits, kit preview/editor and match inventory.
- [ ] Implement unranked matchmaking, FFA, spectators and bot practice.
- [ ] Port `/arena`, `/ladder`, `/setup`, `/duel`, `/accept`, `/settings`, `/practice` and `/ffa` behavior.

## Phase 3 — Ranked
- [ ] ELO and Glicko-2 rating engines.
- [ ] Placement matches, seasons, win streaks and divisions.
- [ ] Ranked queues, leaderboards and anti-boosting controls.
- [ ] Transactional rating updates and match history.

## Phase 4 — Social and tournaments
- [ ] Parties, party chat, leader transfer and party matchmaking.
- [ ] Public parties and friends.
- [ ] Solo/team tournaments, single/double elimination and automatic brackets.

## Phase 5 — Integrated BedWars
- [ ] Solo, Doubles, Triples, 4v4, private matches and custom team sizes.
- [ ] Team/base setup, beds, respawns and final kills.
- [ ] Resource generators and item/upgrade shops.
- [ ] Team upgrades, traps, map rotation and match lifecycle.

## Phase 6 — GUI, profiles, statistics and cosmetics
- [ ] Premium configurable GUI framework, player heads, pagination, animations and sounds.
- [ ] Player profiles, statistics, leaderboards and rewards.
- [ ] Cosmetics and configurable messages/locales.

## Phase 7 — Replay and operations
- [ ] Match recording, playback, movement timeline and event capture.
- [ ] Staff review, audit logs, diagnostics, backups and restore.
- [ ] Anti-boosting/suspicious activity analysis.

## Phase 8 — Migration, compatibility and release
- [ ] Complete player-data migration tooling.
- [ ] Full command/permission/tab-completion parity matrix.
- [ ] Integration/regression tests on Paper 1.21.11 + Java 25.
- [ ] Performance/load testing and release packaging.
