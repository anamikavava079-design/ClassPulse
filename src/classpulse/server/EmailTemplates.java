package classpulse.server;

import classpulse.common.Views.QuestionStat;
import classpulse.common.Views.Role;
import classpulse.common.Views.ScoreEntry;
import classpulse.common.Views.SessionReport;
import java.util.List;
import java.util.Locale;

/**
 * The wording and HTML of every email. Mail apps ignore style sheets, so the styles are
 * inline. Any text that came from a user is escaped, so it cannot inject HTML.
 */
final class EmailTemplates {
    /** Subject, plain-text body and HTML body. */
    record Content(String subject, String text, String html) {
    }

    /** One line of a student's result email. */
    record AnswerLine(int number, String question, String yourAnswer, String correctAnswer, boolean correct) {
    }

    private static final String BRAND = "#4338CA";

    private EmailTemplates() {
    }

    static Content verificationCode(String name, String code, int minutes) {
        String subject = "Your ClassPulse verification code: " + code;
        String text = "Hi " + name + ",\n\nYour ClassPulse verification code is: " + code
                + "\n\nIt expires in " + minutes + " minutes. If you did not create an account, ignore this email.";
        String html = layout("Confirm your email",
                p("Hi " + esc(name) + ",")
                        + p("Enter this code in ClassPulse to confirm that this email address belongs to you:")
                        + codeBox(code)
                        + small("The code expires in " + minutes + " minutes. If you did not create an account, "
                        + "you can ignore this email."));
        return new Content(subject, text, html);
    }

    static Content resetCode(String name, String code, int minutes) {
        String subject = "Reset your ClassPulse password";
        String text = "Hi " + name + ",\n\nSomeone asked to reset your ClassPulse password. Your reset code is: "
                + code + "\n\nIt expires in " + minutes + " minutes. If this was not you, ignore this email;"
                + " your password stays the same.";
        String html = layout("Reset your password",
                p("Hi " + esc(name) + ",")
                        + p("We received a request to reset your ClassPulse password. Use this code:")
                        + codeBox(code)
                        + small("The code expires in " + minutes + " minutes. If this was not you, ignore this "
                        + "email; your password stays the same."));
        return new Content(subject, text, html);
    }

    static Content welcome(String name, Role role) {
        boolean teacher = role == Role.TEACHER;
        String subject = "Welcome to ClassPulse, " + name + "!";
        String next = teacher
                ? "Your account comes with a sample quiz. Host it from My quizzes, and share the room code with your class."
                : "Ask your teacher for a room code, then join from the Join a quiz page. Your results and badges are saved to your account.";
        String text = "Hi " + name + ",\n\nYour email is confirmed and your " + (teacher ? "teacher" : "student")
                + " account is ready.\n\n" + next;
        String html = layout("Welcome aboard!",
                p("Hi " + esc(name) + ",")
                        + p("Your email is confirmed and your <b>" + (teacher ? "teacher" : "student")
                        + "</b> account is ready.")
                        + p(next));
        return new Content(subject, text, html);
    }

    static Content passwordChanged(String name) {
        String subject = "Your ClassPulse password was changed";
        String text = "Hi " + name + ",\n\nThe password for your ClassPulse account was just changed."
                + " If this was not you, reset your password straight away with Forgot password.";
        String html = layout("Password changed",
                p("Hi " + esc(name) + ",")
                        + p("The password for your ClassPulse account was just changed.")
                        + small("If this was not you, reset your password straight away with <b>Forgot password</b>."));
        return new Content(subject, text, html);
    }

    static Content studentResult(String name, String quizTitle, int rank, int players, int score,
                                 int correct, int total, List<AnswerLine> lines) {
        return studentResult(name, quizTitle, rank, players, score, correct, total, lines, null);
    }

    /** certificatePlace is, for example, "1st place" when a certificate is attached, otherwise null. */
    static Content studentResult(String name, String quizTitle, int rank, int players, int score,
                                 int correct, int total, List<AnswerLine> lines, String certificatePlace) {
        String subject = "Your result: " + quizTitle + " (#" + rank + " of " + players + ")";
        StringBuilder text = new StringBuilder("Hi " + name + ",\n\nYou finished #" + rank + " of " + players
                + " in " + quizTitle + " with " + score + " points (" + correct + " of " + total + " correct).\n\n");
        StringBuilder rows = new StringBuilder();
        for (AnswerLine line : lines) {
            text.append(line.number()).append(". ").append(line.question()).append("\n   Your answer: ")
                    .append(line.yourAnswer()).append(line.correct() ? "  (correct)" : "  | Correct: " + line.correctAnswer())
                    .append("\n");
            String colour = line.correct() ? "#15803D" : "#B91C1C";
            rows.append("<tr><td style='padding:8px;border-bottom:1px solid #E5E7EB;vertical-align:top'>")
                    .append(line.number()).append("</td><td style='padding:8px;border-bottom:1px solid #E5E7EB'>")
                    .append(esc(line.question())).append("<br><span style='color:").append(colour).append("'>Your answer: ")
                    .append(esc(line.yourAnswer())).append("</span>")
                    .append(line.correct() ? "" : "<br><span style='color:#15803D'>Correct: " + esc(line.correctAnswer()) + "</span>")
                    .append("</td></tr>");
        }
        String html = layout("Your quiz result",
                p("Hi " + esc(name) + ",")
                        + p("Here is how you did in <b>" + esc(quizTitle) + "</b>.")
                        + stats(new String[][]{{"#" + rank + " of " + players, "Rank"}, {String.valueOf(score), "Points"},
                        {correct + "/" + total, "Correct"}})
                        + "<table style='width:100%;border-collapse:collapse;font-size:14px;margin-top:12px'>" + rows + "</table>"
                        + (certificatePlace == null ? "" : "<p style='margin:18px 0 0;background:#FEF3C7;border-radius:8px;"
                        + "padding:12px;color:#92400E;font-weight:bold'>Congratulations on " + esc(certificatePlace)
                        + "! Your certificate is attached to this email.</p>"));
        if (certificatePlace != null) {
            text.append("\nCongratulations on ").append(certificatePlace).append("! Your certificate is attached.\n");
        }
        return new Content(subject, text.toString(), html);
    }

    static Content sessionReport(String teacherName, SessionReport report, String reteach) {
        var summary = report.summary();
        String subject = "ClassPulse report: " + summary.quizTitle() + " (" + summary.startedAt() + ")";
        StringBuilder text = new StringBuilder("Hi " + teacherName + ",\n\nReport for " + summary.quizTitle()
                + ", room " + summary.roomCode() + ", " + summary.startedAt() + ", " + summary.players()
                + " students.\n" + reteach + "\n\nRanking:\n");
        StringBuilder ranking = new StringBuilder();
        for (ScoreEntry entry : report.ranking()) {
            text.append(entry.rank()).append(". ").append(entry.name()).append(" - ").append(entry.score()).append("\n");
            ranking.append("<tr>").append(td(String.valueOf(entry.rank()))).append(td(esc(entry.name())))
                    .append(td(String.valueOf(entry.score()))).append("</tr>");
        }
        StringBuilder questions = new StringBuilder();
        for (QuestionStat q : report.questions()) {
            int percent = q.answered() == 0 ? 0 : Math.round(100f * q.correct() / q.answered());
            questions.append("<tr>").append(td(String.valueOf(q.number()))).append(td(esc(q.text())))
                    .append(td(percent + "%")).append(td(String.format(Locale.ROOT, "%.1f s", q.averageMs() / 1000.0)))
                    .append("</tr>");
        }
        String html = layout("Session report",
                p("Hi " + esc(teacherName) + ",")
                        + p("Here is the report for <b>" + esc(summary.quizTitle()) + "</b> (room "
                        + esc(summary.roomCode()) + ", " + esc(summary.startedAt()) + ").")
                        + "<p style='background:#EEF2FF;border-radius:8px;padding:12px;color:" + BRAND + ";font-weight:bold'>"
                        + esc(reteach) + "</p>"
                        + "<h3 style='margin:18px 0 6px'>Ranking</h3>" + table(new String[]{"Rank", "Name", "Score"}, ranking)
                        + "<h3 style='margin:18px 0 6px'>Questions</h3>"
                        + table(new String[]{"#", "Question", "Correct", "Avg time"}, questions)
                        + small("The full data is attached as a CSV file you can open in Excel."));
        return new Content(subject, text.toString(), html);
    }

    // ----- Homework emails (added in the homework upgrade) -----

    static Content homeworkAssigned(String studentName, String teacherName, String className, String quizTitle,
                                    int questions, String due) {
        String subject = "New homework: " + quizTitle + " (due " + due + ")";
        String text = "Hi " + studentName + ",\n\n" + teacherName + " set new homework for " + className + ": "
                + quizTitle + " (" + questions + " questions).\nDeadline: " + due
                + "\n\nOpen ClassPulse, sign in, and choose Homework to start it.";
        String html = layout("New homework",
                p("Hi " + esc(studentName) + ",")
                        + p("<b>" + esc(teacherName) + "</b> set new homework for <b>" + esc(className) + "</b>.")
                        + stats(new String[][]{{quizTitle, "Quiz"}, {String.valueOf(questions), "Questions"},
                        {due, "Deadline"}})
                        + p("Open ClassPulse, sign in, and choose <b>Homework</b> to start it. You get one try.")
                        + small("We will remind you before the deadline if it is not done yet."));
        return new Content(subject, text, html);
    }

    static Content homeworkReminder(String studentName, String className, String quizTitle, String due,
                                    String timeLeft) {
        String subject = "Reminder: " + quizTitle + " is due in " + timeLeft;
        String text = "Hi " + studentName + ",\n\nYour homework " + quizTitle + " for " + className
                + " is not handed in yet. It is due " + due + " (in " + timeLeft + ").\n\nOpen ClassPulse and choose Homework.";
        String html = layout("Homework reminder",
                p("Hi " + esc(studentName) + ",")
                        + p("Your homework <b>" + esc(quizTitle) + "</b> for " + esc(className) + " is not handed in yet.")
                        + "<p style='background:#FEF3C7;border-radius:8px;padding:12px;color:#92400E;font-weight:bold'>"
                        + "Due " + esc(due) + " &nbsp;|&nbsp; " + esc(timeLeft) + " left</p>"
                        + p("Open ClassPulse, sign in, and choose <b>Homework</b>."));
        return new Content(subject, text, html);
    }

    static Content homeworkResult(String studentName, String className, String quizTitle, int correct, int total,
                                  List<AnswerLine> lines) {
        int percent = total == 0 ? 0 : Math.round(100f * correct / total);
        String subject = "Homework handed in: " + quizTitle + " (" + correct + "/" + total + ")";
        StringBuilder text = new StringBuilder("Hi " + studentName + ",\n\nYou handed in " + quizTitle + " for "
                + className + ": " + correct + " of " + total + " correct (" + percent + "%).\n\n");
        StringBuilder rows = new StringBuilder();
        for (AnswerLine line : lines) {
            text.append(line.number()).append(". ").append(line.question()).append("\n   Your answer: ")
                    .append(line.yourAnswer()).append(line.correct() ? "  (correct)" : "  | Correct: " + line.correctAnswer())
                    .append("\n");
            String colour = line.correct() ? "#15803D" : "#B91C1C";
            rows.append("<tr><td style='padding:8px;border-bottom:1px solid #E5E7EB;vertical-align:top'>")
                    .append(line.number()).append("</td><td style='padding:8px;border-bottom:1px solid #E5E7EB'>")
                    .append(esc(line.question())).append("<br><span style='color:").append(colour).append("'>Your answer: ")
                    .append(esc(line.yourAnswer())).append("</span>")
                    .append(line.correct() ? "" : "<br><span style='color:#15803D'>Correct: " + esc(line.correctAnswer()) + "</span>")
                    .append("</td></tr>");
        }
        String html = layout("Homework handed in",
                p("Hi " + esc(studentName) + ",")
                        + p("Here is how you did in <b>" + esc(quizTitle) + "</b> for " + esc(className) + ".")
                        + stats(new String[][]{{correct + "/" + total, "Correct"}, {percent + "%", "Score"}})
                        + "<table style='width:100%;border-collapse:collapse;font-size:14px;margin-top:12px'>" + rows + "</table>");
        return new Content(subject, text.toString(), html);
    }

    static Content homeworkSummary(String teacherName, String className, String quizTitle, String due,
                                   List<HomeworkRepository.SummaryRow> rows) {
        long handedIn = rows.stream().filter(HomeworkRepository.SummaryRow::done).count();
        int average = (int) Math.round(rows.stream().filter(HomeworkRepository.SummaryRow::done)
                .mapToDouble(r -> r.questions() == 0 ? 0 : 100.0 * r.correct() / r.questions()).average().orElse(0));
        String subject = "Homework closed: " + quizTitle + " (" + handedIn + " of " + rows.size() + " handed in)";
        StringBuilder text = new StringBuilder("Hi " + teacherName + ",\n\nThe deadline for " + quizTitle + " ("
                + className + ", due " + due + ") has passed. " + handedIn + " of " + rows.size()
                + " students handed it in. Average: " + average + "%.\n\n");
        StringBuilder table = new StringBuilder();
        for (HomeworkRepository.SummaryRow row : rows) {
            String result = row.done() ? row.correct() + "/" + row.questions() : "Not handed in";
            text.append(row.name()).append(": ").append(result).append("\n");
            table.append("<tr>").append(td(esc(row.name())))
                    .append(td(row.done() ? result : "<span style='color:#B91C1C'>Not handed in</span>")).append("</tr>");
        }
        String html = layout("Homework closed",
                p("Hi " + esc(teacherName) + ",")
                        + p("The deadline for <b>" + esc(quizTitle) + "</b> (" + esc(className) + ", due " + esc(due)
                        + ") has passed.")
                        + stats(new String[][]{{handedIn + " / " + rows.size(), "Handed in"}, {average + "%", "Average"}})
                        + "<h3 style='margin:18px 0 6px'>Students</h3>" + table(new String[]{"Student", "Result"}, table)
                        + small("The list is also attached as a CSV file you can open in Excel."));
        return new Content(subject, text.toString(), html);
    }

    // ----- Small HTML helpers -----

    static String esc(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static String layout(String heading, String body) {
        return "<!doctype html><html><body style='margin:0;background:#F4F6FB;font-family:Segoe UI,Arial,sans-serif'>"
                + "<div style='max-width:560px;margin:24px auto;background:#fff;border-radius:14px;overflow:hidden;"
                + "border:1px solid #E5E7EB'>"
                + "<div style='background:linear-gradient(135deg,#4338CA,#7C3AED);background-color:" + BRAND
                + ";color:#fff;padding:22px 28px'>"
                + "<div style='font-size:13px;letter-spacing:2px;opacity:.85'>CLASSPULSE</div>"
                + "<div style='font-size:24px;font-weight:bold;margin-top:4px'>" + heading + "</div></div>"
                + "<div style='padding:24px 28px;color:#1F2937;font-size:15px;line-height:1.5'>" + body + "</div>"
                + "<div style='padding:14px 28px;background:#F9FAFB;color:#6B7280;font-size:12px'>"
                + "Sent by your ClassPulse server. Live quizzes for the classroom.</div></div></body></html>";
    }

    private static String p(String html) {
        return "<p style='margin:0 0 12px'>" + html + "</p>";
    }

    private static String small(String html) {
        return "<p style='margin:16px 0 0;color:#6B7280;font-size:13px'>" + html + "</p>";
    }

    private static String codeBox(String code) {
        return "<div style='margin:18px 0;text-align:center'><span style='display:inline-block;background:#EEF2FF;"
                + "color:" + BRAND + ";font-size:34px;font-weight:bold;letter-spacing:10px;padding:14px 26px;"
                + "border-radius:12px;font-family:Consolas,monospace'>" + esc(code) + "</span></div>";
    }

    private static String stats(String[][] items) {
        StringBuilder html = new StringBuilder("<table style='width:100%;border-collapse:separate;border-spacing:8px'><tr>");
        for (String[] item : items) {
            html.append("<td style='background:#EEF2FF;border-radius:10px;padding:12px;text-align:center'>")
                    .append("<div style='font-size:22px;font-weight:bold;color:").append(BRAND).append("'>")
                    .append(esc(item[0])).append("</div><div style='color:#6B7280;font-size:12px'>")
                    .append(item[1]).append("</div></td>");
        }
        return html.append("</tr></table>").toString();
    }

    private static String td(String html) {
        return "<td style='padding:6px 8px;border-bottom:1px solid #E5E7EB'>" + html + "</td>";
    }

    private static String table(String[] headings, CharSequence rows) {
        StringBuilder html = new StringBuilder("<table style='width:100%;border-collapse:collapse;font-size:14px'><tr>");
        for (String heading : headings) {
            html.append("<th style='text-align:left;padding:6px 8px;background:#F3F4F6'>").append(heading).append("</th>");
        }
        return html.append("</tr>").append(rows).append("</table>").toString();
    }
}
