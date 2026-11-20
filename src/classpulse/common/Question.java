package classpulse.common;

import java.io.Serializable;
import java.util.HashSet;
import java.util.List;

/**
 * One multiple-choice question. The compact constructor validates every field.
 * Java also calls it when a record is deserialized, so bad network data is rejected too.
 */
public record Question(String text, List<String> options, int correctIndex, int seconds) implements Serializable {
    public static final int OPTION_COUNT = 4;
    public static final int MIN_SECONDS = 5;
    public static final int MAX_SECONDS = 120;

    public Question {
        text = Validation.text(text, "Question", 300);
        if (options == null || options.size() != OPTION_COUNT) {
            throw new IllegalArgumentException("A question needs exactly " + OPTION_COUNT + " options.");
        }
        options = options.stream().map(o -> Validation.text(o, "Option", 120)).toList();
        if (new HashSet<>(options).size() != OPTION_COUNT) {
            throw new IllegalArgumentException("Options must all be different.");
        }
        if (correctIndex < 0 || correctIndex >= OPTION_COUNT) {
            throw new IllegalArgumentException("The correct answer must be A, B, C or D.");
        }
        if (seconds < MIN_SECONDS || seconds > MAX_SECONDS) {
            throw new IllegalArgumentException("Time limit must be " + MIN_SECONDS + "-" + MAX_SECONDS + " seconds.");
        }
    }
}
