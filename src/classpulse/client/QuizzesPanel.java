package classpulse.client;

import classpulse.common.Csv;
import classpulse.common.Message.CreateRoom;
import classpulse.common.Message.DeleteQuiz;
import classpulse.common.Message.DuplicateQuiz;
import classpulse.common.Message.GetQuiz;
import classpulse.common.Message.SaveQuiz;
import classpulse.common.Quiz;
import classpulse.common.Views.QuizSummary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;

/** The quiz library: pick a quiz on the left, act on it on the right. */
final class QuizzesPanel extends JPanel {
    private final AppFrame app;
    private final TeacherShell shell;
    final DefaultListModel<QuizSummary> quizModel = new DefaultListModel<>();
    final JList<QuizSummary> quizList = new JList<>(quizModel);
    private final JTextArea selectedTitle = Theme.wrapped("Select a quiz", Font.BOLD, 20, Theme.INK);
    private final JLabel selectedMeta = Theme.label(" ", Font.PLAIN, 13, Theme.MUTED);
    private final JLabel selectedPlays = Theme.label(" ", Font.PLAIN, 13, Theme.MUTED);
    final JCheckBox shuffle = new JCheckBox("Shuffle question order");
    final JButton hostButton = Theme.withIcon(Theme.primary("Host live"), Icons.Kind.PLAY);
    final JButton editButton = Theme.withIcon(Theme.secondary("Edit"), Icons.Kind.EDIT);
    final JButton duplicateButton = Theme.withIcon(Theme.secondary("Duplicate"), Icons.Kind.COPY);
    final JButton deleteButton = Theme.withIcon(Theme.danger("Delete"), Icons.Kind.TRASH);

    QuizzesPanel(AppFrame app, TeacherShell shell) {
        this.app = app;
        this.shell = shell;
        setLayout(new BorderLayout(0, 22));
        setOpaque(false);
        JButton importCsv = Theme.withIcon(Theme.secondary("Import CSV"), Icons.Kind.UPLOAD);
        JButton create = Theme.withIcon(Theme.primary("New quiz"), Icons.Kind.PLUS);
        add(Widgets.header(Widgets.pageTitle("My quizzes"),
                Widgets.pageSubtitle("Create, edit and host your quizzes. Editing keeps old reports intact."),
                importCsv, create), BorderLayout.NORTH);

        quizList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        quizList.setFixedCellHeight(74);
        Theme.fitWidth(quizList);
        quizList.setCellRenderer(new QuizRenderer());
        JPanel listCard = Theme.card();
        listCard.setLayout(new BorderLayout());
        listCard.add(Theme.scroll(quizList));
        JPanel center = Theme.transparent(new BorderLayout(20, 0));
        center.add(listCard, BorderLayout.CENTER);
        center.add(detailCard(), BorderLayout.EAST);
        add(center, BorderLayout.CENTER);

        create.addActionListener(e -> {
            shell.editor.startNew();
            shell.open(TeacherShell.EDITOR);
        });
        importCsv.addActionListener(e -> importCsvFile());
        quizList.addListSelectionListener(e -> showSelected());
        hostButton.addActionListener(e -> withSelected(q -> {
            app.busy(hostButton);
            app.send(new CreateRoom(q.id(), shuffle.isSelected()));
        }));
        editButton.addActionListener(e -> withSelected(q -> app.send(new GetQuiz(q.id()))));
        duplicateButton.addActionListener(e -> withSelected(q -> app.send(new DuplicateQuiz(q.id()))));
        deleteButton.addActionListener(e -> withSelected(this::confirmDelete));
        showSelected();
    }

    private JPanel detailCard() {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 16));
        card.setPreferredSize(new Dimension(300, 400));
        JPanel top = Theme.transparent(new BorderLayout(0, 8));
        top.add(selectedTitle, BorderLayout.NORTH);
        JPanel meta = Theme.transparent(new GridLayout(2, 1, 0, 2));
        meta.add(selectedMeta);
        meta.add(selectedPlays);
        top.add(meta, BorderLayout.CENTER);
        card.add(top, BorderLayout.NORTH);

        JPanel buttons = Theme.transparent(new GridLayout(5, 1, 0, 10));
        shuffle.setOpaque(false);
        shuffle.setFont(Theme.font(Font.BOLD, 14));
        shuffle.setForeground(Theme.INK);
        shuffle.setIcon(Icons.of(Icons.Kind.SHUFFLE, 18, Theme.SUBTLE));
        shuffle.setSelectedIcon(Icons.of(Icons.Kind.SHUFFLE, 18, Theme.PRIMARY));
        shuffle.setIconTextGap(10);
        shuffle.setFocusPainted(false);
        buttons.add(shuffle);
        buttons.add(Theme.height(hostButton, 48));
        buttons.add(editButton);
        buttons.add(duplicateButton);
        buttons.add(deleteButton);
        card.add(buttons, BorderLayout.SOUTH);
        return card;
    }

    private void withSelected(java.util.function.Consumer<QuizSummary> action) {
        QuizSummary selected = quizList.getSelectedValue();
        if (selected == null) {
            app.toast("Select a quiz first.", ToastLayer.Kind.ERROR);
            return;
        }
        action.accept(selected);
    }

    private void confirmDelete(QuizSummary quiz) {
        int answer = JOptionPane.showConfirmDialog(this, "Delete \"" + quiz.title() + "\"?\n"
                        + "Reports of games already played with it are kept.", "Delete quiz",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            app.send(new DeleteQuiz(quiz.id()));
        }
    }

    private void showSelected() {
        QuizSummary quiz = quizList.getSelectedValue();
        boolean any = quiz != null;
        for (JButton button : new JButton[]{hostButton, editButton, duplicateButton, deleteButton}) {
            button.setEnabled(any);
        }
        shuffle.setEnabled(any);
        selectedTitle.setText(any ? quiz.title() : quizModel.isEmpty() ? "No quizzes yet" : "Select a quiz");
        selectedMeta.setText(any ? quiz.questionCount() + " questions  |  created " + quiz.createdAt()
                : quizModel.isEmpty() ? "Create one, or import a CSV file." : " ");
        selectedPlays.setText(any ? (quiz.timesPlayed() == 0 ? "Not played yet"
                : "Played " + quiz.timesPlayed() + (quiz.timesPlayed() == 1 ? " time" : " times")) : " ");
    }

    void setQuizzes(List<QuizSummary> quizzes) {
        long previous = quizList.getSelectedValue() == null ? -1 : quizList.getSelectedValue().id();
        quizModel.clear();
        quizModel.addAll(quizzes);
        int index = 0;
        for (int i = 0; i < quizzes.size(); i++) {
            if (quizzes.get(i).id() == previous) {
                index = i;
            }
        }
        if (!quizzes.isEmpty()) {
            quizList.setSelectedIndex(index);
        }
        showSelected();
    }

    void importCsvFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        importFile(chooser.getSelectedFile());
    }

    void importFile(File file) {
        String title = file.getName().replaceFirst("(?i)\\.csv$", "").replace('-', ' ');
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            Quiz quiz = Csv.parseQuiz(title, lines);
            app.send(new SaveQuiz(quiz));
        } catch (IOException e) {
            app.toast("Could not read the file: " + e.getMessage(), ToastLayer.Kind.ERROR);
        } catch (IllegalArgumentException e) {
            app.toast("This CSV file has a problem. " + e.getMessage(), ToastLayer.Kind.ERROR);
        }
    }

    /** Draws one quiz as a card row: a coloured initial, the title, and its details. */
    private static final class QuizRenderer extends JPanel implements ListCellRenderer<QuizSummary> {
        private final JLabel title = Theme.label(" ", Font.BOLD, 15, Theme.INK);
        private final JLabel meta = Theme.label(" ", Font.PLAIN, 13, Theme.MUTED);
        private boolean selected;
        private int index;

        QuizRenderer() {
            setLayout(new BorderLayout(14, 0));
            setBorder(BorderFactory.createEmptyBorder(12, 72, 12, 16));
            JPanel words = Theme.transparent(new GridLayout(2, 1, 0, 2));
            words.add(title);
            words.add(meta);
            add(words, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends QuizSummary> list, QuizSummary quiz, int index,
                                                      boolean isSelected, boolean focused) {
            this.selected = isSelected;
            this.index = index;
            title.setText(quiz.title());
            meta.setText(quiz.questionCount() + " questions   |   "
                    + (quiz.timesPlayed() == 0 ? "never played" : "played " + quiz.timesPlayed() + "x")
                    + "   |   " + quiz.createdAt());
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            g.setColor(selected ? Theme.PRIMARY_SOFT : Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (selected) {
                g.setColor(Theme.PRIMARY);
                g.fillRoundRect(0, 10, 4, getHeight() - 20, 4, 4);
            }
            g.setColor(new Color(0xF0F1F4));
            g.drawLine(16, getHeight() - 1, getWidth() - 16, getHeight() - 1);
            Color tint = Theme.OPTION_COLORS[index % Theme.OPTION_COLORS.length];
            g.setColor(new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), 30));
            g.fillRoundRect(18, 14, 44, 44, 12, 12);
            g.setColor(tint);
            g.setFont(Theme.font(Font.BOLD, 18));
            String letter = title.getText().isEmpty() ? "?" : title.getText().substring(0, 1).toUpperCase();
            g.drawString(letter, 40 - g.getFontMetrics().stringWidth(letter) / 2, 43);
            g.dispose();
        }
    }
}
