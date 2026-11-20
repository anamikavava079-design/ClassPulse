package classpulse.client;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.Timer;

/**
 * A pill-shaped switch with two or more choices in one row, such as  [ Student | Teacher ].
 * The white highlight slides smoothly to the chosen side. It can be used with the mouse,
 * or with the Left and Right arrow keys after Tab.
 */
final class SegmentedControl extends JComponent {
    private final String[] labels;
    private final Icons.Kind[] icons;
    private final List<IntConsumer> listeners = new ArrayList<>();
    private final Timer slide = new Timer(12, e -> animate());
    private int selected;
    private float position; // Where the highlight is drawn, animated towards selected.

    SegmentedControl(String[] labels, Icons.Kind[] icons) {
        this.labels = labels.clone();
        this.icons = icons.clone();
        setPreferredSize(new Dimension(360, 46));
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                setSelected(Math.min(labels.length - 1, e.getX() * labels.length / Math.max(1, getWidth())));
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    setSelected(Math.max(0, selected - 1));
                } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    setSelected(Math.min(labels.length - 1, selected + 1));
                }
            }
        });
        addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                repaint();
            }
        });
    }

    void onChange(IntConsumer listener) {
        listeners.add(listener);
    }

    int selected() {
        return selected;
    }

    void setSelected(int index) {
        if (index == selected) {
            return;
        }
        selected = index;
        slide.start();
        listeners.forEach(l -> l.accept(index));
    }

    private void animate() {
        float target = selected;
        position += (target - position) * 0.28f;
        if (Math.abs(target - position) < 0.01f) {
            position = target;
            slide.stop();
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Theme.smooth(g);
        int w = getWidth();
        int h = getHeight();
        g.setColor(new Color(0xEEF0F5));
        g.fillRoundRect(0, 0, w, h, h, h);
        int segment = w / labels.length;
        int pillX = Math.round(position * segment) + 4;
        g.setColor(new Color(15, 23, 42, 22));
        g.fillRoundRect(pillX, 6, segment - 8, h - 8, h - 8, h - 8);
        g.setColor(Color.WHITE);
        g.fillRoundRect(pillX, 4, segment - 8, h - 8, h - 8, h - 8);
        if (isFocusOwner()) {
            g.setColor(new Color(99, 102, 241, 140));
            g.drawRoundRect(pillX, 4, segment - 9, h - 9, h - 8, h - 8);
        }
        g.setFont(Theme.font(Font.BOLD, 14));
        FontMetrics m = g.getFontMetrics();
        for (int i = 0; i < labels.length; i++) {
            float closeness = 1 - Math.min(1, Math.abs(position - i));
            Color color = Theme.mix(Theme.MUTED, Theme.PRIMARY_DARK, closeness);
            int textWidth = m.stringWidth(labels[i]);
            int iconSize = 18;
            int total = iconSize + 8 + textWidth;
            int x = i * segment + (segment - total) / 2;
            Icons.of(icons[i], iconSize, color).paintIcon(this, g, x, (h - iconSize) / 2);
            g.setColor(color);
            g.drawString(labels[i], x + iconSize + 8, (h + m.getAscent() - m.getDescent()) / 2);
        }
        g.dispose();
    }
}
