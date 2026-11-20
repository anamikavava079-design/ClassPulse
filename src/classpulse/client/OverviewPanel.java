package classpulse.client;

import classpulse.common.Views.Account;
import classpulse.common.Views.SessionSummary;
import classpulse.common.Views.TeacherStats;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

/** The teacher's first page: key numbers, recent games and quick actions. */
final class OverviewPanel extends JPanel {
    private final JLabel greeting = Widgets.pageTitle(" ");
    final Widgets.StatCard quizzes = new Widgets.StatCard("Quizzes", Icons.Kind.LIST, Theme.PRIMARY);
    final Widgets.StatCard sessions = new Widgets.StatCard("Games hosted", Icons.Kind.PLAY, new Color(0x0EA5E9));
    final Widgets.StatCard students = new Widgets.StatCard("Students reached", Icons.Kind.USERS, new Color(0x10B981));
    final Widgets.StatCard accuracy = new Widgets.StatCard("Average accuracy", Icons.Kind.CHART, new Color(0xF59E0B));
    private final DefaultTableModel recentModel = Theme.readOnlyModel("Started", "Quiz", "Students", "Status");
    private final JTable recentTable = Theme.table(recentModel);
    private List<SessionSummary> recent = List.of();

    OverviewPanel(AppFrame app, TeacherShell shell) {
        setLayout(new BorderLayout(0, 22));
        setOpaque(false);
        JButton newQuiz = Theme.withIcon(Theme.primary("New quiz"), Icons.Kind.PLUS);
        newQuiz.addActionListener(e -> {
            shell.editor.startNew();
            shell.open(TeacherShell.EDITOR);
        });
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH));
        add(Widgets.header(greeting, Widgets.pageSubtitle(date + "  |  Here is how your classes are doing."), newQuiz),
                BorderLayout.NORTH);

        JPanel content = Theme.transparent(new BorderLayout(0, 22));
        JPanel stats = Theme.transparent(new GridLayout(1, 4, 16, 0));
        stats.add(quizzes);
        stats.add(sessions);
        stats.add(students);
        stats.add(accuracy);
        content.add(stats, BorderLayout.NORTH);

        JPanel lower = Theme.transparent(new BorderLayout(20, 0));
        recentTable.getColumnModel().getColumn(0).setPreferredWidth(130);
        recentTable.getColumnModel().getColumn(1).setPreferredWidth(240);
        recentTable.getColumnModel().getColumn(2).setPreferredWidth(80);
        JPanel recentCard = Widgets.titledCard("Recent games", Theme.scroll(recentTable));
        JLabel hint = Theme.label("Double-click a game to open its report.", Font.PLAIN, 12, Theme.MUTED);
        recentCard.add(hint, BorderLayout.SOUTH);
        lower.add(recentCard, BorderLayout.CENTER);

        JPanel actions = Theme.transparent(new GridLayout(4, 1, 0, 10));
        JButton host = Theme.withIcon(Theme.soft("Host a quiz"), Icons.Kind.PLAY);
        JButton importCsv = Theme.withIcon(Theme.soft("Import quiz from CSV"), Icons.Kind.UPLOAD);
        JButton reports = Theme.withIcon(Theme.soft("Open reports"), Icons.Kind.CHART);
        JButton profile = Theme.withIcon(Theme.soft("Account settings"), Icons.Kind.USER);
        for (JButton button : new JButton[]{host, importCsv, reports, profile}) {
            button.setHorizontalAlignment(JButton.LEFT);
            actions.add(button);
        }
        host.addActionListener(e -> shell.open(TeacherShell.QUIZZES));
        importCsv.addActionListener(e -> {
            shell.open(TeacherShell.QUIZZES);
            shell.quizzes.importCsvFile();
        });
        reports.addActionListener(e -> shell.open(TeacherShell.REPORTS));
        profile.addActionListener(e -> shell.open(TeacherShell.PROFILE));
        JPanel side = Theme.transparent(new BorderLayout(0, 14));
        side.add(actions, BorderLayout.NORTH);
        JPanel tip = new Theme.RoundPanel(Theme.PRIMARY_SOFT, 16, false);
        tip.setLayout(new BorderLayout(0, 6));
        tip.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel tipTitle = Theme.label("Tip", Font.BOLD, 13, Theme.PRIMARY_DARK);
        tipTitle.setIcon(Icons.of(Icons.Kind.SHUFFLE, 16, Theme.PRIMARY_DARK));
        tip.add(tipTitle, BorderLayout.NORTH);
        JTextArea tipText = Theme.wrapped("Turn on \"Shuffle question order\" before hosting, so students "
                + "sitting together see the questions in a different order from last time.", Font.PLAIN, 13, Theme.INK);
        tip.add(tipText, BorderLayout.CENTER);
        side.add(tip, BorderLayout.CENTER);
        JPanel sideCard = Widgets.titledCard("Quick actions", side);
        sideCard.setPreferredSize(new Dimension(290, 300));
        lower.add(sideCard, BorderLayout.EAST);
        content.add(lower, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        recentTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = recentTable.getSelectedRow();
                if (e.getClickCount() == 2 && row >= 0 && row < recent.size()) {
                    shell.open(TeacherShell.REPORTS);
                    shell.reports.selectWhenLoaded(recent.get(row).id());
                }
            }
        });
    }

    void setAccount(Account account) {
        int hour = LocalTime.now().getHour();
        String part = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        greeting.setText(part + ", " + account.fullName().split(" ")[0]);
    }

    void setStats(TeacherStats stats) {
        quizzes.setValue(String.valueOf(stats.quizzes()));
        sessions.setValue(String.valueOf(stats.sessions()));
        students.setValue(String.valueOf(stats.studentsReached()));
        accuracy.setValue(stats.sessions() == 0 ? "-" : stats.averageAccuracy() + "%");
        recent = stats.recent();
        recentModel.setRowCount(0);
        for (SessionSummary s : recent) {
            recentModel.addRow(new Object[]{s.startedAt(), s.quizTitle(), s.players(),
                    s.completed() ? "Completed" : "Ended early"});
        }
    }
}
