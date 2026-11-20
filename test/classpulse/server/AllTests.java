package classpulse.server;

import classpulse.common.Csv;
import classpulse.common.Message.*;
import classpulse.common.Protocol;
import classpulse.common.Question;
import classpulse.common.Quiz;
import classpulse.common.Validation;
import classpulse.common.Views.Badge;
import classpulse.common.Views.HistoryEntry;
import classpulse.common.Views.QuestionStat;
import classpulse.common.Views.QuizSummary;
import classpulse.common.Views.Role;
import classpulse.common.Views.ScoreEntry;
import classpulse.common.Views.StudentStats;
import classpulse.mail.DomainChecker;
import classpulse.mail.Email;
import classpulse.mail.Mailer;
import classpulse.mail.MimeBuilder;
import classpulse.mail.SmtpMailer;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Console;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A small test runner with no libraries. Usage: AllTests [database-config-file]
 * Without a config file, only the tests that do not need MySQL are run.
 */
public final class AllTests {
    private static int passed;
    private static int failed;
    private static final List<Question> SAMPLE = SampleQuiz.create().questions();
    private static final String PASSWORD = "password1";

    /** Remembers every email instead of sending it, so tests can read the codes. */
    static final class TestMailer implements Mailer {
        final ConcurrentLinkedQueue<Email> sent = new ConcurrentLinkedQueue<>();

        @Override
        public void send(Email email) {
            sent.add(email);
        }

        @Override
        public String describe() {
            return "test mailer";
        }

        List<Email> to(String address) {
            return sent.stream().filter(e -> e.to().equals(address)).toList();
        }

        /** Waits briefly, because some emails are sent on a background thread. */
        Email awaitLast(String address, String subjectPart) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (System.nanoTime() < deadline) {
                List<Email> matches = to(address).stream().filter(e -> e.subject().contains(subjectPart)).toList();
                if (!matches.isEmpty()) {
                    return matches.get(matches.size() - 1);
                }
                Thread.sleep(50);
            }
            throw new AssertionError("No email to " + address + " with subject containing " + subjectPart);
        }

        /** The code from the newest email that has one. Code emails are sent before the server replies. */
        String lastCode(String address) {
            List<Email> emails = to(address);
            for (int i = emails.size() - 1; i >= 0; i--) {
                Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(emails.get(i).textBody());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            throw new AssertionError("No email with a code was sent to " + address);
        }
    }

    /** Pretends DNS: any domain ending in "nomail.test" has no mail server. */
    static final class TestDomains extends DomainChecker {
        @Override
        protected Status lookup(String domain) {
            return domain.endsWith("nomail.test") ? Status.NO_MAIL_SERVER : Status.ACCEPTS_MAIL;
        }
    }

    private static final TestMailer MAIL = new TestMailer();

    private AllTests() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1) {
            // A second argument names a file that also gets a copy of the results (used by the report).
            java.io.PrintStream file = new java.io.PrintStream(new java.io.FileOutputStream(args[1]), true,
                    StandardCharsets.UTF_8);
            java.io.PrintStream screen = System.out;
            System.setOut(new java.io.PrintStream(new OutputStream() {
                @Override
                public void write(int b) {
                    screen.write(b);
                    file.write(b);
                }

                @Override
                public void write(byte[] bytes, int offset, int length) {
                    screen.write(bytes, offset, length);
                    file.write(bytes, offset, length);
                }

                @Override
                public void flush() {
                    screen.flush();
                    file.flush();
                }
            }, true, StandardCharsets.UTF_8));
        }
        System.out.println("--- Unit tests ---");
        run("score calculation and streaks", AllTests::scores);
        run("password hashing", AllTests::passwords);
        run("email and password rules", AllTests::accountRules);
        run("CSV quiz import", AllTests::csvImport);
        run("sample CSV file", AllTests::sampleCsvFile);
        run("CSV export safety", AllTests::csvExport);
        run("question validation", AllTests::questionValidation);
        run("protocol round trip", AllTests::protocolRoundTrip);
        run("protocol rejects unsafe data", AllTests::protocolRejects);
        run("MIME email format", AllTests::mimeFormat);
        run("SMTP client talks to a mail server", AllTests::smtpConversation);
        run("SMTP client reports a rejected password", AllTests::smtpRejected);
        run("badges and student statistics", AllTests::badges);
        run("shuffled quiz keeps questions and ids together", AllTests::shuffle);
        // Added with the upgrades.
        run("certificate rule and image", UpgradeTests::certificate);
        run("QR code reads back: format, error correction, text", UpgradeTests::qrCode);
        run("phone page JSON is escaped safely", UpgradeTests::phoneJson);

        if (args.length > 0) {
            try (ClassPulseServer server = openServer(args[0])) {
                Thread accept = new Thread(server::acceptClients);
                accept.setDaemon(true);
                accept.start();
                int port = server.port();
                server.startPhoneServer(0, "127.0.0.1"); // Any free port, for the phone test.
                System.out.println("--- Integration tests (real sockets and real MySQL) ---");
                run("register needs a verified email", () -> registerAndVerify(port));
                run("wrong codes are limited", () -> wrongCodes(port));
                run("domains with no mail server are refused", () -> noMailDomain(port));
                run("duplicate username and email", () -> duplicates(port));
                run("sign in with username or email", () -> loginByEmail(port));
                run("reset code only goes to registered emails", () -> forgotPassword(port));
                run("change password and profile", () -> changePassword(port));
                run("permissions", () -> permissions(port));
                run("edit, duplicate and delete quizzes", () -> quizManagement(port));
                run("SQL injection is harmless", () -> sqlInjection(port));
                run("whole game, disconnect, report", () -> fullGame(port));
                run("signed-in student: history, badges, result email", () -> studentAccount(port));
                run("report email with CSV attachment", () -> reportEmail(port));
                run("teacher overview numbers", () -> overview(port));
                run("teacher removes a student from the lobby", () -> kick(port));
                run("server timer ends a question", () -> timer(port));
                run("teacher disconnect closes room", () -> hostDisconnect(port));
                run("50 students at the same time", () -> manyStudents(port));
                run("garbage bytes do not stop the server", () -> garbage(port));
                run("repeated wrong passwords disconnect", () -> loginLimit(port));
                // Added with the upgrades.
                run("the winner's result email has a certificate", () -> certificateEmail(port));
                run("classes and homework: emails, one try, reminder, summary", () -> homework(port, server));
                run("phone players join and answer over HTTP", () -> phonePlay(port, server));
            }
        } else {
            System.out.println("Integration tests skipped: no database config file was given.");
        }

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    @FunctionalInterface
    private interface Test {
        void run() throws Exception;
    }

    private static void run(String name, Test test) {
        try {
            test.run();
            passed++;
            System.out.println("PASS  " + name);
        } catch (Exception | AssertionError e) {
            failed++;
            System.out.println("FAIL  " + name + ": " + e);
            e.printStackTrace(System.out);
        }
    }

    static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static void equal(Object expected, Object actual, String what) {
        if (!expected.equals(actual)) {
            throw new AssertionError(what + ": expected " + expected + " but got " + actual);
        }
    }

    private static void rejects(Runnable action, String what) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            return;
        }
        throw new AssertionError("Should have been rejected: " + what);
    }

    // ----- Unit tests -----

    private static void scores() {
        equal(1000, ScoreCalculator.points(true, 0, 20_000), "instant answer");
        equal(750, ScoreCalculator.points(true, 10_000, 20_000), "halfway");
        equal(500, ScoreCalculator.points(true, 20_000, 20_000), "last moment");
        equal(500, ScoreCalculator.points(true, 99_000, 20_000), "past the limit");
        equal(0, ScoreCalculator.points(false, 0, 20_000), "wrong answer");
        equal(0, ScoreCalculator.streakBonus(1), "no bonus for the first correct answer");
        equal(50, ScoreCalculator.streakBonus(2), "two in a row");
        equal(100, ScoreCalculator.streakBonus(3), "three in a row");
        equal(200, ScoreCalculator.streakBonus(10), "the bonus is capped");
    }

    private static void passwords() {
        String first = PasswordHasher.hash("secret-one");
        check(PasswordHasher.verify("secret-one", first), "the right password should match");
        check(!PasswordHasher.verify("secret-two", first), "a wrong password should not match");
        check(!first.contains("secret-one"), "the hash should not contain the password");
        check(!first.equals(PasswordHasher.hash("secret-one")), "random salt should make hashes differ");
    }

    private static void accountRules() {
        equal("asha@school.edu", Validation.email("  Asha@School.EDU "), "emails are trimmed and lower-cased");
        Validation.email("first.last+tag@mail.co.in");
        rejects(() -> Validation.email("asha"), "no @");
        rejects(() -> Validation.email("asha@school"), "no top-level domain");
        rejects(() -> Validation.email("a b@school.edu"), "a space");
        rejects(() -> Validation.email("a..b@school.edu"), "two dots");
        rejects(() -> Validation.email("asha@-school.edu"), "a domain starting with -");
        rejects(() -> Validation.password("short1"), "a short password");
        rejects(() -> Validation.password("onlyletters"), "no digit");
        rejects(() -> Validation.password("12345678"), "no letter");
        Validation.password("letters123");
        equal(0, Validation.passwordStrength("abc"), "too short");
        equal(1, Validation.passwordStrength("abcdefg1"), "weak");
        equal(2, Validation.passwordStrength("Abcdefg1"), "fair");
        equal(3, Validation.passwordStrength("Abcdefg1!"), "strong");
        equal("Anamika Edathatta", Validation.fullName("  Anamika   Edathatta "), "spaces are tidied");
        Validation.fullName("D'Souza-Lee Jr.");
        rejects(() -> Validation.fullName("<script>"), "symbols in a name");
        equal("asha@gmail.com", Validation.suggestEmail("asha@gmial.com"), "a swapped letter");
        equal("asha@outlook.com", Validation.suggestEmail("asha@outlok.com"), "a missing letter");
        equal("asha@yahoo.com", Validation.suggestEmail("asha@yaho.com"), "yaho");
        check(Validation.suggestEmail("asha@gmail.com") == null, "a correct domain");
        check(Validation.suggestEmail("asha@school.edu") == null, "an unrelated domain");
        check(Validation.suggestEmail("asha@mail.com") == null, "mail.com is a real provider, not a typo");
        equal("123456", Validation.emailCode(" 123 456 "), "spaces in a code are ignored");
        rejects(() -> Validation.emailCode("12345a"), "letters in a code");
    }

    private static void csvImport() {
        Quiz quiz = Csv.parseQuiz("T", List.of(
                (char) 0xFEFF + "question,A,B,C,D,correct,seconds",
                "\"Comma, and \"\"quotes\"\"\",w,x,y,z,c,30",
                "",
                "Second,1,2,3,4,A,10"));
        equal(2, quiz.questions().size(), "question count");
        equal("Comma, and \"quotes\"", quiz.questions().get(0).text(), "quoted text");
        equal(2, quiz.questions().get(0).correctIndex(), "correct answer C");
        equal(10, quiz.questions().get(1).seconds(), "seconds");
        try {
            Csv.parseQuiz("T", List.of("Good,1,2,3,4,A,10", "Bad,1,2,3,4,Z,10"));
            throw new AssertionError("A bad answer letter should be rejected");
        } catch (IllegalArgumentException e) {
            check(e.getMessage().startsWith("Line 2"), "the error should name the line: " + e.getMessage());
        }
        rejects(() -> Csv.parseQuiz("T", List.of("question,A,B,C,D,correct,seconds")), "a quiz with no questions");
    }

    private static void sampleCsvFile() throws IOException {
        Quiz quiz = Csv.parseQuiz("OOP", Files.readAllLines(Path.of("sample-data", "oop-concepts-quiz.csv")));
        equal(5, quiz.questions().size(), "questions in the sample file");
        equal("a12", quiz.questions().get(4).options().get(quiz.questions().get(4).correctIndex()), "answer to the last question");
    }

    private static void csvExport() {
        equal("plain", Csv.cell("plain"), "plain text");
        equal("\"a,b\"", Csv.cell("a,b"), "comma");
        equal("\"say \"\"hi\"\"\"", Csv.cell("say \"hi\""), "quotes");
        equal("'=1+1", Csv.cell("=1+1"), "formula injection");
    }

    private static void questionValidation() {
        List<String> ok = List.of("a", "b", "c", "d");
        new Question("Fine?", ok, 0, 20);
        rejects(() -> new Question(" ", ok, 0, 20), "blank text");
        rejects(() -> new Question("Q", List.of("a", "b"), 0, 20), "too few options");
        rejects(() -> new Question("Q", List.of("a", "a", "c", "d"), 0, 20), "duplicate options");
        rejects(() -> new Question("Q", ok, 4, 20), "bad correct index");
        rejects(() -> new Question("Q", ok, 0, 2), "too little time");
        rejects(() -> new Question("<html><img src=x>", ok, 0, 20), "HTML in text");
        new Question("Is List<String> generic?", ok, 0, 20); // Angle brackets inside text are fine.
    }

    private static void protocolRoundTrip() throws IOException {
        Quiz quiz = SampleQuiz.create();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        Protocol.write(new DataOutputStream(bytes), new SaveQuiz(quiz));
        Protocol.write(new DataOutputStream(bytes), new QuestionEnded(0, 6, 1, List.of(1, 2, 3, 4),
                List.of(new ScoreEntry(1, "A", 900)), null));
        Protocol.write(new DataOutputStream(bytes), new Register("Asha Rao", "asha", "a@b.co", "pass1234", Role.STUDENT));
        Protocol.write(new DataOutputStream(bytes), new ResendCode("a@b.co", CodePurpose.RESET_PASSWORD));
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        equal(new SaveQuiz(quiz), Protocol.read(in), "saved quiz");
        check(Protocol.read(in) instanceof QuestionEnded ended && ended.counts().equals(List.of(1, 2, 3, 4)), "counts");
        check(Protocol.read(in) instanceof Register r && r.role() == Role.STUDENT, "an enum survives the trip");
        check(Protocol.read(in) instanceof ResendCode r && r.purpose() == CodePurpose.RESET_PASSWORD, "code purpose");
    }

    private static void protocolRejects() throws IOException {
        // A class that is not on the allow-list.
        ByteArrayOutputStream object = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(object)) {
            out.writeObject(new java.util.Date());
        }
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(frame);
        data.writeInt(object.size());
        object.writeTo(data);
        expectIOException(frame.toByteArray(), "a class that is not allowed");

        // A length that claims a huge message.
        ByteArrayOutputStream huge = new ByteArrayOutputStream();
        new DataOutputStream(huge).writeInt(Protocol.MAX_FRAME_BYTES + 1);
        expectIOException(huge.toByteArray(), "an oversized frame");
    }

    private static void expectIOException(byte[] bytes, String what) {
        try {
            Protocol.read(new DataInputStream(new ByteArrayInputStream(bytes)));
        } catch (IOException e) {
            return;
        }
        throw new AssertionError("Should have been rejected: " + what);
    }

    private static void mimeFormat() {
        Email email = new Email("asha@school.edu", "Résultat: 5/6", "Plain body", "<b>HTML body</b>",
                List.of(new Email.Attachment("report file.csv", "text/csv", "a,b\r\n1,2".getBytes(StandardCharsets.UTF_8))));
        String raw = MimeBuilder.build(email, "server@school.edu", "ClassPulse");
        check(raw.contains("To: <asha@school.edu>\r\n"), "recipient header");
        check(raw.contains("Subject: =?UTF-8?B?"), "the subject is encoded for any language");
        check(raw.contains("multipart/alternative"), "text and HTML versions");
        check(raw.contains("filename=\"report_file.csv\""), "the attachment name is made safe");
        check(raw.contains(Base64.getEncoder().encodeToString("<b>HTML body</b>".getBytes(StandardCharsets.UTF_8))),
                "the HTML body is base64-encoded");
        rejects(() -> new Email("a@b.co\r\nBcc: victim@x.com", "S", "t", "h"), "header injection in the recipient");
        rejects(() -> new Email("a@b.co", "Hi\r\nBcc: victim@x.com", "t", "h"), "header injection in the subject");
    }

    /**
     * A fake SMTP server on a local port. It plays the server side of the conversation and
     * records what the client sent, so the real SmtpMailer code can be tested without internet.
     */
    private static List<String> fakeSmtp(ServerSocket listener, boolean acceptPassword) {
        List<String> received = java.util.Collections.synchronizedList(new ArrayList<>());
        Thread.startVirtualThread(() -> {
            try (Socket socket = listener.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 OutputStream out = socket.getOutputStream()) {
                reply(out, "220 fake.smtp ready");
                boolean inData = false;
                String line;
                while ((line = in.readLine()) != null) {
                    received.add(line);
                    if (inData) {
                        if (line.equals(".")) {
                            inData = false;
                            reply(out, "250 OK queued");
                        }
                    } else if (line.startsWith("EHLO")) {
                        reply(out, "250-fake.smtp\r\n250-AUTH LOGIN\r\n250 OK");
                    } else if (line.equals("AUTH LOGIN")) {
                        reply(out, "334 VXNlcm5hbWU6");
                    } else if (received.size() >= 2 && received.get(received.size() - 2).equals("AUTH LOGIN")) {
                        reply(out, "334 UGFzc3dvcmQ6");
                    } else if (received.size() >= 3 && received.get(received.size() - 3).equals("AUTH LOGIN")) {
                        reply(out, acceptPassword ? "235 Authenticated" : "535 5.7.8 Bad credentials");
                    } else if (line.startsWith("MAIL FROM") || line.startsWith("RCPT TO")) {
                        reply(out, "250 OK");
                    } else if (line.equals("DATA")) {
                        inData = true;
                        reply(out, "354 Go ahead");
                    } else if (line.equals("QUIT")) {
                        reply(out, "221 Bye");
                        return;
                    }
                }
            } catch (IOException ignored) {
                // The test checks what was received.
            }
        });
        return received;
    }

    private static void reply(OutputStream out, String text) throws IOException {
        out.write((text + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void smtpConversation() throws Exception {
        try (ServerSocket listener = new ServerSocket(0)) {
            List<String> received = fakeSmtp(listener, true);
            SmtpMailer mailer = new SmtpMailer("localhost", listener.getLocalPort(), SmtpMailer.Security.NONE,
                    "teacher@school.edu", "app-password", "teacher@school.edu", "ClassPulse");
            mailer.send(new Email("asha@school.edu", "Code", "Your code is 123456", "<p>.hidden line</p>"));
            Thread.sleep(100);
            Base64.Encoder b64 = Base64.getEncoder();
            check(received.contains(b64.encodeToString("teacher@school.edu".getBytes(StandardCharsets.UTF_8))),
                    "the username is sent in base64");
            check(received.contains("MAIL FROM:<teacher@school.edu>"), "sender");
            check(received.contains("RCPT TO:<asha@school.edu>"), "recipient");
            check(received.contains("."), "the message ends with a lone dot");
            check(received.get(received.size() - 1).equals("QUIT"), "the client says goodbye");
        }
    }

    private static void smtpRejected() throws Exception {
        try (ServerSocket listener = new ServerSocket(0)) {
            fakeSmtp(listener, false);
            SmtpMailer mailer = new SmtpMailer("localhost", listener.getLocalPort(), SmtpMailer.Security.NONE,
                    "teacher@school.edu", "wrong", "teacher@school.edu", "ClassPulse");
            try {
                mailer.send(new Email("asha@school.edu", "Code", "t", "h"));
                throw new AssertionError("A rejected password should throw");
            } catch (IOException e) {
                check(e.getMessage().contains("App Password"), "the error explains what to do: " + e.getMessage());
                check(!e.getMessage().contains("wrong"), "the password must not appear in the error");
            }
        }
    }

    private static void badges() {
        List<HistoryEntry> none = List.of();
        StudentStats empty = StatsRepository.studentStats(none, 0);
        check(StatsRepository.badges(none, empty).stream().noneMatch(Badge::earned), "a new student has no badges");

        List<HistoryEntry> history = List.of(
                new HistoryEntry("2026-11-10 10:00", "Q1", "T", 5200, 1, 12, 6, 6),
                new HistoryEntry("2026-11-09 10:00", "Q2", "T", 3000, 4, 12, 4, 6),
                new HistoryEntry("2026-11-08 10:00", "Q3", "T", 2900, 2, 12, 5, 6));
        StudentStats stats = StatsRepository.studentStats(history, 4200);
        equal(3, stats.played(), "games played");
        equal(11100, stats.totalPoints(), "total points");
        equal(83, stats.averageAccuracy(), "accuracy is 15 of 18");
        equal(1, stats.bestRank(), "best rank");
        equal(2, stats.podiums(), "podium finishes");
        List<String> earned = StatsRepository.badges(history, stats).stream()
                .filter(Badge::earned).map(Badge::name).toList();
        equal(List.of("First Steps", "Perfect Round", "Podium", "Champion", "Sharp Mind", "Speedster",
                "Point Collector"), earned, "earned badges");
    }

    private static void shuffle() {
        var quiz = new QuizRepository.LoadedQuiz(1, "T", SAMPLE, List.of(10L, 11L, 12L, 13L, 14L, 15L));
        var mixed = quiz.shuffled(new Random(7));
        equal(SAMPLE.size(), mixed.questions().size(), "no question lost");
        for (int i = 0; i < SAMPLE.size(); i++) {
            int original = SAMPLE.indexOf(mixed.questions().get(i));
            equal(quiz.questionIds().get(original), mixed.questionIds().get(i), "question " + i + " keeps its id");
        }
        check(!mixed.questions().equals(SAMPLE), "the order should change");
    }

    // ----- Integration tests -----

    /** A fake client with no GUI, speaking the real protocol over a real socket. */
    private static final class Client implements AutoCloseable {
        private final Socket socket;
        private final DataOutputStream out;
        private final BlockingQueue<Response> inbox = new LinkedBlockingQueue<>();
        private volatile boolean closedByServer;
        String email;
        String username;

        Client(int port) throws IOException {
            socket = new Socket("localhost", port);
            socket.setTcpNoDelay(true);
            out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());
            Thread.startVirtualThread(() -> {
                try {
                    while (true) {
                        if (Protocol.read(in) instanceof Response response) {
                            inbox.add(response);
                        }
                    }
                } catch (IOException e) {
                    closedByServer = true;
                }
            });
        }

        synchronized void send(Request request) throws IOException {
            Protocol.write(out, request);
        }

        /** Waits for the next message of the given type, skipping any others. */
        <T extends Response> T await(Class<T> type) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (true) {
                long left = deadline - System.nanoTime();
                Response next = inbox.poll(Math.max(0, left), TimeUnit.NANOSECONDS);
                if (next == null) {
                    throw new AssertionError("Timed out waiting for " + type.getSimpleName());
                }
                if (type.isInstance(next)) {
                    return type.cast(next);
                }
            }
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static int nextUser;

    private static synchronized String uniqueUser() {
        return "u" + (System.currentTimeMillis() % 100_000_000L) + "_" + nextUser++;
    }

    /** Registers and verifies a new account through the real email-code flow. */
    private static Client account(int port, Role role, String fullName) throws Exception {
        Client client = new Client(port);
        client.username = uniqueUser();
        client.email = client.username + "@school.edu";
        client.send(new Register(fullName, client.username, client.email, PASSWORD, role));
        CodeSent sent = client.await(CodeSent.class);
        equal(CodePurpose.VERIFY_EMAIL, sent.purpose(), "a verification code is sent");
        client.send(new VerifyEmail(client.email, MAIL.lastCode(client.email)));
        AuthResult result = client.await(AuthResult.class);
        check(result.ok(), "verification should sign the user in: " + result.text());
        equal(role, result.account().role(), "role");
        return client;
    }

    private static Client teacher(int port) throws Exception {
        Client client = account(port, Role.TEACHER, "Test Teacher");
        client.await(QuizList.class);
        return client;
    }

    private static List<QuizSummary> quizzes(Client teacher) throws Exception {
        teacher.send(new ListQuizzes());
        return teacher.await(QuizList.class).quizzes();
    }

    private static Client student(int port, String code, String name) throws Exception {
        Client client = new Client(port);
        client.send(new JoinRoom(code, name));
        check(client.await(JoinResult.class).ok(), name + " should be able to join");
        return client;
    }

    private static void registerAndVerify(int port) throws Exception {
        String username = uniqueUser();
        String email = username + "@School.edu";
        try (Client a = new Client(port); Client b = new Client(port)) {
            a.send(new Register("Asha Rao", username, email, "short", Role.TEACHER));
            check(a.await(ErrorText.class).text().contains("8-100"), "a short password should be refused");

            a.send(new Register("Asha Rao", username, email, PASSWORD, Role.TEACHER));
            CodeSent sent = a.await(CodeSent.class);
            equal(email.toLowerCase(), sent.email(), "the email is stored in lower case");
            Email message = MAIL.awaitLast(sent.email(), "verification code");
            check(message.htmlBody().contains("Asha Rao"), "the email greets the user by name");

            // Until the email is verified, signing in only sends the user back to the code screen.
            b.send(new Login(username, PASSWORD));
            check(b.await(CodeSent.class).text().contains("verify"), "an unverified account cannot sign in");

            a.send(new VerifyEmail(sent.email(), MAIL.lastCode(sent.email())));
            AuthResult result = a.await(AuthResult.class);
            check(result.ok(), "the right code verifies the email");
            equal("Asha Rao", result.account().fullName(), "full name");
            equal("Java Basics (sample)", a.await(QuizList.class).quizzes().get(0).title(), "sample quiz");
            MAIL.awaitLast(sent.email(), "Welcome");

            b.send(new Login(username, PASSWORD));
            check(b.await(AuthResult.class).ok(), "a verified account signs in");
        }
    }

    private static void wrongCodes(int port) throws Exception {
        String username = uniqueUser();
        String email = username + "@school.edu";
        try (Client client = new Client(port)) {
            client.send(new Register("Ben Das", username, email, PASSWORD, Role.STUDENT));
            client.await(CodeSent.class);
            String right = MAIL.lastCode(email);
            String wrong = right.equals("000000") ? "111111" : "000000";
            client.send(new VerifyEmail(email, wrong));
            check(client.await(ErrorText.class).text().contains("4 attempts left"), "attempts are counted");
            for (int i = 0; i < 4; i++) {
                client.send(new VerifyEmail(email, wrong));
                client.await(ErrorText.class);
            }
            client.send(new VerifyEmail(email, right));
            check(client.await(ErrorText.class).text().contains("Too many"), "after 5 wrong tries even the right code fails");

            client.send(new ResendCode(email, CodePurpose.VERIFY_EMAIL));
            check(client.await(ErrorText.class).text().contains("wait"), "a new code needs a 60 second wait");
        }
    }

    private static void noMailDomain(int port) throws Exception {
        String username = uniqueUser();
        int before = MAIL.sent.size();
        try (Client client = new Client(port)) {
            client.send(new Register("Cara Iyer", username, username + "@fake-nomail.test", PASSWORD, Role.STUDENT));
            AuthResult result = client.await(AuthResult.class);
            check(!result.ok() && result.text().contains("cannot receive email"), "refused: " + result.text());
            client.send(new Login(username, PASSWORD));
            check(!client.await(AuthResult.class).ok(), "no account was created");
        }
        equal(before, MAIL.sent.size(), "no email was sent");
    }

    private static void duplicates(int port) throws Exception {
        try (Client first = account(port, Role.STUDENT, "Dev Shah"); Client other = new Client(port)) {
            other.send(new Register("Dev Shah", first.username, uniqueUser() + "@school.edu", PASSWORD, Role.STUDENT));
            check(other.await(AuthResult.class).text().contains("username is already taken"), "duplicate username");
            other.send(new Register("Dev Shah", uniqueUser(), first.email.toUpperCase(), PASSWORD, Role.STUDENT));
            check(other.await(AuthResult.class).text().contains("email already exists"), "duplicate email, any case");
        }
    }

    private static void loginByEmail(int port) throws Exception {
        try (Client created = account(port, Role.STUDENT, "Esha Nair"); Client c = new Client(port)) {
            c.send(new Login(created.email.toUpperCase(), PASSWORD));
            AuthResult result = c.await(AuthResult.class);
            check(result.ok(), "sign in with the email address, in any case");
            equal(created.username, result.account().username(), "the right account");
            c.send(new Login(created.username, PASSWORD));
            check(c.await(ErrorText.class).text().contains("already signed in"), "one account per connection");
            c.send(new Logout());
            c.await(Notice.class);
            c.send(new Login(created.username, "wrong-password1"));
            check(!c.await(AuthResult.class).ok(), "wrong password");
        }
    }

    private static void forgotPassword(int port) throws Exception {
        try (Client created = account(port, Role.TEACHER, "Farah Khan"); Client c = new Client(port)) {
            String unknown = uniqueUser() + "@school.edu";
            c.send(new ForgotPassword(unknown));
            check(c.await(ErrorText.class).text().contains("No ClassPulse account"), "an unknown email is refused");
            check(MAIL.to(unknown).isEmpty(), "no email is sent to an address without an account");

            c.send(new ForgotPassword(created.email));
            equal(CodePurpose.RESET_PASSWORD, c.await(CodeSent.class).purpose(), "a reset code is sent");
            String code = MAIL.lastCode(created.email);
            check(MAIL.awaitLast(created.email, "Reset").textBody().contains(code), "the reset email has the code");

            c.send(new ResetPassword(created.email, code, "brand-new-pass9"));
            c.await(PasswordResetDone.class);
            MAIL.awaitLast(created.email, "password was changed");
            c.send(new ResetPassword(created.email, code, "another-pass9"));
            check(c.await(ErrorText.class).text().contains("no active code"), "a code works only once");

            c.send(new Login(created.username, PASSWORD));
            check(!c.await(AuthResult.class).ok(), "the old password stops working");
            c.send(new Login(created.username, "brand-new-pass9"));
            check(c.await(AuthResult.class).ok(), "the new password works");
        }
    }

    private static void changePassword(int port) throws Exception {
        try (Client user = account(port, Role.STUDENT, "Gita Menon")) {
            user.send(new ChangePassword("wrong-pass1", "newer-pass1"));
            check(user.await(ErrorText.class).text().contains("current password"), "the current password is checked");
            user.send(new ChangePassword(PASSWORD, "newer-pass1"));
            user.await(Notice.class);
            user.send(new UpdateProfile("Gita M. Menon"));
            equal("Gita M. Menon", user.await(ProfileUpdated.class).account().fullName(), "new name");
            user.send(new UpdateProfile("<b>bad</b>"));
            user.await(ErrorText.class);
        }
    }

    private static void permissions(int port) throws Exception {
        try (Client stranger = new Client(port); Client owner = teacher(port); Client other = teacher(port);
             Client pupil = account(port, Role.STUDENT, "Hari Pillai")) {
            stranger.send(new ListQuizzes());
            stranger.await(ErrorText.class);
            stranger.send(new StartGame());
            stranger.await(ErrorText.class);
            stranger.send(new SubmitAnswer(0, 0));
            stranger.await(ErrorText.class);
            stranger.send(new GetReport(1));
            stranger.await(ErrorText.class);
            stranger.send(new GetStudentDashboard());
            stranger.await(ErrorText.class);
            stranger.send(new ChangePassword(PASSWORD, "x-password1"));
            stranger.await(ErrorText.class);

            pupil.send(new ListQuizzes());
            check(pupil.await(ErrorText.class).text().contains("teacher"), "students cannot use teacher requests");
            owner.send(new GetStudentDashboard());
            owner.await(ErrorText.class);

            long ownersQuiz = quizzes(owner).get(0).id();
            other.send(new CreateRoom(ownersQuiz, false));
            check(other.await(ErrorText.class).text().contains("not found"), "another teacher's quiz must stay hidden");
            other.send(new DeleteQuiz(ownersQuiz));
            other.await(ErrorText.class);
            other.send(new GetQuiz(ownersQuiz));
            other.await(ErrorText.class);

            owner.send(new CreateRoom(ownersQuiz, false));
            String code = owner.await(RoomCreated.class).code();
            other.send(new JoinRoom(code, "Teacher"));
            check(other.await(ErrorText.class).text().contains("Teachers host"), "a teacher cannot join as a student");
            try (Client student = student(port, code, "Eve")) {
                student.send(new NextStep());
                student.await(ErrorText.class);
                student.send(new CloseRoom());
                student.await(ErrorText.class);
                student.send(new KickPlayer("Eve"));
                student.await(ErrorText.class);
            }
        }
    }

    private static void quizManagement(int port) throws Exception {
        try (Client host = teacher(port)) {
            long id = quizzes(host).get(0).id();
            host.send(new GetQuiz(id));
            QuizDetail detail = host.await(QuizDetail.class);
            equal(SAMPLE, detail.quiz().questions(), "the quiz is loaded for editing");

            List<Question> edited = new ArrayList<>(SAMPLE.subList(0, 3));
            host.send(new UpdateQuiz(id, new Quiz("Java Basics v2", edited)));
            host.await(Notice.class);
            List<QuizSummary> list = host.await(QuizList.class).quizzes();
            equal(1, list.size(), "the old version is replaced in the list");
            equal("Java Basics v2", list.get(0).title(), "new title");
            equal(3, list.get(0).questionCount(), "new question count");
            host.send(new CreateRoom(id, false));
            check(host.await(ErrorText.class).text().contains("not found"), "the old version cannot be hosted");

            long v2 = list.get(0).id();
            host.send(new DuplicateQuiz(v2));
            host.await(Notice.class);
            list = host.await(QuizList.class).quizzes();
            equal(2, list.size(), "the copy is added");
            equal("Java Basics v2 (copy)", list.get(0).title(), "copy title");

            host.send(new DeleteQuiz(list.get(0).id()));
            host.await(Notice.class);
            equal(1, host.await(QuizList.class).quizzes().size(), "the copy is deleted");
            host.send(new DeleteQuiz(list.get(0).id()));
            host.await(ErrorText.class);
        }
    }

    private static void sqlInjection(int port) throws Exception {
        String attack = "x'); DROP TABLE quizzes; --";
        try (Client teacher = teacher(port)) {
            teacher.send(new SaveQuiz(new Quiz(attack, SAMPLE)));
            teacher.await(Notice.class);
            equal(attack, teacher.await(QuizList.class).quizzes().get(0).title(), "stored as plain text");
            try (Client other = new Client(port)) {
                other.send(new Login("' OR '1'='1", "' OR '1'='1"));
                check(!other.await(AuthResult.class).ok(), "injection in login");
                other.send(new ForgotPassword("x'or'1'='1@a.co"));
                other.await(ErrorText.class);
            }
            equal(2, quizzes(teacher).size(), "the tables must still exist");
        }
    }

    private static int wrongChoice(Question question) {
        return (question.correctIndex() + 1) % Question.OPTION_COUNT;
    }

    private static void fullGame(int port) throws Exception {
        try (Client host = teacher(port); Client other = teacher(port)) {
            host.send(new CreateRoom(quizzes(host).get(0).id(), false));
            RoomCreated room = host.await(RoomCreated.class);
            equal(SAMPLE.size(), room.questionCount(), "question count");

            try (Client asha = student(port, room.code(), "Asha");
                 Client ben = student(port, room.code(), "Ben");
                 Client cara = student(port, room.code(), "Cara");
                 Client copy = new Client(port);
                 Client lost = new Client(port)) {
                copy.send(new JoinRoom(room.code(), "ASHA"));
                check(!copy.await(JoinResult.class).ok(), "names should be unique, ignoring case");
                lost.send(new JoinRoom("ZZZZZZ", "Dan"));
                check(!lost.await(JoinResult.class).ok(), "an unknown room code");

                // Question 1: Asha is right, Ben is wrong, Cara is right but later.
                host.send(new StartGame());
                QuestionStarted first = asha.await(QuestionStarted.class);
                equal(SAMPLE.get(0).text(), first.text(), "first question");
                ben.await(QuestionStarted.class);
                cara.await(QuestionStarted.class);

                host.send(new NextStep());
                host.await(ErrorText.class); // Too early: the question is still running.

                asha.send(new SubmitAnswer(0, SAMPLE.get(0).correctIndex()));
                check(asha.await(AnswerReceived.class).accepted(), "Asha's answer");
                asha.send(new SubmitAnswer(0, wrongChoice(SAMPLE.get(0))));
                check(!asha.await(AnswerReceived.class).accepted(), "a second answer must be refused");
                ben.send(new SubmitAnswer(0, wrongChoice(SAMPLE.get(0))));
                ben.await(AnswerReceived.class);
                ben.send(new SubmitAnswer(0, 99));
                check(!ben.await(AnswerReceived.class).accepted(), "an invalid choice must be refused");
                Thread.sleep(300);
                cara.send(new SubmitAnswer(0, SAMPLE.get(0).correctIndex()));
                cara.await(AnswerReceived.class);

                QuestionEnded hostView = host.await(QuestionEnded.class);
                check(hostView.you() == null, "the teacher has no personal result");
                equal(2, hostView.counts().get(SAMPLE.get(0).correctIndex()), "correct count");
                QuestionEnded ashaView = asha.await(QuestionEnded.class);
                QuestionEnded benView = ben.await(QuestionEnded.class);
                QuestionEnded caraView = cara.await(QuestionEnded.class);
                check(ashaView.you().correct() && ashaView.you().pointsEarned() > 500, "Asha earned a bonus");
                equal(0, ashaView.you().streakBonus(), "no streak bonus on the first question");
                equal(0, benView.you().pointsEarned(), "Ben's points");
                check(caraView.you().pointsEarned() < ashaView.you().pointsEarned(), "a faster answer earns more");
                equal(1, ashaView.you().rank(), "Asha's rank");
                equal(3, benView.you().rank(), "Ben's rank");

                asha.send(new SubmitAnswer(0, 0));
                check(!asha.await(AnswerReceived.class).accepted(), "a late answer must be refused");

                // Cara disconnects while question 2 is running. The question should still end.
                host.send(new NextStep());
                asha.await(QuestionStarted.class);
                ben.await(QuestionStarted.class);
                cara.close();
                asha.send(new SubmitAnswer(1, SAMPLE.get(1).correctIndex()));
                ben.send(new SubmitAnswer(1, SAMPLE.get(1).correctIndex()));
                host.await(QuestionEnded.class);
                QuestionEnded ashaSecond = asha.await(QuestionEnded.class);
                equal(2, ashaSecond.you().streak(), "two in a row");
                equal(50, ashaSecond.you().streakBonus(), "streak bonus for two in a row");
                equal(0, ben.await(QuestionEnded.class).you().streakBonus(), "Ben has no streak yet");

                // Play the remaining questions.
                for (int i = 2; i < SAMPLE.size(); i++) {
                    host.send(new NextStep());
                    asha.await(QuestionStarted.class);
                    ben.await(QuestionStarted.class);
                    asha.send(new SubmitAnswer(i, SAMPLE.get(i).correctIndex()));
                    ben.send(new SubmitAnswer(i, wrongChoice(SAMPLE.get(i))));
                    host.await(QuestionEnded.class);
                    asha.await(QuestionEnded.class);
                    ben.await(QuestionEnded.class);
                }
                host.send(new NextStep());
                GameOver finalHost = host.await(GameOver.class);
                check(finalHost.saved(), "results should be saved");
                equal(3, finalHost.ranking().size(), "Cara stays in the ranking after leaving");
                equal("Asha", finalHost.ranking().get(0).name(), "the winner");
                GameOver ashaFinal = asha.await(GameOver.class);
                equal(1, ashaFinal.you().rank(), "Asha's final rank");
                equal(6, ashaFinal.correct(), "Asha got all six");
                check(!ashaFinal.emailed(), "a guest gets no email");

                // The report is read back from MySQL.
                host.send(new ListSessions());
                var session = host.await(SessionList.class).sessions().get(0);
                equal(3, session.players(), "players saved");
                check(session.completed(), "session marked complete");
                host.send(new GetReport(session.id()));
                var report = host.await(Report.class).report();
                equal(finalHost.ranking(), report.ranking(), "saved ranking");
                equal(SAMPLE.size(), report.questions().size(), "one row per question");
                QuestionStat q1 = report.questions().get(0);
                equal(3, q1.answered(), "question 1 answered");
                equal(2, q1.correct(), "question 1 correct");
                equal(2, report.questions().get(1).answered(), "question 2 answered");
                equal(1, report.questions().get(2).correct(), "question 3 correct");

                other.send(new GetReport(session.id()));
                check(other.await(ErrorText.class).text().contains("not found"), "another teacher's report");

                // The room is gone once the game ends.
                try (Client late = new Client(port)) {
                    late.send(new JoinRoom(room.code(), "Late"));
                    check(!late.await(JoinResult.class).ok(), "a finished room cannot be joined");
                }
            }
        }
    }

    private static void studentAccount(int port) throws Exception {
        try (Client host = teacher(port); Client pupil = account(port, Role.STUDENT, "Isha Verma");
             Client guest = new Client(port)) {
            pupil.send(new GetStudentDashboard());
            StudentDashboard before = pupil.await(StudentDashboard.class);
            equal(0, before.stats().played(), "nothing played yet");

            host.send(new CreateRoom(quizzes(host).get(0).id(), true));
            RoomCreated room = host.await(RoomCreated.class);
            check(room.shuffled(), "the room is shuffled");
            pupil.send(new JoinRoom(room.code(), "Isha"));
            check(pupil.await(JoinResult.class).ok(), "a signed-in student joins");
            guest.send(new JoinRoom(room.code(), "Guest"));
            guest.await(JoinResult.class);

            host.send(new StartGame());
            for (int i = 0; i < SAMPLE.size(); i++) {
                QuestionStarted q = pupil.await(QuestionStarted.class);
                guest.await(QuestionStarted.class);
                Question question = SAMPLE.stream().filter(s -> s.text().equals(q.text())).findFirst().orElseThrow();
                pupil.send(new SubmitAnswer(i, question.correctIndex()));
                guest.send(new SubmitAnswer(i, wrongChoice(question)));
                host.await(QuestionEnded.class);
                pupil.await(QuestionEnded.class);
                guest.await(QuestionEnded.class);
                host.send(new NextStep());
            }
            GameOver over = pupil.await(GameOver.class);
            check(over.emailed(), "a signed-in student is told about the email");
            host.await(GameOver.class);

            Email result = MAIL.awaitLast(pupil.email, "Your result");
            check(result.subject().contains("#1 of 2"), "the subject has the rank: " + result.subject());
            check(result.textBody().contains("6 of 6 correct"), "the email has the score");

            pupil.send(new GetStudentDashboard());
            StudentDashboard after = pupil.await(StudentDashboard.class);
            equal(1, after.stats().played(), "one game in the history");
            equal(100, after.stats().averageAccuracy(), "all correct");
            HistoryEntry entry = after.history().get(0);
            equal("Test Teacher", entry.teacherName(), "teacher's name");
            equal(1, entry.rank(), "rank");
            equal(2, entry.players(), "players");
            check(after.badges().stream().anyMatch(b -> b.name().equals("Perfect Round") && b.earned()),
                    "Perfect Round badge");
        }
    }

    private static void reportEmail(int port) throws Exception {
        try (Client host = teacher(port)) {
            host.send(new CreateRoom(quizzes(host).get(0).id(), false));
            String code = host.await(RoomCreated.class).code();
            try (Client s = student(port, code, "Jay")) {
                host.send(new StartGame());
                s.await(QuestionStarted.class);
                s.send(new SubmitAnswer(0, SAMPLE.get(0).correctIndex()));
                host.await(QuestionEnded.class);
                host.send(new CloseRoom());
                host.await(RoomClosed.class);
            }
            Thread.sleep(500); // The partial session is saved on a background thread.
            host.send(new ListSessions());
            long id = host.await(SessionList.class).sessions().get(0).id();
            host.send(new EmailReport(id));
            check(host.await(Notice.class).text().contains(host.email), "the teacher is told where it went");
            Email email = MAIL.awaitLast(host.email, "ClassPulse report");
            equal(1, email.attachments().size(), "a CSV attachment");
            String csv = new String(email.attachments().get(0).content(), StandardCharsets.UTF_8);
            check(csv.startsWith("Rank,Name,Score") && csv.contains("Jay"), "the CSV has the ranking");
            check(email.htmlBody().contains("Worth reteaching"), "the email has the insight");
        }
    }

    private static void overview(int port) throws Exception {
        try (Client host = teacher(port)) {
            host.send(new GetOverview());
            var stats = host.await(Overview.class).stats();
            equal(1, stats.quizzes(), "the sample quiz");
            equal(0, stats.sessions(), "no games yet");
            host.send(new CreateRoom(quizzes(host).get(0).id(), false));
            String code = host.await(RoomCreated.class).code();
            try (Client s = student(port, code, "Kim")) {
                host.send(new StartGame());
                for (int i = 0; i < SAMPLE.size(); i++) {
                    s.await(QuestionStarted.class);
                    s.send(new SubmitAnswer(i, i % 2 == 0 ? SAMPLE.get(i).correctIndex() : wrongChoice(SAMPLE.get(i))));
                    host.await(QuestionEnded.class);
                    host.send(new NextStep());
                }
                host.await(GameOver.class);
            }
            host.send(new GetOverview());
            stats = host.await(Overview.class).stats();
            equal(1, stats.sessions(), "one game");
            equal(1, stats.studentsReached(), "one student");
            equal(50, stats.averageAccuracy(), "half right");
            equal(1, stats.recent().size(), "recent sessions");
        }
    }

    private static void kick(int port) throws Exception {
        try (Client host = teacher(port)) {
            host.send(new CreateRoom(quizzes(host).get(0).id(), false));
            String code = host.await(RoomCreated.class).code();
            try (Client rude = student(port, code, "Rude"); Client kind = student(port, code, "Kind")) {
                host.send(new KickPlayer("Rude"));
                check(rude.await(RoomClosed.class).reason().contains("removed"), "the student is told");
                // Skip the older updates from when each student joined; await times out if Rude is never removed.
                while (!host.await(PlayersChanged.class).names().equals(List.of("Kind"))) {
                    Thread.onSpinWait();
                }
                rude.send(new JoinRoom(code, "Rude2"));
                check(rude.await(JoinResult.class).ok(), "a removed student may rejoin with a better name");
                host.send(new CloseRoom());
                kind.await(RoomClosed.class);
            }
        }
    }

    private static void timer(int port) throws Exception {
        Question quick = new Question("Quick?", List.of("a", "b", "c", "d"), 0, Question.MIN_SECONDS);
        try (Client host = teacher(port)) {
            host.send(new SaveQuiz(new Quiz("Timer", List.of(quick))));
            long id = host.await(QuizList.class).quizzes().get(0).id();
            host.send(new CreateRoom(id, false));
            String code = host.await(RoomCreated.class).code();
            try (Client student = student(port, code, "Slow")) {
                host.send(new StartGame());
                student.await(QuestionStarted.class);
                long start = System.nanoTime();
                QuestionEnded ended = student.await(QuestionEnded.class); // The student never answers.
                long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                check(ms >= 4500 && ms <= 6500, "should end after about 5 seconds but took " + ms);
                check(!ended.you().answered(), "marked as not answered");
                equal(0, ended.you().totalScore(), "no points");
            }
        }
    }

    private static void hostDisconnect(int port) throws Exception {
        try (Client host = teacher(port)) {
            host.send(new CreateRoom(quizzes(host).get(0).id(), false));
            String code = host.await(RoomCreated.class).code();
            try (Client student = student(port, code, "Stuck")) {
                host.send(new StartGame());
                student.await(QuestionStarted.class);
                host.close();
                check(student.await(RoomClosed.class).reason().contains("disconnected"), "the student is told why");
                try (Client late = new Client(port)) {
                    late.send(new JoinRoom(code, "Late"));
                    check(!late.await(JoinResult.class).ok(), "a closed room cannot be joined");
                }
            }
        }
    }

    private static void manyStudents(int port) throws Exception {
        int count = 50;
        try (Client host = teacher(port)) {
            long id = quizzes(host).get(0).id();
            host.send(new CreateRoom(id, false));
            String code = host.await(RoomCreated.class).code();
            List<Client> students = new ArrayList<>();
            try {
                for (int i = 0; i < count; i++) {
                    students.add(student(port, code, "S" + i));
                }
                host.send(new StartGame());
                for (Client student : students) {
                    student.await(QuestionStarted.class);
                }
                // Everyone answers at the same time, each on their own thread.
                List<Thread> threads = new ArrayList<>();
                for (Client student : students) {
                    threads.add(Thread.startVirtualThread(() -> {
                        try {
                            student.send(new SubmitAnswer(0, 0));
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }));
                }
                for (Thread thread : threads) {
                    thread.join();
                }
                QuestionEnded ended = host.await(QuestionEnded.class);
                equal(count, ended.counts().stream().mapToInt(Integer::intValue).sum(), "no answers were lost");
                host.send(new CloseRoom());
                host.await(RoomClosed.class);
            } finally {
                for (Client student : students) {
                    student.close();
                }
            }
        }
    }

    private static void garbage(int port) throws Exception {
        try (Socket raw = new Socket("localhost", port)) {
            raw.getOutputStream().write("GET / HTTP/1.1\r\n\r\nthis is not a valid message".getBytes());
            raw.getOutputStream().flush();
            raw.setSoTimeout(5000);
            equal(-1, raw.getInputStream().read(), "the server should close the connection");
        }
        try (Client after = new Client(port)) {
            after.send(new ListQuizzes());
            after.await(ErrorText.class); // The server still answers.
        }
    }

    private static void loginLimit(int port) throws Exception {
        try (Client client = new Client(port)) {
            for (int i = 0; i < 5; i++) {
                client.send(new Login("nobody_here", "wrong-password1"));
                check(!client.await(AuthResult.class).ok(), "a wrong password is refused");
            }
            client.await(ErrorText.class);
            Thread.sleep(600);
            check(client.closedByServer, "the connection should be closed");
        }
    }

    // ----- Integration tests added with the upgrades -----

    private static void certificateEmail(int port) throws Exception {
        try (Client host = teacher(port); Client winner = account(port, Role.STUDENT, "Cert Winner");
             Client fourth = account(port, Role.STUDENT, "Cert Fourth");
             Client a = new Client(port); Client b = new Client(port)) {
            host.send(new CreateRoom(quizzes(host).get(0).id(), false));
            String code = host.await(RoomCreated.class).code();
            Client[] players = {winner, a, b, fourth};
            String[] names = {"Win", "GuestA", "GuestB", "Fourth"};
            for (int i = 0; i < players.length; i++) {
                players[i].send(new JoinRoom(code, names[i]));
                check(players[i].await(JoinResult.class).ok(), names[i] + " joins");
            }
            host.send(new StartGame());
            for (int q = 0; q < SAMPLE.size(); q++) {
                for (Client player : players) {
                    player.await(QuestionStarted.class);
                }
                // The winner is always right, the guests sometimes, the fourth never.
                winner.send(new SubmitAnswer(q, SAMPLE.get(q).correctIndex()));
                a.send(new SubmitAnswer(q, q % 2 == 0 ? SAMPLE.get(q).correctIndex() : wrongChoice(SAMPLE.get(q))));
                b.send(new SubmitAnswer(q, q % 3 == 0 ? SAMPLE.get(q).correctIndex() : wrongChoice(SAMPLE.get(q))));
                fourth.send(new SubmitAnswer(q, wrongChoice(SAMPLE.get(q))));
                host.await(QuestionEnded.class);
                host.send(new NextStep());
            }
            host.await(GameOver.class);
            Email first = MAIL.awaitLast(winner.email, "Your result");
            equal(1, first.attachments().size(), "the winner gets one attachment");
            equal("image/png", first.attachments().get(0).contentType(), "a PNG image");
            check(first.htmlBody().contains("certificate is attached"), "the email mentions the certificate");
            Email last = MAIL.awaitLast(fourth.email, "Your result");
            equal(0, last.attachments().size(), "4th place gets no certificate");
        }
    }

    private static void homework(int port, ClassPulseServer server) throws Exception {
        try (Client host = teacher(port); Client done = account(port, Role.STUDENT, "Hw Done");
             Client late = account(port, Role.STUDENT, "Hw Late"); Client outsider = account(port, Role.STUDENT, "Hw Out")) {
            host.send(new CreateClass("Java A"));
            check(host.await(Notice.class).text().contains("Class created"), "the teacher is told");
            var info = host.await(ClassList.class).classes().get(0);
            equal("Java A", info.name(), "class name");
            equal(6, info.joinCode().length(), "a 6-character join code");

            done.send(new JoinClass(info.joinCode().toLowerCase()));
            check(done.await(MyHomework.class).classNames().contains("Java A"), "joined (codes ignore case)");
            late.send(new JoinClass(info.joinCode()));
            late.await(MyHomework.class);
            outsider.send(new JoinClass("ZZZZZ9"));
            check(outsider.await(ErrorText.class).text().contains("No class"), "a wrong code is refused");
            host.send(new JoinClass(info.joinCode()));
            check(host.await(ErrorText.class).text().contains("student"), "a teacher cannot join a class");

            host.send(new AssignHomework(info.id(), quizzes(host).get(0).id(), 120));
            host.await(Notice.class);
            host.await(ClassList.class);
            var list = host.await(HomeworkList.class).homework();
            equal(2, list.get(0).members(), "two students in the class");
            equal(0, list.get(0).submitted(), "none handed in yet");
            check(MAIL.awaitLast(done.email, "New homework").textBody().contains("Java A"), "students are emailed");
            MAIL.awaitLast(late.email, "New homework");
            check(MAIL.to(outsider.email).stream().noneMatch(e -> e.subject().contains("New homework")),
                    "students outside the class are not emailed");

            done.send(new GetMyHomework());
            var mine = done.await(MyHomework.class).homework().get(0);
            check(mine.open() && !mine.done(), "open and not done");
            outsider.send(new StartHomework(mine.id()));
            check(outsider.await(ErrorText.class).text().contains("not found"), "outsiders cannot open it");

            done.send(new StartHomework(mine.id()));
            HomeworkStarted started = done.await(HomeworkStarted.class);
            equal(SAMPLE.size(), started.questions().size(), "all questions");
            List<Integer> choices = new ArrayList<>();
            for (Question q : SAMPLE) {
                choices.add(q.correctIndex());
            }
            choices.set(1, -1); // Ran out of time on question 2.
            done.send(new SubmitHomework(mine.id(), choices));
            HomeworkDone result = done.await(HomeworkDone.class);
            equal(SAMPLE.size() - 1, result.correct(), "marked by the server");
            check(MAIL.awaitLast(done.email, "Homework handed in").textBody().contains("(no answer)"),
                    "the result email lists every answer");
            done.send(new SubmitHomework(mine.id(), choices));
            check(done.await(ErrorText.class).text().contains("already"), "only one try");
            done.send(new StartHomework(mine.id()));
            check(done.await(ErrorText.class).text().contains("already"), "cannot start again");

            // The reminder comes halfway (60 minutes) for homework due in 2 hours, only to those not done.
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            server.homework().runChecks(now.plusMinutes(30));
            check(MAIL.to(late.email).stream().noneMatch(e -> e.subject().startsWith("Reminder")), "not yet");
            server.homework().runChecks(now.plusMinutes(61));
            MAIL.awaitLast(late.email, "Reminder");
            server.homework().runChecks(now.plusMinutes(62));
            equal(1L, MAIL.to(late.email).stream().filter(e -> e.subject().startsWith("Reminder")).count(),
                    "only one reminder");
            check(MAIL.to(done.email).stream().noneMatch(e -> e.subject().startsWith("Reminder")),
                    "no reminder for a student who handed it in");

            // After the deadline the teacher gets a summary with a CSV.
            server.homework().runChecks(now.plusMinutes(121));
            Email summary = MAIL.awaitLast(host.email, "Homework closed");
            check(summary.subject().contains("1 of 2"), "1 of 2 handed in: " + summary.subject());
            String csv = new String(summary.attachments().get(0).content(), StandardCharsets.UTF_8);
            check(csv.contains("Hw Done,Yes,5,6") && csv.contains("Hw Late,No"), "the CSV lists everyone: " + csv);
        }
    }

    private static void phonePlay(int port, ClassPulseServer server) throws Exception {
        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        try (Client host = teacher(port); Client app = new Client(port)) {
            host.send(new CreateRoom(quizzes(host).get(0).id(), false));
            String code = host.await(RoomCreated.class).code();
            String link = host.await(PhoneLink.class).url();
            check(link.startsWith("http://127.0.0.1:") && link.endsWith("?code=" + code), "the QR link: " + link);
            String base = link.substring(0, link.indexOf('?'));

            String page = UpgradeTests.get(http, base);
            check(page.contains("Join a live quiz") && page.contains("/api/poll"), "the phone page is served");
            check(UpgradeTests.post(http, base + "api/join", "code=" + code + "&name=%3Cb%3E").contains("\"ok\":false"),
                    "unsafe names are refused");
            check(UpgradeTests.post(http, base + "api/join", "code=ZZZZZZ&name=Phone").contains("No room"),
                    "a wrong code is refused");
            String joined = UpgradeTests.post(http, base + "api/join", "code=" + code + "&name=Phone+One");
            check(joined.contains("\"ok\":true"), "a phone joins: " + joined);
            String token = UpgradeTests.field(joined, "token");

            app.send(new JoinRoom(code, "App Kid"));
            check(app.await(JoinResult.class).ok(), "an app student joins the same room");
            while (host.await(PlayersChanged.class).names().size() < 2) {
                Thread.onSpinWait();
            }

            host.send(new StartGame());
            app.await(QuestionStarted.class);
            String events = UpgradeTests.pollUntil(http, base, token, "\"type\":\"question\"");
            check(events.contains(UpgradeTests.jsonText(SAMPLE.get(0).text())), "the phone gets the question");
            UpgradeTests.post(http, base + "api/answer",
                    "token=" + token + "&index=0&choice=" + SAMPLE.get(0).correctIndex());
            app.send(new SubmitAnswer(0, wrongChoice(SAMPLE.get(0))));
            QuestionEnded ended = host.await(QuestionEnded.class);
            equal(2, ended.counts().stream().mapToInt(Integer::intValue).sum(), "both answers counted");
            equal("Phone One", ended.top().get(0).name(), "the phone player is first");
            String reveal = UpgradeTests.pollUntil(http, base, token, "\"type\":\"ended\"");
            check(reveal.contains("\"correct\":true"), "the phone sees it was right");

            host.send(new CloseRoom());
            host.await(RoomClosed.class);
            UpgradeTests.pollUntil(http, base, token, "\"type\":\"closed\"");
            check(server.phoneServer().connectedPhones() >= 1, "the phone stays until it has seen the end");
        }
    }

    private static ClassPulseServer openServer(String configFile) throws Exception {
        Properties config = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(configFile))) {
            config.load(reader);
        }
        String password = config.getProperty("db.password");
        if (password == null) {
            Console console = System.console();
            char[] typed = console == null ? null
                    : console.readPassword("MySQL password for the test database: ");
            password = typed == null ? "" : new String(typed);
        }
        return ClassPulseServer.open(config.getProperty("db.url"), config.getProperty("db.user"), password, 0,
                MAIL, new TestDomains());
    }
}
