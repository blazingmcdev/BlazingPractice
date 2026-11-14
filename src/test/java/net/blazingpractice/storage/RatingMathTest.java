package net.blazingpractice.storage;

/** Dependency-free executable smoke test: run with assertions enabled. */
public final class RatingMathTest {
    public static void main(String[] args) {
        assert Math.abs(RatingMath.expectedScore(1000, 1000) - 0.5) < 0.000001;
        assert RatingMath.newRating(1000, 1000, true, 32, 100) == 1016;
        assert RatingMath.newRating(1000, 1000, false, 32, 100) == 984;
        assert RatingMath.newRating(100, 3000, false, 32, 100) == 100;
        System.out.println("RatingMath smoke tests passed.");
    }
}
