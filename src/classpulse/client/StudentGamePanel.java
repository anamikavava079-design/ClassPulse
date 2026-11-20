package classpulse.client;

import classpulse.common.Message.AnswerReceived;
import classpulse.common.Message.QuestionEnded;
import classpulse.common.Message.QuestionStarted;
import classpulse.common.Message.SubmitAnswer;
import classpulse.common.Question;
import classpulse.common.Views.PersonalResult;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;

/** The student's game screen, which moves between waiting, question and result views. */
final class StudentGamePanel extends JPanel {
    private static final String MESSAGE_VIEW = "message";
    private static final String QUESTION_VIEW = "question";

    private final AppFrame app;
    private final CardLayout views = new CardLayout();

    private final Badge badge = new Badge();
    private final JLabel messageTitle = Theme.label(" ", Font.BOLD, 34, Theme.INK);
    private final JLabel messageDetail = Theme.label(" ", Font.PLAIN, 18, Theme.MUTED);
    private final JLabel messageExtra = Theme.label(" ", Font.BOLD, 17, Theme.PRIMARY);
    private final JLabel streakLabel = Theme.pill(" ", new Color(0xFFEDD5), new Color(0xC2410C));

    private final JLabel progressLabel = Theme.pill(" ", Theme.PRIMARY_SOFT, Theme.PRIMARY_DARK);
    private final JTextArea questionText = Theme.wrapped(" ", Font.BOLD, 26, Theme.INK);
    private final CountdownBar countdown = new CountdownBar();
    private final JPanel optionGrid = new JPanel(new GridLayout(2, 2, 14, 14));
    final List<JButton> optionButtons = new ArrayList<>();

    private int currentIndex;
    private String quizTitle = "";

    StudentGamePanel(AppFrame app) {
        this.app = app;
        setLayout(views);
        setBackground(Theme.BACKGROUND);
        add(messageView(), MESSAGE_VIEW);
        add(questionView(), QUESTION_VIEW);
    }

    private JPanel messageView() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(Theme.BACKGROUND);
        JPanel card = new Theme.RoundPanel(Theme.CARD, 28, true);
        card.setLayout(new BorderLayout(0, 18));
        card.setBorder(BorderFactory.createEmptyBorder(34, 48, 34, 48));
        JPanel badgeRow = Theme.transparent(new FlowLayout(FlowLayout.CENTER));
        badgeRow.add(badge);
        card.add(badgeRow, BorderLayout.NORTH);
        JPanel stack = Theme.transparent(new GridLayout(3, 1, 0, 8));
        for (JLabel label : List.of(messageTitle, messageDetail, messageExtra)) {
            label.setHorizontalAlignment(SwingConstants.CENTER);
            stack.add(label);
        }
        card.add(stack, BorderLayout.CENTER);
        JPanel streakRow = Theme.transparent(new FlowLayout(FlowLayout.CENTER));
        streakLabel.setIcon(Icons.of(Icons.Kind.FIRE, 14, new Color(0xEA580C)));
        streakLabel.setFont(Theme.font(Font.BOLD, 14));
        streakRow.add(streakLabel);
        card.add(streakRow, BorderLayout.SOUTH);
        card.setPreferredSize(new Dimension(560, 360));
        page.add(card);
        return page;
    }

    private JPanel questionView() {
        JPanel page = new JPanel(new BorderLayout(0, 20));
        page.setBackground(Theme.BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(26, 32, 28, 32));
        JPanel north = Theme.transparent(new BorderLayout(0, 12));
        JPanel meta = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        meta.add(progressLabel);
        north.add(meta, BorderLayout.NORTH);
        north.add(questionText, BorderLayout.CENTER);
        north.add(countdown, BorderLayout.SOUTH);
        page.add(north, BorderLayout.NORTH);
        optionGrid.setOpaque(false);
        page.add(optionGrid, BorderLayout.CENTER);
        return page;
    }

    private void showMessage(Badge.Kind kind, String title, Color color, String detail, String extra, String streak) {
        badge.setKind(kind);
        messageTitle.setText(title);
        messageTitle.setForeground(color);
        messageDetail.setText(detail);
        messageExtra.setText(extra);
        streakLabel.setText(streak == null ? "" : streak);
        streakLabel.setVisible(streak != null);
        views.show(this, MESSAGE_VIEW);
    }

    void joined(String title) {
        quizTitle = title;
        showMessage(Badge.Kind.WAITING, "You're in!", Theme.PRIMARY, "Waiting for the teacher to start " + title + ".",
                " ", null);
    }

    void setPlayers(int count) {
        messageExtra.setText(count + (count == 1 ? " student" : " students") + " in the room");
    }

    void startQuestion(QuestionStarted question) {
        currentIndex = question.index();
        progressLabel.setText(quizTitle + "   |   Question " + (question.index() + 1) + " of " + question.total());
        questionText.setText(question.text());
        optionGrid.removeAll();
        optionButtons.clear();
        for (int i = 0; i < Question.OPTION_COUNT; i++) {
            int choice = i;
            JButton button = Theme.option(i, question.options().get(i));
            button.addActionListener(e -> choose(choice));
            optionButtons.add(button);
            optionGrid.add(button);
        }
        optionGrid.revalidate();
        optionGrid.repaint();
        countdown.start(question.seconds());
        views.show(this, QUESTION_VIEW);
    }

    private void choose(int choice) {
        optionButtons.forEach(button -> button.setEnabled(false));
        app.send(new SubmitAnswer(currentIndex, choice));
    }

    void answerReceived(AnswerReceived answer) {
        if (answer.accepted()) {
            countdown.stop();
            showMessage(Badge.Kind.LOCKED, "Answer locked in", Theme.PRIMARY, "Waiting for the others to finish...",
                    " ", null);
        } else {
            showMessage(Badge.Kind.WRONG, "Not counted", Theme.WRONG, answer.text(), " ", null);
        }
    }

    void reveal(QuestionEnded result) {
        countdown.stop();
        PersonalResult you = result.you();
        String extra = "Total " + you.totalScore() + " points   |   Rank #" + you.rank();
        String correctLetter = Theme.OPTION_LETTERS[result.correctIndex()];
        String streak = you.streak() >= 2
                ? you.streak() + " in a row!" + (you.streakBonus() > 0 ? "  +" + you.streakBonus() + " streak bonus" : "")
                : null;
        if (!you.answered()) {
            showMessage(Badge.Kind.TIME, "Time's up", Theme.WARN, "The correct answer was " + correctLetter + ".", extra, null);
        } else if (you.correct()) {
            showMessage(Badge.Kind.RIGHT, "Correct!", Theme.CORRECT, "+" + you.pointsEarned() + " points", extra, streak);
        } else {
            showMessage(Badge.Kind.WRONG, "Not this time", Theme.WRONG, "The correct answer was " + correctLetter + ".",
                    extra, null);
        }
    }

    void stop() {
        countdown.stop();
    }

    String messageTitleForTest() {
        return messageTitle.getText();
    }

    /** A large round symbol: a tick, a cross, a clock, or a pulse while waiting. */
    private static final class Badge extends JComponent {
        enum Kind { WAITING, LOCKED, RIGHT, WRONG, TIME }

        private Kind kind = Kind.WAITING;

        Badge() {
            setPreferredSize(new Dimension(96, 96));
        }

        void setKind(Kind kind) {
            this.kind = kind;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            Color color = switch (kind) {
                case RIGHT -> Theme.CORRECT;
                case WRONG -> Theme.WRONG;
                case TIME -> Theme.WARN;
                default -> Theme.PRIMARY;
            };
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
            g.fillOval(0, 0, 96, 96);
            g.setColor(color);
            g.fillOval(14, 14, 68, 68);
            g.translate(28, 28);
            g.scale(40 / 24.0, 40 / 24.0);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Icons.draw(g, switch (kind) {
                case RIGHT -> Icons.Kind.CHECK;
                case WRONG -> Icons.Kind.CROSS;
                case TIME -> Icons.Kind.CLOCK;
                case LOCKED -> Icons.Kind.LOCK;
                case WAITING -> Icons.Kind.USERS;
            });
            g.dispose();
        }
    }
}
