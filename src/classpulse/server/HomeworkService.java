package classpulse.server;

import classpulse.common.Csv;
import classpulse.common.Message.HomeworkDone;
import classpulse.common.Message.HomeworkStarted;
import classpulse.common.Question;
import classpulse.common.Validation;
import classpulse.common.Views.HomeworkQuestion;
import classpulse.mail.Email.Attachment;
import classpulse.server.EmailTemplates.AnswerLine;
import classpulse.server.HomeworkRepository.Member;
import classpulse.server.HomeworkRepository.SummaryRow;
import classpulse.server.HomeworkRepository.Task;
import classpulse.server.QuizRepository.LoadedQuiz;
import classpulse.server.UserRepository.UserRecord;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Classes and homework (added in the homework upgrade).
 *
 * A teacher makes a class and gets a 6-character join code. Students join with the code.
 * The teacher sets a quiz as homework with a deadline, and every student in the class is
 * emailed. A background clock (a ScheduledExecutorService) checks every 30 seconds and:
 *   - sends a reminder to students who have not handed it in, once the reminder time comes;
 *   - after the deadline, sends the teacher a summary with a CSV attachment.
 * Nobody has to press anything for these emails: the server does it on its own.
 */
final class HomeworkService implements AutoCloseable {
    static final int MAX_MINUTES = 30 * 24 * 60;
    /** Students who started just before the deadline may still hand in for a short time. */
    static final Duration GRACE = Duration.ofMinutes(10);
    private static final String CODE_LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final HomeworkRepository homework;
    private final QuizRepository quizzes;
    private final MailService mail;
    private final SecureRandom random = new SecureRandom();
    /** Its own thread, so slow emails can never delay a live game's question timer. */
    private final ScheduledExecutorService clock = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "homework-clock");
        thread.setDaemon(true);
        return thread;
    });

    HomeworkService(HomeworkRepository homework, QuizRepository quizzes, MailService mail) {
        this.homework = homework;
        this.quizzes = quizzes;
        this.mail = mail;
    }

    HomeworkRepository repository() {
        return homework;
    }

    void startClock() {
        clock.scheduleWithFixedDelay(() -> {
            try {
                runChecks(LocalDateTime.now());
            } catch (RuntimeException e) {
                // One failed check must not stop the clock: log it and try again next time.
                System.err.println("Homework check failed: " + e.getMessage());
            }
        }, 5, 30, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        clock.shutdownNow();
    }

    // ----- Teacher -----

    long createClass(long teacherId, String name) {
        String clean = Validation.text(name, "Class name", 60);
        return homework.createClass(teacherId, clean, this::randomCode);
    }

    /**
     * Sets homework and emails the class (on a background thread). The reminder goes out
     * 24 hours before the deadline, or halfway there for homework due in less than 2 days.
     */
    void assign(UserRecord teacher, long classId, long quizId, int dueInMinutes) {
        if (dueInMinutes < 1 || dueInMinutes > MAX_MINUTES) {
            throw new IllegalArgumentException("The deadline must be between 1 minute and 30 days away.");
        }
        LocalDateTime now = LocalDateTime.now().withNano(0);
        LocalDateTime due = now.plusMinutes(dueInMinutes);
        LocalDateTime remindAt = reminderTime(now, due);
        long id = homework.assign(teacher.id(), classId, quizId, now, due, remindAt);
        if (id == 0) {
            throw new IllegalArgumentException("That class or quiz was not found.");
        }
        LoadedQuiz quiz = quizzes.loadQuizAnyVersion(quizId).orElseThrow();
        List<Member> members = homework.members(classId);
        String className = homework.listClasses(teacher.id()).stream()
                .filter(c -> c.id() == classId).map(c -> c.name()).findFirst().orElse("your class");
        String dueText = due.format(QuizRepository.DISPLAY_TIME);
        Thread.startVirtualThread(() -> {
            for (Member member : members) {
                mail.send(member.email(), "HOMEWORK", EmailTemplates.homeworkAssigned(member.fullName(),
                        teacher.fullName(), className, quiz.title(), quiz.questions().size(), dueText));
            }
        });
    }

    static LocalDateTime reminderTime(LocalDateTime now, LocalDateTime due) {
        Duration total = Duration.between(now, due);
        Duration before = total.compareTo(Duration.ofHours(48)) >= 0 ? Duration.ofHours(24) : total.dividedBy(2);
        return due.minus(before);
    }

    // ----- Student -----

    String joinClass(long studentId, String code) {
        String clean = code == null ? "" : code.strip().toUpperCase(Locale.ROOT);
        if (!clean.matches("[A-Z0-9]{6}")) {
            throw new IllegalArgumentException("A class code is 6 letters and digits.");
        }
        return homework.joinClass(studentId, clean)
                .orElseThrow(() -> new IllegalArgumentException("No class has that code. Check it with your teacher."));
    }

    HomeworkStarted start(long studentId, long homeworkId) {
        Task task = taskFor(studentId, homeworkId);
        if (homework.hasSubmitted(homeworkId, studentId)) {
            throw new IllegalStateException("You have already handed in this homework.");
        }
        if (!LocalDateTime.now().isBefore(task.dueAt())) {
            throw new IllegalStateException("The deadline for this homework has passed.");
        }
        LoadedQuiz quiz = load(task);
        List<HomeworkQuestion> questions = quiz.questions().stream()
                .map(q -> new HomeworkQuestion(q.text(), q.options(), q.seconds())).toList();
        return new HomeworkStarted(homeworkId, quiz.title(), questions);
    }

    /** Marks the answers on the server (the client never had the correct answers), saves, and emails the result. */
    HomeworkDone submit(UserRecord student, long homeworkId, List<Integer> choices) {
        Task task = taskFor(student.id(), homeworkId);
        if (LocalDateTime.now().isAfter(task.dueAt().plus(GRACE))) {
            throw new IllegalStateException("The deadline for this homework has passed.");
        }
        LoadedQuiz quiz = load(task);
        List<Question> questions = quiz.questions();
        if (choices.size() != questions.size()) {
            throw new IllegalArgumentException("Every question needs an answer (or a skip).");
        }
        int correct = 0;
        List<Integer> correctChoices = new ArrayList<>();
        List<AnswerLine> lines = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            Integer choice = choices.get(i);
            if (choice == null || choice < -1 || choice >= Question.OPTION_COUNT) {
                throw new IllegalArgumentException("That is not a valid option.");
            }
            boolean right = choice == question.correctIndex();
            if (right) {
                correct++;
            }
            correctChoices.add(question.correctIndex());
            String yours = choice < 0 ? "(no answer)" : letter(choice) + ". " + question.options().get(choice);
            String rightText = letter(question.correctIndex()) + ". " + question.options().get(question.correctIndex());
            lines.add(new AnswerLine(i + 1, question.text(), yours, rightText, right));
        }
        if (!homework.saveSubmission(homeworkId, student.id(), correct, questions.size(), LocalDateTime.now())) {
            throw new IllegalStateException("You have already handed in this homework.");
        }
        EmailTemplates.Content email = EmailTemplates.homeworkResult(student.fullName(), task.className(),
                quiz.title(), correct, questions.size(), lines);
        mail.sendLater(student.email(), "HOMEWORK", email);
        List<HomeworkQuestion> shown = questions.stream()
                .map(q -> new HomeworkQuestion(q.text(), q.options(), q.seconds())).toList();
        return new HomeworkDone(homeworkId, quiz.title(), correct, choices, correctChoices, shown);
    }

    // ----- The clock -----

    /** Sends any reminders and summaries that are due at this time. Tests call it with a chosen time. */
    void runChecks(LocalDateTime now) {
        for (Task task : homework.remindersDue(now)) {
            if (!homework.claim(task.id(), false)) {
                continue;
            }
            String due = task.dueAt().format(QuizRepository.DISPLAY_TIME);
            String left = timeLeft(Duration.between(now, task.dueAt()));
            for (Member member : homework.notSubmitted(task.id())) {
                mail.send(member.email(), "REMINDER", EmailTemplates.homeworkReminder(member.fullName(),
                        task.className(), task.quizTitle(), due, left));
            }
        }
        for (Task task : homework.summariesDue(now)) {
            if (!homework.claim(task.id(), true)) {
                continue;
            }
            List<SummaryRow> rows = homework.summary(task.id());
            String due = task.dueAt().format(QuizRepository.DISPLAY_TIME);
            List<String> csv = new ArrayList<>();
            csv.add("Student,Handed in,Correct,Questions,Percent");
            for (SummaryRow row : rows) {
                int percent = row.done() && row.questions() > 0 ? Math.round(100f * row.correct() / row.questions()) : 0;
                csv.add(Csv.cell(row.name()) + "," + (row.done() ? "Yes" : "No") + ","
                        + (row.done() ? row.correct() : "") + "," + (row.done() ? row.questions() : "") + ","
                        + (row.done() ? percent : ""));
            }
            Attachment attachment = new Attachment("homework-" + task.id() + ".csv", "text/csv",
                    String.join("\r\n", csv).getBytes(StandardCharsets.UTF_8));
            mail.send(task.teacherEmail(), "SUMMARY", EmailTemplates.homeworkSummary(task.teacherName(),
                    task.className(), task.quizTitle(), due, rows), List.of(attachment));
        }
    }

    static String timeLeft(Duration left) {
        long minutes = Math.max(1, left.toMinutes());
        if (minutes < 60) {
            return minutes + (minutes == 1 ? " minute" : " minutes");
        }
        long hours = Math.round(minutes / 60.0);
        return hours + (hours == 1 ? " hour" : " hours");
    }

    // ----- Helpers -----

    private Task taskFor(long studentId, long homeworkId) {
        return homework.taskForStudent(studentId, homeworkId)
                .orElseThrow(() -> new IllegalArgumentException("That homework was not found."));
    }

    private LoadedQuiz load(Task task) {
        return quizzes.loadQuizAnyVersion(task.quizId())
                .orElseThrow(() -> new IllegalStateException("The quiz for this homework was not found."));
    }

    private String randomCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(CODE_LETTERS.charAt(random.nextInt(CODE_LETTERS.length())));
        }
        return code.toString();
    }

    private static String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }
}
