package classpulse.mail;

import java.io.IOException;

/**
 * Sends an email. This is the Strategy pattern: the server works with any Mailer.
 * SmtpMailer sends real email, ConsoleMailer prints it (for demos without internet),
 * and the tests use a Mailer that just remembers what was sent.
 */
public interface Mailer {
    void send(Email email) throws IOException;

    /** A short description for the server's start-up message. */
    String describe();
}
