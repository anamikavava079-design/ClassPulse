package classpulse.common;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads quizzes from CSV files and writes safe CSV cells for exported reports. */
public final class Csv {
    private static final char BYTE_ORDER_MARK = (char) 0xFEFF;

    private Csv() {
    }

    /** Expected columns: question, A, B, C, D, correct letter, seconds. A header row is optional. */
    public static Quiz parseQuiz(String title, List<String> lines) {
        List<Question> questions = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0 && !line.isEmpty() && line.charAt(0) == BYTE_ORDER_MARK) {
                line = line.substring(1); // Excel adds an invisible byte-order mark.
            }
            if (line.isBlank()) {
                continue;
            }
            List<String> cells = splitLine(line);
            if (questions.isEmpty() && cells.get(0).strip().equalsIgnoreCase("question")) {
                continue;
            }
            try {
                questions.add(toQuestion(cells));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Line " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return new Quiz(title, questions);
    }

    private static Question toQuestion(List<String> cells) {
        if (cells.size() != 7) {
            throw new IllegalArgumentException("expected 7 columns but found " + cells.size() + ".");
        }
        String letter = cells.get(5).strip().toUpperCase(Locale.ROOT);
        if (!List.of("A", "B", "C", "D").contains(letter)) {
            throw new IllegalArgumentException("correct answer must be A, B, C or D.");
        }
        int seconds;
        try {
            seconds = Integer.parseInt(cells.get(6).strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("seconds must be a whole number.");
        }
        return new Question(cells.get(0), cells.subList(1, 5), letter.charAt(0) - 'A', seconds);
    }

    /** Splits one line, understanding quoted cells and "" as an escaped quote. */
    static List<String> splitLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cell.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    cell.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
            } else {
                cell.append(c);
            }
        }
        cells.add(cell.toString());
        return cells;
    }

    /** A session report as CSV lines: the ranking, a blank line, then one row per question. */
    public static List<String> reportLines(Views.SessionReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Rank,Name,Score");
        for (Views.ScoreEntry entry : report.ranking()) {
            lines.add(entry.rank() + "," + cell(entry.name()) + "," + entry.score());
        }
        lines.add("");
        lines.add("Question,Text,Answered,Correct,Average ms");
        for (Views.QuestionStat q : report.questions()) {
            lines.add(q.number() + "," + cell(q.text()) + "," + q.answered() + "," + q.correct() + "," + q.averageMs());
        }
        return lines;
    }

    /**
     * Formats one value for an exported CSV file. A leading apostrophe stops a
     * spreadsheet running a student name such as =HYPERLINK(...) as a formula.
     */
    public static String cell(String value) {
        String text = value == null ? "" : value;
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}
