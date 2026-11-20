package classpulse.server;

import classpulse.common.Protocol;
import classpulse.mail.ConsoleMailer;
import classpulse.mail.DomainChecker;
import classpulse.mail.Mailer;
import classpulse.mail.SmtpMailer;
import java.io.Console;
import java.io.IOException;
import java.io.Reader;
import java.net.BindException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Properties;

/**
 * Program entry point for the server.
 * Usage: ServerMain [--config file] [--mail file] [--port number] [--phone-port number|off] [--phone-host address]
 */
public final class ServerMain {
    private ServerMain() {
    }

    public static void main(String[] args) {
        Path configPath = Path.of("config", "db.properties");
        Path mailPath = Path.of("config", "mail.properties");
        int port = Protocol.DEFAULT_PORT;
        int phonePort = 8080;      // Phone play web server; --phone-port off turns it off.
        String phoneHost = null;   // Address in the QR code; found automatically unless given.
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--config" -> configPath = Path.of(args[i + 1]);
                case "--mail" -> mailPath = Path.of(args[i + 1]);
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                case "--phone-port" -> phonePort = args[i + 1].equalsIgnoreCase("off") ? -1 : Integer.parseInt(args[i + 1]);
                case "--phone-host" -> phoneHost = args[i + 1];
                default -> exit("Unknown option: " + args[i]);
            }
        }

        Properties config = load(configPath);
        String url = config.getProperty("db.url");
        String user = config.getProperty("db.user");
        String password = config.getProperty("db.password");
        if (password == null) {
            password = ask("MySQL password for user '" + user + "': ");
        }
        Properties mail = Files.exists(mailPath) ? load(mailPath) : new Properties();
        Mailer mailer = createMailer(mail);
        DomainChecker domains = Boolean.parseBoolean(mail.getProperty("mail.checkDomain", "true"))
                ? new DomainChecker() : new DomainChecker() {
                    @Override
                    protected Status lookup(String domain) {
                        return Status.UNKNOWN; // Checking turned off in mail.properties.
                    }
                };

        try (ClassPulseServer server = ClassPulseServer.open(url, user, password, port, mailer, domains)) {
            System.out.println("ClassPulse server is running on port " + server.port() + ".");
            System.out.println("Connected to MySQL. Email: " + server.mailDescription());
            if (phonePort >= 0) {
                try {
                    String phoneUrl = server.startPhoneServer(phonePort, phoneHost);
                    System.out.println("Phone play is on: phones on the same Wi-Fi can open " + phoneUrl
                            + " (or scan the QR code in the lobby).");
                } catch (IOException e) {
                    System.out.println("Phone play is off: port " + phonePort + " could not be opened ("
                            + e.getMessage() + "). Everything else works as usual.");
                }
            }
            System.out.println("Homework reminders are checked every 30 seconds.");
            System.out.println("Press Ctrl+C to stop.");
            server.acceptClients();
        } catch (SQLException e) {
            exit("Could not connect to MySQL: " + e.getMessage()
                    + "\nCheck that the MySQL service is running and the password is correct.");
        } catch (BindException e) {
            exit("Port " + port + " is already in use. Is another ClassPulse server open?");
        } catch (IOException e) {
            exit("Server error: " + e.getMessage());
        }
    }

    /**
     * mail.mode=console prints emails in this window (no internet needed).
     * mail.mode=smtp sends real email. The SMTP password is read from the CLASSPULSE_MAIL_PASSWORD
     * environment variable, or asked for at start-up, so it never has to be saved in a file.
     */
    static Mailer createMailer(Properties mail) {
        String mode = mail.getProperty("mail.mode", "console").strip().toLowerCase(Locale.ROOT);
        if (!mode.equals("smtp")) {
            return new ConsoleMailer(Path.of(mail.getProperty("mail.outbox", "mail-outbox")));
        }
        String host = mail.getProperty("mail.smtp.host", "smtp.gmail.com").strip();
        int port = Integer.parseInt(mail.getProperty("mail.smtp.port", "465").strip());
        SmtpMailer.Security security = SmtpMailer.Security.valueOf(
                mail.getProperty("mail.smtp.security", "ssl").strip().toUpperCase(Locale.ROOT));
        String username = mail.getProperty("mail.smtp.username", "").strip();
        String from = mail.getProperty("mail.from.address", username).strip();
        String fromName = mail.getProperty("mail.from.name", "ClassPulse").strip();
        String password = System.getenv("CLASSPULSE_MAIL_PASSWORD");
        if (password == null || password.isBlank()) {
            password = mail.getProperty("mail.smtp.password");
        }
        if ((password == null || password.isBlank()) && !username.isEmpty()) {
            password = ask("Email (SMTP) app password for " + username + ": ").replace(" ", "");
        }
        return new SmtpMailer(host, port, security, username, password, from, fromName);
    }

    private static Properties load(Path path) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        } catch (IOException e) {
            exit("Could not read " + path + ": " + e.getMessage());
        }
        return properties;
    }

    /** Asking at startup means passwords never have to be saved in a file. */
    private static String ask(String prompt) {
        Console console = System.console();
        if (console == null) {
            exit("No console is available to ask: " + prompt.strip() + " Set it in the config file instead.");
        }
        char[] typed = console.readPassword("%s", prompt);
        return typed == null ? "" : new String(typed);
    }

    private static void exit(String message) {
        System.err.println(message);
        System.exit(1);
    }
}
