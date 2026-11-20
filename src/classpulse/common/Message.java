package classpulse.common;

import classpulse.common.Views.Account;
import classpulse.common.Views.Badge;
import classpulse.common.Views.ClassInfo;
import classpulse.common.Views.HomeworkInfo;
import classpulse.common.Views.HomeworkQuestion;
import classpulse.common.Views.StudentHomework;
import classpulse.common.Views.HistoryEntry;
import classpulse.common.Views.PersonalResult;
import classpulse.common.Views.QuizSummary;
import classpulse.common.Views.Role;
import classpulse.common.Views.ScoreEntry;
import classpulse.common.Views.SessionReport;
import classpulse.common.Views.SessionSummary;
import classpulse.common.Views.StudentStats;
import classpulse.common.Views.TeacherStats;
import java.io.Serializable;
import java.util.List;

/**
 * Every message that can travel over the socket.
 * Because the interfaces are sealed, the compiler reports an error if a switch
 * forgets to handle a message type.
 */
public sealed interface Message extends Serializable {

    /** Client to server. */
    sealed interface Request extends Message {
    }

    /** Server to client. */
    sealed interface Response extends Message {
    }

    /** Why an email code was sent. */
    enum CodePurpose { VERIFY_EMAIL, RESET_PASSWORD }

    // ----- Requests: accounts -----
    record Register(String fullName, String username, String email, String password, Role role) implements Request {
    }

    record VerifyEmail(String email, String code) implements Request {
    }

    record ResendCode(String email, CodePurpose purpose) implements Request {
    }

    /** login is a username or an email address. */
    record Login(String login, String password) implements Request {
    }

    record ForgotPassword(String email) implements Request {
    }

    record ResetPassword(String email, String code, String newPassword) implements Request {
    }

    record ChangePassword(String currentPassword, String newPassword) implements Request {
    }

    record UpdateProfile(String fullName) implements Request {
    }

    record Logout() implements Request {
    }

    // ----- Requests: teacher -----
    record ListQuizzes() implements Request {
    }

    record GetQuiz(long quizId) implements Request {
    }

    record SaveQuiz(Quiz quiz) implements Request {
    }

    /** Saves a changed copy. The old version is archived, so past reports stay correct. */
    record UpdateQuiz(long quizId, Quiz quiz) implements Request {
    }

    record DuplicateQuiz(long quizId) implements Request {
    }

    record DeleteQuiz(long quizId) implements Request {
    }

    record CreateRoom(long quizId, boolean shuffle) implements Request {
    }

    record KickPlayer(String name) implements Request {
    }

    record StartGame() implements Request {
    }

    record NextStep() implements Request {
    }

    record CloseRoom() implements Request {
    }

    record ListSessions() implements Request {
    }

    record GetReport(long sessionId) implements Request {
    }

    record EmailReport(long sessionId) implements Request {
    }

    record GetOverview() implements Request {
    }

    // ----- Requests: student -----
    record JoinRoom(String code, String name) implements Request {
    }

    record SubmitAnswer(int questionIndex, int choice) implements Request {
    }

    record GetStudentDashboard() implements Request {
    }

    // ----- Requests: classes and homework (added in the homework upgrade) -----
    record CreateClass(String name) implements Request {
    }

    record ListClasses() implements Request {
    }

    /** dueInMinutes is counted from now, so the teacher's and server's clocks never disagree. */
    record AssignHomework(long classId, long quizId, int dueInMinutes) implements Request {
    }

    record ListHomework() implements Request {
    }

    record JoinClass(String code) implements Request {
    }

    record GetMyHomework() implements Request {
    }

    record StartHomework(long homeworkId) implements Request {
    }

    /** One choice per question, in order; -1 means the time ran out. */
    record SubmitHomework(long homeworkId, List<Integer> choices) implements Request {
        public SubmitHomework {
            choices = List.copyOf(choices);
        }
    }

    // ----- Responses -----
    record AuthResult(boolean ok, String text, Account account) implements Response {
    }

    /** A code was emailed. The client shows the code entry screen. */
    record CodeSent(String email, CodePurpose purpose, String text) implements Response {
    }

    record PasswordResetDone(String text) implements Response {
    }

    /** A success message, shown as a short notification. */
    record Notice(String text) implements Response {
    }

    record ProfileUpdated(Account account) implements Response {
    }

    record QuizList(List<QuizSummary> quizzes) implements Response {
        public QuizList {
            quizzes = List.copyOf(quizzes);
        }
    }

    record QuizDetail(long quizId, Quiz quiz) implements Response {
    }

    record RoomCreated(String code, String title, int questionCount, boolean shuffled) implements Response {
    }

    record JoinResult(boolean ok, String text, String title) implements Response {
    }

    record PlayersChanged(List<String> names) implements Response {
        public PlayersChanged {
            names = List.copyOf(names);
        }
    }

    /** Deliberately carries no correct answer, so a modified client cannot cheat. */
    record QuestionStarted(int index, int total, String text, List<String> options, int seconds) implements Response {
        public QuestionStarted {
            options = List.copyOf(options);
        }
    }

    record AnswerReceived(boolean accepted, String text) implements Response {
    }

    record AnswerProgress(int answered, int total) implements Response {
    }

    record QuestionEnded(int index, int total, int correctIndex, List<Integer> counts,
                         List<ScoreEntry> top, PersonalResult you) implements Response {
        public QuestionEnded {
            counts = List.copyOf(counts);
            top = List.copyOf(top);
        }
    }

    /** correct and questions describe the receiving student; they are 0 for the teacher. */
    record GameOver(List<ScoreEntry> ranking, PersonalResult you, boolean saved, int correct, int questions,
                    boolean emailed) implements Response {
        public GameOver {
            ranking = List.copyOf(ranking);
        }
    }

    record RoomClosed(String reason) implements Response {
    }

    record SessionList(List<SessionSummary> sessions) implements Response {
        public SessionList {
            sessions = List.copyOf(sessions);
        }
    }

    record Report(SessionReport report) implements Response {
    }

    record Overview(TeacherStats stats) implements Response {
    }

    record StudentDashboard(StudentStats stats, List<HistoryEntry> history, List<Badge> badges) implements Response {
        public StudentDashboard {
            history = List.copyOf(history);
            badges = List.copyOf(badges);
        }
    }

    record ErrorText(String text) implements Response {
    }

    // ----- Responses: classes, homework and phone play (added in the upgrades) -----
    record ClassList(List<ClassInfo> classes) implements Response {
        public ClassList {
            classes = List.copyOf(classes);
        }
    }

    record HomeworkList(List<HomeworkInfo> homework) implements Response {
        public HomeworkList {
            homework = List.copyOf(homework);
        }
    }

    record MyHomework(List<String> classNames, List<StudentHomework> homework) implements Response {
        public MyHomework {
            classNames = List.copyOf(classNames);
            homework = List.copyOf(homework);
        }
    }

    record HomeworkStarted(long homeworkId, String title, List<HomeworkQuestion> questions) implements Response {
        public HomeworkStarted {
            questions = List.copyOf(questions);
        }
    }

    /** Sent after a submission. Only now are the correct answers revealed. */
    record HomeworkDone(long homeworkId, String title, int correct, List<Integer> choices,
                        List<Integer> correctChoices, List<HomeworkQuestion> questions) implements Response {
        public HomeworkDone {
            choices = List.copyOf(choices);
            correctChoices = List.copyOf(correctChoices);
            questions = List.copyOf(questions);
        }
    }

    /** The web address phones can open (or scan as a QR code) to join the room. */
    record PhoneLink(String url) implements Response {
    }
}
