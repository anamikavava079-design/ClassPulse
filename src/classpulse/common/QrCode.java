package classpulse.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A small QR code generator written from the ISO/IEC 18004 standard (added in the phone
 * upgrade). It supports what ClassPulse needs: byte mode, error correction level M, and
 * versions 1 to 6 (up to 106 bytes), which is plenty for a web address with a room code.
 *
 * Steps: encode the text as bits, add Reed-Solomon error-correction bytes (so a phone can
 * still read a smudged or partly hidden code), place everything in the square grid, then
 * try all 8 mask patterns and keep the one that is easiest for a camera to read.
 */
public final class QrCode {
    private static final int MAX_VERSION = 6;
    /** Error-correction bytes per block for level M, versions 1 to 6. */
    private static final int[] ECC_PER_BLOCK = {-1, 10, 16, 26, 18, 24, 16};
    /** Number of blocks for level M, versions 1 to 6. */
    private static final int[] BLOCKS = {-1, 1, 1, 1, 2, 2, 4};
    /** The 2-bit code for level M in the format information. */
    private static final int LEVEL_M_BITS = 0;

    private final int version;
    private final int size;
    private final boolean[][] modules;   // [y][x], true = dark
    private final boolean[][] isFunction;

    private QrCode(int version) {
        this.version = version;
        this.size = version * 4 + 17;
        this.modules = new boolean[size][size];
        this.isFunction = new boolean[size][size];
    }

    public int size() {
        return size;
    }

    public int version() {
        return version;
    }

    /** True if the square at column x, row y is dark. */
    public boolean dark(int x, int y) {
        return x >= 0 && y >= 0 && x < size && y < size && modules[y][x];
    }

    // ----- Encoding -----

    public static QrCode encode(String text) {
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        int version = 1;
        while (version <= MAX_VERSION && 4 + 8 + data.length * 8 > dataCodewords(version) * 8) {
            version++;
        }
        if (version > MAX_VERSION) {
            throw new IllegalArgumentException("Text is too long for this QR code generator.");
        }

        // 1. The bit stream: mode (0100 = bytes), length, the bytes, a terminator, then padding.
        BitBuffer bits = new BitBuffer();
        bits.append(0b0100, 4);
        bits.append(data.length, 8);
        for (byte b : data) {
            bits.append(b & 0xFF, 8);
        }
        int capacity = dataCodewords(version) * 8;
        bits.append(0, Math.min(4, capacity - bits.size()));
        bits.append(0, (8 - bits.size() % 8) % 8);
        for (int pad = 0xEC; bits.size() < capacity; pad ^= 0xEC ^ 0x11) {
            bits.append(pad, 8);
        }
        byte[] codewords = bits.toBytes();

        // 2. Error correction and interleaving, then 3. placing it in the grid.
        QrCode qr = new QrCode(version);
        qr.drawFunctionPatterns();
        qr.drawCodewords(addEccAndInterleave(codewords, version));

        // 4. Choose the mask with the lowest penalty score.
        int bestMask = 0;
        long bestPenalty = Long.MAX_VALUE;
        for (int mask = 0; mask < 8; mask++) {
            qr.applyMask(mask);
            qr.drawFormatBits(mask);
            long penalty = qr.penalty();
            if (penalty < bestPenalty) {
                bestPenalty = penalty;
                bestMask = mask;
            }
            qr.applyMask(mask); // Masking twice undoes it (XOR).
        }
        qr.applyMask(bestMask);
        qr.drawFormatBits(bestMask);
        return qr;
    }

    /** All the light/dark squares a module can hold, minus the fixed patterns. */
    static int rawDataModules(int version) {
        int result = (16 * version + 128) * version + 64;
        if (version >= 2) {
            int alignments = version / 7 + 2;
            result -= (25 * alignments - 10) * alignments - 55;
            if (version >= 7) {
                result -= 36;
            }
        }
        return result;
    }

    static int dataCodewords(int version) {
        return rawDataModules(version) / 8 - ECC_PER_BLOCK[version] * BLOCKS[version];
    }

    // ----- Fixed patterns -----

    private void drawFunctionPatterns() {
        for (int i = 0; i < size; i++) {
            setFunction(6, i, i % 2 == 0); // Timing patterns.
            setFunction(i, 6, i % 2 == 0);
        }
        drawFinder(3, 3);
        drawFinder(size - 4, 3);
        drawFinder(3, size - 4);
        if (version >= 2) {
            drawAlignment(size - 7, size - 7); // Versions 2-6 have exactly one.
        }
        drawFormatBits(0); // Reserves the format areas; the real bits are drawn later.
    }

    private void drawFinder(int cx, int cy) {
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int distance = Math.max(Math.abs(dx), Math.abs(dy));
                int x = cx + dx;
                int y = cy + dy;
                if (x >= 0 && x < size && y >= 0 && y < size) {
                    setFunction(x, y, distance != 2 && distance != 4);
                }
            }
        }
    }

    private void drawAlignment(int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                setFunction(cx + dx, cy + dy, Math.max(Math.abs(dx), Math.abs(dy)) != 1);
            }
        }
    }

    /** 15 bits: the level and mask, protected by a BCH code, drawn twice around the finders. */
    private void drawFormatBits(int mask) {
        int bits = formatBits(mask);
        for (int i = 0; i <= 5; i++) {
            setFunction(8, i, bit(bits, i));
        }
        setFunction(8, 7, bit(bits, 6));
        setFunction(8, 8, bit(bits, 7));
        setFunction(7, 8, bit(bits, 8));
        for (int i = 9; i < 15; i++) {
            setFunction(14 - i, 8, bit(bits, i));
        }
        for (int i = 0; i < 8; i++) {
            setFunction(size - 1 - i, 8, bit(bits, i));
        }
        for (int i = 8; i < 15; i++) {
            setFunction(8, size - 15 + i, bit(bits, i));
        }
        setFunction(8, size - 8, true); // The "dark module", always dark.
    }

    static int formatBits(int mask) {
        int data = LEVEL_M_BITS << 3 | mask;
        int remainder = data;
        for (int i = 0; i < 10; i++) {
            remainder = (remainder << 1) ^ ((remainder >>> 9) * 0x537);
        }
        return (data << 10 | remainder) ^ 0x5412;
    }

    private void setFunction(int x, int y, boolean dark) {
        modules[y][x] = dark;
        isFunction[y][x] = true;
    }

    // ----- Data placement -----

    /** Splits the data into blocks, adds Reed-Solomon bytes to each, and interleaves them. */
    static byte[] addEccAndInterleave(byte[] data, int version) {
        int blocks = BLOCKS[version];
        int eccLength = ECC_PER_BLOCK[version];
        int rawCodewords = rawDataModules(version) / 8;
        int shortBlocks = blocks - rawCodewords % blocks;
        int shortBlockLength = rawCodewords / blocks;
        byte[] divisor = reedSolomonDivisor(eccLength);
        List<byte[]> allBlocks = new ArrayList<>();
        for (int i = 0, k = 0; i < blocks; i++) {
            int dataLength = shortBlockLength - eccLength + (i < shortBlocks ? 0 : 1);
            byte[] blockData = java.util.Arrays.copyOfRange(data, k, k + dataLength);
            k += dataLength;
            byte[] ecc = reedSolomonRemainder(blockData, divisor);
            byte[] block = new byte[shortBlockLength + 1];
            System.arraycopy(blockData, 0, block, 0, dataLength);
            // Short blocks get a placeholder byte so all blocks line up for interleaving.
            System.arraycopy(ecc, 0, block, shortBlockLength + 1 - eccLength, eccLength);
            allBlocks.add(block);
        }
        byte[] result = new byte[rawCodewords];
        int index = 0;
        for (int i = 0; i < shortBlockLength + 1; i++) {
            for (int j = 0; j < allBlocks.size(); j++) {
                if (i != shortBlockLength - eccLength || j >= shortBlocks) {
                    result[index++] = allBlocks.get(j)[i];
                }
            }
        }
        return result;
    }

    /** Places the bytes in the zig-zag order the standard uses: two columns at a time, up then down. */
    private void drawCodewords(byte[] data) {
        int i = 0;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) {
                right = 5; // Skip the vertical timing pattern.
            }
            for (int step = 0; step < size; step++) {
                for (int j = 0; j < 2; j++) {
                    int x = right - j;
                    boolean upward = ((right + 1) & 2) == 0;
                    int y = upward ? size - 1 - step : step;
                    if (!isFunction[y][x] && i < data.length * 8) {
                        modules[y][x] = bit(data[i >>> 3], 7 - (i & 7));
                        i++;
                    }
                }
            }
        }
    }

    private void applyMask(int mask) {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                if (!isFunction[y][x] && maskBit(mask, x, y)) {
                    modules[y][x] = !modules[y][x];
                }
            }
        }
    }

    static boolean maskBit(int mask, int x, int y) {
        return switch (mask) {
            case 0 -> (x + y) % 2 == 0;
            case 1 -> y % 2 == 0;
            case 2 -> x % 3 == 0;
            case 3 -> (x + y) % 3 == 0;
            case 4 -> (x / 3 + y / 2) % 2 == 0;
            case 5 -> x * y % 2 + x * y % 3 == 0;
            case 6 -> (x * y % 2 + x * y % 3) % 2 == 0;
            case 7 -> ((x + y) % 2 + x * y % 3) % 2 == 0;
            default -> throw new IllegalArgumentException("Mask must be 0-7.");
        };
    }

    // ----- Choosing a mask -----

    /** The standard's four penalty rules; lower means easier to scan. */
    private long penalty() {
        long result = 0;
        for (int y = 0; y < size; y++) {
            final int row = y;
            result += runPenalty(i -> modules[row][i]);
        }
        for (int x = 0; x < size; x++) {
            final int column = x;
            result += runPenalty(i -> modules[i][column]);
        }
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean c = modules[y][x];
                if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) {
                    result += 3; // Rule 2: 2x2 blocks of one colour.
                }
            }
        }
        int dark = 0;
        for (boolean[] row : modules) {
            for (boolean module : row) {
                if (module) {
                    dark++;
                }
            }
        }
        int total = size * size;
        int deviation = Math.abs(dark * 20 - total * 10); // Rule 4: aim for half dark.
        result += (long) ((deviation + total - 1) / total - 1) * 10;
        return result;
    }

    @FunctionalInterface
    private interface Line {
        boolean get(int i);
    }

    /** Rule 1 (runs of 5+ same colour) and rule 3 (finder-like 1:1:3:1:1 patterns) for one line. */
    private long runPenalty(Line line) {
        long result = 0;
        int run = 1;
        for (int i = 1; i <= size; i++) {
            if (i < size && line.get(i) == line.get(i - 1)) {
                run++;
            } else {
                if (run >= 5) {
                    result += 3 + (run - 5);
                }
                run = 1;
            }
        }
        boolean[] pattern = {true, false, true, true, true, false, true};
        for (int i = 0; i + 7 <= size; i++) {
            boolean match = true;
            for (int k = 0; k < 7 && match; k++) {
                match = line.get(i + k) == pattern[k];
            }
            if (match) {
                boolean lightBefore = i >= 4 && !line.get(i - 1) && !line.get(i - 2) && !line.get(i - 3) && !line.get(i - 4);
                boolean lightAfter = i + 11 <= size && !line.get(i + 7) && !line.get(i + 8) && !line.get(i + 9)
                        && !line.get(i + 10);
                if (lightBefore || lightAfter) {
                    result += 40;
                }
            }
        }
        return result;
    }

    // ----- Reed-Solomon over GF(256) -----

    static byte[] reedSolomonDivisor(int degree) {
        byte[] result = new byte[degree];
        result[degree - 1] = 1;
        int root = 1;
        for (int i = 0; i < degree; i++) {
            for (int j = 0; j < result.length; j++) {
                result[j] = (byte) multiply(result[j] & 0xFF, root);
                if (j + 1 < result.length) {
                    result[j] ^= result[j + 1];
                }
            }
            root = multiply(root, 0x02);
        }
        return result;
    }

    static byte[] reedSolomonRemainder(byte[] data, byte[] divisor) {
        byte[] result = new byte[divisor.length];
        for (byte b : data) {
            int factor = (b ^ result[0]) & 0xFF;
            System.arraycopy(result, 1, result, 0, result.length - 1);
            result[result.length - 1] = 0;
            for (int i = 0; i < result.length; i++) {
                result[i] ^= (byte) multiply(divisor[i] & 0xFF, factor);
            }
        }
        return result;
    }

    /** Multiplication in GF(2^8) with the QR polynomial x^8 + x^4 + x^3 + x^2 + 1. */
    static int multiply(int x, int y) {
        int z = 0;
        for (int i = 7; i >= 0; i--) {
            z = (z << 1) ^ ((z >>> 7) * 0x11D);
            z ^= ((y >>> i) & 1) * x;
        }
        return z;
    }

    private static boolean bit(int value, int index) {
        return ((value >>> index) & 1) != 0;
    }

    /** Collects bits most-significant first. */
    private static final class BitBuffer {
        private final List<Boolean> bits = new ArrayList<>();

        void append(int value, int length) {
            for (int i = length - 1; i >= 0; i--) {
                bits.add(((value >>> i) & 1) != 0);
            }
        }

        int size() {
            return bits.size();
        }

        byte[] toBytes() {
            byte[] result = new byte[bits.size() / 8];
            for (int i = 0; i < bits.size(); i++) {
                if (bits.get(i)) {
                    result[i >>> 3] |= (byte) (0x80 >>> (i & 7));
                }
            }
            return result;
        }
    }
}
