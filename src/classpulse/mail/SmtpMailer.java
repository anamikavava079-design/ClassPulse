package classpulse.mail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * A small SMTP client (RFC 5321) written on plain Java sockets, with no mail library.
 *
 * Conversation with the mail server:
 *   S: 220 ready             C: EHLO classpulse        S: 250 ...
 *   C: STARTTLS (port 587)   S: 220  -> the socket is upgraded to TLS, then EHLO again
 *   C: AUTH LOGIN            S: 334  C: base64(username)  S: 334  C: base64(password)  S: 235
 *   C: MAIL FROM:<from>      S: 250
 *   C: RCPT TO:<to>          S: 250
 *   C: DATA                  S: 354  C: <message> + "."   S: 250
 *   C: QUIT                  S: 221
 *
 * With Gmail use smtp.gmail.com, port 465 (SSL) and an App Password, not your normal password.
 */
public final class SmtpMailer implements Mailer {
    public enum Security { SSL, STARTTLS, NONE }

    private static final int TIMEOUT_MS = 20_000;

    private final String host;
    private final int port;
    private final Security security;
    private final String username;
    private final String password;
    private final String fromAddress;
    private final String fromName;

    public SmtpMailer(String host, int port, Security security, String username, String password,
                      String fromAddress, String fromName) {
        this.host = host;
        this.port = port;
        this.security = security;
        this.username = username;
        this.password = password;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public String describe() {
        return "SMTP " + host + ":" + port + " (" + security.name().toLowerCase(Locale.ROOT) + ") as " + fromAddress;
    }

    @Override
    public void send(Email email) throws IOException {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
        socket.setSoTimeout(TIMEOUT_MS);
        try {
            if (security == Security.SSL) {
                socket = upgradeToTls(socket);
            }
            Session session = new Session(socket);
            session.expect(220);
            session.command("EHLO classpulse", 250);
            if (security == Security.STARTTLS) {
                session.command("STARTTLS", 220);
                socket = upgradeToTls(socket);
                session = new Session(socket);
                session.command("EHLO classpulse", 250);
            }
            if (username != null && !username.isBlank()) {
                Base64.Encoder base64 = Base64.getEncoder();
                session.command("AUTH LOGIN", 334);
                session.command(base64.encodeToString(username.getBytes(StandardCharsets.UTF_8)), 334);
                session.commandHidden(base64.encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
            }
            session.command("MAIL FROM:<" + fromAddress + ">", 250);
            session.command("RCPT TO:<" + email.to() + ">", 250, 251);
            session.command("DATA", 354);
            session.writeData(MimeBuilder.build(email, fromAddress, fromName));
            session.command(".", 250);
            session.commandQuietly("QUIT");
        } finally {
            socket.close();
        }
    }

    /** Wraps the connection in TLS and checks the certificate belongs to this host. */
    private SSLSocket upgradeToTls(Socket plain) throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket tls = (SSLSocket) factory.createSocket(plain, host, port, true);
        SSLParameters parameters = tls.getSSLParameters();
        parameters.setEndpointIdentificationAlgorithm("HTTPS"); // Without this, Java skips the host name check.
        tls.setSSLParameters(parameters);
        tls.startHandshake();
        return tls;
    }

    /** Reads and writes SMTP lines on one socket. */
    private static final class Session {
        private final BufferedReader in;
        private final OutputStream out;

        Session(Socket socket) throws IOException {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            out = socket.getOutputStream();
        }

        void command(String line, int... expected) throws IOException {
            write(line);
            expect(expected);
        }

        /** Like command, but keeps the password out of any error message. */
        void commandHidden(String line, int expected) throws IOException {
            write(line);
            try {
                expect(expected);
            } catch (IOException e) {
                throw new IOException("The mail server rejected the username or password. "
                        + "For Gmail, use an App Password. (" + e.getMessage() + ")");
            }
        }

        void commandQuietly(String line) {
            try {
                write(line);
                in.readLine();
            } catch (IOException ignored) {
                // The email is already sent; a failed QUIT does not matter.
            }
        }

        private void write(String line) throws IOException {
            out.write((line + "\r\n").getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        /** The message body. A line starting with "." gets an extra "." (dot-stuffing), so it cannot end DATA early. */
        void writeData(String message) throws IOException {
            StringBuilder stuffed = new StringBuilder();
            for (String line : message.split("\r\n", -1)) {
                stuffed.append(line.startsWith(".") ? "." + line : line).append("\r\n");
            }
            out.write(stuffed.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        /** A reply can span several lines: "250-first", "250-second", "250 last". */
        void expect(int... expected) throws IOException {
            String line;
            do {
                line = in.readLine();
                if (line == null) {
                    throw new IOException("The mail server closed the connection.");
                }
            } while (line.length() > 3 && line.charAt(3) == '-');
            int code;
            try {
                code = Integer.parseInt(line.substring(0, 3));
            } catch (RuntimeException e) {
                throw new IOException("Unexpected reply from the mail server: " + line);
            }
            for (int ok : expected) {
                if (code == ok) {
                    return;
                }
            }
            throw new IOException("Mail server said: " + line);
        }
    }
}
