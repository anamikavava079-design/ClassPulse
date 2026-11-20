package classpulse.server;

import classpulse.common.Csv;
import classpulse.common.Message;
import classpulse.common.Message.*;
import classpulse.common.Protocol;
import classpulse.common.Validation;
import classpulse.common.Views.Role;
import classpulse.common.Views.SessionReport;
import classpulse.mail.Email.Attachment;
import classpulse.server.AuthService.Outcome;
import classpulse.server.QuizRepository.LoadedQuiz;
import classpulse.server.UserRepository.UserRecord;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Serves one connected client. The reader runs on one thread and the writer on another,
 * joined by a bounded queue (the producer-consumer pattern). So a slow student's
 * connection can never block a GameRoom or the other students.
 */
final class ClientHandler implements Runnable, PlayerLink {
    private static final int OUTBOX_CAPACITY = 512;
    private static final int MAX_LOGIN_FAILURES = 5;
    /** Limits how many emails one connection can trigger, so the server cannot be used to spam. */
    private static final int MAX_EMAIL_REQUESTS = 8;

    private final ClassPulseServer server;
    private final Socket socket;
    private final BlockingQueue<Response> outbox = new ArrayBlockingQueue<>(OUTBOX_CAPACITY);

    private UserRecord user;
    private int loginFailures;
    private int emailRequests;
    private volatile GameRoom hostedRoom;
    private volatile GameRoom joinedRoom;

    ClientHandler(ClassPulseServer server, Socket socket) {
        this.server = server;
        this.socket = socket;
    }

    /** Safe to call from any thread, and never blocks. */
    @Override
    public void send(Response message) {
        if (!outbox.offer(message)) {
            System.err.println("A client is too slow to keep up, so it was disconnected.");
            closeSocket();
        }
    }

    /** A room calls this when it ends, so this client can create or join another. */
    @Override
    public void clearRoom(GameRoom room) {
        if (hostedRoom == room) {
            hostedRoom = null;
        }
        if (joinedRoom == room) {
            joinedRoom = null;
        }
    }

    @Override
    public void run() {
        Thread writer = Thread.startVirtualThread(this::writeLoop);
        try (socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            while (true) {
                Message message = Protocol.read(in);
                if (message instanceof Request request) {
                    handleSafely(request);
                } else {
                    throw new IOException("A client sent a server-only message.");
                }
            }
        } catch (EOFException | SocketException e) {
            // The client closed its window or lost its network. This is normal.
        } catch (IOException e) {
            System.err.println("Closed a connection after bad data: " + e.getMessage());
        } finally {
            leaveRooms("The teacher disconnected.");
            writer.interrupt();
        }
    }

    private void writeLoop() {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            while (true) {
                Protocol.write(out, outbox.take());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            closeSocket(); // This also wakes the reader, which then cleans up.
        }
    }

    private void leaveRooms(String reason) {
        GameRoom hosted = hostedRoom;
        if (hosted != null) {
            hosted.close(reason);
        }
        GameRoom joined = joinedRoom;
        if (joined != null) {
            joined.leave(this);
            joinedRoom = null;
        }
    }

    private void closeSocket() {
        try {
            socket.close();
        } catch (IOException e) {
            // Already closed, so there is nothing else to do.
        }
    }

    private void handleSafely(Request request) {
        try {
            handle(request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(new ErrorText(e.getMessage()));
        } catch (RepositoryException e) {
            System.err.println(e.getMessage() + " " + e.getCause());
            send(new ErrorText("The server had a database problem. Please try again."));
        }
    }

    /** The compiler checks that this switch covers every kind of Request. */
    private void handle(Request request) {
        AuthService auth = server.auth();
        switch (request) {
            // ----- Accounts -----
            case Register m -> {
                requireSignedOut();
                countEmailRequest();
                apply(auth.register(m));
            }
            case VerifyEmail m -> {
                requireSignedOut();
                apply(auth.verify(m));
            }
            case ResendCode m -> {
                countEmailRequest();
                apply(auth.resend(m));
            }
            case Login m -> login(m);
            case ForgotPassword m -> {
                countEmailRequest();
                apply(auth.forgot(m));
            }
            case ResetPassword m -> apply(auth.reset(m));
            case ChangePassword m -> send(auth.changePassword(requireUser().id(), m));
            case UpdateProfile m -> {
                Response reply = auth.updateProfile(requireUser().id(), m.fullName());
                user = server.users().findById(user.id()).orElse(user);
                send(reply);
            }
            case Logout m -> {
                leaveRooms("The teacher signed out.");
                user = null;
                send(new Notice("You have signed out."));
            }

            // ----- Teacher: quizzes -----
            case ListQuizzes m -> sendQuizList();
            case GetQuiz m -> {
                LoadedQuiz quiz = loadOwnQuiz(m.quizId());
                send(new QuizDetail(quiz.id(), new classpulse.common.Quiz(quiz.title(), quiz.questions())));
            }
            case SaveQuiz m -> {
                server.quizzes().saveQuiz(requireTeacher(), m.quiz());
                send(new Notice("Saved \"" + m.quiz().title() + "\"."));
                sendQuizList();
            }
            case UpdateQuiz m -> {
                if (!server.quizzes().updateQuiz(requireTeacher(), m.quizId(), m.quiz())) {
                    throw new IllegalArgumentException("That quiz was not found.");
                }
                send(new Notice("Updated \"" + m.quiz().title() + "\". Old reports are kept."));
                sendQuizList();
            }
            case DuplicateQuiz m -> {
                LoadedQuiz quiz = loadOwnQuiz(m.quizId());
                String title = quiz.title().length() > 92 ? quiz.title().substring(0, 92) : quiz.title();
                server.quizzes().saveQuiz(user.id(), new classpulse.common.Quiz(title + " (copy)", quiz.questions()));
                send(new Notice("Made a copy of \"" + quiz.title() + "\"."));
                sendQuizList();
            }
            case DeleteQuiz m -> {
                if (!server.quizzes().deleteQuiz(requireTeacher(), m.quizId())) {
                    throw new IllegalArgumentException("That quiz was not found.");
                }
                send(new Notice("Quiz deleted. Its past reports are kept."));
                sendQuizList();
            }

            // ----- Teacher: hosting -----
            case CreateRoom m -> createRoom(m);
            case KickPlayer m -> reportProblem(requireHostedRoom().kick(m.name()));
            case StartGame m -> reportProblem(requireHostedRoom().start());
            case NextStep m -> reportProblem(requireHostedRoom().next());
            case CloseRoom m -> requireHostedRoom().close("The teacher closed the room.");

            // ----- Teacher: reports -----
            case ListSessions m -> send(new SessionList(server.quizzes().listSessions(requireTeacher(), 500)));
            case GetReport m -> send(new Report(loadOwnReport(m.sessionId())));
            case EmailReport m -> emailReport(m.sessionId());
            case GetOverview m -> send(new Overview(server.stats().teacherStats(requireTeacher())));

            // ----- Students -----
            case JoinRoom m -> joinRoom(m);
            case SubmitAnswer m -> {
                GameRoom room = joinedRoom;
                if (room == null) {
                    throw new IllegalStateException("You are not in a room.");
                }
                room.answer(this, m.questionIndex(), m.choice());
            }
            case GetStudentDashboard m -> {
                long id = requireRole(Role.STUDENT);
                var history = server.stats().history(id);
                var stats = StatsRepository.studentStats(history, server.stats().averageCorrectMs(id));
                send(new StudentDashboard(stats, history, StatsRepository.badges(history, stats)));
            }

            // ----- Classes and homework (added in the homework upgrade) -----
            case CreateClass m -> {
                server.homework().createClass(requireTeacher(), m.name());
                send(new Notice("Class created. Share its code with your students."));
                sendClassesAndHomework();
            }
            case ListClasses m -> sendClassesAndHomework();
            case AssignHomework m -> {
                requireTeacher();
                server.homework().assign(user, m.classId(), m.quizId(), m.dueInMinutes());
                send(new Notice("Homework set. Every student in the class is being emailed."));
                sendClassesAndHomework();
            }
            case ListHomework m -> sendClassesAndHomework();
            case JoinClass m -> {
                String name = server.homework().joinClass(requireRole(Role.STUDENT), m.code());
                send(new Notice("You joined " + name + "."));
                sendMyHomework();
            }
            case GetMyHomework m -> sendMyHomework();
            case StartHomework m -> send(server.homework().start(requireRole(Role.STUDENT), m.homeworkId()));
            case SubmitHomework m -> {
                requireRole(Role.STUDENT);
                send(server.homework().submit(user, m.homeworkId(), m.choices()));
                sendMyHomework();
            }
        }
    }

    private void sendClassesAndHomework() {
        long teacherId = requireTeacher();
        HomeworkRepository repository = server.homework().repository();
        send(new ClassList(repository.listClasses(teacherId)));
        send(new HomeworkList(repository.listHomework(teacherId, java.time.LocalDateTime.now())));
    }

    private void sendMyHomework() {
        long studentId = requireRole(Role.STUDENT);
        HomeworkRepository repository = server.homework().repository();
        send(new MyHomework(repository.classNamesOf(studentId),
                repository.studentHomework(studentId, java.time.LocalDateTime.now())));
    }

    /** Sends the reply, and remembers the user if the outcome signed someone in. */
    private void apply(Outcome outcome) {
        if (outcome.signedIn() != null) {
            user = outcome.signedIn();
            loginFailures = 0;
        }
        send(outcome.response());
        if (outcome.signedIn() != null && user.role() == Role.TEACHER) {
            sendQuizList();
        }
    }

    private void login(Login message) {
        requireSignedOut();
        Outcome outcome = server.auth().login(message);
        if (outcome.response() instanceof AuthResult result && !result.ok()) {
            loginFailures++;
            send(result);
            if (loginFailures >= MAX_LOGIN_FAILURES) {
                send(new ErrorText("Too many failed sign-ins. Please reconnect."));
                closeAfterOutboxDrains();
            }
            return;
        }
        apply(outcome);
    }

    private void countEmailRequest() {
        if (++emailRequests > MAX_EMAIL_REQUESTS) {
            throw new IllegalStateException("Too many email requests from this window. Please restart the app.");
        }
    }

    private void emailReport(long sessionId) {
        SessionReport report = loadOwnReport(sessionId);
        String reteach = report.questions().stream()
                .filter(q -> q.answered() > 0)
                .min(Comparator.comparingDouble(q -> (double) q.correct() / q.answered()))
                .map(q -> "Worth reteaching: question " + q.number() + " ("
                        + Math.round(100f * q.correct() / q.answered()) + "% correct)")
                .orElse("No answers were recorded in this session.");
        byte[] csv = String.join("\r\n", Csv.reportLines(report)).getBytes(StandardCharsets.UTF_8);
        Attachment attachment = new Attachment("classpulse-" + report.summary().roomCode() + ".csv", "text/csv", csv);
        String error = server.mail().send(user.email(), "REPORT",
                EmailTemplates.sessionReport(user.fullName(), report, reteach), List.of(attachment));
        if (error != null) {
            throw new IllegalStateException("The report could not be emailed: " + error);
        }
        send(new Notice("Report sent to " + user.email() + "."));
    }

    private void closeAfterOutboxDrains() {
        Thread.startVirtualThread(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            closeSocket();
        });
    }

    private void sendQuizList() {
        send(new QuizList(server.quizzes().listQuizzes(requireTeacher())));
    }

    private LoadedQuiz loadOwnQuiz(long quizId) {
        return server.quizzes().loadQuiz(requireTeacher(), quizId)
                .orElseThrow(() -> new IllegalArgumentException("That quiz was not found."));
    }

    private SessionReport loadOwnReport(long sessionId) {
        return server.quizzes().loadReport(requireTeacher(), sessionId)
                .orElseThrow(() -> new IllegalArgumentException("That session was not found."));
    }

    private void createRoom(CreateRoom message) {
        requireTeacher();
        if (hostedRoom != null) {
            throw new IllegalStateException("You are already hosting a room.");
        }
        LoadedQuiz quiz = loadOwnQuiz(message.quizId());
        GameRoom room = server.createRoom(this, quiz, message.shuffle());
        hostedRoom = room;
        send(new RoomCreated(room.code(), room.title(), room.questionCount(), room.shuffled()));
        String phoneLink = server.phoneLink(room.code());
        if (phoneLink != null) {
            send(new PhoneLink(phoneLink)); // The lobby shows it as a QR code.
        }
    }

    private void joinRoom(JoinRoom message) {
        String code = Validation.roomCode(message.code());
        String name = Validation.displayName(message.name());
        if (user != null && user.role() == Role.TEACHER) {
            throw new IllegalStateException("Teachers host quizzes. Sign in with a student account to play.");
        }
        if (joinedRoom != null) {
            throw new IllegalStateException("You are already in a room.");
        }
        GameRoom room = server.findRoom(code);
        if (room == null) {
            send(new JoinResult(false, "No room has that code.", null));
            return;
        }
        // Set first, so a room that closes right after this join can still clear it.
        joinedRoom = room;
        String problem = room.join(this, name, user == null ? null : user.id(), user == null ? null : user.email());
        if (problem != null) {
            joinedRoom = null;
            send(new JoinResult(false, problem, null));
        }
    }

    private void requireSignedOut() {
        if (user != null) {
            throw new IllegalStateException("You are already signed in. Sign out first.");
        }
    }

    private UserRecord requireUser() {
        if (user == null) {
            throw new IllegalStateException("Please sign in first.");
        }
        return user;
    }

    private long requireRole(Role role) {
        if (user == null || user.role() != role) {
            throw new IllegalStateException(role == Role.TEACHER
                    ? "Please sign in as a teacher first." : "Please sign in as a student first.");
        }
        return user.id();
    }

    private long requireTeacher() {
        return requireRole(Role.TEACHER);
    }

    private GameRoom requireHostedRoom() {
        requireTeacher();
        GameRoom room = hostedRoom;
        if (room == null) {
            throw new IllegalStateException("You are not hosting a room.");
        }
        return room;
    }

    private void reportProblem(String problem) {
        if (problem != null) {
            send(new ErrorText(problem));
        }
    }
}
