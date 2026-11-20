package classpulse.mail;

import java.util.List;

/** One email: a plain-text body for old mail apps, an HTML body for everyone else, and attachments. */
public record Email(String to, String subject, String textBody, String htmlBody, List<Attachment> attachments) {
    public record Attachment(String fileName, String contentType, byte[] content) {
    }

    public Email {
        if (to == null || to.isBlank() || to.contains("\r") || to.contains("\n")) {
            throw new IllegalArgumentException("Invalid recipient.");
        }
        if (subject.contains("\r") || subject.contains("\n")) {
            throw new IllegalArgumentException("A subject cannot contain line breaks."); // Stops header injection.
        }
        attachments = List.copyOf(attachments);
    }

    public Email(String to, String subject, String textBody, String htmlBody) {
        this(to, subject, textBody, htmlBody, List.of());
    }
}
