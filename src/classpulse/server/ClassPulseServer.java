package classpulse.server;

import classpulse.mail.DomainChecker;
import classpulse.mail.Mailer;
import classpulse.server.QuizRepository.LoadedQuiz;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/** Accepts socket connections, gives each one a thread, and keeps track of live rooms. */
public final class ClassPulseServer implements AutoCloseable {
    // Leaves out 0, O, 1 and I, which are easy to misread from a projector.
    private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;

    private final UserRepository users;
    private final QuizRepository quizzes;
    private final StatsRepository stats;
    private final MailService mail;
    private final AuthService auth;
    private final HomeworkService homework;
    private final ServerSocket serverSocket;
    private final ExecutorService clientThreads = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, GameRoom> rooms = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * Connects to MySQL, creates any missing tables, and opens the port. Port 0 picks a free one.
     * The mailer sends the emails, and the domain checker looks up mail servers in DNS.
     */
    public static ClassPulseServer open(String jdbcUrl, String user, String password, int port,
                                        Mailer mailer, DomainChecker domains)
            throws java.sql.SQLException, IOException {
        Database database = new Database(jdbcUrl, user, password);
        database.createTables();
        ClassPulseServer server = new ClassPulseServer(database, mailer, domains, new ServerSocket(port));
        server.homework.startClock();
        return server;
    }

    private ClassPulseServer(Database database, Mailer mailer, DomainChecker domains, ServerSocket serverSocket) {
        this.users = new UserRepository(database);
        this.quizzes = new QuizRepository(database);
        this.stats = new StatsRepository(database, quizzes);
        this.mail = new MailService(mailer, users);
        this.auth = new AuthService(users, quizzes, mail, domains);
        this.homework = new HomeworkService(new HomeworkRepository(database), quizzes, mail);
        this.serverSocket = serverSocket;
    }

    HomeworkService homework() {
        return homework;
    }

    // ----- Phone play (added in the phone upgrade) -----

    private volatile PhoneServer phone;

    /**
     * Starts the web server that phones use, and returns its address. If the port is busy this
     * throws, and the rest of ClassPulse keeps working without phone play.
     * host is the address to put in the QR code; null finds this computer's Wi-Fi address.
     */
    public String startPhoneServer(int port, String host) throws IOException {
        phone = PhoneServer.start(this, port, host);
        return phone.baseUrl();
    }

    /** The link for a room's QR code, or null when phone play is off. */
    String phoneLink(String roomCode) {
        PhoneServer current = phone;
        return current == null ? null : current.linkFor(roomCode);
    }

    PhoneServer phoneServer() {
        return phone;
    }

    public int port() {
        return serverSocket.getLocalPort();
    }

    public String mailDescription() {
        return mail.describe();
    }

    /** Blocks, accepting clients until the server is closed. */
    public void acceptClients() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true); // Send small messages at once, for fair quiz timing.
                socket.setKeepAlive(true);
                clientThreads.submit(new ClientHandler(this, socket));
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    System.err.println("Could not accept a connection: " + e.getMessage());
                }
            }
        }
    }

    UserRepository users() {
        return users;
    }

    QuizRepository quizzes() {
        return quizzes;
    }

    StatsRepository stats() {
        return stats;
    }

    MailService mail() {
        return mail;
    }

    AuthService auth() {
        return auth;
    }

    ScheduledExecutorService timers() {
        return timers;
    }

    GameRoom findRoom(String code) {
        return rooms.get(code);
    }

    void removeRoom(String code) {
        rooms.remove(code);
    }

    GameRoom createRoom(ClientHandler host, LoadedQuiz quiz, boolean shuffle) {
        LoadedQuiz played = shuffle ? quiz.shuffled(random) : quiz;
        while (true) {
            String code = randomCode();
            GameRoom room = new GameRoom(this, host, played, code, shuffle);
            // putIfAbsent is atomic, so two rooms can never end up with one code.
            if (rooms.putIfAbsent(code, room) == null) {
                return room;
            }
        }
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
        }
        return code.toString();
    }

    @Override
    public void close() throws IOException {
        serverSocket.close();
        homework.close();
        if (phone != null) {
            phone.close();
        }
        timers.shutdownNow();
        clientThreads.shutdownNow();
    }
}
