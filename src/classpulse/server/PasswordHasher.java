package classpulse.server;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Stores passwords as salted PBKDF2 hashes, never as plain text.
 * Stored format: iterations:salt:hash
 */
final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    static String hash(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        byte[] hash = derive(password, salt, ITERATIONS);
        Base64.Encoder base64 = Base64.getEncoder();
        return ITERATIONS + ":" + base64.encodeToString(salt) + ":" + base64.encodeToString(hash);
    }

    static boolean verify(String password, String stored) {
        String[] parts = stored.split(":");
        if (parts.length != 3) {
            return false;
        }
        Base64.Decoder base64 = Base64.getDecoder();
        byte[] expected = base64.decode(parts[2]);
        byte[] actual = derive(password, base64.decode(parts[1]), Integer.parseInt(parts[0]));
        return MessageDigest.isEqual(expected, actual); // Constant time, unlike Arrays.equals.
    }

    private static byte[] derive(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available on this Java runtime.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
