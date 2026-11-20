package classpulse.client;

import classpulse.common.Message;
import classpulse.common.Message.Request;
import classpulse.common.Message.Response;
import classpulse.common.Protocol;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * The client's socket. Network work never runs on Swing's Event Dispatch Thread (EDT),
 * or the window would freeze. Incoming messages are handed back with invokeLater,
 * because Swing components may only be touched from the EDT.
 */
final class ServerConnection implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final Socket socket;
    private final DataOutputStream out;
    private final ExecutorService sender = Executors.newSingleThreadExecutor();
    private volatile boolean closing;

    /** Blocks while connecting, so it must be called from a background thread. */
    ServerConnection(String host, int port, Consumer<Response> onMessage, Runnable onLost) throws IOException {
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        Thread reader = new Thread(() -> readLoop(in, onMessage, onLost), "server-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void readLoop(DataInputStream in, Consumer<Response> onMessage, Runnable onLost) {
        try {
            while (true) {
                Message message = Protocol.read(in);
                if (message instanceof Response response) {
                    SwingUtilities.invokeLater(() -> onMessage.accept(response));
                }
            }
        } catch (IOException e) {
            if (!closing) {
                SwingUtilities.invokeLater(onLost);
            }
        }
    }

    /** Queues the message and returns at once, so a click never waits on the network. */
    void send(Request request) {
        sender.execute(() -> {
            try {
                Protocol.write(out, request);
            } catch (IOException e) {
                try {
                    socket.close(); // The reader then notices and reports the lost connection.
                } catch (IOException ignored) {
                    // Already closed.
                }
            }
        });
    }

    @Override
    public void close() {
        closing = true;
        sender.shutdown();
        try {
            socket.close();
        } catch (IOException ignored) {
            // Already closed.
        }
    }
}
