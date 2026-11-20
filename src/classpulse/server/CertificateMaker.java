package classpulse.server;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;

/**
 * Draws a certificate for a winning student (see deserves), using Java2D, and returns it
 * as PNG bytes so it can be attached to the result email. No image files or libraries are
 * needed: every line, star and letter is drawn by this class.
 */
final class CertificateMaker {
    static final int WIDTH = 1600;
    static final int HEIGHT = 1130;

    private static final Color INDIGO = new Color(0x4338CA);
    private static final Color VIOLET = new Color(0x7C3AED);
    private static final Color INK = new Color(0x1F2937);
    private static final Color MUTED = new Color(0x6B7280);
    private static final Color GOLD = new Color(0xF59E0B);
    private static final Color SILVER = new Color(0x94A3B8);
    private static final Color BRONZE = new Color(0xB45309);

    private CertificateMaker() {
    }

    /**
     * Who gets a certificate:
     *   - playing alone: full marks, or just one mark less (e.g. 6/6 or 5/6);
     *   - with others: the winner. Equal top scores share rank 1, so every one of them gets one.
     */
    static boolean deserves(int rank, int players, int correct, int questions) {
        if (players <= 1) {
            return questions > 0 && correct >= Math.max(1, questions - 1);
        }
        return rank == 1;
    }

    /** The reason printed on the certificate and in the email, for example "1st place" or "5 of 6 correct". */
    static String achievement(int rank, int players, int correct, int questions) {
        return players <= 1 ? correct + " of " + questions + " correct" : placeName(rank);
    }

    static String placeName(int rank) {
        return switch (rank) {
            case 1 -> "1st place";
            case 2 -> "2nd place";
            case 3 -> "3rd place";
            default -> "Rank " + rank;
        };
    }

    static String fileName(String studentName) {
        String safe = studentName.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return "ClassPulse-certificate-" + (safe.isEmpty() ? "student" : safe) + ".png";
    }

    static byte[] png(String studentName, String quizTitle, int rank, int players, int score, int correct,
                      int questions, String date) {
        BufferedImage image = draw(studentName, quizTitle, rank, players, score, correct, questions, date);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not encode the certificate.", e);
        }
    }

    static BufferedImage draw(String studentName, String quizTitle, int rank, int players, int score, int correct,
                              int questions, String date) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Background and a double border in the app's indigo colours.
        g.setColor(new Color(0xF8F7FF));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setPaint(new GradientPaint(0, 0, INDIGO, WIDTH, HEIGHT, VIOLET));
        g.setStroke(new BasicStroke(26));
        g.drawRect(13, 13, WIDTH - 26, HEIGHT - 26);
        g.setStroke(new BasicStroke(3));
        g.drawRect(54, 54, WIDTH - 108, HEIGHT - 108);

        // Soft decorative circles in two corners.
        g.setColor(new Color(124, 58, 237, 22));
        g.fillOval(-160, -160, 520, 520);
        g.fillOval(WIDTH - 360, HEIGHT - 360, 520, 520);

        Color medal = switch (rank) {
            case 1 -> GOLD;
            case 2 -> SILVER;
            default -> BRONZE;
        };

        centered(g, "CLASSPULSE", new Font(Font.SANS_SERIF, Font.BOLD, 30), INDIGO, 150, 10f);
        centered(g, "Certificate of Achievement", new Font(Font.SERIF, Font.BOLD, 76), INK, 250, 0);
        centered(g, "This certificate is proudly presented to", new Font(Font.SANS_SERIF, Font.PLAIN, 30), MUTED, 340, 0);

        Font nameFont = fitted(g, studentName, new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 96), WIDTH - 360);
        centered(g, studentName, nameFont, INDIGO, 470, 0);
        g.setColor(new Color(0xC7D2FE));
        g.setStroke(new BasicStroke(3));
        g.drawLine(WIDTH / 2 - 420, 505, WIDTH / 2 + 420, 505);

        String line1 = players <= 1
                ? "for scoring " + correct + " out of " + questions + " in"
                : "for winning " + placeName(rank) + " out of " + players + " players in";
        centered(g, line1, new Font(Font.SANS_SERIF, Font.PLAIN, 32), INK, 580, 0);
        Font quizFont = fitted(g, quizTitle, new Font(Font.SANS_SERIF, Font.BOLD, 44), WIDTH - 360);
        centered(g, quizTitle, quizFont, INK, 645, 0);
        centered(g, "with " + score + " points", new Font(Font.SANS_SERIF, Font.PLAIN, 32), MUTED, 700, 0);

        drawMedal(g, WIDTH / 2, 895, medal, rank);

        Font small = new Font(Font.SANS_SERIF, Font.PLAIN, 26);
        g.setFont(small);
        g.setColor(MUTED);
        g.drawString(date, 180, HEIGHT - 150);
        g.drawLine(170, HEIGHT - 190, 520, HEIGHT - 190);
        g.drawString("Date", 180, HEIGHT - 115);
        String issuer = "ClassPulse Live Quiz";
        int issuerWidth = g.getFontMetrics().stringWidth(issuer);
        g.drawString(issuer, WIDTH - 180 - issuerWidth, HEIGHT - 150);
        g.drawLine(WIDTH - 520, HEIGHT - 190, WIDTH - 170, HEIGHT - 190);
        String issued = "Issued by";
        g.drawString(issued, WIDTH - 180 - g.getFontMetrics().stringWidth(issued), HEIGHT - 115);

        g.dispose();
        return image;
    }

    /** A round medal with a ribbon, a star and the place number. */
    private static void drawMedal(Graphics2D g, int cx, int cy, Color colour, int rank) {
        g.setColor(INDIGO);
        Path2D ribbon = new Path2D.Double();
        ribbon.moveTo(cx - 70, cy - 150);
        ribbon.lineTo(cx - 20, cy - 40);
        ribbon.lineTo(cx + 20, cy - 40);
        ribbon.lineTo(cx + 70, cy - 150);
        ribbon.lineTo(cx + 30, cy - 150);
        ribbon.lineTo(cx, cy - 80);
        ribbon.lineTo(cx - 30, cy - 150);
        ribbon.closePath();
        g.fill(ribbon);

        int r = 92;
        g.setPaint(new GradientPaint(cx - r, cy - r, colour.brighter(), cx + r, cy + r, colour.darker()));
        g.fillOval(cx - r, cy - r, r * 2, r * 2);
        g.setColor(new Color(255, 255, 255, 120));
        g.setStroke(new BasicStroke(5));
        g.drawOval(cx - r + 12, cy - r + 12, (r - 12) * 2, (r - 12) * 2);

        g.setColor(new Color(255, 255, 255, 230));
        g.fill(star(cx, cy - 18, 40, 17));
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, 40);
        g.setFont(font);
        String text = String.valueOf(rank);
        FontMetrics metrics = g.getFontMetrics();
        g.setColor(Color.WHITE);
        g.drawString(text, cx - metrics.stringWidth(text) / 2, cy + 62);
    }

    private static Path2D star(double cx, double cy, double outer, double inner) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double radius = i % 2 == 0 ? outer : inner;
            double angle = Math.PI / 5 * i - Math.PI / 2;
            double x = cx + Math.cos(angle) * radius;
            double y = cy + Math.sin(angle) * radius;
            if (i == 0) {
                path.moveTo(x, y);
            } else {
                path.lineTo(x, y);
            }
        }
        path.closePath();
        return path;
    }

    /** Draws text centred across the page; tracking adds letter spacing. */
    private static void centered(Graphics2D g, String text, Font font, Color colour, int baseline, float tracking) {
        Font used = tracking == 0 ? font
                : font.deriveFont(java.util.Map.of(java.awt.font.TextAttribute.TRACKING, tracking / font.getSize()));
        g.setFont(used);
        g.setColor(colour);
        int width = g.getFontMetrics().stringWidth(text);
        g.drawString(text, (WIDTH - width) / 2, baseline);
    }

    /** Shrinks the font until a long name or title fits the page. */
    private static Font fitted(Graphics2D g, String text, Font font, int maxWidth) {
        Font current = font;
        while (current.getSize() > 24 && g.getFontMetrics(current).stringWidth(text) > maxWidth) {
            current = current.deriveFont((float) current.getSize() - 4);
        }
        return current;
    }
}
