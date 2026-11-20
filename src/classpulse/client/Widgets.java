package classpulse.client;

import classpulse.common.Validation;
import classpulse.common.Views.Badge;
import classpulse.common.Views.QuestionStat;
import classpulse.common.Views.ScoreEntry;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

/** Small hand-drawn components used on several screens. */
final class Widgets {
    private Widgets() {
    }

    /** A page title with a smaller line under it, and action buttons on the right. */
    static JPanel header(JLabel title, JLabel subtitle, JComponent... actions) {
        JPanel header = Theme.transparent(new BorderLayout(16, 0));
        JPanel words = Theme.transparent(new BorderLayout(0, 4));
        words.add(title, BorderLayout.NORTH);
        words.add(subtitle, BorderLayout.CENTER);
        header.add(words, BorderLayout.CENTER);
        JPanel buttons = Theme.transparent(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 10, 4));
        for (JComponent action : actions) {
            buttons.add(action);
        }
        header.add(buttons, BorderLayout.EAST);
        return header;
    }

    static JLabel pageTitle(String text) {
        return Theme.label(text, Font.BOLD, 26, Theme.INK);
    }

    static JLabel pageSubtitle(String text) {
        return Theme.label(text, Font.PLAIN, 14, Theme.MUTED);
    }

    /** A card with a bold heading at the top. */
    static JPanel titledCard(String heading, JComponent body) {
        JPanel card = Theme.card();
        card.setLayout(new BorderLayout(0, 12));
        card.add(Theme.label(heading, Font.BOLD, 16, Theme.INK), BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    /** A number with a caption and a coloured icon, for dashboards. */
    static final class StatCard extends Theme.RoundPanel {
        private static final java.util.regex.Pattern NUMBER = java.util.regex.Pattern.compile("^(\\D*)([\\d,]+)(.*)$");
        private final JLabel value = Theme.label("-", Font.BOLD, 26, Theme.INK);
        private final Color accent;
        /** The final text. The label may show a counting-up number on the way there. */
        private String target = "-";
        private Timer counter;

        StatCard(String caption, Icons.Kind icon, Color accent) {
            super(Theme.CARD, 18, true);
            this.accent = accent;
            setLayout(new BorderLayout(14, 0));
            setBorder(BorderFactory.createEmptyBorder(16, 18, 16, 18));
            JLabel badge = new JLabel(Icons.of(icon, 22, accent)) {
                @Override
                protected void paintComponent(Graphics graphics) {
                    Graphics2D g = (Graphics2D) graphics.create();
                    Theme.smooth(g);
                    g.setPaint(new GradientPaint(0, 0, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40),
                            getWidth(), getHeight(), new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 14)));
                    g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                    g.dispose();
                    super.paintComponent(graphics);
                }
            };
            badge.setPreferredSize(new Dimension(46, 46));
            add(badge, BorderLayout.WEST);
            JPanel text = Theme.transparent(new java.awt.GridLayout(2, 1));
            text.add(value);
            text.add(Theme.label(caption, Font.PLAIN, 13, Theme.MUTED));
            add(text, BorderLayout.CENTER);
        }

        /** Shows the new value; a number counts up to it in about half a second. */
        void setValue(String text) {
            if (counter != null) {
                counter.stop();
            }
            boolean changed = !text.equals(target);
            target = text;
            java.util.regex.Matcher m = NUMBER.matcher(text);
            if (!changed || !m.matches()) {
                value.setText(text);
                return;
            }
            String prefix = m.group(1);
            String digits = m.group(2);
            String suffix = m.group(3);
            boolean commas = digits.contains(",");
            long end;
            try {
                end = Long.parseLong(digits.replace(",", ""));
            } catch (NumberFormatException e) {
                value.setText(text);
                return;
            }
            if (end == 0) {
                value.setText(text);
                return;
            }
            long startTime = System.nanoTime();
            long durationNanos = 650_000_000L;
            counter = new Timer(16, null);
            counter.addActionListener(e -> {
                double t = Math.min(1.0, (System.nanoTime() - startTime) / (double) durationNanos);
                double eased = 1 - Math.pow(1 - t, 3); // Fast at first, gently slowing down.
                long now = Math.round(end * eased);
                String number = commas ? String.format(Locale.ROOT, "%,d", now) : String.valueOf(now);
                value.setText(t >= 1.0 ? target : prefix + number + suffix);
                if (t >= 1.0) {
                    ((Timer) e.getSource()).stop();
                }
            });
            counter.start();
        }

        /** The final value, even while the number is still counting up. */
        String value() {
            return target;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            // A thin coloured accent line along the top of the card.
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            g.setPaint(new GradientPaint(18, 0, accent, getWidth() - 18, 0,
                    new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 0)));
            g.fillRoundRect(18, 1, getWidth() - 36, 3, 3, 3);
            g.dispose();
        }
    }

    /** Four bars that fill up as a password gets stronger. */
    static final class StrengthMeter extends JComponent {
        private int level = -1;

        StrengthMeter() {
            setPreferredSize(new Dimension(200, 18));
        }

        void update(String password) {
            level = password.isEmpty() ? -1 : Validation.passwordStrength(password);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            Color[] colors = {Theme.WRONG, Theme.WARN, new Color(0x65A30D), Theme.CORRECT};
            String[] names = {"Too short", "Weak", "Fair", "Strong"};
            int barWidth = (getWidth() - 110) / 4;
            if (level >= 0) {
                for (int i = 0; i < 4; i++) {
                    g.setColor(level >= i ? colors[level] : Theme.TRACK);
                    g.fillRoundRect(i * (barWidth + 5), 6, barWidth, 5, 5, 5);
                }
                g.setFont(Theme.font(Font.BOLD, 12));
                g.setColor(colors[level]);
                g.drawString(names[level], 4 * (barWidth + 5) + 6, 13);
            } else {
                g.setFont(Theme.font(Font.PLAIN, 12));
                g.setColor(Theme.SUBTLE);
                g.drawString("8+ chars, a letter and a digit", 0, 13);
            }
            g.dispose();
        }
    }

    /** Accuracy over time for a student, oldest game on the left. */
    static final class LineChart extends JComponent {
        private List<Integer> values = List.of();
        private List<String> labels = List.of();

        LineChart() {
            setPreferredSize(new Dimension(400, 190));
        }

        void setData(List<Integer> percentages, List<String> pointLabels) {
            values = List.copyOf(percentages);
            labels = List.copyOf(pointLabels);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            int left = 40;
            int right = getWidth() - 16;
            int top = 12;
            int bottom = getHeight() - 26;
            g.setFont(Theme.font(Font.PLAIN, 11));
            for (int p = 0; p <= 100; p += 25) {
                int y = bottom - (bottom - top) * p / 100;
                g.setColor(new Color(0xF1F2F6));
                g.drawLine(left, y, right, y);
                g.setColor(Theme.SUBTLE);
                g.drawString(p + "%", 4, y + 4);
            }
            if (values.isEmpty()) {
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(Font.PLAIN, 14));
                g.drawString("Play a quiz to see your progress here.", left + 20, (top + bottom) / 2);
                g.dispose();
                return;
            }
            int n = values.size();
            double step = n == 1 ? 0 : (right - left - 20) / (double) (n - 1);
            Path2D line = new Path2D.Double();
            Path2D area = new Path2D.Double();
            for (int i = 0; i < n; i++) {
                double x = left + 10 + (n == 1 ? (right - left - 20) / 2.0 : i * step);
                double y = bottom - (bottom - top) * values.get(i) / 100.0;
                if (i == 0) {
                    line.moveTo(x, y);
                    area.moveTo(x, bottom);
                }
                line.lineTo(x, y);
                area.lineTo(x, y);
                if (i == n - 1) {
                    area.lineTo(x, bottom);
                }
            }
            area.closePath();
            g.setPaint(new GradientPaint(0, top, new Color(79, 70, 229, 70), 0, bottom, new Color(79, 70, 229, 0)));
            g.fill(area);
            g.setColor(Theme.PRIMARY);
            g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(line);
            for (int i = 0; i < n; i++) {
                int x = (int) Math.round(left + 10 + (n == 1 ? (right - left - 20) / 2.0 : i * step));
                int y = (int) Math.round(bottom - (bottom - top) * values.get(i) / 100.0);
                g.setColor(Color.WHITE);
                g.fillOval(x - 5, y - 5, 10, 10);
                g.setColor(Theme.PRIMARY);
                g.drawOval(x - 5, y - 5, 10, 10);
                if (n <= 12 || i % 2 == 0) {
                    g.setColor(Theme.SUBTLE);
                    String label = labels.get(i);
                    g.drawString(label, x - g.getFontMetrics().stringWidth(label) / 2, bottom + 18);
                }
            }
            g.dispose();
        }
    }

    /** One row per question: how many got it right, coloured green, amber or red. */
    static final class PercentChart extends JComponent {
        private List<QuestionStat> stats = List.of();

        PercentChart() {
            setPreferredSize(new Dimension(420, 240));
        }

        void setStats(List<QuestionStat> questions) {
            stats = List.copyOf(questions);
            setPreferredSize(new Dimension(420, Math.max(120, stats.size() * 50 + 10)));
            revalidate();
            repaint();
        }

        static int percent(QuestionStat q) {
            return q.answered() == 0 ? 0 : Math.round(100f * q.correct() / q.answered());
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            if (stats.isEmpty()) {
                g.setColor(Theme.MUTED);
                g.setFont(Theme.font(Font.PLAIN, 14));
                g.drawString("Select a session to see how each question went.", 8, 30);
                g.dispose();
                return;
            }
            int width = getWidth() - 22; // Room for the scroll bar, so the times are never clipped.
            for (int i = 0; i < stats.size(); i++) {
                QuestionStat q = stats.get(i);
                int top = i * 50 + 6;
                int pct = percent(q);
                Color color = q.answered() == 0 ? Theme.SUBTLE : pct >= 70 ? Theme.CORRECT : pct >= 40 ? Theme.WARN : Theme.WRONG;
                g.setFont(Theme.font(Font.BOLD, 13));
                g.setColor(Theme.INK);
                String right = q.answered() == 0 ? "no answers"
                        : pct + "%  |  " + String.format(Locale.ROOT, "%.1f s", q.averageMs() / 1000.0);
                int rightWidth = g.getFontMetrics().stringWidth(right);
                g.drawString(Theme.fit("Q" + q.number() + "  " + q.text(), g.getFontMetrics(), width - rightWidth - 20), 0, top + 14);
                g.setColor(color);
                g.drawString(right, width - rightWidth, top + 14);
                g.setColor(Theme.TRACK);
                g.fillRoundRect(0, top + 24, width, 10, 10, 10);
                g.setColor(color);
                if (pct > 0) {
                    g.fillRoundRect(0, top + 24, Math.max(10, width * pct / 100), 10, 10, 10);
                }
            }
            g.dispose();
        }
    }

    /** First, second and third place on stands, with a rising animation. */
    static final class Podium extends JComponent {
        private List<ScoreEntry> top = List.of();
        private float growth = 1f;
        private final Timer rise = new Timer(15, e -> {
            growth = Math.min(1f, growth + 0.04f);
            if (growth >= 1f) {
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });

        Podium() {
            setPreferredSize(new Dimension(520, 230));
        }

        // ----- Confetti (added in the design upgrade) -----
        private static final Color[] CONFETTI = {new Color(0x4F46E5), new Color(0x7C3AED), new Color(0xF59E0B),
                new Color(0x10B981), new Color(0xEC4899), new Color(0x0EA5E9)};
        private final java.util.List<float[]> pieces = new java.util.ArrayList<>(); // x, y, vx, vy, angle, spin, colour, size
        private final java.util.Random random = new java.util.Random();
        private long confettiStart;
        private final Timer confetti = new Timer(16, e -> {
            for (float[] p : pieces) {
                p[0] += p[2];
                p[1] += p[3];
                p[3] = Math.min(p[3] + 0.06f, 3.2f); // Gravity, with a gentle top speed.
                p[2] *= 0.99f;
                p[4] += p[5];
            }
            if (System.nanoTime() - confettiStart > 4_500_000_000L) {
                pieces.clear();
                ((Timer) e.getSource()).stop();
            }
            repaint();
        });

        void setRanking(List<ScoreEntry> ranking) {
            top = ranking.stream().limit(3).toList();
            growth = 0f;
            rise.start();
            launchConfetti();
        }

        private void launchConfetti() {
            pieces.clear();
            int width = Math.max(300, getWidth());
            for (int i = 0; i < 140; i++) {
                pieces.add(new float[]{random.nextFloat() * width, -random.nextFloat() * 260 - 10,
                        (random.nextFloat() - 0.5f) * 1.6f, 1f + random.nextFloat() * 2f,
                        random.nextFloat() * 6.28f, (random.nextFloat() - 0.5f) * 0.25f,
                        random.nextInt(CONFETTI.length), 5 + random.nextFloat() * 5});
            }
            confettiStart = System.nanoTime();
            confetti.restart();
        }

        void finishAnimation() {
            rise.stop();
            confetti.stop();
            pieces.clear();
            growth = 1f;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            int columnWidth = Math.min(150, getWidth() / 3 - 12);
            int center = getWidth() / 2;
            int[] order = {1, 0, 2}; // Second place on the left, first in the middle, third on the right.
            int[] heights = {110, 80, 60};
            Color[] colors = {new Color(0xF59E0B), new Color(0x94A3B8), new Color(0xB45309)};
            int base = getHeight() - 4;
            for (int slot = 0; slot < 3; slot++) {
                int place = order[slot];
                if (place >= top.size()) {
                    continue;
                }
                ScoreEntry entry = top.get(place);
                int x = center + (slot - 1) * (columnWidth + 12) - columnWidth / 2;
                int h = Math.round(heights[place] * growth);
                g.setPaint(new GradientPaint(0, base - h, colors[place], 0, base, colors[place].darker()));
                g.fillRoundRect(x, base - h, columnWidth, h + 10, 14, 14);
                g.setColor(Color.WHITE);
                g.setFont(Theme.font(Font.BOLD, 26));
                String number = String.valueOf(entry.rank());
                FontMetrics big = g.getFontMetrics();
                if (h > 40) {
                    g.drawString(number, x + (columnWidth - big.stringWidth(number)) / 2, base - h + 36);
                }
                g.setFont(Theme.font(Font.BOLD, 15));
                g.setColor(Theme.INK);
                FontMetrics m = g.getFontMetrics();
                String name = Theme.fit(entry.name(), m, columnWidth);
                g.drawString(name, x + (columnWidth - m.stringWidth(name)) / 2, base - h - 30);
                g.setFont(Theme.font(Font.PLAIN, 13));
                g.setColor(Theme.MUTED);
                String score = entry.score() + " pts";
                g.drawString(score, x + (columnWidth - g.getFontMetrics().stringWidth(score)) / 2, base - h - 12);
                if (place == 0) {
                    g.setColor(new Color(0xF59E0B));
                    g.fill(Icons.star(x + columnWidth / 2.0, base - h - 62, 13, 5.5));
                }
            }
            for (float[] p : pieces) {
                java.awt.geom.AffineTransform before = g.getTransform();
                g.translate(p[0], p[1]);
                g.rotate(p[4]);
                g.setColor(CONFETTI[(int) p[6]]);
                g.fillRect(Math.round(-p[7] / 2), Math.round(-p[7] / 4), Math.round(p[7]), Math.round(p[7] / 2));
                g.setTransform(before);
            }
            g.dispose();
        }
    }

    /** An achievement: a coloured medal when earned, grey with a lock when not yet. */
    static final class BadgeTile extends Theme.RoundPanel {
        private final Badge badge;

        BadgeTile(Badge badge) {
            super(Theme.CARD, 18, true);
            this.badge = badge;
            setLayout(new BorderLayout(0, 4));
            setBorder(BorderFactory.createEmptyBorder(92, 16, 16, 16));
            JLabel name = Theme.label(badge.name(), Font.BOLD, 15, badge.earned() ? Theme.INK : Theme.MUTED);
            name.setHorizontalAlignment(JLabel.CENTER);
            add(name, BorderLayout.NORTH);
            javax.swing.JTextArea description = Theme.wrapped(badge.description(), Font.PLAIN, 12, Theme.MUTED);
            add(description, BorderLayout.CENTER);
            JLabel progress = Theme.label(badge.earned() ? "Unlocked" : badge.progress(), Font.BOLD, 12,
                    badge.earned() ? Theme.CORRECT : Theme.SUBTLE);
            progress.setHorizontalAlignment(JLabel.CENTER);
            add(progress, BorderLayout.SOUTH);
            setPreferredSize(new Dimension(200, 196));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            Theme.smooth(g);
            int size = 64;
            int x = (getWidth() - size) / 2;
            int y = 18;
            if (badge.earned()) {
                g.setPaint(new GradientPaint(x, y, new Color(0xFBBF24), x + size, y + size, new Color(0xF97316)));
            } else {
                g.setColor(new Color(0xE5E7EB));
            }
            g.fillOval(x, y, size, size);
            g.setColor(badge.earned() ? new Color(255, 255, 255, 90) : new Color(0xF3F4F6));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(x + 5, y + 5, size - 10, size - 10);
            g.translate(x + 18, y + 18);
            g.scale(28 / 24.0, 28 / 24.0);
            g.setColor(badge.earned() ? Color.WHITE : Theme.SUBTLE);
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Icons.draw(g, badge.earned() ? Icons.Kind.STAR : Icons.Kind.LOCK);
            g.dispose();
        }
    }
}
