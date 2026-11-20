package classpulse.client;

import javax.swing.SwingUtilities;

/** Program entry point for the teacher and student window. */
public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        // Swing objects must be created on the Event Dispatch Thread.
        SwingUtilities.invokeLater(() -> new AppFrame().setVisible(true));
    }
}
