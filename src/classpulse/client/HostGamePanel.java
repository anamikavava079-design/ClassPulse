package classpulse.client;

import classpulse.common.Message.CloseRoom;
import classpulse.common.Message.NextStep;
import classpulse.common.Message.QuestionEnded;
import classpulse.common.Message.QuestionStarted;
import classpulse.common.Views.ScoreEntry;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/** What the teacher projects during a question: live progress, then the results chart. */
final class HostGamePanel extends JPanel {
    private final JLabel progressLabel = Theme.pill(" ", Theme.PRIMARY_SOFT, Theme.PRIMARY_DARK);
    private final JLabel answeredLabel = Theme.pill(" ", Theme.CORRECT_SOFT, Theme.CORRECT);
    private final JTextArea questionText = Theme.wrapped(" ", Font.BOLD, 26, Theme.INK);
    private final CountdownBar countdown = new CountdownBar();
    final BarChart chart = new BarChart();
    private final JPanel topPanel = new JPanel();
    final JButton nextButton = Theme.primary("Next question");

    HostGamePanel(AppFrame app) {
        setLayout(new BorderLayout(0, 16));
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(26, 32, 24, 32));

        JPanel north = Theme.transparent(new BorderLayout(0, 12));
        JPanel meta = Theme.transparent(new BorderLayout());
        JPanel left = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(progressLabel);
        meta.add(left, BorderLayout.WEST);
        JPanel right = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.add(answeredLabel);
        meta.add(right, BorderLayout.EAST);
        north.add(meta, BorderLayout.NORTH);
        north.add(questionText, BorderLayout.CENTER);
        north.add(countdown, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        JPanel center = Theme.transparent(new BorderLayout(20, 0));
        JPanel chartCard = Theme.card();
        chartCard.setLayout(new BorderLayout());
        chartCard.add(chart, BorderLayout.CENTER);
        center.add(chartCard, BorderLayout.CENTER);

        JPanel scoreCard = Theme.card();
        scoreCard.setLayout(new BorderLayout(0, 12));
        scoreCard.setPreferredSize(new Dimension(270, 200));
        JLabel heading = Theme.label("Top scores", Font.BOLD, 16, Theme.INK);
        heading.setIcon(Icons.of(Icons.Kind.TROPHY, 18, new java.awt.Color(0xF59E0B)));
        heading.setIconTextGap(8);
        scoreCard.add(heading, BorderLayout.NORTH);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setOpaque(false);
        scoreCard.add(topPanel, BorderLayout.CENTER);
        center.add(scoreCard, BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        JButton close = Theme.secondary("End quiz early");
        buttons.add(close);
        buttons.add(Theme.height(nextButton, 46));
        add(buttons, BorderLayout.SOUTH);

        nextButton.addActionListener(e -> {
            nextButton.setEnabled(false);
            app.send(new NextStep());
        });
        close.addActionListener(e -> app.send(new CloseRoom()));
    }

    void startQuestion(QuestionStarted question) {
        progressLabel.setText("Question " + (question.index() + 1) + " of " + question.total());
        questionText.setText(question.text());
        answeredLabel.setText("Waiting for answers");
        chart.showOptions(question.options());
        countdown.start(question.seconds());
        topPanel.removeAll();
        topPanel.add(Theme.label("Scores appear when the question ends.", Font.PLAIN, 13, Theme.MUTED));
        topPanel.revalidate();
        topPanel.repaint();
        nextButton.setEnabled(false);
        nextButton.setText("Next question");
    }

    void setProgress(int answered, int total) {
        answeredLabel.setText(answered + " of " + total + " answered");
    }

    void reveal(QuestionEnded result) {
        countdown.stop();
        chart.showResults(result.counts(), result.correctIndex());
        topPanel.removeAll();
        for (ScoreEntry entry : result.top()) {
            JPanel row = Theme.transparent(new BorderLayout(10, 0));
            JLabel name = Theme.label(entry.rank() + ".   " + entry.name(), Font.BOLD, 15, Theme.INK);
            name.setIcon(Theme.avatar(entry.name(), 26));
            name.setIconTextGap(10);
            row.add(name, BorderLayout.CENTER);
            row.add(Theme.label(String.valueOf(entry.score()), Font.BOLD, 15, Theme.PRIMARY), BorderLayout.EAST);
            row.setAlignmentX(LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            topPanel.add(row);
            topPanel.add(Box.createVerticalStrut(8));
        }
        topPanel.revalidate();
        topPanel.repaint();
        boolean last = result.index() + 1 == result.total();
        nextButton.setText(last ? "Show final results" : "Next question");
        nextButton.setEnabled(true);
    }

    void stop() {
        countdown.stop();
    }
}
