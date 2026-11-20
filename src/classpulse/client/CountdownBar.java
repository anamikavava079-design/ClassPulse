package classpulse.client;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JComponent;
import javax.swing.Timer;

/** Shows the time left. It is display only: the server's timer decides when a question ends. */
final class CountdownBar extends JComponent {
    private final Timer repaintTimer = new Timer(100, e -> repaint());
    private long endNanos;
    private long totalNanos = 1;

    CountdownBar() {
        setPreferredSize(new Dimension(400, 26));
    }

    void start(int seconds) {
        totalNanos = seconds * 1_000_000_000L;
        endNanos = System.nanoTime() + totalNanos;
        repaintTimer.start();
    }

    void stop() {
        repaintTimer.stop();
        endNanos = 0;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        long remaining = Math.max(0, endNanos - System.nanoTime());
        double fraction = (double) remaining / totalNanos;
        int textWidth = 54;
        int barWidth = getWidth() - textWidth;
        int barHeight = 12;
        int y = (getHeight() - barHeight) / 2;
        g.setColor(Theme.TRACK);
        g.fillRoundRect(0, y, barWidth, barHeight, barHeight, barHeight);
        g.setColor(fraction < 0.25 ? Theme.WRONG : Theme.PRIMARY);
        g.fillRoundRect(0, y, (int) (barWidth * fraction), barHeight, barHeight, barHeight);
        g.setColor(Theme.INK);
        g.setFont(Theme.font(Font.BOLD, 15));
        String text = (int) Math.ceil(remaining / 1e9) + "s";
        int textX = getWidth() - g.getFontMetrics().stringWidth(text);
        g.drawString(text, textX, getHeight() / 2 + 5);
        g.dispose();
    }
}
