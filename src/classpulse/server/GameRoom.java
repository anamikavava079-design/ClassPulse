package classpulse.server;

import classpulse.common.Message.AnswerProgress;
import classpulse.common.Message.AnswerReceived;
import classpulse.common.Message.GameOver;
import classpulse.common.Message.JoinResult;
import classpulse.common.Message.PlayersChanged;
import classpulse.common.Message.QuestionEnded;
import classpulse.common.Message.QuestionStarted;
import classpulse.common.Message.RoomClosed;
import classpulse.common.Message.Response;
import classpulse.common.Question;
import classpulse.common.Views.PersonalResult;
import classpulse.common.Views.ScoreEntry;
import classpulse.server.EmailTemplates.AnswerLine;
import classpulse.server.QuizRepository.AnswerRecord;
import classpulse.server.QuizRepository.LoadedQuiz;
import classpulse.server.QuizRepository.ParticipantRecord;
import classpulse.server.QuizRepository.SessionRecord;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * One live quiz, run as a state machine: LOBBY -> QUESTION -> REVIEW -> ... -> FINISHED.
 * Socket threads and the timer thread all call this object at the same time, so every
 * method that touches its state is synchronized.
 */
final class GameRoom {
    enum State { LOBBY, QUESTION, REVIEW, FINISHED }

    static final int MAX_PLAYERS = 200;
    private static final int TOP_COUNT = 5;

    /** A player stays in the results even after disconnecting. */
    private static final class Player {
        final PlayerLink connection; // An app player (ClientHandler) or a phone player (WebPlayer).
        final String name;
        final Long userId;   // null for a guest
        final String email;  // null for a guest
        final Map<Integer, AnswerRecord> answers = new HashMap<>();
        final Map<Integer, Integer> streakBonuses = new HashMap<>();
        int score;
        int correctCount;
        int streak;
        boolean connected = true;

        Player(PlayerLink connection, String name, Long userId, String email) {
            this.connection = connection;
            this.name = name;
            this.userId = userId;
            this.email = email;
        }
    }

    private final ClassPulseServer server;
    private final ClientHandler host;
    private final LoadedQuiz quiz;
    private final String code;
    private final boolean shuffled;
    private final Map<PlayerLink, Player> players = new LinkedHashMap<>();

    private State state = State.LOBBY;
    private int currentIndex = -1;
    private long questionStartNanos;
    private ScheduledFuture<?> timer;
    private LocalDateTime startedAt;

    GameRoom(ClassPulseServer server, ClientHandler host, LoadedQuiz quiz, String code, boolean shuffled) {
        this.server = server;
        this.host = host;
        this.quiz = quiz;
        this.code = code;
        this.shuffled = shuffled;
    }

    String code() {
        return code;
    }

    String title() {
        return quiz.title();
    }

    int questionCount() {
        return quiz.questions().size();
    }

    boolean shuffled() {
        return shuffled;
    }

    /** Returns null on success, otherwise the reason the student cannot join. */
    synchronized String join(PlayerLink connection, String name, Long userId, String email) {
        if (state != State.LOBBY) {
            return "This quiz has already started.";
        }
        if (players.size() >= MAX_PLAYERS) {
            return "This room is full.";
        }
        if (userId != null && players.values().stream().anyMatch(p -> userId.equals(p.userId))) {
            return "Your account is already in this room.";
        }
        boolean nameTaken = players.values().stream().anyMatch(p -> p.name.equalsIgnoreCase(name));
        if (nameTaken) {
            return "That name is already taken in this room.";
        }
        players.put(connection, new Player(connection, name, userId, email));
        connection.send(new JoinResult(true, "Joined.", quiz.title()));
        broadcastPlayers();
        return null;
    }

    synchronized void leave(PlayerLink connection) {
        Player player = players.get(connection);
        if (player == null) {
            return;
        }
        if (state == State.LOBBY) {
            players.remove(connection);
        } else {
            player.connected = false;
        }
        broadcastPlayers();
        if (state == State.QUESTION) {
            announceProgressAndMaybeEnd();
        }
    }

    /** The teacher removes a student from the lobby, for example one with a rude name. */
    synchronized String kick(String name) {
        if (state != State.LOBBY) {
            return "Students can only be removed before the quiz starts.";
        }
        Player player = players.values().stream().filter(p -> p.name.equals(name)).findFirst().orElse(null);
        if (player == null) {
            return "That student is no longer in the room.";
        }
        players.remove(player.connection);
        player.connection.send(new RoomClosed("The teacher removed you from the room."));
        player.connection.clearRoom(this);
        broadcastPlayers();
        return null;
    }

    synchronized String start() {
        if (state != State.LOBBY) {
            return "The quiz has already started.";
        }
        if (players.isEmpty()) {
            return "Wait for at least one student to join.";
        }
        startedAt = LocalDateTime.now();
        beginQuestion(0);
        return null;
    }

    /** The teacher's Next button: shows the next question, or finishes after the last one. */
    synchronized String next() {
        if (state != State.REVIEW) {
            return "You can move on after the current question ends.";
        }
        if (currentIndex + 1 < questionCount()) {
            beginQuestion(currentIndex + 1);
        } else {
            finish();
        }
        return null;
    }

    private void beginQuestion(int index) {
        state = State.QUESTION;
        currentIndex = index;
        questionStartNanos = System.nanoTime();
        Question question = quiz.questions().get(index);
        broadcast(new QuestionStarted(index, questionCount(), question.text(), question.options(), question.seconds()));
        host.send(new AnswerProgress(0, connectedCount()));
        timer = server.timers().schedule(() -> timeUp(index), question.seconds(), TimeUnit.SECONDS);
    }

    private synchronized void timeUp(int index) {
        // The check matters: this timer may fire just after the question already ended.
        if (state == State.QUESTION && currentIndex == index) {
            endQuestion();
        }
    }

    synchronized void answer(PlayerLink connection, int questionIndex, int choice) {
        Player player = players.get(connection);
        if (player == null) {
            connection.send(new AnswerReceived(false, "You are not in this room."));
            return;
        }
        if (state != State.QUESTION || questionIndex != currentIndex) {
            connection.send(new AnswerReceived(false, "Too late: this question has ended."));
            return;
        }
        if (player.answers.containsKey(currentIndex)) {
            connection.send(new AnswerReceived(false, "You have already answered."));
            return;
        }
        if (choice < 0 || choice >= Question.OPTION_COUNT) {
            connection.send(new AnswerReceived(false, "That is not a valid option."));
            return;
        }
        // The server's own clock decides the time, so a client cannot fake a fast answer.
        Question question = quiz.questions().get(currentIndex);
        long responseMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - questionStartNanos);
        boolean correct = choice == question.correctIndex();
        int points = ScoreCalculator.points(correct, responseMs, question.seconds() * 1000L);
        if (correct) {
            player.streak++;
            player.correctCount++;
            int bonus = ScoreCalculator.streakBonus(player.streak);
            player.streakBonuses.put(currentIndex, bonus);
            points += bonus;
        } else {
            player.streak = 0;
        }
        player.answers.put(currentIndex, new AnswerRecord(quiz.questionIds().get(currentIndex),
                choice, correct, responseMs, points));
        player.score += points;
        connection.send(new AnswerReceived(true, "Answer locked in."));
        announceProgressAndMaybeEnd();
    }

    private void announceProgressAndMaybeEnd() {
        long answered = players.values().stream()
                .filter(p -> p.connected && p.answers.containsKey(currentIndex)).count();
        int connected = connectedCount();
        host.send(new AnswerProgress((int) answered, connected));
        if (answered >= connected) {
            endQuestion();
        }
    }

    private void endQuestion() {
        state = State.REVIEW;
        if (timer != null) {
            timer.cancel(false);
        }
        int[] counts = new int[Question.OPTION_COUNT];
        for (Player player : players.values()) {
            AnswerRecord answer = player.answers.get(currentIndex);
            if (answer != null) {
                counts[answer.choice()]++;
            } else {
                player.streak = 0; // Not answering breaks a streak too.
            }
        }
        List<Integer> countList = java.util.Arrays.stream(counts).boxed().toList();
        List<ScoreEntry> ranking = ranking();
        List<ScoreEntry> top = ranking.stream().limit(TOP_COUNT).toList();
        int correctIndex = quiz.questions().get(currentIndex).correctIndex();
        for (Player player : players.values()) {
            player.connection.send(new QuestionEnded(currentIndex, questionCount(), correctIndex, countList, top,
                    personalResult(player, ranking)));
        }
        host.send(new QuestionEnded(currentIndex, questionCount(), correctIndex, countList, top, null));
    }

    private void finish() {
        state = State.FINISHED;
        List<ScoreEntry> ranking = ranking();
        int total = questionCount();
        for (Player player : players.values()) {
            player.connection.send(new GameOver(ranking, personalResult(player, ranking), false,
                    player.correctCount, total, player.email != null));
            player.connection.clearRoom(this);
        }
        SessionRecord session = buildSession(true, total);
        List<Runnable> emails = resultEmails(ranking);
        cleanUp();
        // Saving and emailing can be slow, so they run on their own thread instead of holding this room's lock.
        Thread.startVirtualThread(() -> {
            boolean saved = save(session);
            host.send(new GameOver(ranking, null, saved, 0, 0, false));
            emails.forEach(Runnable::run);
        });
    }

    /** One email per signed-in student, built now (inside the lock) and sent later. */
    private List<Runnable> resultEmails(List<ScoreEntry> ranking) {
        List<Runnable> emails = new ArrayList<>();
        for (Player player : players.values()) {
            if (player.email == null) {
                continue;
            }
            List<AnswerLine> lines = new ArrayList<>();
            for (int i = 0; i < questionCount(); i++) {
                Question question = quiz.questions().get(i);
                AnswerRecord answer = player.answers.get(i);
                String yours = answer == null ? "(no answer)" : letter(answer.choice()) + ". " + question.options().get(answer.choice());
                String right = letter(question.correctIndex()) + ". " + question.options().get(question.correctIndex());
                lines.add(new AnswerLine(i + 1, question.text(), yours, right, answer != null && answer.correct()));
            }
            int rank = rankOf(player, ranking);
            int playerCount = players.size();
            int correct = player.correctCount;
            int total = questionCount();
            boolean certificate = CertificateMaker.deserves(rank, playerCount, correct, total);
            EmailTemplates.Content content = EmailTemplates.studentResult(player.name, quiz.title(), rank,
                    playerCount, player.score, correct, total, lines,
                    certificate ? CertificateMaker.achievement(rank, playerCount, correct, total) : null);
            String to = player.email;
            String name = player.name;
            int score = player.score;
            String title = quiz.title();
            emails.add(() -> {
                List<classpulse.mail.Email.Attachment> attachments = List.of();
                if (certificate) {
                    try {
                        // Drawn here, on the background thread, so the room's lock is not held while drawing.
                        byte[] png = CertificateMaker.png(name, title, rank, playerCount, score, correct, total,
                                java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")));
                        attachments = List.of(new classpulse.mail.Email.Attachment(CertificateMaker.fileName(name),
                                "image/png", png));
                    } catch (RuntimeException e) {
                        System.err.println("Could not draw a certificate: " + e.getMessage());
                    }
                }
                server.mail().send(to, "RESULT", content, attachments);
            });
        }
        return emails;
    }

    private static String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }

    /** Called when the teacher closes the room or disconnects. */
    synchronized void close(String reason) {
        if (state == State.FINISHED) {
            return;
        }
        boolean hasResults = startedAt != null
                && (state == State.REVIEW || currentIndex > 0);
        state = State.FINISHED;
        if (timer != null) {
            timer.cancel(false);
        }
        for (Player player : players.values()) {
            player.connection.send(new RoomClosed(reason));
            player.connection.clearRoom(this);
        }
        host.send(new RoomClosed(reason));
        SessionRecord session = hasResults ? buildSession(false, currentIndex + 1) : null;
        cleanUp();
        if (session != null) {
            Thread.startVirtualThread(() -> save(session));
        }
    }

    private void cleanUp() {
        host.clearRoom(this);
        server.removeRoom(code);
    }

    private boolean save(SessionRecord session) {
        try {
            server.quizzes().saveSession(session);
            return true;
        } catch (RepositoryException e) {
            System.err.println("[room " + code + "] " + e.getMessage() + " " + e.getCause());
            return false;
        }
    }

    private SessionRecord buildSession(boolean completed, int questionsAsked) {
        List<ParticipantRecord> participants = new ArrayList<>();
        for (Player player : players.values()) {
            participants.add(new ParticipantRecord(player.userId, player.name, player.score, player.correctCount,
                    List.copyOf(player.answers.values())));
        }
        return new SessionRecord(quiz.id(), code, startedAt, LocalDateTime.now(), completed, shuffled,
                questionsAsked, participants);
    }

    private int connectedCount() {
        return (int) players.values().stream().filter(p -> p.connected).count();
    }

    private static int rankOf(Player player, List<ScoreEntry> ranking) {
        return ranking.stream()
                .filter(e -> e.name().equals(player.name))
                .mapToInt(ScoreEntry::rank).findFirst().orElse(ranking.size());
    }

    private PersonalResult personalResult(Player player, List<ScoreEntry> ranking) {
        AnswerRecord answer = player.answers.get(currentIndex);
        int bonus = player.streakBonuses.getOrDefault(currentIndex, 0);
        return new PersonalResult(answer != null, answer != null && answer.correct(),
                answer == null ? 0 : answer.points(), player.score, rankOf(player, ranking), player.streak, bonus);
    }

    /** Highest score first. Equal scores share a rank: 1, 2, 2, 4. */
    private List<ScoreEntry> ranking() {
        List<Player> sorted = players.values().stream()
                .sorted(Comparator.comparingInt((Player p) -> p.score).reversed().thenComparing(p -> p.name))
                .toList();
        List<ScoreEntry> entries = new ArrayList<>();
        int rank = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i == 0 || sorted.get(i).score != sorted.get(i - 1).score) {
                rank = i + 1;
            }
            entries.add(new ScoreEntry(rank, sorted.get(i).name, sorted.get(i).score));
        }
        return entries;
    }

    private void broadcastPlayers() {
        List<String> names = players.values().stream().filter(p -> p.connected).map(p -> p.name).toList();
        broadcast(new PlayersChanged(names));
    }

    private void broadcast(Response message) {
        host.send(message);
        for (Player player : players.values()) {
            player.connection.send(message);
        }
    }
}
