package classpulse.client;

import classpulse.common.Csv;
import classpulse.common.Message.EmailReport;
import classpulse.common.Message.GetReport;
import classpulse.common.Views.QuestionStat;
import classpulse.common.Views.ScoreEntry;
import classpulse.common.Views.SessionReport;
import classpulse.common.Views.SessionSummary;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Comparator;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/** Past games saved in MySQL, with analysis to show the teacher what to reteach. */
final class ReportsPanel extends JPanel {
    private final AppFrame app;
    private final DefaultTableModel sessionModel = Theme.readOnlyModel("Started", "Quiz", "Room", "Students", "Status");
    private final DefaultTableModel rankingModel = Theme.readOnlyModel("Rank", "Name", "Score");
    final JTable sessionTable = Theme.table(sessionModel);
    private final Widgets.PercentChart chart = new Widgets.PercentChart();
    private final JLabel insight = Theme.label("Select a game to see its report.", Font.BOLD, 14, Theme.PRIMARY_DARK);
    final JButton exportButton = Theme.withIcon(Theme.secondary("Export CSV"), Icons.Kind.DOWNLOAD);
    final JButton emailButton = Theme.withIcon(Theme.primary("Email to me"), Icons.Kind.SEND);
    private List<SessionSummary> sessions = List.of();
    private SessionReport currentReport;
    private long selectWhenLoaded = -1;

    ReportsPanel(AppFrame app) {
        this.app = app;
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        add(Widgets.header(Widgets.pageTitle("Reports"),
                Widgets.pageSubtitle("Every game is saved. Pick one to see the ranking and the question to reteach."),
                exportButton, emailButton), BorderLayout.NORTH);

        sessionTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel sessionCard = Widgets.titledCard("Games", Theme.scroll(sessionTable));
        sessionCard.setPreferredSize(new Dimension(800, 210));

        JTable rankingTable = Theme.table(rankingModel);
        rankingTable.getColumnModel().getColumn(0).setMaxWidth(56);
        rankingTable.getColumnModel().getColumn(2).setMaxWidth(80);
        JPanel rankingCard = Widgets.titledCard("Ranking", Theme.scroll(rankingTable));
        rankingCard.setPreferredSize(new Dimension(320, 260));

        JPanel insightBox = new Theme.RoundPanel(Theme.PRIMARY_SOFT, 12, false);
        insightBox.setLayout(new BorderLayout());
        insightBox.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        insight.setIcon(Icons.of(Icons.Kind.STAR, 16, Theme.PRIMARY_DARK));
        insight.setIconTextGap(8);
        insightBox.add(insight);
        JPanel questionBody = Theme.transparent(new BorderLayout(0, 12));
        questionBody.add(insightBox, BorderLayout.NORTH);
        chart.setOpaque(false);
        JPanel chartHolder = Theme.transparent(new BorderLayout());
        chartHolder.add(chart, BorderLayout.NORTH);
        JScrollPane chartScroll = new JScrollPane(chartHolder);
        chartScroll.setBorder(null);
        chartScroll.setOpaque(false);
        chartScroll.getViewport().setOpaque(false);
        chartScroll.getVerticalScrollBar().setUnitIncrement(16);
        Theme.slim(chartScroll);
        questionBody.add(chartScroll, BorderLayout.CENTER);
        JPanel questionCard = Widgets.titledCard("Questions", questionBody);

        JPanel details = Theme.transparent(new BorderLayout(20, 0));
        details.add(rankingCard, BorderLayout.WEST);
        details.add(questionCard, BorderLayout.CENTER);
        JPanel center = Theme.transparent(new BorderLayout(0, 18));
        center.add(sessionCard, BorderLayout.NORTH);
        center.add(details, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        exportButton.setEnabled(false);
        emailButton.setEnabled(false);
        exportButton.addActionListener(e -> export());
        emailButton.addActionListener(e -> {
            if (currentReport != null) {
                app.busy(emailButton);
                app.send(new EmailReport(currentReport.summary().id()));
            }
        });
        sessionTable.getSelectionModel().addListSelectionListener(e -> {
            int row = sessionTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && row >= 0 && row < sessions.size()) {
                this.app.send(new GetReport(sessions.get(row).id()));
            }
        });
    }

    /** Used when a game is double-clicked on the overview page. */
    void selectWhenLoaded(long sessionId) {
        selectWhenLoaded = sessionId;
    }

    void setSessions(List<SessionSummary> list) {
        sessions = list;
        sessionModel.setRowCount(0);
        for (SessionSummary s : list) {
            sessionModel.addRow(new Object[]{s.startedAt(), s.quizTitle(), s.roomCode(), s.players(),
                    s.completed() ? "Completed" : "Ended early"});
        }
        rankingModel.setRowCount(0);
        chart.setStats(List.of());
        currentReport = null;
        exportButton.setEnabled(false);
        emailButton.setEnabled(false);
        insight.setText(list.isEmpty() ? "No games yet. Host a quiz first." : "Select a game to see its report.");
        int target = list.isEmpty() ? -1 : 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id() == selectWhenLoaded) {
                target = i;
            }
        }
        selectWhenLoaded = -1;
        if (target >= 0) {
            sessionTable.setRowSelectionInterval(target, target);
        }
    }

    void showReport(SessionReport report) {
        currentReport = report;
        rankingModel.setRowCount(0);
        for (ScoreEntry entry : report.ranking()) {
            rankingModel.addRow(new Object[]{entry.rank(), entry.name(), entry.score()});
        }
        chart.setStats(report.questions());
        insight.setText(hardestQuestion(report));
        exportButton.setEnabled(true);
        emailButton.setEnabled(true);
    }

    SessionReport currentReportForTest() {
        return currentReport;
    }

    /** Finds the question to reteach: the lowest percentage correct among those answered. */
    static String hardestQuestion(SessionReport report) {
        return report.questions().stream()
                .filter(q -> q.answered() > 0)
                .min(Comparator.comparingInt(Widgets.PercentChart::percent))
                .map(q -> "Worth reteaching: question " + q.number() + " (" + Widgets.PercentChart.percent(q)
                        + "% correct)")
                .orElse("No answers were recorded in this game.");
    }

    private void export() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("classpulse-" + currentReport.summary().roomCode() + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        try {
            Files.write(chooser.getSelectedFile().toPath(), Csv.reportLines(currentReport), StandardCharsets.UTF_8);
            app.toast("Saved " + chooser.getSelectedFile().getName(), ToastLayer.Kind.SUCCESS);
        } catch (IOException e) {
            app.toast("Could not save the file: " + e.getMessage(), ToastLayer.Kind.ERROR);
        }
    }

    static int percent(QuestionStat q) {
        return Widgets.PercentChart.percent(q);
    }
}
