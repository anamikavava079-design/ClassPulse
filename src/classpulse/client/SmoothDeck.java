package classpulse.client;

import java.awt.AlphaComposite;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * A CardLayout that animates: the old screen fades and slides out while the new one
 * fades and slides in. Everything stays in one window; only the content changes.
 *
 * How: just before switching, the old screen is painted into an image. For the next
 * ~240 ms the new screen is painted with growing opacity and the old image on top
 * with shrinking opacity. A Swing Timer drives the frames on the Event Dispatch Thread.
 */
final class SmoothDeck extends JPanel {
    private static final int DURATION_MS = 240;
    private static final int SLIDE_PX = 36;

    private final CardLayout layout = new CardLayout();
    private final Map<String, Component> cards = new HashMap<>();
    private final Timer timer = new Timer(12, e -> step());
    private String current;
    private BufferedImage outgoing;
    private long startNanos;
    private float progress = 1f;
    private int direction;
    private boolean animations = true;

    SmoothDeck() {
        setLayout(layout);
        setOpaque(false);
    }

    private boolean sizeToCurrent;

    /** Turned off by the automatic screenshot test, so images are never half-faded. */
    void setAnimations(boolean on) {
        animations = on;
    }

    /** Makes the deck as tall as the visible card only (CardLayout normally uses the tallest card). */
    void setSizeToCurrent(boolean on) {
        sizeToCurrent = on;
    }

    @Override
    public java.awt.Dimension getPreferredSize() {
        Component shown = cards.get(current);
        if (!sizeToCurrent || shown == null) {
            return super.getPreferredSize();
        }
        java.awt.Dimension size = shown.getPreferredSize();
        java.awt.Insets insets = getInsets();
        return new java.awt.Dimension(size.width + insets.left + insets.right, size.height + insets.top + insets.bottom);
    }

    void addCard(Component card, String name) {
        cards.put(name, card);
        add(card, name);
        if (current == null) {
            current = name;
        }
    }

    String current() {
        return current;
    }

    void show(String name) {
        show(name, 0);
    }

    /** direction: 1 slides in from the right, -1 from the left, 0 just fades. */
    void show(String name, int direction) {
        if (name.equals(current)) {
            return;
        }
        Component old = cards.get(current);
        boolean canAnimate = animations && isShowing() && getWidth() > 0 && getHeight() > 0 && old != null;
        if (canAnimate) {
            outgoing = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = outgoing.createGraphics();
            old.paint(g);
            g.dispose();
        }
        current = name;
        layout.show(this, name);
        if (sizeToCurrent) {
            revalidate();
        }
        if (canAnimate) {
            this.direction = direction;
            progress = 0f;
            startNanos = System.nanoTime();
            timer.start();
        }
        repaint();
    }

    private void step() {
        progress = Math.min(1f, (System.nanoTime() - startNanos) / (DURATION_MS * 1_000_000f));
        if (progress >= 1f) {
            timer.stop();
            outgoing = null;
        }
        repaint();
    }

    /** Ease-out cubic: fast at first, gentle at the end. */
    private float eased() {
        float t = 1 - progress;
        return 1 - t * t * t;
    }

    @Override
    protected void paintChildren(Graphics graphics) {
        if (outgoing == null || progress >= 1f) {
            super.paintChildren(graphics);
            return;
        }
        float e = eased();
        Graphics2D in = (Graphics2D) graphics.create();
        in.translate(Math.round((1 - e) * SLIDE_PX * direction), 0);
        in.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, e));
        super.paintChildren(in);
        in.dispose();

        Graphics2D out = (Graphics2D) graphics.create();
        out.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1 - e));
        out.drawImage(outgoing, Math.round(-e * SLIDE_PX * direction), 0, null);
        out.dispose();
    }
}
