package classpulse.server;

import classpulse.common.Views.ClassInfo;
import classpulse.common.Views.HomeworkInfo;
import classpulse.common.Views.StudentHomework;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * All SQL for classes and homework (added in the homework upgrade). Like the other
 * repositories it extends Repository, uses only PreparedStatements, and checks ownership
 * in every query: a teacher only sees their own classes, a student only their own homework.
 */
final class HomeworkRepository extends Repository {
    /** A student in a class, with what is needed to email them. */
    record Member(long id, String fullName, String email) {
    }

    /** One homework, with everything the server needs to run it. */
    record Task(long id, long quizId, String quizTitle, String className, long teacherId, String teacherName,
                String teacherEmail, LocalDateTime dueAt) {
    }

    /** One line of the summary the teacher gets after the deadline. done is false if nothing was handed in. */
    record SummaryRow(String name, boolean done, int correct, int questions) {
    }

    private static final String TASK_COLUMNS = """
            SELECT h.id, h.quiz_id, z.title, c.name, u.id, u.full_name, u.email, h.due_at
            FROM homework h
            JOIN classes c ON c.id = h.class_id
            JOIN quizzes z ON z.id = h.quiz_id
            JOIN users u ON u.id = c.teacher_id
            """;

    HomeworkRepository(Database database) {
        super(database);
    }

    // ----- Classes -----

    /** Creates a class, trying new codes until one is free. Returns the class id. */
    long createClass(long teacherId, String name, Supplier<String> codes) {
        String sql = "INSERT INTO classes (teacher_id, name, join_code) VALUES (?, ?, ?)";
        for (int attempt = 0; attempt < 20; attempt++) {
            try (Connection connection = database.connect();
                 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, teacherId);
                statement.setString(2, name);
                statement.setString(3, codes.get());
                statement.executeUpdate();
                return generatedId(statement);
            } catch (SQLIntegrityConstraintViolationException e) {
                // That join code is already used by another class: try another one.
            } catch (SQLException e) {
                throw new RepositoryException("Could not create the class.", e);
            }
        }
        throw new RepositoryException("Could not find a free class code.", null);
    }

    List<ClassInfo> listClasses(long teacherId) {
        String sql = """
                SELECT c.id, c.name, c.join_code, (SELECT COUNT(*) FROM class_members m WHERE m.class_id = c.id)
                FROM classes c WHERE c.teacher_id = ? ORDER BY c.id DESC
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            List<ClassInfo> classes = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    classes.add(new ClassInfo(row.getLong(1), row.getString(2), row.getString(3), row.getInt(4)));
                }
            }
            return classes;
        } catch (SQLException e) {
            throw new RepositoryException("Could not list classes.", e);
        }
    }

    /** Adds the student to the class with this code. Returns the class name, or empty if no class has the code. */
    Optional<String> joinClass(long studentId, String code) {
        String find = "SELECT id, name FROM classes WHERE join_code = ?";
        String insert = "INSERT IGNORE INTO class_members (class_id, student_id) VALUES (?, ?)";
        try (Connection connection = database.connect();
             PreparedStatement findStatement = connection.prepareStatement(find);
             PreparedStatement insertStatement = connection.prepareStatement(insert)) {
            findStatement.setString(1, code);
            long classId;
            String name;
            try (ResultSet row = findStatement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                classId = row.getLong(1);
                name = row.getString(2);
            }
            insertStatement.setLong(1, classId);
            insertStatement.setLong(2, studentId);
            insertStatement.executeUpdate(); // IGNORE: joining twice is harmless.
            return Optional.of(name);
        } catch (SQLException e) {
            throw new RepositoryException("Could not join the class.", e);
        }
    }

    List<String> classNamesOf(long studentId) {
        String sql = """
                SELECT c.name FROM classes c JOIN class_members m ON m.class_id = c.id
                WHERE m.student_id = ? ORDER BY c.name
                """;
        List<String> names = new ArrayList<>();
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    names.add(row.getString(1));
                }
            }
            return names;
        } catch (SQLException e) {
            throw new RepositoryException("Could not list your classes.", e);
        }
    }

    List<Member> members(long classId) {
        String sql = """
                SELECT u.id, u.full_name, u.email FROM class_members m JOIN users u ON u.id = m.student_id
                WHERE m.class_id = ? ORDER BY u.full_name
                """;
        return memberQuery(sql, classId);
    }

    /** Members of the homework's class who have not handed it in yet. */
    List<Member> notSubmitted(long homeworkId) {
        String sql = """
                SELECT u.id, u.full_name, u.email
                FROM homework h
                JOIN class_members m ON m.class_id = h.class_id
                JOIN users u ON u.id = m.student_id
                WHERE h.id = ? AND NOT EXISTS
                      (SELECT 1 FROM homework_submissions s WHERE s.homework_id = h.id AND s.student_id = u.id)
                ORDER BY u.full_name
                """;
        return memberQuery(sql, homeworkId);
    }

    private List<Member> memberQuery(String sql, long id) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            List<Member> members = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    members.add(new Member(row.getLong(1), row.getString(2), row.getString(3)));
                }
            }
            return members;
        } catch (SQLException e) {
            throw new RepositoryException("Could not list the class members.", e);
        }
    }

    // ----- Homework -----

    /**
     * Sets homework, but only if the teacher owns both the class and the (current) quiz.
     * Returns the new id, or 0 if either was not found.
     */
    long assign(long teacherId, long classId, long quizId, LocalDateTime createdAt, LocalDateTime dueAt,
                LocalDateTime remindAt) {
        String sql = """
                INSERT INTO homework (class_id, quiz_id, created_at, due_at, remind_at)
                SELECT c.id, z.id, ?, ?, ?
                FROM classes c JOIN quizzes z ON z.teacher_id = c.teacher_id
                WHERE c.id = ? AND c.teacher_id = ? AND z.id = ? AND z.archived = FALSE
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setObject(1, createdAt);
            statement.setObject(2, dueAt);
            statement.setObject(3, remindAt);
            statement.setLong(4, classId);
            statement.setLong(5, teacherId);
            statement.setLong(6, quizId);
            return statement.executeUpdate() == 1 ? generatedId(statement) : 0;
        } catch (SQLException e) {
            throw new RepositoryException("Could not set the homework.", e);
        }
    }

    List<HomeworkInfo> listHomework(long teacherId, LocalDateTime now) {
        String sql = """
                SELECT h.id, c.name, z.title, h.due_at,
                       (SELECT COUNT(*) FROM homework_submissions s WHERE s.homework_id = h.id),
                       (SELECT COUNT(*) FROM class_members m WHERE m.class_id = h.class_id),
                       (SELECT COALESCE(AVG(100 * s.correct_count / NULLIF(s.questions, 0)), 0)
                        FROM homework_submissions s WHERE s.homework_id = h.id)
                FROM homework h JOIN classes c ON c.id = h.class_id JOIN quizzes z ON z.id = h.quiz_id
                WHERE c.teacher_id = ?
                ORDER BY h.due_at DESC
                LIMIT 200
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, teacherId);
            List<HomeworkInfo> list = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    LocalDateTime due = row.getObject(4, LocalDateTime.class);
                    list.add(new HomeworkInfo(row.getLong(1), row.getString(2), row.getString(3),
                            due.format(QuizRepository.DISPLAY_TIME), row.getInt(5), row.getInt(6),
                            (int) Math.round(row.getDouble(7)), now.isBefore(due)));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RepositoryException("Could not list homework.", e);
        }
    }

    List<StudentHomework> studentHomework(long studentId, LocalDateTime now) {
        String sql = """
                SELECT h.id, c.name, z.title, u.full_name, h.due_at, s.correct_count, s.questions,
                       (SELECT COUNT(*) FROM questions q WHERE q.quiz_id = z.id)
                FROM homework h
                JOIN classes c ON c.id = h.class_id
                JOIN class_members m ON m.class_id = c.id AND m.student_id = ?
                JOIN quizzes z ON z.id = h.quiz_id
                JOIN users u ON u.id = c.teacher_id
                LEFT JOIN homework_submissions s ON s.homework_id = h.id AND s.student_id = m.student_id
                ORDER BY h.due_at DESC
                LIMIT 200
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            List<StudentHomework> list = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    LocalDateTime due = row.getObject(5, LocalDateTime.class);
                    int correct = row.getInt(6);
                    boolean done = !row.wasNull();
                    int questions = done ? row.getInt(7) : row.getInt(8);
                    list.add(new StudentHomework(row.getLong(1), row.getString(2), row.getString(3),
                            row.getString(4), due.format(QuizRepository.DISPLAY_TIME), done, correct, questions,
                            now.isBefore(due)));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RepositoryException("Could not list your homework.", e);
        }
    }

    /** Loads homework only if the student is a member of its class. */
    Optional<Task> taskForStudent(long studentId, long homeworkId) {
        String sql = TASK_COLUMNS + """
                JOIN class_members m ON m.class_id = c.id
                WHERE h.id = ? AND m.student_id = ?
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, homeworkId);
            statement.setLong(2, studentId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? Optional.of(toTask(row)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not load the homework.", e);
        }
    }

    boolean hasSubmitted(long homeworkId, long studentId) {
        String sql = "SELECT 1 FROM homework_submissions WHERE homework_id = ? AND student_id = ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, homeworkId);
            statement.setLong(2, studentId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not check the homework.", e);
        }
    }

    /** Saves a submission. Returns false if this student already handed it in (the UNIQUE key stops it). */
    boolean saveSubmission(long homeworkId, long studentId, int correct, int questions, LocalDateTime at) {
        String sql = """
                INSERT INTO homework_submissions (homework_id, student_id, correct_count, questions, submitted_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, homeworkId);
            statement.setLong(2, studentId);
            statement.setInt(3, correct);
            statement.setInt(4, questions);
            statement.setObject(5, at);
            statement.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            return false;
        } catch (SQLException e) {
            throw new RepositoryException("Could not save the homework.", e);
        }
    }

    // ----- Automatic emails -----

    /** Homework whose reminder time has come, still before the deadline, and not reminded yet. */
    List<Task> remindersDue(LocalDateTime now) {
        return tasks(TASK_COLUMNS + " WHERE h.reminder_sent = FALSE AND h.remind_at <= ? AND h.due_at > ?", now, now);
    }

    /** Homework whose deadline has passed and whose summary was not sent yet. */
    List<Task> summariesDue(LocalDateTime now) {
        return tasks(TASK_COLUMNS + " WHERE h.summary_sent = FALSE AND h.due_at <= ?", now);
    }

    /**
     * Marks the reminder or summary as sent. Only one caller can win this UPDATE, so even if
     * two checks ran at once, nobody would get the same email twice.
     */
    boolean claim(long homeworkId, boolean summary) {
        String column = summary ? "summary_sent" : "reminder_sent"; // Fixed names, never user input.
        String sql = "UPDATE homework SET " + column + " = TRUE WHERE id = ? AND " + column + " = FALSE";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, homeworkId);
            return statement.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RepositoryException("Could not update the homework.", e);
        }
    }

    List<SummaryRow> summary(long homeworkId) {
        String sql = """
                SELECT u.full_name, s.correct_count, s.questions
                FROM homework h
                JOIN class_members m ON m.class_id = h.class_id
                JOIN users u ON u.id = m.student_id
                LEFT JOIN homework_submissions s ON s.homework_id = h.id AND s.student_id = u.id
                WHERE h.id = ?
                ORDER BY s.correct_count IS NULL, s.correct_count DESC, u.full_name
                """;
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, homeworkId);
            List<SummaryRow> rows = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    int correct = row.getInt(2);
                    boolean done = !row.wasNull();
                    rows.add(new SummaryRow(row.getString(1), done, correct, row.getInt(3)));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new RepositoryException("Could not load the homework summary.", e);
        }
    }

    private List<Task> tasks(String sql, Object... values) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            List<Task> list = new ArrayList<>();
            try (ResultSet row = statement.executeQuery()) {
                while (row.next()) {
                    list.add(toTask(row));
                }
            }
            return list;
        } catch (SQLException e) {
            throw new RepositoryException("Could not check homework deadlines.", e);
        }
    }

    private static Task toTask(ResultSet row) throws SQLException {
        return new Task(row.getLong(1), row.getLong(2), row.getString(3), row.getString(4), row.getLong(5),
                row.getString(6), row.getString(7), row.getObject(8, LocalDateTime.class));
    }
}
