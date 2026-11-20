package classpulse.client;

import classpulse.common.Message.HomeworkDone;
import classpulse.common.Message.HomeworkStarted;
import classpulse.common.Message.SubmitHomework;
import classpulse.common.Question;
import classpulse.common.Views.HomeworkQuestion;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.Timer;

/**
 * Answering homework (added in the homework upgrade). Questions come one at a time with a
 * timer, like a live game, but at the student's own pace. The answers are sent together at
 * the end; only the server knows the correct answers, and it marks them.
 */
final class HomeworkPlayPanel extends JPanel {
    private static final String QUESTION_VIEW = "question";
    private static final String RESULT_VIEW = "result";

    private final AppFrame app;
    private final CardLayout views = new CardLayout();

    private final JLabel progressLabel = Theme.pill(" ", Theme.PRIMARY_SOFT, Theme.PRIMARY_DARK);
    private final JTextArea questionText = Theme.wrapped(" ", Font.BOLD, 26, Theme.INK);
    private final CountdownBar countdown = new CountdownBar();
    private final JPanel optionGrid = new JPanel(new GridLayout(2, 2, 14, 14));
    /** The four answer buttons of the current question (the GUI walkthrough clicks them). */
    final List<JButton> optionButtons = new ArrayList<>();
    private final Timer timeUp;

    private final JLabel resultTitle = Theme.label(" ", Font.BOLD, 30, Theme.INK);
    private final JLabel resultDetail = Theme.label(" ", Font.PLAIN, 16, Theme.MUTED);
    private final JTextArea resultLines = Theme.wrapped(" ", Font.PLAIN, 15, Theme.INK);

    private long homeworkId;
    private String title = "";
    private List<HomeworkQuestion> questions = List.of();
    private final List<Integer> choices = new ArrayList<>();
    private boolean submitted;
    private boolean resultShown;

    HomeworkPlayPanel(AppFrame app) {
        this.app = app;
        setLayout(views);
        setBackground(Theme.BACKGROUND);
        timeUp = new Timer(1000, e -> answer(-1));
        timeUp.setRepeats(false);
        add(questionView(), QUESTION_VIEW);
        add(resultView(), RESULT_VIEW);
    }

    private JPanel questionView() {
        JPanel page = new JPanel(new BorderLayout(0, 20));
        page.setBackground(Theme.BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(26, 32, 28, 32));
        JPanel north = Theme.transparent(new BorderLayout(0, 12));
        JPanel meta = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        progressLabel.setIcon(Icons.of(Icons.Kind.CLOCK, 14, Theme.PRIMARY_DARK));
        meta.add(progressLabel);
        north.add(meta, BorderLayout.NORTH);
        north.add(questionText, BorderLayout.CENTER);
        north.add(countdown, BorderLayout.SOUTH);
        page.add(north, BorderLayout.NORTH);
        optionGrid.setOpaque(false);
        page.add(optionGrid, BorderLayout.CENTER);
        return page;
    }

    private JPanel resultView() {
        JPanel page = new JPanel(new BorderLayout(0, 18));
        page.setBackground(Theme.BACKGROUND);
        page.setBorder(BorderFactory.createEmptyBorder(28, 32, 26, 32));
        JPanel top = Theme.transparent(new BorderLayout(0, 6));
        resultTitle.setIcon(Icons.of(Icons.Kind.CHECK, 30, Theme.CORRECT));
        resultTitle.setIconTextGap(12);
        top.add(resultTitle, BorderLayout.NORTH);
        top.add(resultDetail, BorderLayout.CENTER);
        JLabel emailed = Theme.pill("A copy with every answer is on its way to your email.", Theme.PRIMARY_SOFT,
                Theme.PRIMARY_DARK);
        emailed.setIcon(Icons.of(Icons.Kind.MAIL, 14, Theme.PRIMARY_DARK));
        JPanel pillRow = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 4));
        pillRow.add(emailed);
        top.add(pillRow, BorderLayout.SOUTH);
        page.add(top, BorderLayout.NORTH);
        resultLines.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        page.add(Widgets.titledCard("Your answers", Theme.scroll(resultLines)), BorderLayout.CENTER);
        JPanel buttons = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton back = Theme.height(Theme.primary("Back to homework"), 46);
        back.addActionListener(e -> app.backToHomework());
        buttons.add(back);
        page.add(buttons, BorderLayout.SOUTH);
        return page;
    }

    void begin(HomeworkStarted started) {
        homeworkId = started.homeworkId();
        title = started.title();
        questions = started.questions();
        choices.clear();
        submitted = false;
        resultShown = false;
        showQuestion(0);
        views.show(this, QUESTION_VIEW);
    }

    private void showQuestion(int index) {
        HomeworkQuestion question = questions.get(index);
        progressLabel.setText(title + "   |   Homework question " + (index + 1) + " of " + questions.size());
        questionText.setText(question.text());
        optionGrid.removeAll();
        optionButtons.clear();
        optionGrid.setLayout(new GridLayout(2, 2, 14, 14));
        for (int i = 0; i < Question.OPTION_COUNT; i++) {
            int choice = i;
            JButton button = Theme.option(i, question.options().get(i));
            button.addActionListener(e -> answer(choice));
            optionGrid.add(button);
            optionButtons.add(button);
        }
        optionGrid.revalidate();
        optionGrid.repaint();
        countdown.start(question.seconds());
        timeUp.setInitialDelay(question.seconds() * 1000);
        timeUp.restart();
    }

    /** choice -1 means the time ran out. */
    private void answer(int choice) {
        if (submitted || choices.size() >= questions.size()) {
            return;
        }
        timeUp.stop();
        choices.add(choice);
        if (choices.size() < questions.size()) {
            showQuestion(choices.size());
            return;
        }
        submitted = true;
        countdown.stop();
        optionGrid.removeAll();
        JLabel sending = Theme.label("Handing in your answers...", Font.BOLD, 20, Theme.PRIMARY);
        sending.setHorizontalAlignment(JLabel.CENTER);
        optionGrid.setLayout(new BorderLayout());
        optionGrid.add(sending);
        optionGrid.revalidate();
        optionGrid.repaint();
        app.send(new SubmitHomework(homeworkId, List.copyOf(choices)));
    }

    void showResult(HomeworkDone done) {
        stop();
        int total = done.questions().size();
        int percent = total == 0 ? 0 : Math.round(100f * done.correct() / total);
        resultTitle.setText("Homework handed in: " + done.correct() + " of " + total + " correct");
        resultTitle.setForeground(percent >= 50 ? Theme.CORRECT : Theme.WARN);
        resultDetail.setText(done.title() + "   |   " + percent + "%");
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < total; i++) {
            HomeworkQuestion q = done.questions().get(i);
            int yours = done.choices().get(i);
            int right = done.correctChoices().get(i);
            text.append(i + 1).append(". ").append(q.text()).append('\n');
            text.append("    Your answer: ").append(yours < 0 ? "(no answer)" : letter(yours) + ". " + q.options().get(yours))
                    .append(yours == right ? "   (correct)" : "   (wrong)").append('\n');
            if (yours != right) {
                text.append("    Correct: ").append(letter(right)).append(". ").append(q.options().get(right)).append('\n');
            }
            text.append('\n');
        }
        resultLines.setText(text.toString().strip());
        resultLines.setCaretPosition(0);
        views.show(this, RESULT_VIEW);
        resultShown = true;
    }

    boolean resultShown() {
        return resultShown;
    }

    /** If the server refused the hand-in, let the student go back instead of waiting forever. */
    void submitFailed() {
        if (submitted) {
            stop();
            app.backToHomework();
        }
    }

    boolean waitingForResult() {
        return submitted;
    }

    void stop() {
        timeUp.stop();
        countdown.stop();
    }

    private static String letter(int index) {
        return String.valueOf((char) ('A' + index));
    }
}
