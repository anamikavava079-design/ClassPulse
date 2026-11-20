package classpulse.client;

import classpulse.common.QrCode;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/** Draws a QR code on a white rounded tile, with the quiet border the standard asks for (added in the phone upgrade). */
final class QrView extends JComponent {
    private static final int QUIET_ZONE = 3;
    private QrCode qr;

    QrView(int size) {
        setPreferredSize(new Dimension(size, size));
        setMinimumSize(new Dimension(size, size));
        setMaximumSize(new Dimension(size, size));
    }

    void setText(String text) {
        qr = text == null ? null : QrCode.encode(text);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        Theme.smooth(g);
        int side = Math.min(getWidth(), getHeight());
        g.setColor(Color.WHITE);
        g.fillRoundRect(0, 0, side, side, 16, 16);
        if (qr != null) {
            int cells = qr.size() + QUIET_ZONE * 2;
            int cell = Math.max(1, side / cells);
            int offset = (side - cell * qr.size()) / 2;
            g.setColor(new Color(0x111827));
            for (int y = 0; y < qr.size(); y++) {
                for (int x = 0; x < qr.size(); x++) {
                    if (qr.dark(x, y)) {
                        g.fillRect(offset + x * cell, offset + y * cell, cell, cell); // Square, no anti-aliasing gaps.
                    }
                }
            }
        }
        g.dispose();
    }
}
