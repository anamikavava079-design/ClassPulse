package classpulse.client;

import classpulse.mail.DomainChecker;
import classpulse.mail.Email;
import classpulse.mail.Mailer;
import classpulse.server.ClassPulseServer;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * End-to-end GUI test. Drives the real windows against a real server and MySQL, clicking
 * the real buttons, and saves each screen as an image. Windows stay hidden, so nothing
 * appears on screen. Emails are captured instead of sent, so the test can read the codes.
 * Usage: ScreenshotTour database-config-file output-folder
 */
public final class ScreenshotTour {
    private static final int[] CORRECT = {1, 2, 1, 0, 3, 1};
    private static final ConcurrentLinkedQueue<Email> MAIL = new ConcurrentLinkedQueue<>();
    private static Path output;

    private ScreenshotTour() {
    }

    public static void main(String[] args) throws Exception {
        output = Path.of(args[1]);
        Files.createDirectories(output);
        Properties config = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(args[0]))) {
            config.load(reader);
        }
        Mailer mailer = new Mailer() {
            @Override
            public void send(Email email) {
                MAIL.add(email);
            }

            @Override
            public String describe() {
                return "captured";
            }
        };
        DomainChecker noNetwork = new DomainChecker() {
            @Override
            protected Status lookup(String domain) {
                return Status.ACCEPTS_MAIL;
            }
        };
        String password = config.getProperty("db.password");
        if (password == null) {
            java.io.Console console = System.console();
            char[] typed = console == null ? null : console.readPassword("MySQL password for the screenshots database: ");
            password = typed == null ? "" : new String(typed);
        }
        String url = config.getProperty("db.url");
        freshDatabase(url, config.getProperty("db.user"), password);
        try (ClassPulseServer server = ClassPulseServer.open(url, config.getProperty("db.user"), password, 0, mailer,
                noNetwork)) {
            Thread accept = new Thread(server::acceptClients);
            accept.setDaemon(true);
            accept.start();
            try {
                server.startPhoneServer(8080, null); // So the lobby shows the QR code.
            } catch (java.io.IOException busy) {
                server.startPhoneServer(0, null);
            }
            tour(server.port());
        }
        System.out.println("TOUR PASSED");
        System.exit(0);
    }

    /**
     * The walkthrough always starts from an empty database, so it can be run again and again.
     * For safety it only ever drops a database whose name ends in "_screenshots".
     */
    private static void freshDatabase(String url, String user, String password) throws Exception {
        Matcher m = Pattern.compile("jdbc:mysql://([^/]+)/(\\w+)").matcher(url);
        if (!m.find() || !m.group(2).endsWith("_screenshots")) {
            throw new IllegalArgumentException("The walkthrough needs its own database whose name ends in _screenshots.");
        }
        try (java.sql.Connection c = java.sql.DriverManager.getConnection(
                "jdbc:mysql://" + m.group(1) + "/?allowPublicKeyRetrieval=true", user, password);
             java.sql.Statement s = c.createStatement()) {
            s.executeUpdate("DROP DATABASE IF EXISTS " + m.group(2));
        }
    }

    private static String code(String email) {
        List<Email> emails = MAIL.stream().filter(e -> e.to().equals(email)).toList();
        for (int i = emails.size() - 1; i >= 0; i--) {
            Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(emails.get(i).textBody());
            if (m.find()) {
                return m.group(1);
            }
        }
        throw new AssertionError("No code emailed to " + email);
    }

    private static void register(AppFrame frame, boolean teacher, String name, String user, String email,
                                 String password, String shotPrefix) throws Exception {
        onEdt(() -> {
            frame.auth.roleSwitch.setSelected(teacher ? 1 : 0);
            frame.auth.showView(AuthPanel.REGISTER);
            frame.auth.fullNameField.setText(name);
            frame.auth.usernameField.setText(user);
            frame.auth.emailField.setText(email);
            frame.auth.newPasswordField.setText(password);
            frame.auth.confirmField.setText(password);
        });
        if (shotPrefix != null) {
            capture(frame, shotPrefix + "-register");
        }
        onEdt(frame.auth.createButton::doClick);
        waitFor("code screen", () -> frame.auth.currentView().equals(AuthPanel.VERIFY));
        if (shotPrefix != null) {
            capture(frame, shotPrefix + "-verify-email");
        }
        String code = code(email);
        onEdt(() -> {
            frame.auth.codeField.setText(code);
            frame.auth.verifyButton.doClick();
        });
        String expected = teacher ? AppFrame.TEACHER : AppFrame.STUDENT;
        waitFor("signed in", () -> frame.currentCard().equals(expected));
    }

    private static void tour(int port) throws Exception {
        AppFrame host = frame(port);
        capture(host, "01-sign-in-student");
        onEdt(() -> host.auth.roleSwitch.setSelected(1));
        capture(host, "02-sign-in-teacher");
        onEdt(() -> {
            host.auth.showView(AuthPanel.REGISTER);
            host.auth.emailField.setText("anamika@gmial.com");
            host.auth.fullNameField.setText("Anamika Edathatta");
            host.auth.usernameField.setText("anamika_t");
            host.auth.newPasswordField.setText("Teach2026!");
            host.auth.confirmField.setText("Teach2026!");
            host.auth.createButton.doClick();
        });
        capture(host, "03-email-typo-caught");
        register(host, true, "Anamika Edathatta", "anamika_t", "anamika.teacher@school.edu", "Teach2026!", "04");

        waitFor("overview numbers", () -> host.teacher.overview.quizzes.value().equals("1"));
        Thread.sleep(900); // Let the numbers finish counting up.
        capture(host, "05-teacher-overview");

        onEdt(() -> host.teacher.open(TeacherShell.QUIZZES));
        waitFor("quiz list", () -> host.teacher.quizzes.quizModel.size() == 1);
        onEdt(() -> host.teacher.quizzes.importFile(new File("sample-data", "oop-concepts-quiz.csv")));
        waitFor("imported quiz", () -> host.teacher.quizzes.quizModel.size() == 2);
        onEdt(() -> host.teacher.quizzes.quizList.setSelectedIndex(1));
        capture(host, "06-my-quizzes");

        onEdt(host.teacher.quizzes.editButton::doClick);
        waitFor("editor", () -> host.teacher.currentPage().equals(TeacherShell.EDITOR)
                && host.teacher.editor.questions.size() == 6);
        onEdt(() -> host.teacher.editor.questionList.setSelectedIndex(1));
        capture(host, "07-quiz-editor");
        onEdt(() -> host.teacher.open(TeacherShell.QUIZZES));
        waitFor("quizzes again", () -> host.teacher.quizzes.quizModel.size() == 2);
        onEdt(() -> {
            for (int i = 0; i < host.teacher.quizzes.quizModel.size(); i++) {
                if (host.teacher.quizzes.quizModel.get(i).title().startsWith("Java")) {
                    host.teacher.quizzes.quizList.setSelectedIndex(i);
                }
            }
            host.teacher.quizzes.hostButton.doClick();
        });
        waitFor("lobby", () -> host.currentCard().equals(AppFrame.LOBBY));
        String code = onEdtGet(host.lobby::code);

        AppFrame pupil = frame(port);
        register(pupil, false, "Priya Sharma", "priya_s", "priya@school.edu", "Learn2026!", "08");
        waitFor("student dashboard", () -> pupil.student.played.value().equals("0"));
        onEdt(() -> pupil.student.codeField.setText(code));
        capture(pupil, "09-student-join");
        onEdt(pupil.student.joinButton::doClick);
        waitFor("pupil waiting", () -> pupil.currentCard().equals(AppFrame.STUDENT_GAME));
        capture(pupil, "10-student-waiting");

        AppFrame guest = frame(port);
        onEdt(() -> {
            guest.auth.showView(AuthPanel.GUEST);
            guest.auth.guestCodeField.setText(code);
            guest.auth.guestNameField.setText("Rahul");
        });
        capture(guest, "11-guest-join");
        onEdt(guest.auth.guestJoinButton::doClick);
        waitFor("guest waiting", () -> guest.currentCard().equals(AppFrame.STUDENT_GAME));

        waitFor("two players", () -> host.lobby.playerList.getModel().getSize() == 2);
        capture(host, "12-lobby");
        onEdt(host.lobby.copyCodeButton::doClick);
        Thread.sleep(400);
        captureWithToasts(host, "12b-lobby-copy-code");

        onEdt(host.lobby.startButton::doClick);
        waitFor("first question", () -> host.currentCard().equals(AppFrame.HOST_GAME)
                && pupil.studentGame.optionButtons.size() == 4 && guest.studentGame.optionButtons.size() == 4);
        Thread.sleep(1200);
        capture(host, "13-teacher-question");
        capture(pupil, "14-student-question");

        for (int i = 0; i < CORRECT.length; i++) {
            int index = i;
            waitFor("question " + (i + 1), () -> pupil.studentGame.optionButtons.size() == 4
                    && pupil.studentGame.optionButtons.get(0).isEnabled()
                    && guest.studentGame.optionButtons.get(0).isEnabled());
            onEdt(() -> pupil.studentGame.optionButtons.get(CORRECT[index]).doClick());
            if (i == 0) {
                waitFor("locked", () -> pupil.studentGame.messageTitleForTest().equals("Answer locked in"));
                capture(pupil, "15-answer-locked");
            }
            onEdt(() -> guest.studentGame.optionButtons.get((CORRECT[index] + 1) % 4).doClick());
            waitFor("reveal " + (i + 1), () -> host.hostGame.nextButton.isEnabled());
            waitFor("student result", () -> pupil.studentGame.messageTitleForTest().equals("Correct!"));
            if (i == 0) {
                capture(host, "16-teacher-results");
                capture(guest, "17-student-wrong");
            }
            if (i == 2) {
                capture(pupil, "18-student-correct-streak");
            }
            onEdt(host.hostGame.nextButton::doClick);
        }
        waitFor("final results", () -> host.currentCard().equals(AppFrame.RESULTS)
                && pupil.currentCard().equals(AppFrame.RESULTS));
        onEdt(() -> {
            host.results.podium.finishAnimation();
            pupil.results.podium.finishAnimation();
        });
        capture(host, "19-teacher-final");
        capture(pupil, "20-student-final");
        if (!onEdtGet(pupil.results::headlineText).equals("You finished #1")) {
            throw new AssertionError("Priya should win");
        }

        onEdt(host.results.actionButton::doClick);
        waitFor("teacher back", () -> host.currentCard().equals(AppFrame.TEACHER));
        onEdt(() -> host.teacher.open(TeacherShell.REPORTS));
        waitFor("report loaded", () -> host.teacher.reports.sessionTable.getRowCount() == 1
                && host.teacher.reports.currentReportForTest() != null);
        capture(host, "21-reports");
        onEdt(host.teacher.reports.emailButton::doClick);
        waitFor("report emailed", () -> {
            String toast = host.toasts().currentText();
            return toast != null && toast.startsWith("Report sent");
        });
        Thread.sleep(400);
        captureWithToasts(host, "22-report-emailed");
        onEdt(() -> host.teacher.open(TeacherShell.OVERVIEW));
        waitFor("overview after game", () -> host.teacher.overview.sessions.value().equals("1"));
        Thread.sleep(900);
        capture(host, "23-teacher-overview-after-game");

        onEdt(pupil.results.actionButton::doClick);
        waitFor("student results", () -> pupil.currentCard().equals(AppFrame.STUDENT)
                && pupil.student.played.value().equals("1"));
        Thread.sleep(900);
        capture(pupil, "24-student-results");
        onEdt(() -> pupil.student.open(StudentShell.BADGES));
        Thread.sleep(500);
        capture(pupil, "25-student-achievements");
        onEdt(() -> pupil.student.open(StudentShell.PROFILE));
        capture(pupil, "26-profile");

        // Classes and homework (added in the homework upgrade).
        onEdt(() -> host.teacher.open(TeacherShell.HOMEWORK));
        onEdt(() -> {
            host.teacher.homework.classNameField.setText("CSE-A Java");
            host.teacher.homework.createClassButton.doClick();
        });
        waitFor("class created", () -> host.teacher.homework.classModel.getRowCount() == 1
                && host.teacher.homework.quizBox.getItemCount() == 2);
        String joinCode = onEdtGet(() -> String.valueOf(host.teacher.homework.classModel.getValueAt(0, 1)));
        onEdt(() -> pupil.student.open(StudentShell.HOMEWORK));
        onEdt(() -> {
            pupil.student.homework.codeField.setText(joinCode);
            pupil.student.homework.joinButton.doClick();
        });
        waitFor("joined class", () -> pupil.student.homework.classesLabel.getText().contains("CSE-A Java"));
        onEdt(() -> {
            TeacherHomeworkPanel page = host.teacher.homework;
            for (int i = 0; i < page.quizBox.getItemCount(); i++) {
                if (String.valueOf(page.quizBox.getItemAt(i)).startsWith("Java")) {
                    page.quizBox.setSelectedIndex(i);
                }
            }
            page.dueAmount.setValue(2);
            page.dueUnit.setSelectedItem("days");
            page.assignButton.doClick();
        });
        waitFor("homework set", () -> host.teacher.homework.homeworkModel.getRowCount() == 1
                && "1".equals(String.valueOf(host.teacher.homework.classModel.getValueAt(0, 2))));
        capture(host, "31-teacher-homework");
        onEdt(() -> pupil.student.open(StudentShell.HOMEWORK));
        waitFor("student homework", () -> pupil.student.homework.table.getRowCount() == 1
                && pupil.student.homework.startButton.isEnabled());
        capture(pupil, "32-student-homework");
        onEdt(pupil.student.homework.startButton::doClick);
        waitFor("homework question", () -> pupil.currentCard().equals(AppFrame.HOMEWORK_PLAY)
                && pupil.homeworkPlay.optionButtons.size() == 4);
        capture(pupil, "33-homework-question");
        for (int i = 0; i < CORRECT.length; i++) {
            int choice = i == 3 ? (CORRECT[i] + 1) % 4 : CORRECT[i]; // One wrong answer, to show the marking.
            onEdt(() -> pupil.homeworkPlay.optionButtons.get(choice).doClick());
        }
        waitFor("homework result", pupil.homeworkPlay::resultShown);
        capture(pupil, "34-homework-result");
        waitFor("homework email", () -> MAIL.stream().anyMatch(e -> e.to().equals("priya@school.edu")
                && e.subject().startsWith("Homework handed in")));

        // Emails: the signed-in student got a result; the guest has no email at all.
        waitFor("result email", () -> MAIL.stream().anyMatch(e -> e.to().equals("priya@school.edu")
                && e.subject().startsWith("Your result")));
        Email result = MAIL.stream().filter(e -> e.subject().startsWith("Your result")).findFirst().orElseThrow();
        Files.writeString(output.resolve("email-result.html"), result.htmlBody(), StandardCharsets.UTF_8);
        Email verify = MAIL.stream().filter(e -> e.subject().contains("verification code")).findFirst().orElseThrow();
        Files.writeString(output.resolve("email-verification.html"), verify.htmlBody(), StandardCharsets.UTF_8);
        Email report = MAIL.stream().filter(e -> e.subject().startsWith("ClassPulse report")).findFirst().orElseThrow();
        Files.writeString(output.resolve("email-report.html"), report.htmlBody(), StandardCharsets.UTF_8);
        // Priya won against Rahul, so her result email carries the certificate.
        if (result.attachments().isEmpty()) {
            throw new AssertionError("The winner should get a certificate");
        }
        Files.write(output.resolve("certificate.png"), result.attachments().get(0).content());
        Email homework = MAIL.stream().filter(e -> e.subject().startsWith("New homework")).findFirst().orElseThrow();
        Files.writeString(output.resolve("email-new-homework.html"), homework.htmlBody(), StandardCharsets.UTF_8);

        // Forgot password: an unknown email gets nothing; a registered one gets a code.
        onEdt(guest.results.actionButton::doClick);
        waitFor("guest home", () -> guest.currentCard().equals(AppFrame.AUTH));
        int before = MAIL.size();
        onEdt(() -> {
            guest.auth.showView(AuthPanel.FORGOT);
            guest.auth.forgotEmailField.setText("nobody@school.edu");
            guest.auth.sendResetButton.doClick();
        });
        waitFor("unknown email refused", () -> guest.auth.statusText().contains("No ClassPulse account"));
        capture(guest, "27-forgot-unknown-email");
        if (MAIL.size() != before) {
            throw new AssertionError("No email should be sent to an unknown address");
        }
        onEdt(() -> {
            guest.auth.forgotEmailField.setText("priya@school.edu");
            guest.auth.sendResetButton.doClick();
        });
        waitFor("reset view", () -> guest.auth.currentView().equals(AuthPanel.RESET));
        String resetCode = code("priya@school.edu");
        onEdt(() -> {
            guest.auth.resetCodeField.setText(resetCode);
            guest.auth.resetPasswordField.setText("NewPass2026!");
            guest.auth.resetConfirmField.setText("NewPass2026!");
        });
        capture(guest, "28-reset-password");
        onEdt(guest.auth.resetButton::doClick);
        waitFor("reset done", () -> guest.auth.statusText().contains("has been reset"));
        capture(guest, "29-reset-done");

        onEdt(() -> {
            guest.auth.loginField.setText("priya_s");
            guest.auth.passwordField.setText("Learn2026!");
            guest.auth.signInButton.doClick();
        });
        waitFor("old password refused", () -> guest.auth.statusText().contains("Wrong"));
        capture(guest, "30-wrong-password");
        onEdt(() -> {
            guest.auth.passwordField.setText("NewPass2026!");
            guest.auth.signInButton.doClick();
        });
        waitFor("new password works", () -> guest.currentCard().equals(AppFrame.STUDENT));

        // The show-password eye and the green tick when both passwords match.
        AppFrame extra = frame(port);
        onEdt(() -> {
            extra.auth.showView(AuthPanel.REGISTER);
            extra.auth.fullNameField.setText("Asha Rao");
            extra.auth.usernameField.setText("asha_r");
            extra.auth.emailField.setText("asha@school.edu");
            extra.auth.newPasswordField.setText("Study2026!");
            extra.auth.confirmField.setText("Study2026!");
            ((Theme.RoundPasswordField) extra.auth.newPasswordField).setShown(true);
        });
        capture(extra, "35-password-eye");
    }

    private static AppFrame frame(int port) throws Exception {
        return onEdtGet(() -> {
            AppFrame frame = new AppFrame();
            frame.setSize(1200, 780);
            frame.auth.portField.setText(String.valueOf(port));
            frame.addNotify();
            frame.validate();
            return frame;
        });
    }

    private static void capture(java.awt.Window window, String name) throws Exception {
        capture(window, name, false);
    }

    private static void captureWithToasts(java.awt.Window window, String name) throws Exception {
        capture(window, name, true);
    }

    /** Paints a hidden window at double size into an image, so it stays sharp in the PDF report. */
    private static void capture(java.awt.Window window, String name, boolean withGlass) throws Exception {
        Thread.sleep(150);
        onEdt(() -> {
            AppFrame frame = (AppFrame) window;
            frame.validate();
            Component content = withGlass ? frame.getRootPane() : frame.getContentPane();
            content.validate();
            BufferedImage image = new BufferedImage(content.getWidth() * 2, content.getHeight() * 2,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.scale(2, 2);
            content.printAll(g);
            g.dispose();
            try {
                ImageIO.write(image, "png", new File(output.toFile(), name + ".png"));
            } catch (java.io.IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void waitFor(String what, Callable<Boolean> condition) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            if (onEdtGet(condition)) {
                return;
            }
            Thread.sleep(40);
        }
        throw new AssertionError("Timed out waiting for: " + what);
    }

    private static void onEdt(Runnable action) throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(action);
    }

    private static <T> T onEdtGet(Callable<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                result.set(action.call());
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        return result.get();
    }
}
