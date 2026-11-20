package classpulse.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.border.AbstractBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.JTextComponent;

/** One place for all colours, fonts and shared widgets, so every screen matches. */
final class Theme {
    static final Color BACKGROUND = new Color(0xF4F6FB);
    static final Color CARD = Color.WHITE;
    static final Color INK = new Color(0x111827);
    static final Color MUTED = new Color(0x6B7280);
    static final Color SUBTLE = new Color(0x9CA3AF);
    static final Color BORDER = new Color(0xE5E7EB);
    static final Color FIELD_BORDER = new Color(0xD1D5DB);
    static final Color PRIMARY = new Color(0x4F46E5);
    static final Color PRIMARY_DARK = new Color(0x4338CA);
    static final Color PRIMARY_SOFT = new Color(0xEEF2FF);
    static final Color ACCENT = new Color(0x7C3AED);
    static final Color CORRECT = new Color(0x16A34A);
    static final Color CORRECT_SOFT = new Color(0xDCFCE7);
    static final Color WRONG = new Color(0xDC2626);
    static final Color WRONG_SOFT = new Color(0xFEE2E2);
    static final Color WARN = new Color(0xD97706);
    static final Color WARN_SOFT = new Color(0xFEF3C7);
    static final Color TRACK = new Color(0xE5E7EB);
    static final Color SIDEBAR = new Color(0x0F172A);
    static final Color SIDEBAR_HOVER = new Color(0x1E293B);
    static final Color SIDEBAR_TEXT = new Color(0xCBD5E1);
    static final Color[] OPTION_COLORS = {
            new Color(0x2563EB), new Color(0xEA580C), new Color(0x0D9488), new Color(0xDB2777)
    };
    static final String[] OPTION_LETTERS = {"A", "B", "C", "D"};

    private Theme() {
    }

    static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    static JLabel label(String text, int style, int size, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font(style, size));
        label.setForeground(color);
        return label;
    }

    /** Multi-line read-only text. Unlike a JLabel, it never treats its text as HTML. */
    static JTextArea wrapped(String text, int style, int size, Color color) {
        JTextArea area = new JTextArea(text);
        area.setFont(font(style, size));
        area.setForeground(color);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setBorder(null);
        return area;
    }

    /** A white card with rounded corners and a soft shadow. */
    static JPanel card() {
        JPanel panel = new RoundPanel(CARD, 18, true);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 22, 20, 22));
        return panel;
    }

    static JPanel page() {
        JPanel panel = new JPanel();
        panel.setBackground(BACKGROUND);
        panel.setBorder(BorderFactory.createEmptyBorder(26, 30, 26, 30));
        return panel;
    }

    static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    static JTextField field(int columns) {
        return field(columns, "");
    }

    static JTextField field(int columns, String placeholder) {
        return new RoundField(columns, placeholder);
    }

    static JPasswordField passwordField(int columns) {
        return passwordField(columns, "");
    }

    static JPasswordField passwordField(int columns, String placeholder) {
        return new RoundPasswordField(columns, placeholder);
    }

    static JButton primary(String text) {
        return new RoundButton(text, PRIMARY, PRIMARY_DARK, Color.WHITE);
    }

    static JButton secondary(String text) {
        return new RoundButton(text, new Color(0xEEF0F4), new Color(0xE0E3EA), INK);
    }

    static JButton danger(String text) {
        return new RoundButton(text, WRONG_SOFT, new Color(0xFECACA), WRONG);
    }

    static JButton soft(String text) {
        return new RoundButton(text, PRIMARY_SOFT, new Color(0xE0E7FF), PRIMARY_DARK);
    }

    static JButton withIcon(JButton button, Icons.Kind kind) {
        button.setIcon(Icons.of(kind, 16, button.getForeground()));
        button.setIconTextGap(8);
        return button;
    }

    /** A text-only button that looks like a link. */
    static JButton link(String text) {
        JButton button = new JButton(text);
        button.setFont(font(Font.BOLD, 13));
        button.setForeground(PRIMARY);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setForeground(ACCENT);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setForeground(PRIMARY);
            }
        });
        return button;
    }

    static JButton option(int index, String text) {
        Color color = OPTION_COLORS[index];
        JButton button = new RoundButton(html(OPTION_LETTERS[index] + ".  " + text), color, color.darker(), Color.WHITE);
        button.setFont(font(Font.BOLD, 17));
        button.setPreferredSize(new Dimension(300, 96));
        return button;
    }

    /** Wraps text for a button with its special characters escaped, so List<String> displays correctly. */
    static String html(String text) {
        String escaped = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        return "<html><div style='text-align:center'>" + escaped + "</div></html>";
    }

    /** A small coloured label with a rounded background, e.g. "Verified". */
    static JLabel pill(String text, Color background, Color foreground) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                smooth(g);
                g.setColor(background);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        label.setFont(font(Font.BOLD, 12));
        label.setForeground(foreground);
        label.setBorder(BorderFactory.createEmptyBorder(3, 10, 3, 14));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(font(Font.PLAIN, 14));
        table.setRowHeight(34);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setGridColor(new Color(0xF0F1F4));
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setBackground(CARD);
        table.setSelectionBackground(PRIMARY_SOFT);
        table.setSelectionForeground(INK);
        table.getTableHeader().setFont(font(Font.BOLD, 12));
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setBackground(new Color(0xF9FAFB));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(10, 36));
        // Clean, left-aligned headers in small capitals with a single line underneath.
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(t, value, false, false, row, column);
                setText(value == null ? "" : value.toString().toUpperCase(java.util.Locale.ROOT));
                setFont(font(Font.BOLD, 11));
                setForeground(MUTED);
                setBackground(new Color(0xF8F9FC));
                setOpaque(true);
                setHorizontalAlignment(LEFT);
                setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                        BorderFactory.createEmptyBorder(0, 12, 0, 12)));
                return this;
            }
        });
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus,
                                                           int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, false, row, column);
                setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
                if (!selected) {
                    setBackground(row % 2 == 0 ? CARD : new Color(0xFAFAFE)); // Soft stripes are easier to follow.
                }
                return this;
            }
        });
        return table;
    }

    static JScrollPane scroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(CARD);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        slim(scroll);
        return scroll;
    }

    /** Thin, rounded scroll bars without arrow buttons, like modern apps. */
    static JScrollPane slim(JScrollPane scroll) {
        scroll.getVerticalScrollBar().setUI(new SlimScrollBarUI());
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(10, 0));
        scroll.getHorizontalScrollBar().setUI(new SlimScrollBarUI());
        scroll.getHorizontalScrollBar().setPreferredSize(new Dimension(0, 10));
        return scroll;
    }

    static final class SlimScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override
        protected void configureScrollBarColors() {
            thumbColor = new Color(0xCBD5E1);
            trackColor = new Color(0, 0, 0, 0);
        }

        @Override
        protected JButton createDecreaseButton(int orientation) {
            return noButton();
        }

        @Override
        protected JButton createIncreaseButton(int orientation) {
            return noButton();
        }

        private static JButton noButton() {
            JButton button = new JButton();
            button.setPreferredSize(new Dimension(0, 0));
            button.setMinimumSize(new Dimension(0, 0));
            button.setMaximumSize(new Dimension(0, 0));
            return button;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, java.awt.Rectangle bounds) {
            // A clear track: only the thumb is drawn.
        }

        @Override
        protected void paintThumb(Graphics graphics, JComponent c, java.awt.Rectangle r) {
            if (r.isEmpty() || !scrollbar.isEnabled()) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            g.setColor(isThumbRollover() ? new Color(0x94A3B8) : thumbColor);
            g.fillRoundRect(r.x + 2, r.y + 2, r.width - 4, r.height - 4, 8, 8);
            g.dispose();
        }
    }

    /** A page background: the usual soft grey, with two faint indigo glows in the corners. */
    static JPanel backdrop(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout) {
            @Override
            protected void paintComponent(Graphics graphics) {
                paintBackdrop(this, graphics);
            }
        };
        panel.setBackground(BACKGROUND);
        panel.setOpaque(true);
        return panel;
    }

    static void paintBackdrop(JComponent c, Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        smooth(g);
        int w = c.getWidth();
        int h = c.getHeight();
        g.setColor(BACKGROUND);
        g.fillRect(0, 0, w, h);
        float r1 = Math.max(260, w * 0.45f);
        g.setPaint(new java.awt.RadialGradientPaint(w, 0, r1, new float[]{0f, 1f},
                new Color[]{new Color(124, 58, 237, 26), new Color(124, 58, 237, 0)}));
        g.fillRect(0, 0, w, h);
        float r2 = Math.max(220, w * 0.35f);
        g.setPaint(new java.awt.RadialGradientPaint(0, h, r2, new float[]{0f, 1f},
                new Color[]{new Color(79, 70, 229, 20), new Color(79, 70, 229, 0)}));
        g.fillRect(0, 0, w, h);
        g.dispose();
    }

    /** A read-only table model with the given column names. */
    static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    /** "Anamika Edathatta" -> "AE". */
    static String initials(String name) {
        String[] parts = name.strip().split("\\s+");
        String first = parts[0].isEmpty() ? "?" : parts[0].substring(0, 1);
        String last = parts.length > 1 ? parts[parts.length - 1].substring(0, 1) : "";
        return (first + last).toUpperCase(java.util.Locale.ROOT);
    }

    /** Shortens text with "..." if it is wider than the space available. */
    static String fit(String text, FontMetrics metrics, int width) {
        if (metrics.stringWidth(text) <= width) {
            return text;
        }
        int length = text.length();
        while (length > 0 && metrics.stringWidth(text.substring(0, length) + "...") > width) {
            length--;
        }
        return text.substring(0, length) + "...";
    }

    /** A circle with the user's initials. */
    static Icon avatar(String name, int size) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                smooth(g);
                g.setPaint(new GradientPaint(x, y, PRIMARY, x + size, y + size, ACCENT));
                g.fillOval(x, y, size, size);
                g.setColor(Color.WHITE);
                g.setFont(font(Font.BOLD, size * 2 / 5));
                String text = initials(name);
                FontMetrics m = g.getFontMetrics();
                g.drawString(text, x + (size - m.stringWidth(text)) / 2, y + (size + m.getAscent() - m.getDescent()) / 2);
                g.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    // ----- Custom-painted widgets -----

    /** A panel with rounded corners and an optional soft shadow. */
    static class RoundPanel extends JPanel {
        private final int radius;
        private final boolean shadow;

        RoundPanel(Color background, int radius, boolean shadow) {
            this.radius = radius;
            this.shadow = shadow;
            setBackground(background);
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            int inset = shadow ? 3 : 0;
            if (shadow) {
                for (int i = 0; i < 3; i++) {
                    g.setColor(new Color(15, 23, 42, 8 + i * 3));
                    g.fillRoundRect(i, i + 1, getWidth() - i * 2, getHeight() - i * 2, radius + 4, radius + 4);
                }
            }
            g.setColor(getBackground());
            g.fillRoundRect(inset - 1, inset - 2, getWidth() - inset * 2 + 2, getHeight() - inset * 2 + 1, radius, radius);
            Color bg = getBackground();
            boolean dark = bg.getRed() + bg.getGreen() + bg.getBlue() < 300;
            g.setColor(dark ? new Color(255, 255, 255, 22) : new Color(0xE9EBF0)); // A faint edge on dark cards.
            g.drawRoundRect(inset - 1, inset - 2, getWidth() - inset * 2 + 1, getHeight() - inset * 2, radius, radius);
            g.dispose();
        }
    }

    /** A flat button with rounded corners and a smooth hover fade, drawn by hand. */
    static final class RoundButton extends JButton {
        private final Color normal;
        private final Color hover;
        private float hoverAmount;
        private final Timer fade = new Timer(15, null);
        private boolean hovering;
        int minHeight;

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();
            return new Dimension(size.width, Math.max(size.height, minHeight));
        }

        RoundButton(String text, Color normal, Color hover, Color foreground) {
            super(text);
            this.normal = normal;
            this.hover = hover;
            setForeground(foreground);
            setFont(font(Font.BOLD, 14));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(11, 20, 11, 20));
            fade.addActionListener(e -> {
                hoverAmount = Math.max(0, Math.min(1, hoverAmount + (hovering ? 0.15f : -0.15f)));
                if (hoverAmount == 0 || hoverAmount == 1) {
                    fade.stop();
                }
                repaint();
            });
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hovering = true;
                    fade.start();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hovering = false;
                    fade.start();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            if (isEnabled() && normal.equals(PRIMARY)) {
                // Main action buttons: an indigo-to-violet gradient with a soft glow that grows on hover.
                int w = getWidth();
                int h = getHeight();
                g.setColor(new Color(79, 70, 229, Math.round(22 + 40 * hoverAmount)));
                g.fillRoundRect(2, 3, w - 4, h - 3, 14, 14);
                Color from = mix(PRIMARY, new Color(0x6366F1), hoverAmount);
                Color to = mix(ACCENT, new Color(0x8B5CF6), hoverAmount);
                if (getModel().isPressed()) {
                    from = from.darker();
                    to = to.darker();
                }
                g.setPaint(new GradientPaint(0, 0, from, w, h, to));
                g.fillRoundRect(0, 0, w, h - 2, 12, 12);
                g.setColor(new Color(255, 255, 255, 28)); // A faint shine on the top half.
                g.fillRoundRect(1, 1, w - 2, (h - 2) / 2, 12, 12);
            } else {
                Color fill = !isEnabled() ? new Color(0xD1D5DB) : getModel().isPressed() ? hover.darker() : mix(normal, hover, hoverAmount);
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
            }
            if (isFocusOwner()) {
                g.setColor(new Color(99, 102, 241, 120));
                g.setStroke(new BasicStroke(2f));
                g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
            }
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    static Color mix(Color a, Color b, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }

    /** Rounded outline that turns indigo when the field has focus. */
    static final class FieldBorder extends AbstractBorder {
        @Override
        public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            smooth(g);
            boolean focused = c.hasFocus();
            if (focused) {
                g.setColor(new Color(99, 102, 241, 50));
                g.setStroke(new BasicStroke(4f));
                g.drawRoundRect(x + 2, y + 2, width - 5, height - 5, 10, 10);
            }
            g.setStroke(new BasicStroke(focused ? 1.6f : 1.2f));
            g.setColor(focused ? PRIMARY : FIELD_BORDER);
            g.drawRoundRect(x + 1, y + 1, width - 3, height - 3, 10, 10);
            g.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(10, 13, 10, 13);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(10, 13, 10, 13);
            return insets;
        }
    }

    private static void styleField(JTextComponent field) {
        field.setFont(font(Font.PLAIN, 15));
        field.setForeground(INK);
        field.setCaretColor(PRIMARY);
        field.setSelectionColor(new Color(0xC7D2FE));
        field.setOpaque(false);
        field.setBorder(new FieldBorder());
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                field.repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                field.repaint();
            }
        });
    }

    /** Paints the white rounded background, and grey hint text while the field is empty. */
    private static void paintFieldBackground(JTextComponent field, Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        smooth(g);
        g.setColor(field.isEnabled() ? Color.WHITE : new Color(0xF3F4F6));
        g.fillRoundRect(1, 1, field.getWidth() - 2, field.getHeight() - 2, 10, 10);
        g.dispose();
    }

    private static void paintPlaceholder(JTextComponent field, String placeholder, Graphics graphics) {
        if (placeholder.isEmpty() || field.getDocument().getLength() > 0) {
            return;
        }
        Graphics2D g = (Graphics2D) graphics.create();
        smooth(g);
        g.setColor(SUBTLE);
        g.setFont(field.getFont());
        Insets insets = field.getInsets();
        FontMetrics m = g.getFontMetrics();
        int y = (field.getHeight() + m.getAscent() - m.getDescent()) / 2;
        g.drawString(placeholder, insets.left, y);
        g.dispose();
    }

    static final class RoundField extends JTextField {
        private String placeholder;

        RoundField(int columns, String placeholder) {
            super(columns);
            this.placeholder = placeholder;
            styleField(this);
        }

        void setPlaceholder(String text) {
            placeholder = text;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintFieldBackground(this, g);
            super.paintComponent(g);
            paintPlaceholder(this, placeholder, g);
        }
    }

    /**
     * A password box with an eye button on the right that shows or hides what was typed.
     * A "confirm" box also shows a green tick or a red cross while typing, telling whether
     * it matches the first password box.
     */
    static final class RoundPasswordField extends JPasswordField {
        private static final char DOT = '•';
        private static final int EYE_AREA = 34;
        private final String placeholder;
        private boolean shown;
        private JPasswordField original; // Set only on a "confirm" box.

        RoundPasswordField(int columns, String placeholder) {
            super(columns);
            this.placeholder = placeholder;
            styleField(this);
            setEchoChar(DOT);
            // Room on the right for the eye (and the tick on a confirm box).
            setBorder(BorderFactory.createCompoundBorder(new FieldBorder(), BorderFactory.createEmptyBorder(0, 0, 0, EYE_AREA)));
            setToolTipText("Show password");
            java.awt.event.MouseAdapter mouse = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (overEye(e.getX())) {
                        setShown(!shown);
                        e.consume();
                    }
                }

                @Override
                public void mouseMoved(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(overEye(e.getX()) ? Cursor.HAND_CURSOR : Cursor.TEXT_CURSOR));
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        boolean isShown() {
            return shown;
        }

        void setShown(boolean value) {
            shown = value;
            setEchoChar(shown ? (char) 0 : DOT);
            setToolTipText(shown ? "Hide password" : "Show password");
            repaint();
        }

        /** Makes this a confirm box that checks itself against the first password box. */
        void confirms(JPasswordField first) {
            original = first;
            javax.swing.event.DocumentListener repaint = new javax.swing.event.DocumentListener() {
                @Override
                public void insertUpdate(javax.swing.event.DocumentEvent e) {
                    repaint();
                }

                @Override
                public void removeUpdate(javax.swing.event.DocumentEvent e) {
                    repaint();
                }

                @Override
                public void changedUpdate(javax.swing.event.DocumentEvent e) {
                    repaint();
                }
            };
            getDocument().addDocumentListener(repaint);
            first.getDocument().addDocumentListener(repaint);
        }

        /** True when this confirm box is filled in and equals the first box. */
        boolean matches() {
            return original != null && getDocument().getLength() > 0
                    && java.util.Arrays.equals(getPassword(), original.getPassword());
        }

        private boolean overEye(int x) {
            return x >= getWidth() - EYE_AREA - 8;
        }

        @Override
        protected void paintComponent(Graphics g) {
            paintFieldBackground(this, g);
            super.paintComponent(g);
            paintPlaceholder(this, placeholder, g);
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int cy = getHeight() / 2;
            int eyeX = getWidth() - 31;
            // The eye: an almond shape and a pupil; crossed out while the password is hidden.
            g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(shown ? PRIMARY : SUBTLE);
            java.awt.geom.Path2D eye = new java.awt.geom.Path2D.Double();
            eye.moveTo(eyeX, cy);
            eye.quadTo(eyeX + 9, cy - 9, eyeX + 18, cy);
            eye.quadTo(eyeX + 9, cy + 9, eyeX, cy);
            g2.draw(eye);
            g2.fillOval(eyeX + 6, cy - 3, 6, 6);
            if (!shown) {
                g2.drawLine(eyeX + 2, cy + 7, eyeX + 16, cy - 7);
            }
            // The match mark on a confirm box, just left of the eye.
            if (original != null && getDocument().getLength() > 0) {
                int mx = eyeX - 22;
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (matches()) {
                    g2.setColor(CORRECT);
                    g2.drawPolyline(new int[]{mx, mx + 5, mx + 13}, new int[]{cy, cy + 5, cy - 5}, 3);
                } else {
                    g2.setColor(WRONG);
                    g2.drawLine(mx + 1, cy - 5, mx + 11, cy + 5);
                    g2.drawLine(mx + 1, cy + 5, mx + 11, cy - 5);
                }
            }
            g2.dispose();
        }
    }

    /** Puts text on the system clipboard, so it can be pasted with Ctrl+V. Returns false if the clipboard was busy. */
    static boolean copyText(String text) {
        try {
            java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new java.awt.datatransfer.StringSelection(text), null);
            return true;
        } catch (IllegalStateException e) {
            return false; // Another program is using the clipboard right now.
        }
    }

    /** Copies text and briefly shows "Copied!" on the button, then puts its label back. */
    static void copyWithFeedback(JButton button, String text, AppFrame app, String what) {
        if (!copyText(text)) {
            app.toast("Could not copy: the clipboard is busy. Try again.", ToastLayer.Kind.ERROR);
            return;
        }
        String label = button.getText();
        if (!label.equals("Copied!")) {
            button.setText("Copied!");
            Timer reset = new Timer(1500, e -> button.setText(label));
            reset.setRepeats(false);
            reset.start();
        }
        app.toast(what + " copied. Paste it anywhere with Ctrl+V.", ToastLayer.Kind.SUCCESS);
    }

    /** Turns a password box into a "confirm" box for `first`: it shows a tick when both match. */
    static void confirms(JPasswordField confirm, JPasswordField first) {
        if (confirm instanceof RoundPasswordField round) {
            round.confirms(first);
            round.setBorder(BorderFactory.createCompoundBorder(new FieldBorder(),
                    BorderFactory.createEmptyBorder(0, 0, 0, RoundPasswordField.EYE_AREA + 22)));
        }
    }

    /**
     * Gives a component a minimum height. For buttons the width still follows the text,
     * so a button whose text changes later (e.g. "Show final results") is never cut off.
     */
    static <T extends JComponent> T height(T component, int height) {
        if (component instanceof RoundButton button) {
            button.minHeight = height;
            return component;
        }
        Dimension size = component.getPreferredSize();
        component.setPreferredSize(new Dimension(size.width, height));
        return component;
    }

    /** Lists use the viewport width, so long rows are shortened instead of adding a sideways scroll bar. */
    static <T extends javax.swing.JList<?>> T fitWidth(T list) {
        list.setFixedCellWidth(40);
        return list;
    }
}
