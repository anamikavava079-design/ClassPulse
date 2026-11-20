package classpulse.server;

import static classpulse.server.AllTests.check;

import classpulse.common.Message.PlayersChanged;
import classpulse.common.Message.QuestionStarted;
import classpulse.common.Message.RoomClosed;
import classpulse.common.QrCode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;

/** Unit tests and helpers for the certificate, homework and phone upgrades. */
final class UpgradeTests {
    private UpgradeTests() {
    }

    // ----- Certificate -----

    static void certificate() throws Exception {
        // With other players: only rank 1 (players with equal top scores all have rank 1).
        check(CertificateMaker.deserves(1, 30, 2, 6), "the winner gets one, whatever the marks");
        check(!CertificateMaker.deserves(2, 30, 6, 6), "2nd place does not");
        check(CertificateMaker.deserves(1, 2, 5, 6), "a tie for first: each winner gets one");
        // Alone: full marks or one less.
        check(CertificateMaker.deserves(1, 1, 6, 6) && CertificateMaker.deserves(1, 1, 5, 6), "6/6 and 5/6 alone");
        check(!CertificateMaker.deserves(1, 1, 4, 6), "4/6 alone does not");
        check(!CertificateMaker.deserves(1, 1, 0, 1) && CertificateMaker.deserves(1, 1, 1, 1), "a 1-question quiz needs it right");
        equal("5 of 6 correct", CertificateMaker.achievement(1, 1, 5, 6), "solo wording");
        equal("1st place", CertificateMaker.achievement(1, 4, 3, 6), "multiplayer wording");
        byte[] png = CertificateMaker.png("Asha <Rao>", "A very long quiz title that keeps going and going and going "
                + "until it has to shrink", 1, 30, 4210, 5, 6, "17 November 2026");
        check(png.length > 10_000 && png[0] == (byte) 0x89 && png[1] == 'P' && png[2] == 'N' && png[3] == 'G',
                "a real PNG file");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        check(image.getWidth() == CertificateMaker.WIDTH && image.getHeight() == CertificateMaker.HEIGHT, "size");
        check(CertificateMaker.fileName("Asha <Rao>/../x").matches("ClassPulse-certificate-[A-Za-z0-9-]+\\.png"),
                "a safe file name");
    }

    private static void equal(Object expected, Object actual, String what) {
        check(expected.equals(actual), what + ": expected " + expected + " but got " + actual);
    }

    // ----- QR code -----

    /**
     * Reads the QR code back the way a phone's scanner would, using code written separately
     * from the encoder: the format bits, the mask, the Reed-Solomon check and the text.
     */
    static void qrCode() {
        // The standard's byte capacities at level M: version 1 holds 14 bytes, 2: 26, 3: 42, 4: 62, 5: 84, 6: 106.
        int[] capacity = {0, 14, 26, 42, 62, 84, 106};
        for (int version = 1; version <= 6; version++) {
            String full = "ab3:/?=".repeat(20).substring(0, capacity[version]);
            QrCode qr = QrCode.encode(full);
            check(qr.version() == version, capacity[version] + " bytes should fit version " + version);
            check(readBack(qr).equals(full), "the text reads back at version " + version);
            if (version < 6) {
                check(QrCode.encode(full + "x").version() == version + 1, "one more byte needs the next version");
            }
        }
        for (String text : List.of("HELLO", "http://192.168.1.23:8080/?code=W3CFMM",
                "http://10.0.0.5:8080/?code=ABC234&extra=Ünïcödé-name")) {
            check(readBack(QrCode.encode(text)).equals(text), "the text reads back: " + text);
        }
        try {
            QrCode.encode("x".repeat(200));
            throw new AssertionError("text that is too long should be refused");
        } catch (IllegalArgumentException expected) {
            // Good.
        }
    }

    private static final int[] BLOCKS_M = {0, 1, 1, 1, 2, 2, 4};
    private static final int[] ECC_M = {0, 10, 16, 26, 18, 24, 16};

    private static String readBack(QrCode qr) {
        int size = qr.size();
        int version = (size - 17) / 4;
        // The three finder patterns: a dark ring, a light ring, a dark 3x3 centre.
        for (int[] corner : new int[][]{{0, 0}, {size - 7, 0}, {0, size - 7}}) {
            for (int dy = 0; dy < 7; dy++) {
                for (int dx = 0; dx < 7; dx++) {
                    int ring = Math.max(Math.abs(dx - 3), Math.abs(dy - 3));
                    check(qr.dark(corner[0] + dx, corner[1] + dy) == (ring != 2), "finder pattern");
                }
            }
        }
        // Format bits, read from beside the top-left finder.
        int format = 0;
        int[][] positions = {{8, 0}, {8, 1}, {8, 2}, {8, 3}, {8, 4}, {8, 5}, {8, 7}, {8, 8}, {7, 8},
                {5, 8}, {4, 8}, {3, 8}, {2, 8}, {1, 8}, {0, 8}};
        for (int i = 0; i < 15; i++) {
            if (qr.dark(positions[i][0], positions[i][1])) {
                format |= 1 << i;
            }
        }
        int unmasked = format ^ 0x5412;
        check(bchRemainder(unmasked >>> 10) == (unmasked & 0x3FF), "the format bits pass their BCH check");
        check((unmasked >>> 13) == 0, "error-correction level M");
        int mask = (unmasked >>> 10) & 7;
        // The second copy of the format bits must match.
        int second = 0;
        for (int i = 0; i < 8; i++) {
            if (qr.dark(size - 1 - i, 8)) {
                second |= 1 << i;
            }
        }
        for (int i = 8; i < 15; i++) {
            if (qr.dark(8, size - 15 + i)) {
                second |= 1 << i;
            }
        }
        check(second == format, "both copies of the format bits agree");

        // Read the data modules in zig-zag order, removing the mask.
        int total = rawModules(version) / 8;
        byte[] codewords = new byte[total];
        int bit = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5;
            }
            for (int step = 0; step < size; step++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    int y = ((right + 1) & 2) == 0 ? size - 1 - step : step;
                    if (!isFunction(x, y, size, version) && bit < total * 8) {
                        boolean dark = qr.dark(x, y) ^ masked(mask, x, y);
                        if (dark) {
                            codewords[bit >>> 3] |= (byte) (0x80 >>> (bit & 7));
                        }
                        bit++;
                    }
                }
            }
        }
        // De-interleave (all blocks have the same length for level M, versions 1-6) and check each block.
        int blocks = BLOCKS_M[version];
        int blockLength = total / blocks;
        int dataPerBlock = blockLength - ECC_M[version];
        byte[] data = new byte[dataPerBlock * blocks];
        for (int b = 0; b < blocks; b++) {
            byte[] block = new byte[blockLength];
            for (int k = 0; k < blockLength; k++) {
                block[k] = codewords[k * blocks + b];
            }
            for (int i = 0; i < ECC_M[version]; i++) {
                check(syndrome(block, i) == 0, "Reed-Solomon check of block " + b);
            }
            System.arraycopy(block, 0, data, b * dataPerBlock, dataPerBlock);
        }
        // Decode: mode 0100 (bytes), an 8-bit length, then the bytes.
        int mode = (data[0] & 0xFF) >>> 4;
        check(mode == 0b0100, "byte mode");
        int length = ((data[0] & 0x0F) << 4) | ((data[1] & 0xFF) >>> 4);
        byte[] text = new byte[length];
        for (int i = 0; i < length; i++) {
            text[i] = (byte) (((data[1 + i] & 0x0F) << 4) | ((data[2 + i] & 0xFF) >>> 4));
        }
        return new String(text, StandardCharsets.UTF_8);
    }

    private static boolean isFunction(int x, int y, int size, int version) {
        if (x <= 8 && y <= 8) {
            return true; // Top-left finder, separator and format bits.
        }
        if (x >= size - 8 && y <= 8) {
            return true;
        }
        if (x <= 8 && y >= size - 8) {
            return true;
        }
        if (x == 6 || y == 6) {
            return true; // Timing patterns.
        }
        return version >= 2 && Math.abs(x - (size - 7)) <= 2 && Math.abs(y - (size - 7)) <= 2;
    }

    /** Counts the squares left for data once the fixed patterns are taken out. */
    private static int rawModules(int version) {
        int size = version * 4 + 17;
        int count = 0;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!isFunction(x, y, size, version)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static boolean masked(int mask, int x, int y) {
        return switch (mask) {
            case 0 -> (y + x) % 2 == 0;
            case 1 -> y % 2 == 0;
            case 2 -> x % 3 == 0;
            case 3 -> (y + x) % 3 == 0;
            case 4 -> (y / 2 + x / 3) % 2 == 0;
            case 5 -> (y * x) % 2 + (y * x) % 3 == 0;
            case 6 -> ((y * x) % 2 + (y * x) % 3) % 2 == 0;
            default -> ((y + x) % 2 + (y * x) % 3) % 2 == 0;
        };
    }

    private static int bchRemainder(int data) {
        int value = data << 10;
        for (int i = 14; i >= 10; i--) {
            if (((value >>> i) & 1) != 0) {
                value ^= 0x537 << (i - 10);
            }
        }
        return value;
    }

    /** The block, read as a polynomial, evaluated at alpha^i. A valid Reed-Solomon block gives 0. */
    private static int syndrome(byte[] block, int i) {
        int alphaI = 1;
        for (int k = 0; k < i; k++) {
            alphaI = gfTimes(alphaI, 2);
        }
        int result = 0;
        for (byte b : block) {
            result = gfTimes(result, alphaI) ^ (b & 0xFF);
        }
        return result;
    }

    private static int gfTimes(int a, int b) {
        int product = 0;
        while (b > 0) {
            if ((b & 1) != 0) {
                product ^= a;
            }
            a <<= 1;
            if ((a & 0x100) != 0) {
                a ^= 0x11D;
            }
            b >>>= 1;
        }
        return product;
    }

    // ----- Phone JSON -----

    static void phoneJson() {
        String json = WebPlayer.toJson(new QuestionStarted(0, 3, "Is 1 < 2 & \"yes\"?\n</script>",
                List.of("a", "b\\", "c", "d"), 20));
        check(json.startsWith("{\"type\":\"question\""), "a question event");
        check(!json.contains("<") && !json.contains(">") && !json.contains("\n"), "no raw < > or new lines: " + json);
        check(json.contains("\\\"yes\\\"") && json.contains("\"b\\\\\""), "quotes and backslashes escaped");
        check(WebPlayer.toJson(new PlayersChanged(List.of("x", "y"))).equals("{\"type\":\"players\",\"count\":2}"),
                "players event");
        check(WebPlayer.toJson(new RoomClosed("bye")).contains("\"reason\":\"bye\""), "closed event");
        check(PhoneServer.parse("a=1&b=two+words&c=%3C&bad=%zz").get("b").equals("two words"), "form parsing");
    }

    // ----- HTTP helpers for the phone integration test -----

    static String get(HttpClient http, String url) throws Exception {
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        check(response.statusCode() == 200, "GET " + url + " returned " + response.statusCode());
        return response.body();
    }

    static String post(HttpClient http, String url, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + name + "\":\"([^\"]*)\"").matcher(json);
        check(matcher.find(), "no " + name + " in " + json);
        return matcher.group(1);
    }

    /** Polls like the phone page does, until an event containing `wanted` arrives. Returns all events seen. */
    static String pollUntil(HttpClient http, String base, String token, String wanted) throws Exception {
        StringBuilder seen = new StringBuilder();
        int last = 0;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
        while (System.nanoTime() < deadline) {
            String body = get(http, base + "api/poll?token=" + token + "&after=" + last);
            check(body.contains("\"ok\":true"), "poll failed: " + body);
            seen.append(body);
            Matcher matcher = Pattern.compile("\"last\":(\\d+)").matcher(body);
            check(matcher.find(), "no last in " + body);
            last = Integer.parseInt(matcher.group(1));
            if (seen.indexOf(wanted) >= 0) {
                return seen.toString();
            }
        }
        throw new AssertionError("The phone never got " + wanted + ". Seen: " + seen);
    }

    /** The JSON form of some text, without the surrounding quotes. */
    static String jsonText(String text) {
        String quoted = WebPlayer.quote(text);
        return quoted.substring(1, quoted.length() - 1);
    }
}
