package classpulse.server;

import classpulse.common.Views.Badge;
import classpulse.common.Views.HistoryEntry;
import classpulse.common.Views.StudentStats;
import classpulse.common.Views.TeacherStats;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Read-only analytics: the teacher overview, a student's history, and achievements. */
final class StatsRepository extends Repository {
    private final QuizRepository quizzes;

    StatsRepository(Database database, QuizRepository quizzes) {
        super(database);
        this.quizzes = quizzes;
    }

    TeacherStats teacherStats(long teacherId) {
        String sql = """
                SELECT
                  (SELECT COUNT(*) FROM quizzes WHERE teacher_id = ? AND archived = FALSE),
                  (SELECT COUNT(*) FROM sessions s JOIN quizzes z ON z.id = s.quiz_id WHERE z.teacher_id = ?),
                  (SELECT COUNT(*) FROM participants p JOIN sessions s ON s.id = p.session_id
                     JOIN quizzes z ON z.id = s.quiz_id WHERE z.teacher_id = ?),
                  (SELECT COALESCE(ROUND(100 * AVG(a.correct)), 0) FROM answers a
                     JOIN participants p ON p.id = a.participant_id JOIN sessions s ON s.id = p.session_id
                     JOIN quizzes z ON z.id = s.quiz_id WHERE z.teacher_id = ?)
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 1; i <= 4; i++) {
                statement.setLong(i, teacherId);
            }
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return new TeacherStats(row.getInt(1), row.getInt(2), row.getInt(3), row.getInt(4),
                        quizzes.listSessions(teacherId, 6));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load the overview.", e);
        }
    }

    /** Every game a signed-in student played, newest first. */
    List<HistoryEntry> history(long userId) {
        String sql = """
                SELECT s.started_at, z.title, t.full_name, p.score,
                       (SELECT COUNT(*) + 1 FROM participants o WHERE o.session_id = s.id AND o.score > p.score),
                       (SELECT COUNT(*) FROM participants o WHERE o.session_id = s.id),
                       p.correct_count, s.questions_asked
                FROM participants p
                JOIN sessions s ON s.id = p.session_id
                JOIN quizzes z ON z.id = s.quiz_id
                JOIN users t ON t.id = z.teacher_id
                WHERE p.user_id = ?
                ORDER BY s.started_at DESC, s.id DESC
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            List<HistoryEntry> entries = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    entries.add(new HistoryEntry(
                            row.getObject(1, LocalDateTime.class).format(QuizRepository.DISPLAY_TIME),
                            row.getString(2), row.getString(3), row.getInt(4), row.getInt(5), row.getInt(6),
                            row.getInt(7), row.getInt(8)));
                }
            }
            return entries;
        } catch (SQLException e) {
            throw new RepositoryException("Could not load the history.", e);
        }
    }

    /** Average time of the student's correct answers, or 0 if there are none. */
    long averageCorrectMs(long userId) {
        String sql = """
                SELECT COALESCE(AVG(a.response_ms), 0) FROM answers a
                JOIN participants p ON p.id = a.participant_id
                WHERE p.user_id = ? AND a.correct = TRUE
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return Math.round(row.getDouble(1));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load answer times.", e);
        }
    }

    static StudentStats studentStats(List<HistoryEntry> history, long averageCorrectMs) {
        int played = history.size();
        int points = history.stream().mapToInt(HistoryEntry::score).sum();
        int correct = history.stream().mapToInt(HistoryEntry::correct).sum();
        int asked = history.stream().mapToInt(HistoryEntry::questions).sum();
        int accuracy = asked == 0 ? 0 : Math.round(100f * correct / asked);
        int bestRank = history.stream().mapToInt(HistoryEntry::rank).min().orElse(0);
        int podiums = (int) history.stream().filter(h -> h.rank() <= 3).count();
        return new StudentStats(played, points, accuracy, bestRank, podiums, averageCorrectMs);
    }

    /** The achievements, worked out from the history. Pure Java, so it is easy to unit test. */
    static List<Badge> badges(List<HistoryEntry> history, StudentStats stats) {
        int played = stats.played();
        boolean perfect = history.stream().anyMatch(h -> h.questions() > 0 && h.correct() == h.questions());
        boolean champion = history.stream().anyMatch(h -> h.rank() == 1 && h.players() >= 2);
        List<Badge> badges = new ArrayList<>();
        badges.add(new Badge("First Steps", "Play your first quiz", played >= 1, Math.min(played, 1) + " of 1"));
        badges.add(new Badge("Regular", "Play 5 quizzes", played >= 5, Math.min(played, 5) + " of 5"));
        badges.add(new Badge("Dedicated", "Play 20 quizzes", played >= 20, Math.min(played, 20) + " of 20"));
        badges.add(new Badge("Perfect Round", "Answer every question correctly in a quiz", perfect,
                perfect ? "Done" : "Not yet"));
        badges.add(new Badge("Podium", "Finish in the top 3", stats.podiums() > 0,
                stats.podiums() + " podium finishes"));
        badges.add(new Badge("Champion", "Win a quiz against at least one classmate", champion,
                champion ? "Done" : "Not yet"));
        boolean sharp = played >= 3 && stats.averageAccuracy() >= 80;
        badges.add(new Badge("Sharp Mind", "Keep 80% accuracy over 3+ quizzes", sharp,
                stats.averageAccuracy() + "% accuracy"));
        boolean speedy = stats.averageCorrectMs() > 0 && stats.averageCorrectMs() <= 5000;
        badges.add(new Badge("Speedster", "Average under 5 s on correct answers", speedy,
                stats.averageCorrectMs() == 0 ? "No correct answers yet"
                        : String.format(java.util.Locale.ROOT, "%.1f s average", stats.averageCorrectMs() / 1000.0)));
        badges.add(new Badge("Point Collector", "Earn 10,000 points in total", stats.totalPoints() >= 10_000,
                Math.min(stats.totalPoints(), 10_000) + " of 10000"));
        return badges;
    }
}
