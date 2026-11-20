package classpulse.server;

/** An unchecked wrapper, so SQL details stay inside the database layer. */
final class RepositoryException extends RuntimeException {
    RepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
