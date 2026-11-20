package classpulse.client;

import classpulse.common.Message.*;
import classpulse.common.Views.Account;
import classpulse.common.Views.PersonalResult;
import classpulse.common.Views.Role;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingWorker;

/**
 * The single application window. It owns the connection, holds every screen in an
 * animated deck (SmoothDeck), and sends each server message to the screen that needs it.
 * Errors and confirmations appear as toasts inside the window, not as pop-up dialogs.
 */
final class AppFrame extends JFrame {
    static final String AUTH = "auth";
    static final String TEACHER = "teacher";
    static final String STUDENT = "student";
    static final String LOBBY = "lobby";
    static final String HOST_GAME = "hostGame";
    static final String STUDENT_GAME = "studentGame";
    static final String RESULTS = "results";
    static final String HOMEWORK_PLAY = "homeworkPlay";

    private final SmoothDeck deck = new SmoothDeck();
    private final ToastLayer toasts = new ToastLayer();
    private ServerConnection connection;
    private String connectedHost;
    private int connectedPort;
    /** The signed-in user, or null for a guest or nobody. */
    private Account account;
    /** True while this window is hosting a game, false while playing one. */
    private boolean hosting;
    private final List<JButton> busyButtons = new ArrayList<>();
    private final List<String> busyTexts = new ArrayList<>();

    final AuthPanel auth = new AuthPanel(this);
    final TeacherShell teacher = new TeacherShell(this);
    final StudentShell student = new StudentShell(this);
    final LobbyPanel lobby = new LobbyPanel(this);
    final HostGamePanel hostGame = new HostGamePanel(this);
    final StudentGamePanel studentGame = new StudentGamePanel(this);
    final LeaderboardPanel results = new LeaderboardPanel();
    final HomeworkPlayPanel homeworkPlay = new HomeworkPlayPanel(this);

    AppFrame() {
        super("ClassPulse - Live Classroom Quiz");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setIconImage(logoImage());
        deck.setOpaque(true);
        deck.setBackground(Theme.BACKGROUND);
        deck.addCard(auth, AUTH);
        deck.addCard(teacher, TEACHER);
        deck.addCard(student, STUDENT);
        deck.addCard(lobby, LOBBY);
        deck.addCard(hostGame, HOST_GAME);
        deck.addCard(studentGame, STUDENT_GAME);
        deck.addCard(results, RESULTS);
        deck.addCard(homeworkPlay, HOMEWORK_PLAY);
        setContentPane(deck);
        setGlassPane(toasts);
        placeOnScreen();
    }

    private static java.awt.Image logoImage() {
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        Theme.smooth(g);
        g.scale(64 / 24.0, 64 / 24.0);
        g.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        Icons.draw(g, Icons.Kind.LOGO);
        g.dispose();
        return image;
    }

    /** Fits small laptop screens, and offsets each window so several clients do not hide each other. */
    private void placeOnScreen() {
        Rectangle screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int width = Math.min(1200, screen.width - 20);
        int height = Math.min(780, screen.height - 20);
        setMinimumSize(new Dimension(Math.min(1000, width), Math.min(660, height)));
        setSize(width, height);
        int freeWidth = Math.max(1, screen.width - width);
        int freeHeight = Math.max(1, screen.height - height);
        Random random = new Random();
        setLocation(screen.x + random.nextInt(freeWidth), screen.y + random.nextInt(freeHeight));
    }

    void setAnimations(boolean on) {
        deck.setAnimations(on);
        teacher.deck.setAnimations(on);
        student.deck.setAnimations(on);
    }

    void showCard(String name) {
        deck.show(name);
    }

    String currentCard() {
        return deck.current();
    }

    Account account() {
        return account;
    }

    // ----- Talking to the server -----

    void send(Request request) {
        if (connection != null) {
            connection.send(request);
        }
    }

    /** Uses the open connection if it goes to the same server, otherwise connects first (in the background). */
    void connectAndSend(String host, int port, Request request) {
        if (connection != null && host.equals(connectedHost) && port == connectedPort) {
            connection.send(request);
            return;
        }
        new SwingWorker<ServerConnection, Void>() {
            @Override
            protected ServerConnection doInBackground() throws IOException {
                return new ServerConnection(host, port, AppFrame.this::onMessage, AppFrame.this::onConnectionLost);
            }

            @Override
            protected void done() {
                try {
                    disconnect();
                    connection = get();
                    connectedHost = host;
                    connectedPort = port;
                    connection.send(request);
                } catch (ExecutionException e) {
                    clearBusy();
                    auth.showError("Could not reach the server at " + host + ":" + port + ". Is the server running?");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    void disconnect() {
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }

    // ----- Feedback -----

    /** Greys out a button and shows "Please wait..." until the server answers. */
    void busy(JButton button) {
        if (!busyButtons.contains(button)) {
            busyButtons.add(button);
            busyTexts.add(button.getText());
        }
        button.setEnabled(false);
        button.setText("Please wait...");
    }

    void clearBusy() {
        for (int i = 0; i < busyButtons.size(); i++) {
            busyButtons.get(i).setText(busyTexts.get(i));
            busyButtons.get(i).setEnabled(true);
        }
        busyButtons.clear();
        busyTexts.clear();
    }

    void toast(String text, ToastLayer.Kind kind) {
        toasts.show(text, kind);
    }

    ToastLayer toasts() {
        return toasts;
    }

    // ----- Signing in and out -----

    private void signedIn(Account signedIn) {
        account = signedIn;
        if (signedIn.role() == Role.TEACHER) {
            teacher.setAccount(signedIn);
            showCard(TEACHER);
            teacher.open(TeacherShell.OVERVIEW);
        } else {
            student.setAccount(signedIn);
            showCard(STUDENT);
            student.open(StudentShell.JOIN);
        }
        auth.reset(null);
        toast("Welcome, " + signedIn.fullName() + "!", ToastLayer.Kind.SUCCESS);
    }

    void signOut() {
        send(new Logout());
        account = null;
        auth.reset(null);
        showCard(AUTH);
        toast("You have signed out.", ToastLayer.Kind.INFO);
    }

    /** Goes back to the screen this user came from: their dashboard, or the start screen for a guest. */
    private void goBack(String message, ToastLayer.Kind kind) {
        hostGame.stop();
        studentGame.stop();
        clearBusy();
        if (account == null) {
            disconnect();
            auth.reset(message);
            showCard(AUTH);
            return;
        }
        if (account.role() == Role.TEACHER) {
            showCard(TEACHER);
            teacher.open(TeacherShell.OVERVIEW);
        } else {
            showCard(STUDENT);
            student.open(StudentShell.RESULTS);
        }
        if (message != null && !message.isBlank()) {
            toast(message, kind);
        }
    }

    /** Leaves the homework screen and shows the student's homework list again. */
    void backToHomework() {
        homeworkPlay.stop();
        showCard(STUDENT);
        student.open(StudentShell.HOMEWORK);
    }

    private void onConnectionLost() {
        disconnect();
        account = null;
        hostGame.stop();
        studentGame.stop();
        homeworkPlay.stop();
        clearBusy();
        auth.reset("The connection to the server was lost. Check that the server is running, then sign in again.");
        showCard(AUTH);
    }

    // ----- Messages from the server -----

    /** Runs on the EDT. The switch has no default, so the compiler checks that every message is handled. */
    private void onMessage(Response message) {
        switch (message) {
            case AuthResult m -> {
                clearBusy();
                if (m.ok()) {
                    signedIn(m.account());
                } else {
                    auth.showError(m.text());
                }
            }
            case CodeSent m -> {
                clearBusy();
                auth.codeSent(m);
            }
            case PasswordResetDone m -> {
                clearBusy();
                auth.resetDone(m.text());
            }
            case Notice m -> {
                clearBusy();
                if (!AUTH.equals(currentCard())) {
                    toast(m.text(), ToastLayer.Kind.SUCCESS);
                }
            }
            case ProfileUpdated m -> {
                clearBusy();
                account = m.account();
                teacher.setAccount(account);
                student.setAccount(account);
                toast("Your name was updated.", ToastLayer.Kind.SUCCESS);
            }
            case QuizList m -> {
                teacher.quizzes.setQuizzes(m.quizzes());
                teacher.homework.setQuizzes(m.quizzes());
            }
            case QuizDetail m -> {
                teacher.editor.startEdit(m.quizId(), m.quiz());
                teacher.open(TeacherShell.EDITOR);
            }
            case RoomCreated m -> {
                clearBusy();
                hosting = true;
                lobby.open(m);
                showCard(LOBBY);
            }
            case JoinResult m -> {
                clearBusy();
                if (m.ok()) {
                    hosting = false;
                    studentGame.joined(m.title());
                    showCard(STUDENT_GAME);
                } else if (AUTH.equals(currentCard())) {
                    auth.showError(m.text());
                } else {
                    toast(m.text(), ToastLayer.Kind.ERROR);
                }
            }
            case PlayersChanged m -> {
                if (hosting) {
                    lobby.setPlayers(m.names());
                } else {
                    studentGame.setPlayers(m.names().size());
                }
            }
            case QuestionStarted m -> {
                if (hosting) {
                    hostGame.startQuestion(m);
                    showCard(HOST_GAME);
                } else {
                    studentGame.startQuestion(m);
                }
            }
            case AnswerReceived m -> studentGame.answerReceived(m);
            case AnswerProgress m -> hostGame.setProgress(m.answered(), m.total());
            case QuestionEnded m -> {
                if (hosting) {
                    hostGame.reveal(m);
                } else {
                    studentGame.reveal(m);
                }
            }
            case GameOver m -> showGameOver(m);
            case RoomClosed m -> {
                boolean byMe = hosting && m.reason().equals("The teacher closed the room.");
                hosting = false;
                goBack(byMe ? "Room closed." : m.reason(), byMe ? ToastLayer.Kind.INFO : ToastLayer.Kind.ERROR);
            }
            case SessionList m -> teacher.reports.setSessions(m.sessions());
            case Report m -> teacher.reports.showReport(m.report());
            case Overview m -> teacher.overview.setStats(m.stats());
            case StudentDashboard m -> student.setDashboard(m);
            case ErrorText m -> {
                clearBusy();
                if (AUTH.equals(currentCard())) {
                    auth.showError(m.text());
                } else {
                    toast(m.text(), ToastLayer.Kind.ERROR);
                }
                if (HOMEWORK_PLAY.equals(currentCard()) && homeworkPlay.waitingForResult()) {
                    homeworkPlay.submitFailed();
                }
            }

            // ----- Added in the homework and phone upgrades -----
            case ClassList m -> {
                clearBusy();
                teacher.homework.setClasses(m.classes());
            }
            case HomeworkList m -> {
                clearBusy();
                teacher.homework.setHomework(m.homework());
            }
            case MyHomework m -> {
                clearBusy();
                student.homework.setHomework(m);
            }
            case HomeworkStarted m -> {
                clearBusy();
                homeworkPlay.begin(m);
                showCard(HOMEWORK_PLAY);
            }
            case HomeworkDone m -> homeworkPlay.showResult(m);
            case PhoneLink m -> lobby.showPhoneLink(m.url());
        }
    }

    private void showGameOver(GameOver message) {
        hostGame.stop();
        studentGame.stop();
        if (hosting) {
            hosting = false;
            String detail = message.saved()
                    ? "Saved to the database. Signed-in students are getting their results by email."
                    : "Warning: the results could not be saved to the database.";
            results.show("Final results", detail, null, message.ranking(), "Back to dashboard",
                    () -> goBack(null, ToastLayer.Kind.INFO));
        } else {
            PersonalResult you = message.you();
            String note = message.emailed() ? "A copy of your result is on its way to your email." : null;
            String button = account == null ? "Back to start" : "See my results";
            results.show("You finished #" + you.rank(), you.totalScore() + " points   |   " + message.correct()
                            + " of " + message.questions() + " correct", note, message.ranking(), button,
                    () -> goBack(null, ToastLayer.Kind.INFO));
        }
        showCard(RESULTS);
    }
}
