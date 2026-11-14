package net.blazingpractice.storage;

/** Pure Elo calculation helpers. Ratings are clamped to a configurable minimum. */
public final class RatingMath {
    private RatingMath() { }

    public static double expectedScore(int ownRating, int opponentRating) {
        return 1.0 / (1.0 + Math.pow(10.0, (opponentRating - ownRating) / 400.0));
    }

    public static int newRating(int ownRating, int opponentRating, boolean won, int kFactor, int minimum) {
        if (kFactor < 1 || minimum < 0) throw new IllegalArgumentException("Invalid Elo parameters");
        double score = won ? 1.0 : 0.0;
        return Math.max(minimum, ownRating + (int)Math.round(kFactor * (score - expectedScore(ownRating, opponentRating))));
    }
}
