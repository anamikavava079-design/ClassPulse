package classpulse.client;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * Line icons drawn with Java 2D on a 24 x 24 grid and scaled to any size.
 * Drawing them in code means the app needs no image files and stays sharp on any screen.
 */
final class Icons {
    enum Kind {
        STUDENT, TEACHER, GRID, LIST, CHART, USER, LOGOUT, PLAY, TROPHY, CLOCK, MAIL, LOCK,
        PLUS, EDIT, COPY, TRASH, UPLOAD, DOWNLOAD, SEND, CHECK, CROSS, STAR, SHUFFLE, LOGO, USERS, FIRE
    }

    private Icons() {
    }

    static Icon of(Kind kind, int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                Theme.smooth(g);
                g.translate(x, y);
                g.scale(size / 24.0, size / 24.0);
                g.setColor(color);
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                draw(g, kind);
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

    /** Draws one icon into a 24 x 24 box at the current origin. */
    static void draw(Graphics2D g, Kind kind) {
        switch (kind) {
            case STUDENT -> {
                g.draw(path(2, 9, 12, 4, 22, 9, 12, 14, 2, 9));
                Path2D gown = new Path2D.Double();
                gown.moveTo(6, 11.5);
                gown.lineTo(6, 16);
                gown.quadTo(12, 20, 18, 16);
                gown.lineTo(18, 11.5);
                g.draw(gown);
                g.draw(new Line2D.Double(22, 9, 22, 15));
            }
            case TEACHER -> {
                g.draw(new RoundRectangle2D.Double(3, 4, 18, 12, 3, 3));
                g.draw(new Line2D.Double(12, 16, 12, 20));
                g.draw(new Line2D.Double(8, 20, 16, 20));
                g.draw(path(7, 12, 10, 9, 13, 11, 17, 7));
            }
            case GRID -> {
                for (int[] p : new int[][]{{3, 3}, {14, 3}, {3, 14}, {14, 14}}) {
                    g.draw(new RoundRectangle2D.Double(p[0], p[1], 7, 7, 2.5, 2.5));
                }
            }
            case LIST -> {
                for (int y : new int[]{6, 12, 18}) {
                    g.draw(new Line2D.Double(9, y, 21, y));
                    g.fill(new Ellipse2D.Double(3, y - 1.5, 3, 3));
                }
            }
            case CHART -> {
                g.draw(new Line2D.Double(3, 21, 21, 21));
                g.draw(new RoundRectangle2D.Double(5, 13, 3.5, 8, 1, 1));
                g.draw(new RoundRectangle2D.Double(10.5, 8, 3.5, 13, 1, 1));
                g.draw(new RoundRectangle2D.Double(16, 4, 3.5, 17, 1, 1));
            }
            case USER -> {
                g.draw(new Ellipse2D.Double(8, 3, 8, 8));
                g.draw(new Arc2D.Double(4, 13, 16, 16, 0, 180, Arc2D.OPEN));
            }
            case USERS -> {
                g.draw(new Ellipse2D.Double(5, 4, 7, 7));
                g.draw(new Arc2D.Double(2, 13, 13, 13, 0, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(14, 4, 6, 7, -90, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(15, 13, 7, 13, 0, 90, Arc2D.OPEN));
            }
            case LOGOUT -> {
                g.draw(path(10, 4, 5, 4, 5, 20, 10, 20));
                g.draw(new Line2D.Double(10, 12, 20, 12));
                g.draw(path(16, 8, 20, 12, 16, 16));
            }
            case PLAY -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.fill(path(10, 8, 16, 12, 10, 16, 10, 8));
            }
            case TROPHY -> {
                Path2D cup = new Path2D.Double();
                cup.moveTo(7, 4);
                cup.lineTo(17, 4);
                cup.lineTo(17, 9);
                cup.quadTo(17, 14, 12, 14);
                cup.quadTo(7, 14, 7, 9);
                cup.closePath();
                g.draw(cup);
                g.draw(new Arc2D.Double(3, 5, 5, 6, 90, 180, Arc2D.OPEN));
                g.draw(new Arc2D.Double(16, 5, 5, 6, -90, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(12, 14, 12, 19));
                g.draw(new Line2D.Double(8, 20, 16, 20));
            }
            case CLOCK -> {
                g.draw(new Ellipse2D.Double(3, 3, 18, 18));
                g.draw(path(12, 7, 12, 12, 16, 14));
            }
            case MAIL -> {
                g.draw(new RoundRectangle2D.Double(3, 5, 18, 14, 3, 3));
                g.draw(path(3.5, 7, 12, 13, 20.5, 7));
            }
            case LOCK -> {
                g.draw(new RoundRectangle2D.Double(5, 11, 14, 10, 3, 3));
                g.draw(new Arc2D.Double(8, 4, 8, 10, 0, 180, Arc2D.OPEN));
                g.draw(new Line2D.Double(8, 9, 8, 11));
                g.draw(new Line2D.Double(16, 9, 16, 11));
            }
            case PLUS -> {
                g.draw(new Line2D.Double(12, 5, 12, 19));
                g.draw(new Line2D.Double(5, 12, 19, 12));
            }
            case EDIT -> {
                g.draw(path(4, 20, 8, 19, 19, 8, 16, 5, 5, 16, 4, 20));
                g.draw(new Line2D.Double(14, 7, 17, 10));
            }
            case COPY -> {
                g.draw(new RoundRectangle2D.Double(8, 8, 12, 12, 3, 3));
                g.draw(path(16, 5, 16, 4, 4, 4, 4, 16, 5, 16));
            }
            case TRASH -> {
                g.draw(new Line2D.Double(4, 7, 20, 7));
                g.draw(path(6, 7, 7, 20, 17, 20, 18, 7));
                g.draw(path(9, 7, 9, 4, 15, 4, 15, 7));
            }
            case UPLOAD -> {
                g.draw(new Line2D.Double(12, 15, 12, 4));
                g.draw(path(7, 9, 12, 4, 17, 9));
                g.draw(path(4, 15, 4, 20, 20, 20, 20, 15));
            }
            case DOWNLOAD -> {
                g.draw(new Line2D.Double(12, 4, 12, 15));
                g.draw(path(7, 10, 12, 15, 17, 10));
                g.draw(path(4, 15, 4, 20, 20, 20, 20, 15));
            }
            case SEND -> {
                g.draw(path(3, 11, 21, 3, 14, 21, 11, 13, 3, 11));
                g.draw(new Line2D.Double(11, 13, 21, 3));
            }
            case CHECK -> g.draw(path(5, 12.5, 10, 17.5, 19, 7));
            case CROSS -> {
                g.draw(new Line2D.Double(6, 6, 18, 18));
                g.draw(new Line2D.Double(18, 6, 6, 18));
            }
            case STAR -> g.fill(star(12, 12.5, 10, 4.2));
            case SHUFFLE -> {
                g.draw(path(3, 7, 7, 7, 15, 17, 20, 17));
                g.draw(path(3, 17, 7, 17, 15, 7, 20, 7));
                g.draw(path(17, 4, 20, 7, 17, 10));
                g.draw(path(17, 14, 20, 17, 17, 20));
            }
            case FIRE -> {
                Path2D flame = new Path2D.Double();
                flame.moveTo(12, 2);
                flame.curveTo(16, 7, 19, 10, 19, 14);
                flame.curveTo(19, 18.5, 15.5, 22, 12, 22);
                flame.curveTo(8.5, 22, 5, 18.5, 5, 14);
                flame.curveTo(5, 11, 7, 9, 8.5, 7.5);
                flame.curveTo(9, 10, 10, 11, 11, 11);
                flame.curveTo(11, 7, 11, 5, 12, 2);
                flame.closePath();
                g.fill(flame);
            }
            case LOGO -> {
                g.setPaint(new GradientPaint(0, 0, Theme.PRIMARY, 24, 24, Theme.ACCENT));
                g.fill(new RoundRectangle2D.Double(0, 0, 24, 24, 8, 8));
                g.setColor(Color.WHITE);
                g.draw(path(4, 13, 8, 13, 10, 7.5, 13.5, 17.5, 15.5, 11, 20, 11));
            }
        }
    }

    private static Path2D path(double... points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i + 1 < points.length; i += 2) {
            path.lineTo(points[i], points[i + 1]);
        }
        return path;
    }

    static Path2D star(double cx, double cy, double outer, double inner) {
        Path2D star = new Path2D.Double();
        for (int i = 0; i < 10; i++) {
            double angle = Math.PI / 2 + i * Math.PI / 5;
            double r = i % 2 == 0 ? outer : inner;
            double x = cx + r * Math.cos(angle);
            double y = cy - r * Math.sin(angle);
            if (i == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.closePath();
        return star;
    }
}
