package classpulse.client;

import classpulse.common.Views.ScoreEntry;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/** The final results, used by both teacher and student screens: a podium and the full ranking. */
final class LeaderboardPanel extends JPanel {
    private final JLabel headline = Theme.label(" ", Font.BOLD, 30, Theme.INK);
    private final JLabel detail = Theme.label(" ", Font.PLAIN, 15, Theme.MUTED);
    private final JLabel note = Theme.pill(" ", Theme.PRIMARY_SOFT, Theme.PRIMARY_DARK);
    final Widgets.Podium podium = new Widgets.Podium();
    private final DefaultTableModel model = Theme.readOnlyModel("Rank", "Name", "Score");
    final JButton actionButton = Theme.primary(" ");
    private Runnable action = () -> { };

    LeaderboardPanel() {
        setLayout(new BorderLayout(0, 18));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(26, 32, 24, 32));

        JPanel heading = Theme.transparent(new BorderLayout(0, 6));
        headline.setIcon(Icons.of(Icons.Kind.TROPHY, 34, new java.awt.Color(0xF59E0B)));
        headline.setIconTextGap(12);
        heading.add(headline, BorderLayout.NORTH);
        heading.add(detail, BorderLayout.CENTER);
        JPanel noteRow = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 4));
        note.setIcon(Icons.of(Icons.Kind.MAIL, 14, Theme.PRIMARY_DARK));
        noteRow.add(note);
        heading.add(noteRow, BorderLayout.SOUTH);
        add(heading, BorderLayout.NORTH);

        JPanel center = Theme.transparent(new BorderLayout(20, 0));
        JPanel podiumCard = Theme.card();
        podiumCard.setLayout(new BorderLayout());
        podiumCard.add(podium);
        center.add(podiumCard, BorderLayout.CENTER);
        JTable table = Theme.table(model);
        table.getColumnModel().getColumn(0).setMaxWidth(60);
        table.getColumnModel().getColumn(2).setMaxWidth(80);
        JPanel tableCard = Widgets.titledCard("Full ranking", Theme.scroll(table));
        tableCard.setPreferredSize(new java.awt.Dimension(320, 300));
        center.add(tableCard, BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttons.add(Theme.height(actionButton, 46));
        add(buttons, BorderLayout.SOUTH);
        actionButton.addActionListener(e -> action.run());
    }

    @Override
    protected void paintComponent(java.awt.Graphics graphics) {
        Theme.paintBackdrop(this, graphics);
    }

    void show(String title, String subtitle, String noteText, List<ScoreEntry> ranking, String buttonText,
              Runnable onClick) {
        headline.setText(title);
        detail.setText(subtitle);
        note.setText(noteText == null ? "" : noteText);
        note.setVisible(noteText != null);
        podium.setRanking(ranking);
        model.setRowCount(0);
        for (ScoreEntry entry : ranking) {
            model.addRow(new Object[]{"#" + entry.rank(), entry.name(), entry.score()});
        }
        actionButton.setText(buttonText);
        action = onClick;
    }

    String headlineText() {
        return headline.getText();
    }
}
