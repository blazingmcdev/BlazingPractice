package net.blazingpractice.arena;

import java.util.UUID;

/** Runtime-only lock that guarantees one match owns an arena at a time. */
public record ArenaReservation(Arena arena, UUID token) { }
