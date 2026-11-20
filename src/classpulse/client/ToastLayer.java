package classpulse.client;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * Short notifications that slide in at the bottom of the window and fade away,
 * instead of pop-up dialog boxes that the user must click. It is the frame's glass
 * pane: it draws on top of every screen but has no mouse listeners, so clicks pass
 * straight through to the screen underneath.
 */
final class ToastLayer extends JComponent {
    enum Kind { SUCCESS, ERROR, INFO }

    private static final long SHOW_MS = 3600;
    private static final long FADE_MS = 260;

    private String text;
    private Kind kind = Kind.INFO;
    private long shownAt;
    private final Timer timer = new Timer(16, e -> tick());

    ToastLayer() {
        setOpaque(false);
    }

    void show(String message, Kind kind) {
        this.text = message;
        this.kind = kind;
        this.shownAt = System.currentTimeMillis();
        setVisible(true);
        timer.start();
        repaint();
    }

    String currentText() {
        return isVisible() ? text : null;
    }

    private void tick() {
        long age = System.currentTimeMillis() - shownAt;
        long total = SHOW_MS + (text.length() > 60 ? 1800 : 0);
        if (age > total + FADE_MS) {
            timer.stop();
            setVisible(false);
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (text == null) {
            return;
        }
        long age = System.currentTimeMillis() - shownAt;
        long total = SHOW_MS + (text.length() > 60 ? 1800 : 0);
        float alpha = age < FADE_MS ? age / (float) FADE_MS
                : age > total ? Math.max(0, 1 - (age - total) / (float) FADE_MS) : 1f;
        float rise = age < FADE_MS ? (1 - alpha) * 18 : 0;

        Graphics2D g = (Graphics2D) graphics.create();
        Theme.smooth(g);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f, alpha)));
        g.setFont(Theme.font(Font.BOLD, 14));
        FontMetrics m = g.getFontMetrics();
        String shown = Theme.fit(text, m, Math.min(720, getWidth() - 140));
        int width = m.stringWidth(shown) + 78;
        int height = 48;
        int x = (getWidth() - width) / 2;
        int y = (int) (getHeight() - height - 30 + rise);

        g.setColor(new Color(15, 23, 42, 40));
        g.fillRoundRect(x + 1, y + 4, width, height, height, height);
        g.setColor(new Color(0x111827));
        g.fillRoundRect(x, y, width, height, height, height);
        Color accent = switch (kind) {
            case SUCCESS -> new Color(0x22C55E);
            case ERROR -> new Color(0xF87171);
            case INFO -> new Color(0x818CF8);
        };
        g.setColor(accent);
        g.fillOval(x + 12, y + 12, 24, 24);
        g.translate(x + 15, y + 15);
        g.scale(0.75, 0.75);
        g.setColor(new Color(0x111827));
        g.setStroke(new java.awt.BasicStroke(3f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
        Icons.draw(g, kind == Kind.ERROR ? Icons.Kind.CROSS : Icons.Kind.CHECK);
        g.scale(1 / 0.75, 1 / 0.75);
        g.translate(-(x + 15), -(y + 15));
        g.setColor(Color.WHITE);
        g.drawString(shown, x + 48, y + (height + m.getAscent() - m.getDescent()) / 2);
        g.dispose();
    }
}
