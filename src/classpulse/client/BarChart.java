package classpulse.client;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;
import javax.swing.JComponent;

/**
 * Hand-drawn chart of the four options. While a question is running, it shows only
 * the option text. Once the question ends, it adds a bar and a count for each option.
 */
final class BarChart extends JComponent {
    private List<String> options = List.of();
    private List<Integer> counts;
    private int correctIndex = -1;

    BarChart() {
        setPreferredSize(new Dimension(520, 280));
    }

    void showOptions(List<String> options) {
        this.options = options;
        this.counts = null;
        this.correctIndex = -1;
        repaint();
    }

    void showResults(List<Integer> counts, int correctIndex) {
        this.counts = counts;
        this.correctIndex = correctIndex;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        if (options.isEmpty()) {
            g.dispose();
            return;
        }
        int rowHeight = getHeight() / options.size();
        int max = counts == null ? 0 : counts.stream().mapToInt(Integer::intValue).max().orElse(0);
        for (int i = 0; i < options.size(); i++) {
            paintRow(g, i, i * rowHeight, rowHeight, max);
        }
        g.dispose();
    }

    private void paintRow(Graphics2D g, int index, int top, int rowHeight, int max) {
        int chip = 30;
        int textLeft = chip + 12;
        int textBaseline = top + 21;
        g.setColor(Theme.OPTION_COLORS[index]);
        g.fillRoundRect(0, top + 2, chip, chip, 8, 8);
        g.setColor(java.awt.Color.WHITE);
        g.setFont(Theme.font(Font.BOLD, 15));
        FontMetrics letterMetrics = g.getFontMetrics();
        String letter = Theme.OPTION_LETTERS[index];
        g.drawString(letter, (chip - letterMetrics.stringWidth(letter)) / 2, textBaseline);

        boolean correct = index == correctIndex;
        g.setColor(correct ? Theme.CORRECT : Theme.INK);
        g.setFont(Theme.font(correct ? Font.BOLD : Font.PLAIN, 16));
        String text = options.get(index) + (correct ? "   (correct)" : "");
        g.drawString(fit(text, g.getFontMetrics(), getWidth() - textLeft), textLeft, textBaseline);

        if (counts != null) {
            int barTop = top + 30;
            int barHeight = 12;
            int countWidth = 36;
            int trackWidth = getWidth() - textLeft - countWidth;
            int count = counts.get(index);
            g.setColor(Theme.TRACK);
            g.fillRoundRect(textLeft, barTop, trackWidth, barHeight, barHeight, barHeight);
            if (count > 0) {
                g.setColor(correct ? Theme.CORRECT : Theme.OPTION_COLORS[index]);
                g.fillRoundRect(textLeft, barTop, Math.max(barHeight, trackWidth * count / max), barHeight,
                        barHeight, barHeight);
            }
            g.setColor(Theme.INK);
            g.setFont(Theme.font(Font.BOLD, 14));
            String value = String.valueOf(count);
            g.drawString(value, getWidth() - g.getFontMetrics().stringWidth(value), barTop + 11);
        }
    }

    /** Shortens text with "..." if it is wider than the space available. */
    private static String fit(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        int length = text.length();
        while (length > 0 && metrics.stringWidth(text.substring(0, length) + "...") > width) {
            length--;
        }
        return text.substring(0, length) + "...";
    }
}
