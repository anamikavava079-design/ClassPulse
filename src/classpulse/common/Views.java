package classpulse.common;

import java.io.Serializable;
import java.util.List;

/** Small read-only data records that the server sends to clients for display. */
public final class Views {
    private Views() {
    }

    /** The two kinds of account. Stored in MySQL as the enum name. */
    public enum Role { TEACHER, STUDENT }

    /** The signed-in user, as the client sees it. The password hash never leaves the server. */
    public record Account(String username, String fullName, String email, Role role) implements Serializable {
    }

    public record QuizSummary(long id, String title, int questionCount, String createdAt, int timesPlayed)
            implements Serializable {
    }

    public record ScoreEntry(int rank, String name, int score) implements Serializable {
    }

    /** How one student did. The teacher's copy of a message carries null instead. */
    public record PersonalResult(boolean answered, boolean correct, int pointsEarned, int totalScore, int rank,
                                 int streak, int streakBonus) implements Serializable {
    }

    public record SessionSummary(long id, String quizTitle, String roomCode, String startedAt,
                                 int players, boolean completed) implements Serializable {
    }

    public record QuestionStat(int number, String text, int answered, int correct, long averageMs)
            implements Serializable {
    }

    public record SessionReport(SessionSummary summary, List<ScoreEntry> ranking, List<QuestionStat> questions)
            implements Serializable {
        public SessionReport {
            ranking = List.copyOf(ranking);
            questions = List.copyOf(questions);
        }
    }

    /** Numbers for the teacher's overview page. */
    public record TeacherStats(int quizzes, int sessions, int studentsReached, int averageAccuracy,
                               List<SessionSummary> recent) implements Serializable {
        public TeacherStats {
            recent = List.copyOf(recent);
        }
    }

    /** One quiz that a signed-in student played. */
    public record HistoryEntry(String playedAt, String quizTitle, String teacherName, int score, int rank,
                               int players, int correct, int questions) implements Serializable {
        public int accuracy() {
            return questions == 0 ? 0 : Math.round(100f * correct / questions);
        }
    }

    public record StudentStats(int played, int totalPoints, int averageAccuracy, int bestRank, int podiums,
                               long averageCorrectMs) implements Serializable {
    }

    /** An achievement. progress is shown under the name, for example "3 of 5 quizzes". */
    public record Badge(String name, String description, boolean earned, String progress) implements Serializable {
    }

    // ----- Classes and homework (added in the homework upgrade) -----

    /** A teacher's class. Students join it with joinCode. */
    public record ClassInfo(long id, String name, String joinCode, int members) implements Serializable {
    }

    /** One homework assignment, as the teacher sees it. */
    public record HomeworkInfo(long id, String className, String quizTitle, String dueAt, int submitted,
                               int members, int averagePercent, boolean open) implements Serializable {
    }

    /** One homework assignment, as a student sees it. correct is 0 until it is done. */
    public record StudentHomework(long id, String className, String quizTitle, String teacherName, String dueAt,
                                  boolean done, int correct, int questions, boolean open) implements Serializable {
    }

    /** A homework question. Like a live question, it carries no correct answer. */
    public record HomeworkQuestion(String text, List<String> options, int seconds) implements Serializable {
        public HomeworkQuestion {
            options = List.copyOf(options);
        }
    }
}
