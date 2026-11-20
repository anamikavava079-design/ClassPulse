package classpulse.mail;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

/**
 * Builds the raw text of an email in MIME format (RFC 2045-2049).
 *
 * Structure:  multipart/mixed
 *               +- multipart/alternative
 *               |    +- text/plain   (base64)
 *               |    +- text/html    (base64)
 *               +- attachment 1..n  (base64)
 *
 * Every body is base64-encoded, so any language (UTF-8) travels safely over SMTP.
 */
public final class MimeBuilder {
    private static final String CRLF = "\r\n";
    private static final Base64.Encoder MIME = Base64.getMimeEncoder(76, CRLF.getBytes(StandardCharsets.US_ASCII));

    private MimeBuilder() {
    }

    public static String build(Email email, String fromAddress, String fromName) {
        String mixed = "mixed_" + UUID.randomUUID();
        String alternative = "alt_" + UUID.randomUUID();
        StringBuilder out = new StringBuilder();
        header(out, "From", encodeWord(fromName) + " <" + fromAddress + ">");
        header(out, "To", "<" + email.to() + ">");
        header(out, "Subject", encodeWord(email.subject()));
        header(out, "Date", ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
        header(out, "Message-ID", "<" + UUID.randomUUID() + "@classpulse>");
        header(out, "MIME-Version", "1.0");
        header(out, "Content-Type", "multipart/mixed; boundary=\"" + mixed + "\"");
        out.append(CRLF);

        out.append("--").append(mixed).append(CRLF);
        header(out, "Content-Type", "multipart/alternative; boundary=\"" + alternative + "\"");
        out.append(CRLF);
        part(out, alternative, "text/plain; charset=UTF-8", null, email.textBody().getBytes(StandardCharsets.UTF_8));
        part(out, alternative, "text/html; charset=UTF-8", null, email.htmlBody().getBytes(StandardCharsets.UTF_8));
        out.append("--").append(alternative).append("--").append(CRLF);

        for (Email.Attachment attachment : email.attachments()) {
            String safeName = attachment.fileName().replaceAll("[^A-Za-z0-9._-]", "_");
            part(out, mixed, attachment.contentType() + "; name=\"" + safeName + "\"",
                    "attachment; filename=\"" + safeName + "\"", attachment.content());
        }
        out.append("--").append(mixed).append("--").append(CRLF);
        return out.toString();
    }

    private static void header(StringBuilder out, String name, String value) {
        out.append(name).append(": ").append(value).append(CRLF);
    }

    private static void part(StringBuilder out, String boundary, String type, String disposition, byte[] body) {
        out.append("--").append(boundary).append(CRLF);
        header(out, "Content-Type", type);
        header(out, "Content-Transfer-Encoding", "base64");
        if (disposition != null) {
            header(out, "Content-Disposition", disposition);
        }
        out.append(CRLF).append(MIME.encodeToString(body)).append(CRLF);
    }

    /** RFC 2047 encoded word, so subjects and names can contain any character. */
    static String encodeWord(String text) {
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "?=";
    }
}
