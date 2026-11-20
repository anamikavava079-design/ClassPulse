package classpulse.client;

import classpulse.common.Message.SaveQuiz;
import classpulse.common.Message.UpdateQuiz;
import classpulse.common.Question;
import classpulse.common.Quiz;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;

/**
 * Creates or edits a quiz inside the main window (no pop-up). Questions are listed on the
 * left; clicking one loads it into the form on the right to change it. The radio button
 * next to an option marks it as the correct answer.
 */
final class QuizEditorPanel extends JPanel {
    private final AppFrame app;
    private final TeacherShell shell;
    private final JLabel heading = Widgets.pageTitle("New quiz");
    private final JLabel subheading = Widgets.pageSubtitle(" ");
    final JTextField titleField = Theme.field(30, "Quiz title, e.g. Java Basics");
    final JTextField questionField = Theme.field(30, "Type the question");
    final List<JTextField> optionFields = new ArrayList<>();
    final List<JRadioButton> correctButtons = new ArrayList<>();
    private final ButtonGroup correctGroup = new ButtonGroup();
    final JSpinner secondsSpinner = new JSpinner(new SpinnerNumberModel(20, Question.MIN_SECONDS, Question.MAX_SECONDS, 5));
    final List<Question> questions = new ArrayList<>();
    private final DefaultListModel<Question> listModel = new DefaultListModel<>();
    final JList<Question> questionList = new JList<>(listModel);
    private final JLabel countLabel = Theme.label("Questions (0)", Font.BOLD, 16, Theme.INK);
    final JButton addButton = Theme.withIcon(Theme.primary("Add question"), Icons.Kind.PLUS);
    final JButton updateButton = Theme.withIcon(Theme.secondary("Update question"), Icons.Kind.CHECK);
    final JButton saveButton = Theme.withIcon(Theme.primary("Save quiz"), Icons.Kind.CHECK);
    private Long editingId;

    QuizEditorPanel(AppFrame app, TeacherShell shell) {
        this.app = app;
        this.shell = shell;
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        JButton cancel = Theme.secondary("Cancel");
        cancel.addActionListener(e -> shell.open(TeacherShell.QUIZZES));
        JPanel top = Theme.transparent(new BorderLayout(0, 16));
        top.add(Widgets.header(heading, subheading, cancel, saveButton), BorderLayout.NORTH);
        top.add(titleField, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        JPanel body = Theme.transparent(new BorderLayout(20, 0));
        body.add(listCard(), BorderLayout.WEST);
        body.add(formCard(), BorderLayout.CENTER);
        add(body, BorderLayout.CENTER);

        saveButton.addActionListener(e -> save());
        addButton.addActionListener(e -> addQuestion());
        updateButton.addActionListener(e -> updateQuestion());
        questionList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelected();
            }
        });
    }

    private JPanel listCard() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 12));
        card.setPreferredSize(new Dimension(330, 300));
        card.add(countLabel, BorderLayout.NORTH);
        questionList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        questionList.setCellRenderer(new QuestionRenderer());
        questionList.setFixedCellHeight(56);
        Theme.fitWidth(questionList);
        card.add(Theme.scroll(questionList), BorderLayout.CENTER);
        JPanel buttons = Theme.transparent(new GridLayout(1, 3, 8, 0));
        JButton up = Theme.secondary("Up");
        JButton down = Theme.secondary("Down");
        JButton remove = Theme.danger("Remove");
        up.addActionListener(e -> move(-1));
        down.addActionListener(e -> move(1));
        remove.addActionListener(e -> removeSelected());
        buttons.add(up);
        buttons.add(down);
        buttons.add(remove);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private JPanel formCard() {
        JPanel card = Theme.card();
        card.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 3;
        c.gridy = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(Theme.label("Question", Font.BOLD, 13, Theme.MUTED), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 16, 0);
        card.add(questionField, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 6, 0);
        card.add(Theme.label("Options  (choose the correct one on the right)", Font.BOLD, 13, Theme.MUTED), c);
        for (int i = 0; i < Question.OPTION_COUNT; i++) {
            c.gridy++;
            c.gridwidth = 1;
            c.insets = new Insets(4, 0, 4, 10);
            c.weightx = 0;
            c.gridx = 0;
            card.add(chip(i), c);
            JTextField field = Theme.field(20, "Option " + Theme.OPTION_LETTERS[i]);
            optionFields.add(field);
            c.gridx = 1;
            c.weightx = 1;
            card.add(field, c);
            JRadioButton correct = new JRadioButton("Correct");
            correct.setOpaque(false);
            correct.setFont(Theme.font(Font.BOLD, 13));
            correct.setForeground(Theme.CORRECT);
            correct.setFocusPainted(false);
            correctGroup.add(correct);
            correctButtons.add(correct);
            c.gridx = 2;
            c.weightx = 0;
            c.insets = new Insets(4, 0, 4, 0);
            card.add(correct, c);
        }
        correctButtons.get(0).setSelected(true);
        c.gridx = 0;
        c.gridwidth = 3;
        c.gridy++;
        c.insets = new Insets(16, 0, 0, 0);
        JPanel time = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel timeLabel = Theme.label("Time limit", Font.BOLD, 13, Theme.MUTED);
        timeLabel.setIcon(Icons.of(Icons.Kind.CLOCK, 16, Theme.MUTED));
        time.add(timeLabel);
        secondsSpinner.setFont(Theme.font(Font.PLAIN, 15));
        secondsSpinner.setPreferredSize(new Dimension(80, 36));
        time.add(secondsSpinner);
        time.add(Theme.label("seconds", Font.PLAIN, 13, Theme.MUTED));
        card.add(time, c);
        c.gridy++;
        c.insets = new Insets(20, 0, 0, 0);
        JPanel actions = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        actions.add(addButton);
        actions.add(javax.swing.Box.createHorizontalStrut(10));
        actions.add(updateButton);
        JButton clear = Theme.link("Clear form");
        clear.addActionListener(e -> {
            questionList.clearSelection();
            clearForm();
        });
        actions.add(javax.swing.Box.createHorizontalStrut(14));
        actions.add(clear);
        card.add(actions, c);
        c.gridy++;
        c.weighty = 1;
        card.add(Theme.transparent(null), c);
        return card;
    }

    private static JLabel chip(int index) {
        JLabel chip = new JLabel(Theme.OPTION_LETTERS[index], SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                Theme.smooth(g);
                g.setColor(Theme.OPTION_COLORS[index]);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        chip.setForeground(Color.WHITE);
        chip.setFont(Theme.font(Font.BOLD, 15));
        chip.setPreferredSize(new Dimension(36, 36));
        return chip;
    }

    /** Opens an empty editor for a new quiz. */
    void startNew() {
        editingId = null;
        heading.setText("New quiz");
        subheading.setText("Add at least one question, then save.");
        titleField.setText("");
        questions.clear();
        refreshList();
        clearForm();
    }

    /** Opens the editor with a saved quiz. Saving creates a new version; old reports stay as they were. */
    void startEdit(long quizId, Quiz quiz) {
        editingId = quizId;
        heading.setText("Edit quiz");
        subheading.setText("Saving keeps reports of past games exactly as they were.");
        titleField.setText(quiz.title());
        questions.clear();
        questions.addAll(quiz.questions());
        refreshList();
        clearForm();
    }

    private Question readForm() {
        int correct = 0;
        for (int i = 0; i < correctButtons.size(); i++) {
            if (correctButtons.get(i).isSelected()) {
                correct = i;
            }
        }
        List<String> options = optionFields.stream().map(JTextField::getText).toList();
        return new Question(questionField.getText(), options, correct, (Integer) secondsSpinner.getValue());
    }

    private void addQuestion() {
        try {
            if (questions.size() >= Quiz.MAX_QUESTIONS) {
                throw new IllegalArgumentException("A quiz can have at most " + Quiz.MAX_QUESTIONS + " questions.");
            }
            questions.add(readForm());
            refreshList();
            questionList.clearSelection();
            clearForm();
            questionField.requestFocusInWindow();
        } catch (IllegalArgumentException e) {
            app.toast(e.getMessage(), ToastLayer.Kind.ERROR);
        }
    }

    private void updateQuestion() {
        int index = questionList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        try {
            questions.set(index, readForm());
            refreshList();
            questionList.setSelectedIndex(index);
            app.toast("Question " + (index + 1) + " updated.", ToastLayer.Kind.SUCCESS);
        } catch (IllegalArgumentException e) {
            app.toast(e.getMessage(), ToastLayer.Kind.ERROR);
        }
    }

    private void loadSelected() {
        int index = questionList.getSelectedIndex();
        updateButton.setEnabled(index >= 0);
        if (index < 0) {
            return;
        }
        Question q = questions.get(index);
        questionField.setText(q.text());
        for (int i = 0; i < Question.OPTION_COUNT; i++) {
            optionFields.get(i).setText(q.options().get(i));
        }
        correctButtons.get(q.correctIndex()).setSelected(true);
        secondsSpinner.setValue(q.seconds());
    }

    private void move(int delta) {
        int index = questionList.getSelectedIndex();
        int target = index + delta;
        if (index < 0 || target < 0 || target >= questions.size()) {
            return;
        }
        Question moved = questions.remove(index);
        questions.add(target, moved);
        refreshList();
        questionList.setSelectedIndex(target);
    }

    private void removeSelected() {
        int index = questionList.getSelectedIndex();
        if (index < 0) {
            return;
        }
        questions.remove(index);
        refreshList();
        clearForm();
    }

    private void clearForm() {
        questionField.setText("");
        optionFields.forEach(field -> field.setText(""));
        correctButtons.get(0).setSelected(true);
        updateButton.setEnabled(questionList.getSelectedIndex() >= 0);
    }

    private void refreshList() {
        listModel.clear();
        listModel.addAll(questions);
        countLabel.setText("Questions (" + questions.size() + ")");
    }

    private void save() {
        try {
            Quiz quiz = new Quiz(titleField.getText(), questions);
            app.send(editingId == null ? new SaveQuiz(quiz) : new UpdateQuiz(editingId, quiz));
            shell.open(TeacherShell.QUIZZES);
        } catch (IllegalArgumentException e) {
            app.toast(e.getMessage(), ToastLayer.Kind.ERROR);
        }
    }

    /** One question in the list: its number, text, and time. */
    private static final class QuestionRenderer extends JPanel implements ListCellRenderer<Question> {
        private final JLabel number = Theme.label(" ", Font.BOLD, 13, Theme.PRIMARY_DARK);
        private final JLabel text = Theme.label(" ", Font.BOLD, 13, Theme.INK);
        private final JLabel detail = Theme.label(" ", Font.PLAIN, 12, Theme.MUTED);
        private boolean selected;

        QuestionRenderer() {
            setLayout(new BorderLayout(10, 0));
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            number.setHorizontalAlignment(SwingConstants.CENTER);
            number.setPreferredSize(new Dimension(28, 28));
            add(number, BorderLayout.WEST);
            JPanel words = Theme.transparent(new GridLayout(2, 1));
            words.add(text);
            words.add(detail);
            add(words, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Question> list, Question q, int index,
                                                      boolean isSelected, boolean focused) {
            selected = isSelected;
            number.setText(String.valueOf(index + 1));
            text.setText(q.text());
            detail.setText("Answer " + Theme.OPTION_LETTERS[q.correctIndex()] + ": " + q.options().get(q.correctIndex())
                    + "   |   " + q.seconds() + " s");
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            g.setColor(selected ? Theme.PRIMARY_SOFT : Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(selected ? Color.WHITE : Theme.PRIMARY_SOFT);
            g.fillOval(10, (getHeight() - 28) / 2, 28, 28);
            g.dispose();
        }
    }
}
