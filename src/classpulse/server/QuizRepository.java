package classpulse.server;

import classpulse.common.Question;
import classpulse.common.Quiz;
import classpulse.common.Views.QuestionStat;
import classpulse.common.Views.QuizSummary;
import classpulse.common.Views.ScoreEntry;
import classpulse.common.Views.SessionReport;
import classpulse.common.Views.SessionSummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All SQL for quizzes, sessions and reports (the Repository pattern). Every statement is a
 * PreparedStatement, so user input can never change a query. Every query about a quiz or
 * session also filters by teacher_id, so one teacher cannot read another teacher's data.
 *
 * Quizzes are never overwritten or hard-deleted. Editing archives the old version and
 * saves a new one, so reports of games played with the old version stay correct.
 */
final class QuizRepository extends Repository {
    record LoadedQuiz(long id, String title, List<Question> questions, List<Long> questionIds) {
        /** A copy with the questions in a random order, for the "shuffle questions" option. */
        LoadedQuiz shuffled(java.util.Random random) {
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                order.add(i);
            }
            java.util.Collections.shuffle(order, random);
            return new LoadedQuiz(id, title, order.stream().map(questions::get).toList(),
                    order.stream().map(questionIds::get).toList());
        }
    }

    record AnswerRecord(long questionId, int choice, boolean correct, long responseMs, int points) {
    }

    record ParticipantRecord(Long userId, String name, int score, int correctCount, List<AnswerRecord> answers) {
    }

    record SessionRecord(long quizId, String roomCode, LocalDateTime startedAt, LocalDateTime endedAt,
                         boolean completed, boolean shuffled, int questionsAsked,
                         List<ParticipantRecord> participants) {
    }

    static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    QuizRepository(Database database) {
        super(database);
    }

    long saveQuiz(long teacherId, Quiz quiz) {
        try {
            return inTransaction(connection -> insertQuiz(connection, teacherId, quiz));
        } catch (SQLException e) {
            throw new RepositoryException("Could not save the quiz.", e);
        }
    }

    /** Archives the old version and saves the new one in one transaction. Returns false if not found. */
    boolean updateQuiz(long teacherId, long quizId, Quiz quiz) {
        try {
            return inTransaction(connection -> {
                if (!archive(connection, teacherId, quizId)) {
                    return false;
                }
                insertQuiz(connection, teacherId, quiz);
                return true;
            });
        } catch (SQLException e) {
            throw new RepositoryException("Could not update the quiz.", e);
        }
    }

    /** Hides a quiz from the list. Its past games and reports are kept. */
    boolean deleteQuiz(long teacherId, long quizId) {
        try {
            return inTransaction(connection -> archive(connection, teacherId, quizId));
        } catch (SQLException e) {
            throw new RepositoryException("Could not delete the quiz.", e);
        }
    }

    private static boolean archive(Connection connection, long teacherId, long quizId) throws SQLException {
        String sql = "UPDATE quizzes SET archived = TRUE WHERE id = ? AND teacher_id = ? AND archived = FALSE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, quizId);
            statement.setLong(2, teacherId);
            return statement.executeUpdate() == 1;
        }
    }

    private static long insertQuiz(Connection connection, long teacherId, Quiz quiz) throws SQLException {
        long quizId;
        String insertQuiz = "INSERT INTO quizzes (teacher_id, title) VALUES (?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(insertQuiz, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, teacherId);
            statement.setString(2, quiz.title());
            statement.executeUpdate();
            quizId = generatedId(statement);
        }
        String insertQuestion = """
                INSERT INTO questions (quiz_id, position, text, option_a, option_b, option_c, option_d,
                                       correct_index, seconds)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(insertQuestion)) {
            int position = 1;
            for (Question question : quiz.questions()) {
                statement.setLong(1, quizId);
                statement.setInt(2, position++);
                statement.setString(3, question.text());
                for (int i = 0; i < Question.OPTION_COUNT; i++) {
                    statement.setString(4 + i, question.options().get(i));
                }
                statement.setInt(8, question.correctIndex());
                statement.setInt(9, question.seconds());
                statement.addBatch();
            }
            statement.executeBatch();
        }
        return quizId;
    }

    List<QuizSummary> listQuizzes(long teacherId) {
        String sql = """
                SELECT z.id, z.title, COUNT(q.id), z.created_at,
                       (SELECT COUNT(*) FROM sessions s WHERE s.quiz_id = z.id)
                FROM quizzes z JOIN questions q ON q.quiz_id = z.id
                WHERE z.teacher_id = ? AND z.archived = FALSE
                GROUP BY z.id, z.title, z.created_at
                ORDER BY z.id DESC
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            List<QuizSummary> quizzes = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    quizzes.add(new QuizSummary(row.getLong(1), row.getString(2), row.getInt(3),
                            row.getObject(4, LocalDateTime.class).format(DISPLAY_TIME), row.getInt(5)));
                }
            }
            return quizzes;
        } catch (SQLException e) {
            throw new RepositoryException("Could not list quizzes.", e);
        }
    }

    /** Loads a quiz the teacher owns that has not been deleted or replaced. */
    Optional<LoadedQuiz> loadQuiz(long teacherId, long quizId) {
        String titleSql = "SELECT title FROM quizzes WHERE id = ? AND teacher_id = ? AND archived = FALSE";
        String questionSql = """
                SELECT id, text, option_a, option_b, option_c, option_d, correct_index, seconds
                FROM questions WHERE quiz_id = ? ORDER BY position
                """;
        try (Connection connection = database.connect();
             PreparedStatement titleStatement = connection.prepareStatement(titleSql);
             PreparedStatement questionStatement = connection.prepareStatement(questionSql)) {
            titleStatement.setLong(1, quizId);
            titleStatement.setLong(2, teacherId);
            String title;
            try (ResultSet row = titleStatement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                title = row.getString(1);
            }
            questionStatement.setLong(1, quizId);
            List<Question> questions = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            try (ResultSet row = questionStatement.executeQuery()) {
                while (row.next()) {
                    ids.add(row.getLong(1));
                    List<String> options = List.of(row.getString(3), row.getString(4),
                            row.getString(5), row.getString(6));
                    questions.add(new Question(row.getString(2), options, row.getInt(7), row.getInt(8)));
                }
            }
            return Optional.of(new LoadedQuiz(quizId, title, questions, ids));
        } catch (SQLException e) {
            throw new RepositoryException("Could not load the quiz.", e);
        }
    }

    /**
     * Loads a quiz by id even if it was archived later (added in the homework upgrade).
     * Homework points at the version that was set, so editing the quiz never changes it.
     * Callers must check permission first.
     */
    Optional<LoadedQuiz> loadQuizAnyVersion(long quizId) {
        String titleSql = "SELECT title FROM quizzes WHERE id = ?";
        String questionSql = """
                SELECT id, text, option_a, option_b, option_c, option_d, correct_index, seconds
                FROM questions WHERE quiz_id = ? ORDER BY position
                """;
        try (Connection connection = database.connect();
             PreparedStatement titleStatement = connection.prepareStatement(titleSql);
             PreparedStatement questionStatement = connection.prepareStatement(questionSql)) {
            titleStatement.setLong(1, quizId);
            String title;
            try (ResultSet row = titleStatement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                title = row.getString(1);
            }
            questionStatement.setLong(1, quizId);
            List<Question> questions = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            try (ResultSet row = questionStatement.executeQuery()) {
                while (row.next()) {
                    ids.add(row.getLong(1));
                    questions.add(new Question(row.getString(2), List.of(row.getString(3), row.getString(4),
                            row.getString(5), row.getString(6)), row.getInt(7), row.getInt(8)));
                }
            }
            return Optional.of(new LoadedQuiz(quizId, title, questions, ids));
        } catch (SQLException e) {
            throw new RepositoryException("Could not load the quiz.", e);
        }
    }

    /** Saves a whole finished session in one transaction, and returns its id. */
    long saveSession(SessionRecord session) {
        try {
            return inTransaction(connection -> {
                long sessionId;
                String insertSession = """
                        INSERT INTO sessions (quiz_id, room_code, started_at, ended_at, completed, shuffled,
                                              questions_asked)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement statement =
                             connection.prepareStatement(insertSession, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, session.quizId());
                    statement.setString(2, session.roomCode());
                    statement.setObject(3, session.startedAt());
                    statement.setObject(4, session.endedAt());
                    statement.setBoolean(5, session.completed());
                    statement.setBoolean(6, session.shuffled());
                    statement.setInt(7, session.questionsAsked());
                    statement.executeUpdate();
                    sessionId = generatedId(statement);
                }
                String insertParticipant = """
                        INSERT INTO participants (session_id, user_id, name, score, correct_count)
                        VALUES (?, ?, ?, ?, ?)
                        """;
                String insertAnswer = """
                        INSERT INTO answers (participant_id, question_id, choice, correct, response_ms, points)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """;
                try (PreparedStatement participantStatement =
                             connection.prepareStatement(insertParticipant, Statement.RETURN_GENERATED_KEYS);
                     PreparedStatement answerStatement = connection.prepareStatement(insertAnswer)) {
                    for (ParticipantRecord participant : session.participants()) {
                        participantStatement.setLong(1, sessionId);
                        participantStatement.setObject(2, participant.userId());
                        participantStatement.setString(3, participant.name());
                        participantStatement.setInt(4, participant.score());
                        participantStatement.setInt(5, participant.correctCount());
                        participantStatement.executeUpdate();
                        long participantId = generatedId(participantStatement);
                        for (AnswerRecord answer : participant.answers()) {
                            answerStatement.setLong(1, participantId);
                            answerStatement.setLong(2, answer.questionId());
                            answerStatement.setInt(3, answer.choice());
                            answerStatement.setBoolean(4, answer.correct());
                            answerStatement.setLong(5, answer.responseMs());
                            answerStatement.setInt(6, answer.points());
                            answerStatement.addBatch();
                        }
                    }
                    answerStatement.executeBatch();
                }
                return sessionId;
            });
        } catch (SQLException e) {
            throw new RepositoryException("Could not save the session.", e);
        }
    }

    static final String SESSION_COLUMNS = """
            SELECT s.id, z.title, s.room_code, s.started_at, s.completed,
                   (SELECT COUNT(*) FROM participants p WHERE p.session_id = s.id)
            FROM sessions s JOIN quizzes z ON z.id = s.quiz_id
            """;

    List<SessionSummary> listSessions(long teacherId, int limit) {
        String sql = SESSION_COLUMNS + " WHERE z.teacher_id = ? ORDER BY s.id DESC LIMIT ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            statement.setInt(2, limit);
            List<SessionSummary> sessions = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    sessions.add(toSummary(row));
                }
            }
            return sessions;
        } catch (SQLException e) {
            throw new RepositoryException("Could not list sessions.", e);
        }
    }

    Optional<SessionReport> loadReport(long teacherId, long sessionId) {
        String summarySql = SESSION_COLUMNS + " WHERE s.id = ? AND z.teacher_id = ?";
        String rankingSql = "SELECT name, score FROM participants WHERE session_id = ? ORDER BY score DESC, name";
        String statsSql = """
                SELECT q.position, q.text, COUNT(a.id), COALESCE(SUM(a.correct), 0), COALESCE(AVG(a.response_ms), 0)
                FROM sessions s
                JOIN questions q ON q.quiz_id = s.quiz_id
                LEFT JOIN answers a ON a.question_id = q.id
                     AND a.participant_id IN (SELECT id FROM participants WHERE session_id = s.id)
                WHERE s.id = ?
                GROUP BY q.id, q.position, q.text
                ORDER BY q.position
                """;
        try (Connection connection = database.connect();
             PreparedStatement summaryStatement = connection.prepareStatement(summarySql);
             PreparedStatement rankingStatement = connection.prepareStatement(rankingSql);
             PreparedStatement statsStatement = connection.prepareStatement(statsSql)) {
            summaryStatement.setLong(1, sessionId);
            summaryStatement.setLong(2, teacherId);
            SessionSummary summary;
            try (ResultSet row = summaryStatement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                summary = toSummary(row);
            }
            rankingStatement.setLong(1, sessionId);
            List<ScoreEntry> ranking = new ArrayList<>();
            try (ResultSet row = rankingStatement.executeQuery()) {
                int position = 0;
                int rank = 0;
                int previousScore = Integer.MIN_VALUE;
                while (row.next()) {
                    position++;
                    int score = row.getInt(2);
                    if (score != previousScore) {
                        rank = position; // Students with equal scores share a rank.
                        previousScore = score;
                    }
                    ranking.add(new ScoreEntry(rank, row.getString(1), score));
                }
            }
            statsStatement.setLong(1, sessionId);
            List<QuestionStat> stats = new ArrayList<>();
            try (ResultSet row = statsStatement.executeQuery()) {
                while (row.next()) {
                    stats.add(new QuestionStat(row.getInt(1), row.getString(2), row.getInt(3),
                            row.getInt(4), Math.round(row.getDouble(5))));
                }
            }
            return Optional.of(new SessionReport(summary, ranking, stats));
        } catch (SQLException e) {
            throw new RepositoryException("Could not load the report.", e);
        }
    }

    static SessionSummary toSummary(ResultSet row) throws SQLException {
        String started = row.getObject(4, LocalDateTime.class).format(DISPLAY_TIME);
        return new SessionSummary(row.getLong(1), row.getString(2), row.getString(3), started,
                row.getInt(6), row.getBoolean(5));
    }
}
