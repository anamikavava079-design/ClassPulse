package classpulse.client;

import classpulse.common.Message.AssignHomework;
import classpulse.common.Message.CreateClass;
import classpulse.common.Views.ClassInfo;
import classpulse.common.Views.HomeworkInfo;
import classpulse.common.Views.QuizSummary;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

/**
 * The teacher's "Classes & homework" page (added in the homework upgrade): make a class and
 * share its code, set a quiz as homework with a deadline, and follow who has handed it in.
 * Reminder and summary emails are sent by the server on its own.
 */
final class TeacherHomeworkPanel extends JPanel {
    /** Combo box entries show a name but carry an id. */
    private record Choice(long id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final AppFrame app;
    final DefaultTableModel classModel = Theme.readOnlyModel("Class", "Join code", "Students");
    final DefaultTableModel homeworkModel =
            Theme.readOnlyModel("Class", "Quiz", "Due", "Handed in", "Average", "Status");
    final JTextField classNameField = Theme.field(14, "e.g. CSE-A Java");
    final JButton createClassButton = Theme.withIcon(Theme.primary("Create class"), Icons.Kind.PLUS);
    final JComboBox<Choice> classBox = new JComboBox<>();
    final JComboBox<Choice> quizBox = new JComboBox<>();
    final JSpinner dueAmount = new JSpinner(new SpinnerNumberModel(2, 1, 720, 1));
    final JComboBox<String> dueUnit = new JComboBox<>(new String[]{"days", "hours", "minutes"});
    final JButton assignButton = Theme.withIcon(Theme.primary("Set homework"), Icons.Kind.SEND);
    private final JLabel classHint = Theme.label("Students join with the code from My classes.", Font.PLAIN, 12,
            Theme.MUTED);

    TeacherHomeworkPanel(AppFrame app) {
        this.app = app;
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        add(Widgets.header(Widgets.pageTitle("Classes & homework"),
                Widgets.pageSubtitle("Set a quiz as homework. Students are emailed, reminded before the deadline, "
                        + "and you get a summary after it.")), BorderLayout.NORTH);

        // Left: classes
        JTable classTable = Theme.table(classModel);
        classTable.getColumnModel().getColumn(1).setPreferredWidth(90);
        classTable.getColumnModel().getColumn(2).setMaxWidth(80);
        classTable.setToolTipText("Double-click a class to copy its join code");
        classTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = classTable.rowAtPoint(e.getPoint());
                if (e.getClickCount() == 2 && row >= 0) {
                    String code = String.valueOf(classModel.getValueAt(row, 1));
                    if (Theme.copyText(code)) {
                        app.toast("Join code " + code + " copied. Paste it anywhere with Ctrl+V.", ToastLayer.Kind.SUCCESS);
                    }
                }
            }
        });
        JPanel newClass = Theme.transparent(new BorderLayout(8, 0));
        newClass.add(classNameField, BorderLayout.CENTER);
        newClass.add(createClassButton, BorderLayout.EAST);
        JPanel classBody = Theme.transparent(new BorderLayout(0, 12));
        classBody.add(Theme.scroll(classTable), BorderLayout.CENTER);
        JPanel classFooter = Theme.transparent(new BorderLayout(0, 6));
        classFooter.add(newClass, BorderLayout.NORTH);
        classFooter.add(classHint, BorderLayout.SOUTH);
        classBody.add(classFooter, BorderLayout.SOUTH);
        JPanel classCard = Widgets.titledCard("My classes", classBody);
        classCard.setPreferredSize(new Dimension(330, 300));

        // Right: set homework, and the list of homework
        styleCombo(classBox);
        styleCombo(quizBox);
        styleCombo(dueUnit);
        dueAmount.setFont(Theme.font(Font.PLAIN, 14));
        JPanel form = Theme.transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(0, 0, 6, 12);
        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 1;
        form.add(Theme.label("Class", Font.BOLD, 13, Theme.MUTED), c);
        c.gridx = 1;
        c.weightx = 1.5;
        c.insets = new Insets(0, 0, 6, 0);
        form.add(Theme.label("Quiz", Font.BOLD, 13, Theme.MUTED), c);
        c.gridy = 1;
        c.gridx = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 14, 12);
        form.add(Theme.height(classBox, 38), c);
        c.gridx = 1;
        c.weightx = 1.5;
        c.insets = new Insets(0, 0, 14, 0);
        form.add(Theme.height(quizBox, 38), c);

        JPanel due = Theme.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0));
        due.add(Theme.label("Due in", Font.BOLD, 13, Theme.MUTED));
        dueAmount.setPreferredSize(new Dimension(70, 38));
        dueUnit.setPreferredSize(new Dimension(110, 38));
        due.add(dueAmount);
        due.add(dueUnit);
        JPanel bottom = Theme.transparent(new BorderLayout());
        bottom.add(due, BorderLayout.WEST);
        bottom.add(Theme.height(assignButton, 40), BorderLayout.EAST);
        c.gridy = 2;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(bottom, c);
        JPanel assignCard = Widgets.titledCard("Set homework", form);

        JTable homeworkTable = Theme.table(homeworkModel);
        int[] widths = {95, 150, 125, 80, 70, 64};
        for (int i = 0; i < widths.length; i++) {
            homeworkTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        homeworkTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        JPanel homeworkCard = Widgets.titledCard("Homework", Theme.scroll(homeworkTable));

        JPanel right = Theme.transparent(new BorderLayout(0, 18));
        right.add(assignCard, BorderLayout.NORTH);
        right.add(homeworkCard, BorderLayout.CENTER);

        JPanel center = Theme.transparent(new BorderLayout(20, 0));
        center.add(classCard, BorderLayout.WEST);
        center.add(right, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        createClassButton.addActionListener(e -> createClass());
        classNameField.addActionListener(e -> createClass());
        assignButton.addActionListener(e -> assign());
        refreshButtons();
    }

    private static void styleCombo(JComboBox<?> box) {
        box.setFont(Theme.font(Font.PLAIN, 14));
        box.setBackground(Color.WHITE);
    }

    private void createClass() {
        String name = classNameField.getText().strip();
        if (name.isEmpty()) {
            app.toast("Type a name for the class first.", ToastLayer.Kind.ERROR);
            return;
        }
        app.busy(createClassButton);
        app.send(new CreateClass(name));
        classNameField.setText("");
    }

    private void assign() {
        Choice chosenClass = (Choice) classBox.getSelectedItem();
        Choice chosenQuiz = (Choice) quizBox.getSelectedItem();
        if (chosenClass == null || chosenQuiz == null) {
            app.toast("Create a class and a quiz first.", ToastLayer.Kind.ERROR);
            return;
        }
        int amount = (Integer) dueAmount.getValue();
        int minutes = switch (String.valueOf(dueUnit.getSelectedItem())) {
            case "days" -> amount * 24 * 60;
            case "hours" -> amount * 60;
            default -> amount;
        };
        if (minutes > 30 * 24 * 60) {
            app.toast("The deadline can be at most 30 days away.", ToastLayer.Kind.ERROR);
            return;
        }
        app.busy(assignButton);
        app.send(new AssignHomework(chosenClass.id(), chosenQuiz.id(), minutes));
    }

    void setClasses(List<ClassInfo> classes) {
        classModel.setRowCount(0);
        Object selected = classBox.getSelectedItem();
        classBox.removeAllItems();
        for (ClassInfo info : classes) {
            classModel.addRow(new Object[]{info.name(), info.joinCode(), info.members()});
            Choice choice = new Choice(info.id(), info.name());
            classBox.addItem(choice);
            if (choice.equals(selected)) {
                classBox.setSelectedItem(choice);
            }
        }
        classHint.setText(classes.isEmpty() ? "Create your first class, then share its join code."
                : "Double-click a class to copy its join code.");
        refreshButtons();
    }

    void setQuizzes(List<QuizSummary> quizzes) {
        Object selected = quizBox.getSelectedItem();
        quizBox.removeAllItems();
        for (QuizSummary quiz : quizzes) {
            Choice choice = new Choice(quiz.id(), quiz.title() + " (" + quiz.questionCount() + " q)");
            quizBox.addItem(choice);
            if (choice.equals(selected)) {
                quizBox.setSelectedItem(choice);
            }
        }
        refreshButtons();
    }

    void setHomework(List<HomeworkInfo> homework) {
        homeworkModel.setRowCount(0);
        for (HomeworkInfo h : homework) {
            homeworkModel.addRow(new Object[]{h.className(), h.quizTitle(), h.dueAt(),
                    h.submitted() + " / " + h.members(), h.submitted() == 0 ? "-" : h.averagePercent() + "%",
                    h.open() ? "Open" : "Closed"});
        }
    }

    private void refreshButtons() {
        assignButton.setEnabled(classBox.getItemCount() > 0 && quizBox.getItemCount() > 0);
    }
}
