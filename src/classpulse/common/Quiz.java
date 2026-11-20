package classpulse.common;

import java.io.Serializable;
import java.util.List;

/** A titled list of questions, as sent by a teacher when saving a new quiz. */
public record Quiz(String title, List<Question> questions) implements Serializable {
    public static final int MAX_QUESTIONS = 50;

    public Quiz {
        title = Validation.text(title, "Quiz title", 100);
        if (questions == null || questions.isEmpty() || questions.size() > MAX_QUESTIONS) {
            throw new IllegalArgumentException("A quiz needs 1-" + MAX_QUESTIONS + " questions.");
        }
        questions = List.copyOf(questions);
    }
}
