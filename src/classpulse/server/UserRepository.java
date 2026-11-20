package classpulse.server;

import classpulse.common.Message.CodePurpose;
import classpulse.common.Views.Account;
import classpulse.common.Views.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

/** SQL for accounts, email codes and the email log. Every statement is a PreparedStatement. */
final class UserRepository extends Repository {
    /** A user row as the server needs it, including the password hash. */
    record UserRecord(long id, String username, String email, String fullName, Role role,
                      String passwordHash, boolean verified) {
        Account account() {
            return new Account(username, fullName, email, role);
        }
    }

    record CodeRecord(String codeHash, LocalDateTime createdAt, LocalDateTime expiresAt, int attempts) {
    }

    private static final String USER_COLUMNS =
            "SELECT id, username, email, full_name, role, password_hash, email_verified FROM users ";

    UserRepository(Database database) {
        super(database);
    }

    /** Returns the new user's id, or empty if the username or email is already taken. */
    Optional<Long> create(String fullName, String username, String email, Role role, String passwordHash) {
        String sql = "INSERT INTO users (username, email, full_name, role, password_hash) VALUES (?, ?, ?, ?, ?)";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, username);
            statement.setString(2, email);
            statement.setString(3, fullName);
            statement.setString(4, role.name());
            statement.setString(5, passwordHash);
            statement.executeUpdate();
            return Optional.of(generatedId(statement));
        } catch (SQLIntegrityConstraintViolationException e) {
            return Optional.empty();
        } catch (SQLException e) {
            throw new RepositoryException("Could not create the account.", e);
        }
    }

    boolean usernameTaken(String username) {
        return exists("SELECT 1 FROM users WHERE username = ?", username);
    }

    boolean emailTaken(String email) {
        return exists("SELECT 1 FROM users WHERE email = ?", email);
    }

    private boolean exists(String sql, String value) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet row = statement.executeQuery()) {
                return row.next();
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not check the account.", e);
        }
    }

    Optional<UserRecord> findByUsername(String username) {
        return findOne(USER_COLUMNS + "WHERE username = ?", username);
    }

    Optional<UserRecord> findByEmail(String email) {
        return findOne(USER_COLUMNS + "WHERE email = ?", email);
    }

    Optional<UserRecord> findById(long id) {
        return findOne(USER_COLUMNS + "WHERE id = ?", id);
    }

    private Optional<UserRecord> findOne(String sql, Object key) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, key);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new UserRecord(row.getLong(1), row.getString(2), row.getString(3),
                        row.getString(4), Role.valueOf(row.getString(5)), row.getString(6), row.getBoolean(7)));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not look up the account.", e);
        }
    }

    void markVerified(long userId) {
        update("UPDATE users SET email_verified = TRUE WHERE id = ?", userId);
    }

    void recordLogin(long userId) {
        update("UPDATE users SET last_login_at = NOW() WHERE id = ?", userId);
    }

    void updatePassword(long userId, String passwordHash) {
        update("UPDATE users SET password_hash = ? WHERE id = ?", passwordHash, userId);
    }

    void updateFullName(long userId, String fullName) {
        update("UPDATE users SET full_name = ? WHERE id = ?", fullName, userId);
    }

    private void update(String sql, Object... values) {
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                statement.setObject(i + 1, values[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new RepositoryException("Could not update the account.", e);
        }
    }

    // ----- One-time email codes -----

    /** Replaces any earlier code for the same purpose, so only the newest code works. */
    void saveCode(long userId, CodePurpose purpose, String codeHash, LocalDateTime expiresAt) {
        String sql = """
                INSERT INTO email_codes (user_id, purpose, code_hash, created_at, expires_at, attempts)
                VALUES (?, ?, ?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE code_hash = VALUES(code_hash), created_at = VALUES(created_at),
                                        expires_at = VALUES(expires_at), attempts = 0
                """;
        update(sql, userId, purpose.name(), codeHash, LocalDateTime.now(), expiresAt);
    }

    Optional<CodeRecord> findCode(long userId, CodePurpose purpose) {
        String sql = "SELECT code_hash, created_at, expires_at, attempts FROM email_codes WHERE user_id = ? AND purpose = ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            statement.setString(2, purpose.name());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) {
                    return Optional.empty();
                }
                return Optional.of(new CodeRecord(row.getString(1), row.getObject(2, LocalDateTime.class),
                        row.getObject(3, LocalDateTime.class), row.getInt(4)));
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not read the code.", e);
        }
    }

    void countFailedAttempt(long userId, CodePurpose purpose) {
        update("UPDATE email_codes SET attempts = attempts + 1 WHERE user_id = ? AND purpose = ?",
                userId, purpose.name());
    }

    void deleteCode(long userId, CodePurpose purpose) {
        update("DELETE FROM email_codes WHERE user_id = ? AND purpose = ?", userId, purpose.name());
    }

    // ----- Email log -----

    void logEmail(String recipient, String subject, String kind, boolean delivered, String error) {
        String sql = "INSERT INTO email_log (recipient, subject, kind, delivered, error) VALUES (?, ?, ?, ?, ?)";
        String shortError = error == null ? null : error.substring(0, Math.min(300, error.length()));
        String shortSubject = subject.substring(0, Math.min(200, subject.length()));
        update(sql, recipient, shortSubject, kind, delivered, shortError);
    }

    int countEmails(String recipient, String kind) {
        String sql = "SELECT COUNT(*) FROM email_log WHERE recipient = ? AND kind = ?";
        try (Connection connection = database.connect();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, recipient);
            statement.setString(2, kind);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getInt(1);
            }
        } catch (SQLException e) {
            throw new RepositoryException("Could not read the email log.", e);
        }
    }
}
