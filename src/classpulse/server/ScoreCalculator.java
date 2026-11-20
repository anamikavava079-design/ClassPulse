package classpulse.server;

/**
 * A correct answer earns 500 points, plus up to 500 more for answering quickly.
 * Answering several questions correctly in a row adds a streak bonus.
 */
final class ScoreCalculator {
    static final int BASE_POINTS = 500;
    static final int MAX_SPEED_BONUS = 500;
    static final int STREAK_STEP = 50;
    static final int MAX_STREAK_BONUS = 200;

    private ScoreCalculator() {
    }

    static int points(boolean correct, long responseMs, long limitMs) {
        if (!correct) {
            return 0;
        }
        long clamped = Math.max(0, Math.min(responseMs, limitMs));
        double timeLeft = 1.0 - (double) clamped / limitMs;
        return BASE_POINTS + (int) Math.round(MAX_SPEED_BONUS * timeLeft);
    }

    /** streak is the number of correct answers in a row, including this one: 2 gives +50, 3 gives +100, up to +200. */
    static int streakBonus(int streak) {
        return streak < 2 ? 0 : Math.min((streak - 1) * STREAK_STEP, MAX_STREAK_BONUS);
    }
}
