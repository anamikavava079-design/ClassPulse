package classpulse.server;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Opens JDBC connections and creates the tables on first run.
 *
 * Tables (8):
 *   users        - teachers and students, with a verified flag for the email check
 *   email_codes  - one-time codes for email verification and password reset (stored hashed)
 *   email_log    - every email the server tried to send, and whether it worked
 *   quizzes      - owned by a teacher; edited quizzes are archived, never overwritten
 *   questions    - four options each
 *   sessions     - one row per hosted game
 *   participants - one row per student in a game; linked to users when signed in
 *   answers      - one row per answer, with the server-measured response time
 *
 * Added in the homework upgrade (4 more, 12 in total):
 *   classes              - a teacher's class, which students join with a 6-character code
 *   class_members        - which students are in which class (many-to-many)
 *   homework             - a quiz set for a class, with a deadline and a reminder time
 *   homework_submissions - one row per student per homework; UNIQUE stops a second try
 */
final class Database {
    private static final List<String> SCHEMA = List.of(
            """
            CREATE TABLE IF NOT EXISTS users (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(30) NOT NULL UNIQUE,
                email VARCHAR(100) NOT NULL UNIQUE,
                full_name VARCHAR(60) NOT NULL,
                role ENUM('TEACHER', 'STUDENT') NOT NULL,
                password_hash VARCHAR(200) NOT NULL,
                email_verified BOOLEAN NOT NULL DEFAULT FALSE,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                last_login_at DATETIME NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS email_codes (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                user_id BIGINT NOT NULL,
                purpose ENUM('VERIFY_EMAIL', 'RESET_PASSWORD') NOT NULL,
                code_hash VARCHAR(200) NOT NULL,
                created_at DATETIME NOT NULL,
                expires_at DATETIME NOT NULL,
                attempts INT NOT NULL DEFAULT 0,
                UNIQUE (user_id, purpose),
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS email_log (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                recipient VARCHAR(100) NOT NULL,
                subject VARCHAR(200) NOT NULL,
                kind VARCHAR(30) NOT NULL,
                delivered BOOLEAN NOT NULL,
                error VARCHAR(300) NULL,
                sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS quizzes (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                teacher_id BIGINT NOT NULL,
                title VARCHAR(100) NOT NULL,
                archived BOOLEAN NOT NULL DEFAULT FALSE,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS questions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                quiz_id BIGINT NOT NULL,
                position INT NOT NULL,
                text VARCHAR(300) NOT NULL,
                option_a VARCHAR(120) NOT NULL,
                option_b VARCHAR(120) NOT NULL,
                option_c VARCHAR(120) NOT NULL,
                option_d VARCHAR(120) NOT NULL,
                correct_index TINYINT NOT NULL,
                seconds INT NOT NULL,
                UNIQUE (quiz_id, position),
                FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS sessions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                quiz_id BIGINT NOT NULL,
                room_code CHAR(6) NOT NULL,
                started_at DATETIME NOT NULL,
                ended_at DATETIME NOT NULL,
                completed BOOLEAN NOT NULL,
                shuffled BOOLEAN NOT NULL DEFAULT FALSE,
                questions_asked INT NOT NULL,
                FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS participants (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                session_id BIGINT NOT NULL,
                user_id BIGINT NULL,
                name VARCHAR(20) NOT NULL,
                score INT NOT NULL,
                correct_count INT NOT NULL,
                FOREIGN KEY (session_id) REFERENCES sessions(id) ON DELETE CASCADE,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS answers (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                participant_id BIGINT NOT NULL,
                question_id BIGINT NOT NULL,
                choice TINYINT NOT NULL,
                correct BOOLEAN NOT NULL,
                response_ms BIGINT NOT NULL,
                points INT NOT NULL,
                UNIQUE (participant_id, question_id),
                FOREIGN KEY (participant_id) REFERENCES participants(id) ON DELETE CASCADE,
                FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            // ----- Added in the homework upgrade. CREATE IF NOT EXISTS leaves existing data untouched. -----
            """
            CREATE TABLE IF NOT EXISTS classes (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                teacher_id BIGINT NOT NULL,
                name VARCHAR(60) NOT NULL,
                join_code CHAR(6) NOT NULL UNIQUE,
                created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS class_members (
                class_id BIGINT NOT NULL,
                student_id BIGINT NOT NULL,
                joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (class_id, student_id),
                FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE,
                FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS homework (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                class_id BIGINT NOT NULL,
                quiz_id BIGINT NOT NULL,
                created_at DATETIME NOT NULL,
                due_at DATETIME NOT NULL,
                remind_at DATETIME NOT NULL,
                reminder_sent BOOLEAN NOT NULL DEFAULT FALSE,
                summary_sent BOOLEAN NOT NULL DEFAULT FALSE,
                FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE CASCADE,
                FOREIGN KEY (quiz_id) REFERENCES quizzes(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """,
            """
            CREATE TABLE IF NOT EXISTS homework_submissions (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                homework_id BIGINT NOT NULL,
                student_id BIGINT NOT NULL,
                correct_count INT NOT NULL,
                questions INT NOT NULL,
                submitted_at DATETIME NOT NULL,
                UNIQUE (homework_id, student_id),
                FOREIGN KEY (homework_id) REFERENCES homework(id) ON DELETE CASCADE,
                FOREIGN KEY (student_id) REFERENCES users(id) ON DELETE CASCADE
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """);

    private final String url;
    private final String user;
    private final String password;

    Database(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    void createTables() throws SQLException {
        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String sql : SCHEMA) {
                statement.executeUpdate(sql);
            }
        }
    }
}
