package classpulse.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * The wire format: a 4-byte length, followed by one serialized Message.
 * The length lets the reader reject oversized data before allocating memory.
 * The filter is an allow-list, so an attacker cannot make us deserialize other classes.
 */
public final class Protocol {
    public static final int DEFAULT_PORT = 5050;
    public static final int MAX_FRAME_BYTES = 1024 * 1024;

    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "classpulse.common.*;java.util.CollSer;java.util.ImmutableCollections$*;java.lang.Object;"
                    + "java.lang.Record;java.lang.Enum;java.lang.String;java.lang.Number;java.lang.Integer;"
                    + "maxdepth=12;maxrefs=50000;maxarray=20000;!*");

    private Protocol() {
    }

    public static void write(DataOutputStream out, Message message) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream objects = new ObjectOutputStream(buffer)) {
            objects.writeObject(message);
        }
        if (buffer.size() > MAX_FRAME_BYTES) {
            throw new IOException("Message is too large to send.");
        }
        out.writeInt(buffer.size());
        buffer.writeTo(out);
        out.flush();
    }

    public static Message read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length <= 0 || length > MAX_FRAME_BYTES) {
            throw new IOException("Invalid message length: " + length);
        }
        byte[] frame = new byte[length];
        in.readFully(frame);
        try (ObjectInputStream objects = new ObjectInputStream(new ByteArrayInputStream(frame))) {
            objects.setObjectInputFilter(FILTER);
            if (objects.readObject() instanceof Message message) {
                return message;
            }
            throw new InvalidObjectException("Received an object that is not a Message.");
        } catch (ClassNotFoundException e) {
            throw new InvalidObjectException("Received an unknown class: " + e.getMessage());
        }
    }
}
