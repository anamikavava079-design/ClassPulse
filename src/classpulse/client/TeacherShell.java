package classpulse.client;

import classpulse.common.Message.GetOverview;
import classpulse.common.Message.ListQuizzes;
import classpulse.common.Message.ListSessions;
import classpulse.common.Views.Account;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** The teacher's home: a sidebar on the left and one page at a time on the right. */
final class TeacherShell extends JPanel {
    static final String OVERVIEW = "overview";
    static final String QUIZZES = "quizzes";
    static final String EDITOR = "editor";
    static final String REPORTS = "reports";
    static final String PROFILE = "profile";
    static final String HOMEWORK = "homework";
    private static final List<String> ORDER = List.of(OVERVIEW, QUIZZES, EDITOR, REPORTS, HOMEWORK, PROFILE);

    private final AppFrame app;
    final Sidebar sidebar;
    final SmoothDeck deck = new SmoothDeck();
    final OverviewPanel overview;
    final QuizzesPanel quizzes;
    final QuizEditorPanel editor;
    final ReportsPanel reports;
    final ProfilePanel profile;
    final TeacherHomeworkPanel homework;

    TeacherShell(AppFrame app) {
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        sidebar = new Sidebar("Teacher", List.of(
                new Sidebar.Item(OVERVIEW, "Overview", Icons.Kind.GRID),
                new Sidebar.Item(QUIZZES, "My quizzes", Icons.Kind.LIST),
                new Sidebar.Item(REPORTS, "Reports", Icons.Kind.CHART),
                new Sidebar.Item(HOMEWORK, "Classes & homework", Icons.Kind.USERS),
                new Sidebar.Item(PROFILE, "Profile", Icons.Kind.USER)),
                this::open, app::signOut);
        add(sidebar, BorderLayout.WEST);

        overview = new OverviewPanel(app, this);
        quizzes = new QuizzesPanel(app, this);
        editor = new QuizEditorPanel(app, this);
        reports = new ReportsPanel(app);
        profile = new ProfilePanel(app);
        deck.addCard(padded(overview), OVERVIEW);
        deck.addCard(padded(quizzes), QUIZZES);
        deck.addCard(padded(editor), EDITOR);
        deck.addCard(padded(reports), REPORTS);
        deck.addCard(padded(profile), PROFILE);
        homework = new TeacherHomeworkPanel(app);
        deck.addCard(padded(homework), HOMEWORK);
        add(deck, BorderLayout.CENTER);
    }

    private static JPanel padded(Component page) {
        JPanel panel = Theme.backdrop(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(28, 32, 26, 32));
        panel.add(page);
        return panel;
    }

    void setAccount(Account account) {
        sidebar.setAccount(account);
        overview.setAccount(account);
        profile.setAccount(account);
    }

    /** Shows a page and asks the server for the data it needs. */
    void open(String page) {
        int direction = Integer.compare(ORDER.indexOf(page), ORDER.indexOf(deck.current()));
        sidebar.select(page.equals(EDITOR) ? QUIZZES : page);
        deck.show(page, direction);
        switch (page) {
            case OVERVIEW -> app.send(new GetOverview());
            case QUIZZES -> app.send(new ListQuizzes());
            case REPORTS -> app.send(new ListSessions());
            case HOMEWORK -> {
                app.send(new ListQuizzes());
                app.send(new classpulse.common.Message.ListClasses());
            }
            default -> {
                // The editor and profile pages already have what they need.
            }
        }
    }

    String currentPage() {
        return deck.current();
    }
}
