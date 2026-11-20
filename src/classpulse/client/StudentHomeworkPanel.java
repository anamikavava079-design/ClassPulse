package classpulse.client;

import classpulse.common.Message.JoinClass;
import classpulse.common.Message.MyHomework;
import classpulse.common.Message.StartHomework;
import classpulse.common.Views.StudentHomework;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

/** The student's Homework page (added in the homework upgrade): join a class, see and start homework. */
final class StudentHomeworkPanel extends JPanel {
    private final AppFrame app;
    final JTextField codeField = Theme.field(8, "Class code");
    final JButton joinButton = Theme.withIcon(Theme.primary("Join class"), Icons.Kind.USERS);
    final JLabel classesLabel =Theme.label("You are not in a class yet.", Font.PLAIN, 13, Theme.MUTED);
    private final DefaultTableModel model = Theme.readOnlyModel("Class", "Quiz", "Teacher", "Due", "Status");
    final JTable table = Theme.table(model);
    final JButton startButton = Theme.withIcon(Theme.primary("Start homework"), Icons.Kind.PLAY);
    private List<StudentHomework> homework = List.of();

    StudentHomeworkPanel(AppFrame app) {
        this.app = app;
        setLayout(new BorderLayout(0, 18));
        setOpaque(false);
        add(Widgets.header(Widgets.pageTitle("Homework"),
                Widgets.pageSubtitle("Quizzes your teachers set, with a deadline. You get one try, and the result is emailed.")),
                BorderLayout.NORTH);

        codeField.setFont(Theme.font(Font.BOLD, 18));
        codeField.setHorizontalAlignment(SwingConstants.CENTER);
        codeField.setPreferredSize(new Dimension(150, 40));
        JPanel joinRow = Theme.transparent(new FlowLayout(FlowLayout.LEFT, 10, 0));
        joinRow.add(Theme.label("Got a class code from your teacher?", Font.BOLD, 14, Theme.INK));
        joinRow.add(codeField);
        joinRow.add(Theme.height(joinButton, 40));
        JPanel joinBody = Theme.transparent(new BorderLayout(0, 8));
        joinBody.add(joinRow, BorderLayout.NORTH);
        joinBody.add(classesLabel, BorderLayout.SOUTH);
        JPanel joinCard = Widgets.titledCard("My classes", joinBody);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] widths = {120, 200, 130, 130, 120};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        JPanel listBody = Theme.transparent(new BorderLayout(0, 12));
        listBody.add(Theme.scroll(table), BorderLayout.CENTER);
        JPanel startRow = Theme.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        startRow.add(Theme.height(startButton, 42));
        listBody.add(startRow, BorderLayout.SOUTH);
        JPanel listCard = Widgets.titledCard("My homework", listBody);

        JPanel center = Theme.transparent(new BorderLayout(0, 18));
        center.add(joinCard, BorderLayout.NORTH);
        center.add(listCard, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        joinButton.addActionListener(e -> join());
        codeField.addActionListener(e -> join());
        startButton.addActionListener(e -> start());
        table.getSelectionModel().addListSelectionListener(e -> refreshStart());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && startButton.isEnabled()) {
                    start();
                }
            }
        });
        refreshStart();
    }

    private void join() {
        String code = codeField.getText().strip();
        if (code.isEmpty()) {
            app.toast("Type the class code from your teacher.", ToastLayer.Kind.ERROR);
            return;
        }
        app.busy(joinButton);
        app.send(new JoinClass(code));
        codeField.setText("");
    }

    private void start() {
        StudentHomework chosen = selected();
        if (chosen != null && chosen.open() && !chosen.done()) {
            app.busy(startButton);
            app.send(new StartHomework(chosen.id()));
        }
    }

    private StudentHomework selected() {
        int row = table.getSelectedRow();
        return row >= 0 && row < homework.size() ? homework.get(row) : null;
    }

    void setHomework(MyHomework message) {
        homework = message.homework();
        classesLabel.setText(message.classNames().isEmpty() ? "You are not in a class yet."
                : "Your classes: " + String.join(", ", message.classNames()));
        model.setRowCount(0);
        int firstOpen = -1;
        for (int i = 0; i < homework.size(); i++) {
            StudentHomework h = homework.get(i);
            String status;
            if (h.done()) {
                status = "Done  " + h.correct() + "/" + h.questions();
            } else if (h.open()) {
                status = "To do";
                if (firstOpen < 0) {
                    firstOpen = i;
                }
            } else {
                status = "Missed";
            }
            model.addRow(new Object[]{h.className(), h.quizTitle(), h.teacherName(), h.dueAt(), status});
        }
        if (firstOpen >= 0) {
            table.setRowSelectionInterval(firstOpen, firstOpen);
        }
        refreshStart();
    }

    private void refreshStart() {
        StudentHomework chosen = selected();
        startButton.setEnabled(chosen != null && chosen.open() && !chosen.done());
    }
}
