package classpulse.server;

import classpulse.mail.Email;
import classpulse.mail.Email.Attachment;
import classpulse.mail.Mailer;
import java.util.List;

/** Sends emails through the configured Mailer and records every attempt in the email_log table. */
final class MailService {
    private final Mailer mailer;
    private final UserRepository users;

    MailService(Mailer mailer, UserRepository users) {
        this.mailer = mailer;
        this.users = users;
    }

    String describe() {
        return mailer.describe();
    }

    /** Sends now and returns the error text, or null if the email was sent. Blocks while sending. */
    String send(String to, String kind, EmailTemplates.Content content, List<Attachment> attachments) {
        String error = null;
        try {
            mailer.send(new Email(to, content.subject(), content.text(), content.html(), attachments));
        } catch (Exception e) {
            error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            System.err.println("Could not send " + kind + " email to " + to + ": " + error);
        }
        try {
            users.logEmail(to, content.subject(), kind, error == null, error);
        } catch (RepositoryException e) {
            System.err.println("Could not write the email log: " + e.getCause());
        }
        return error;
    }

    String send(String to, String kind, EmailTemplates.Content content) {
        return send(to, kind, content, List.of());
    }

    /** Sends on a background (virtual) thread, for emails nobody is waiting for. */
    void sendLater(String to, String kind, EmailTemplates.Content content) {
        Thread.startVirtualThread(() -> send(to, kind, content));
    }
}
