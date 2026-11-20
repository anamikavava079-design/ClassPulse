package classpulse.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Input rules shared by client and server. The client uses them to show friendly
 * errors early; the server uses them again because it must never trust a client.
 */
public final class Validation {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.]{3,30}");
    private static final Pattern ROOM_CODE = Pattern.compile("[A-Z2-9]{6}");
    private static final Pattern EMAIL_CODE = Pattern.compile("[0-9]{6}");
    private static final Pattern FULL_NAME = Pattern.compile("[\\p{L}][\\p{L} .'-]{1,59}");
    /**
     * A practical email rule: a local part, an @, and a domain with at least one dot and
     * a top-level domain of 2+ letters. Whether the address really exists is checked
     * later by the server (mail-server lookup) and by the emailed code.
     */
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+-]{1,64}@(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,24}");

    public static final int MIN_PASSWORD = 8;

    private Validation() {
    }

    /** Trims text and rejects anything empty, too long, or unsafe to display. */
    public static String text(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be empty.");
        }
        String clean = value.strip();
        if (clean.length() > maxLength) {
            throw new IllegalArgumentException(field + " must be at most " + maxLength + " characters.");
        }
        if (clean.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " cannot contain control characters.");
        }
        // Swing renders any label text starting with <html> as HTML, so block it.
        if (clean.toLowerCase(Locale.ROOT).startsWith("<html")) {
            throw new IllegalArgumentException(field + " cannot start with <html>.");
        }
        return clean;
    }

    public static String displayName(String value) {
        String clean = text(value, "Name", 20);
        if (clean.contains("<") || clean.contains(">")) {
            throw new IllegalArgumentException("Name cannot contain < or >.");
        }
        return clean;
    }

    public static String fullName(String value) {
        String clean = value == null ? "" : value.strip().replaceAll("\\s+", " ");
        if (!FULL_NAME.matcher(clean).matches()) {
            throw new IllegalArgumentException("Full name must be 2-60 letters (spaces, . ' and - are allowed).");
        }
        return clean;
    }

    public static String username(String value) {
        String clean = value == null ? "" : value.strip();
        if (!USERNAME.matcher(clean).matches()) {
            throw new IllegalArgumentException("Username must be 3-30 letters, digits, _ or . characters.");
        }
        return clean;
    }

    /** Lower-cases the address, because mail providers treat it case-insensitively. */
    public static String email(String value) {
        String clean = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (clean.length() > 100 || !EMAIL.matcher(clean).matches() || clean.contains("..")) {
            throw new IllegalArgumentException("Please enter a valid email address, like name@example.com.");
        }
        return clean;
    }

    private static final java.util.List<String> POPULAR_DOMAINS = java.util.List.of(
            "gmail.com", "yahoo.com", "yahoo.co.in", "outlook.com", "hotmail.com", "live.com",
            "icloud.com", "rediffmail.com", "protonmail.com", "vidyashilp.edu.in",
            // Real providers that are close to the ones above, so they must never be "corrected".
            "mail.com", "ymail.com", "email.com", "aol.com", "gmx.com", "zoho.com", "yandex.com", "proton.me");

    /**
     * Catches typos in popular email domains, such as "gmial.com" or "outlok.com".
     * Some typo domains really exist (and may collect mail), so DNS alone cannot catch them.
     * Returns the corrected address, or null if the domain looks fine.
     */
    public static String suggestEmail(String email) {
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return null;
        }
        String domain = email.substring(at + 1);
        if (POPULAR_DOMAINS.contains(domain)) {
            return null;
        }
        for (String popular : POPULAR_DOMAINS) {
            if (editDistance(domain, popular) <= 2 && Math.abs(domain.length() - popular.length()) <= 2) {
                return email.substring(0, at + 1) + popular;
            }
        }
        return null;
    }

    /** Levenshtein distance: the fewest single-letter edits that turn a into b. */
    static int editDistance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int replace = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(replace, Math.min(previous[j] + 1, current[j - 1] + 1));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    public static boolean looksLikeEmail(String value) {
        return value != null && value.contains("@");
    }

    /** At least 8 characters with at least one letter and one digit. */
    public static String password(String value) {
        if (value == null || value.length() < MIN_PASSWORD || value.length() > 100) {
            throw new IllegalArgumentException("Password must be " + MIN_PASSWORD + "-100 characters.");
        }
        if (value.chars().noneMatch(Character::isLetter) || value.chars().noneMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Password must contain at least one letter and one digit.");
        }
        return value;
    }

    /** 0 = too weak, 1 = weak, 2 = fair, 3 = strong. Used by the strength meter. */
    public static int passwordStrength(String value) {
        if (value == null || value.length() < MIN_PASSWORD) {
            return 0;
        }
        int kinds = 0;
        if (value.chars().anyMatch(Character::isLowerCase)) {
            kinds++;
        }
        if (value.chars().anyMatch(Character::isUpperCase)) {
            kinds++;
        }
        if (value.chars().anyMatch(Character::isDigit)) {
            kinds++;
        }
        if (value.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) {
            kinds++;
        }
        if (kinds >= 4 || (kinds >= 3 && value.length() >= 12)) {
            return 3;
        }
        return kinds >= 3 ? 2 : 1;
    }

    public static String emailCode(String value) {
        String clean = value == null ? "" : value.replace(" ", "").strip();
        if (!EMAIL_CODE.matcher(clean).matches()) {
            throw new IllegalArgumentException("The code is the 6 digits from the email.");
        }
        return clean;
    }

    public static String roomCode(String value) {
        String clean = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!ROOM_CODE.matcher(clean).matches()) {
            throw new IllegalArgumentException("Room code must be 6 characters.");
        }
        return clean;
    }
}
