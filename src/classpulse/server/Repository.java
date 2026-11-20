package classpulse.server;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

/**
 * Shared base class for the three repositories (inheritance). It holds the database
 * and the two helpers every repository needs: transactions and generated ids.
 */
abstract class Repository {
    @FunctionalInterface
    protected interface TransactionWork<T> {
        T run(Connection connection) throws SQLException;
    }

    protected final Database database;

    protected Repository(Database database) {
        this.database = database;
    }

    /** Runs work so that either all of its changes are saved, or none of them are. */
    protected <T> T inTransaction(TransactionWork<T> work) throws SQLException {
        try (Connection connection = database.connect()) {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    protected static long generatedId(Statement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            if (keys.next()) {
                return keys.getLong(1);
            }
            throw new SQLException("The database did not return a generated id.");
        }
    }
}
