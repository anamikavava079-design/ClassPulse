package classpulse.client;

import classpulse.common.Message.GetStudentDashboard;
import classpulse.common.Message.JoinRoom;
import classpulse.common.Message.StudentDashboard;
import classpulse.common.Validation;
import classpulse.common.Views.Account;
import classpulse.common.Views.Badge;
import classpulse.common.Views.HistoryEntry;
import classpulse.common.Views.StudentStats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

/** The signed-in student's home: join a quiz, see past results, achievements and the profile. */
final class StudentShell extends JPanel {
    static final String JOIN = "join";
    static final String RESULTS = "results";
    static final String BADGES = "badges";
    static final String PROFILE = "profile";
    static final String HOMEWORK = "homework";
    private static final List<String> ORDER = List.of(JOIN, HOMEWORK, RESULTS, BADGES, PROFILE);

    private final AppFrame app;
    final Sidebar sidebar;
    final SmoothDeck deck = new SmoothDeck();
    final ProfilePanel profile;
    final StudentHomeworkPanel homework;

    // Join page
    final JTextField codeField = Theme.field(8, "ABC123");
    final JTextField nameField = Theme.field(14, "Name shown in the room");
    final JButton joinButton = Theme.withIcon(Theme.primary("Join quiz"), Icons.Kind.PLAY);
    private final JLabel joinGreeting = Widgets.pageTitle(" ");
    private final Widgets.StatCard joinPlayed = new Widgets.StatCard("Quizzes played", Icons.Kind.PLAY, Theme.PRIMARY);
    private final Widgets.StatCard joinAccuracy = new Widgets.StatCard("Accuracy", Icons.Kind.CHECK, new Color(0x10B981));
    private final Widgets.StatCard joinBadges = new Widgets.StatCard("Badges", Icons.Kind.STAR, new Color(0xF59E0B));

    // Results page
    final Widgets.StatCard played = new Widgets.StatCard("Quizzes played", Icons.Kind.PLAY, Theme.PRIMARY);
    final Widgets.StatCard points = new Widgets.StatCard("Total points", Icons.Kind.STAR, new Color(0xF59E0B));
    final Widgets.StatCard accuracy = new Widgets.StatCard("Accuracy", Icons.Kind.CHECK, new Color(0x10B981));
    final Widgets.StatCard bestRank = new Widgets.StatCard("Best rank", Icons.Kind.TROPHY, new Color(0x0EA5E9));
    private final Widgets.LineChart chart = new Widgets.LineChart();
    private final DefaultTableModel historyModel =
            Theme.readOnlyModel("Date", "Quiz", "Teacher", "Score", "Rank", "Correct");

    // Badges page
    private final JPanel badgeGrid = Theme.transparent(new GridLayout(0, 3, 18, 18));
    private final JLabel badgeSummary = Widgets.pageSubtitle(" ");
    private final BadgeProgress badgeProgress = new BadgeProgress();

    StudentShell(AppFrame app) {
        this.app = app;
        setLayout(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        sidebar = new Sidebar("Student", List.of(
                new Sidebar.Item(JOIN, "Join a quiz", Icons.Kind.PLAY),
                new Sidebar.Item(HOMEWORK, "Homework", Icons.Kind.CLOCK),
                new Sidebar.Item(RESULTS, "My results", Icons.Kind.CHART),
                new Sidebar.Item(BADGES, "Achievements", Icons.Kind.TROPHY),
                new Sidebar.Item(PROFILE, "Profile", Icons.Kind.USER)),
                this::open, app::signOut);
        add(sidebar, BorderLayout.WEST);
        profile = new ProfilePanel(app);
        deck.addCard(padded(joinPage()), JOIN);
        deck.addCard(padded(resultsPage()), RESULTS);
        deck.addCard(padded(badgesPage()), BADGES);
        deck.addCard(padded(profile), PROFILE);
        homework = new StudentHomeworkPanel(app);
        deck.addCard(padded(homework), HOMEWORK);
        add(deck, BorderLayout.CENTER);
    }

    private static JPanel padded(Component page) {
        JPanel panel = Theme.backdrop(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(28, 32, 26, 32));
        panel.add(page);
        return panel;
    }

    // ----- Pages -----

    private JPanel joinPage() {
        JPanel page = Theme.transparent(new BorderLayout(0, 22));
        page.add(Widgets.header(joinGreeting, Widgets.pageSubtitle("Ready for a quiz? Ask your teacher for the room code.")),
                BorderLayout.NORTH);

        JPanel card = new Theme.RoundPanel(Theme.CARD, 24, true);
        card.setLayout(new GridBagLayout());
        card.setBorder(BorderFactory.createEmptyBorder(30, 40, 30, 40));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = GridBagConstraints.RELATIVE;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        JLabel icon = new JLabel(Icons.of(Icons.Kind.PLAY, 54, Theme.PRIMARY), SwingConstants.CENTER);
        card.add(icon, c);
        c.insets = new Insets(12, 0, 4, 0);
        JLabel title = Theme.label("Join a live quiz", Font.BOLD, 22, Theme.INK);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(title, c);
        c.insets = new Insets(0, 0, 20, 0);
        JLabel hint = Theme.label("Your score is saved to your account and emailed to you.", Font.PLAIN, 13, Theme.MUTED);
        hint.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(hint, c);
        codeField.setFont(Theme.font(Font.BOLD, 30));
        codeField.setHorizontalAlignment(SwingConstants.CENTER);
        ((javax.swing.text.AbstractDocument) codeField.getDocument()).setDocumentFilter(new UpperCaseLimit(6));
        c.insets = new Insets(0, 0, 6, 0);
        card.add(Theme.label("Room code", Font.BOLD, 13, Theme.MUTED), c);
        c.insets = new Insets(0, 0, 14, 0);
        card.add(codeField, c);
        c.insets = new Insets(0, 0, 6, 0);
        card.add(Theme.label("Name in the room", Font.BOLD, 13, Theme.MUTED), c);
        c.insets = new Insets(0, 0, 20, 0);
        card.add(nameField, c);
        c.insets = new Insets(0, 0, 0, 0);
        card.add(Theme.height(joinButton, 50), c);
        card.setPreferredSize(new Dimension(440, 420));

        JPanel center = Theme.transparent(new GridBagLayout());
        center.add(card);
        page.add(center, BorderLayout.CENTER);

        JPanel stats = Theme.transparent(new GridLayout(1, 3, 16, 0));
        stats.add(joinPlayed);
        stats.add(joinAccuracy);
        stats.add(joinBadges);
        page.add(stats, BorderLayout.SOUTH);

        joinButton.addActionListener(e -> join());
        codeField.addActionListener(e -> join());
        nameField.addActionListener(e -> join());
        return page;
    }

    private JPanel resultsPage() {
        JPanel page = Theme.transparent(new BorderLayout(0, 20));
        page.add(Widgets.header(Widgets.pageTitle("My results"),
                Widgets.pageSubtitle("Every quiz you play while signed in is saved here.")), BorderLayout.NORTH);
        JPanel stats = Theme.transparent(new GridLayout(1, 4, 16, 0));
        stats.add(played);
        stats.add(points);
        stats.add(accuracy);
        stats.add(bestRank);
        JPanel body = Theme.transparent(new BorderLayout(0, 20));
        body.add(stats, BorderLayout.NORTH);
        JPanel lower = Theme.transparent(new BorderLayout(0, 20));
        JPanel chartCard = Widgets.titledCard("Accuracy over time", chart);
        chart.setPreferredSize(new Dimension(400, 150));
        lower.add(chartCard, BorderLayout.NORTH);
        JTable table = Theme.table(historyModel);
        int[] widths = {110, 190, 130, 70, 80, 70};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        lower.add(Widgets.titledCard("History", Theme.scroll(table)), BorderLayout.CENTER);
        body.add(lower, BorderLayout.CENTER);
        page.add(body, BorderLayout.CENTER);
        return page;
    }

    private JPanel badgesPage() {
        JPanel page = Theme.transparent(new BorderLayout(0, 20));
        JPanel heading = Theme.transparent(new BorderLayout(0, 10));
        heading.add(Widgets.header(Widgets.pageTitle("Achievements"), badgeSummary), BorderLayout.NORTH);
        heading.add(badgeProgress, BorderLayout.CENTER);
        page.add(heading, BorderLayout.NORTH);
        JPanel holder = Theme.transparent(new BorderLayout());
        holder.add(badgeGrid, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(holder);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        Theme.slim(scroll);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    // ----- Behaviour -----

    void setAccount(Account account) {
        sidebar.setAccount(account);
        profile.setAccount(account);
        joinGreeting.setText("Hi, " + account.fullName().split(" ")[0] + "!");
        String name = account.fullName();
        nameField.setText(name.length() > 20 ? name.substring(0, 20).strip() : name);
    }

    void open(String page) {
        int direction = Integer.compare(ORDER.indexOf(page), ORDER.indexOf(deck.current()));
        sidebar.select(page);
        deck.show(page, direction);
        if (page.equals(HOMEWORK)) {
            app.send(new classpulse.common.Message.GetMyHomework());
        } else if (!page.equals(PROFILE)) {
            app.send(new GetStudentDashboard());
        }
    }

    private void join() {
        try {
            String code = Validation.roomCode(codeField.getText());
            String name = Validation.displayName(nameField.getText());
            app.busy(joinButton);
            app.send(new JoinRoom(code, name));
        } catch (IllegalArgumentException e) {
            app.toast(e.getMessage(), ToastLayer.Kind.ERROR);
        }
    }

    void setDashboard(StudentDashboard dashboard) {
        StudentStats stats = dashboard.stats();
        long earned = dashboard.badges().stream().filter(Badge::earned).count();
        joinPlayed.setValue(String.valueOf(stats.played()));
        joinAccuracy.setValue(stats.played() == 0 ? "-" : stats.averageAccuracy() + "%");
        joinBadges.setValue(earned + " / " + dashboard.badges().size());
        played.setValue(String.valueOf(stats.played()));
        points.setValue(String.format(Locale.ROOT, "%,d", stats.totalPoints()));
        accuracy.setValue(stats.played() == 0 ? "-" : stats.averageAccuracy() + "%");
        bestRank.setValue(stats.played() == 0 ? "-" : "#" + stats.bestRank());

        historyModel.setRowCount(0);
        for (HistoryEntry h : dashboard.history()) {
            historyModel.addRow(new Object[]{h.playedAt().substring(5), h.quizTitle(), h.teacherName(), h.score(),
                    "#" + h.rank() + " / " + h.players(), h.correct() + "/" + h.questions()});
        }
        List<Integer> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<HistoryEntry> history = dashboard.history();
        for (int i = Math.min(history.size(), 12) - 1; i >= 0; i--) { // Oldest first, last 12 games.
            values.add(history.get(i).accuracy());
            labels.add(history.get(i).playedAt().substring(5, 10));
        }
        chart.setData(values, labels);

        badgeGrid.removeAll();
        for (Badge badge : dashboard.badges()) {
            badgeGrid.add(new Widgets.BadgeTile(badge));
        }
        badgeSummary.setText(earned + " of " + dashboard.badges().size() + " unlocked. Keep playing to earn the rest.");
        badgeProgress.setFraction(dashboard.badges().isEmpty() ? 0 : earned / (double) dashboard.badges().size());
        badgeGrid.revalidate();
        badgeGrid.repaint();
    }

    /** A wide progress bar for the badges page. */
    private static final class BadgeProgress extends javax.swing.JComponent {
        private double fraction;

        BadgeProgress() {
            setPreferredSize(new Dimension(400, 12));
        }

        void setFraction(double value) {
            fraction = value;
            repaint();
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            java.awt.Graphics2D g = (java.awt.Graphics2D) graphics.create();
            Theme.smooth(g);
            g.setColor(Theme.TRACK);
            g.fillRoundRect(0, 0, getWidth(), 10, 10, 10);
            g.setPaint(new java.awt.GradientPaint(0, 0, new Color(0xFBBF24), getWidth(), 0, new Color(0xF97316)));
            if (fraction > 0) {
                g.fillRoundRect(0, 0, Math.max(10, (int) (getWidth() * fraction)), 10, 10, 10);
            }
            g.dispose();
        }
    }

    /** Upper-cases room codes as they are typed, and stops at the maximum length. */
    private static final class UpperCaseLimit extends javax.swing.text.DocumentFilter {
        private final int max;

        UpperCaseLimit(int max) {
            this.max = max;
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String text, javax.swing.text.AttributeSet attrs)
                throws javax.swing.text.BadLocationException {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, javax.swing.text.AttributeSet attrs)
                throws javax.swing.text.BadLocationException {
            String clean = text == null ? "" : text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            int room = max - (fb.getDocument().getLength() - length);
            fb.replace(offset, length, clean.substring(0, Math.max(0, Math.min(room, clean.length()))), attrs);
        }
    }
}
