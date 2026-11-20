package classpulse.mail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Demo mode: instead of sending, prints each email in the server window and saves it
 * in the mail-outbox folder (an .html file you can open, and an .eml file that a mail
 * app can open). Useful when there is no internet or no SMTP account.
 */
public final class ConsoleMailer implements Mailer {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private final Path folder;

    public ConsoleMailer(Path folder) {
        this.folder = folder;
    }

    @Override
    public String describe() {
        return "demo mode: emails are printed here and saved in " + folder.toAbsolutePath();
    }

    @Override
    public synchronized void send(Email email) throws IOException {
        System.out.println();
        System.out.println("=============== EMAIL (demo mode) ===============");
        System.out.println("To:      " + email.to());
        System.out.println("Subject: " + email.subject());
        System.out.println("-------------------------------------------------");
        System.out.println(email.textBody());
        System.out.println("=================================================");
        Files.createDirectories(folder);
        String name = LocalDateTime.now().format(STAMP) + "-" + email.to().replaceAll("[^A-Za-z0-9.@_-]", "_");
        Files.writeString(folder.resolve(name + ".html"), email.htmlBody(), StandardCharsets.UTF_8);
        Files.writeString(folder.resolve(name + ".eml"), MimeBuilder.build(email, "classpulse@localhost", "ClassPulse"),
                StandardCharsets.UTF_8);
    }
}
