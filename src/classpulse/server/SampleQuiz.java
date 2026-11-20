package classpulse.server;

import classpulse.common.Question;
import classpulse.common.Quiz;
import java.util.List;

/** Added to every new teacher account, so the app can be tried straight away. */
final class SampleQuiz {
    private SampleQuiz() {
    }

    static Quiz create() {
        return new Quiz("Java Basics (sample)", List.of(
                new Question("Which keyword creates a subclass of a class in Java?",
                        List.of("implements", "extends", "inherits", "super"), 1, 20),
                new Question("Which collection does not allow duplicate elements?",
                        List.of("ArrayList", "LinkedList", "HashSet", "ArrayDeque"), 2, 20),
                new Question("What does the JVM execute?",
                        List.of("Source code", "Bytecode", "Assembly language", "Header files"), 1, 15),
                new Question("Which interface must a class implement to be passed to new Thread(...)?",
                        List.of("Runnable", "Serializable", "Comparable", "Cloneable"), 0, 20),
                new Question("Which class accepts incoming TCP connections on a server?",
                        List.of("Socket", "DatagramSocket", "URLConnection", "ServerSocket"), 3, 20),
                new Question("A resource used in try-with-resources must implement which interface?",
                        List.of("Runnable", "AutoCloseable", "Serializable", "Iterable"), 1, 25)));
    }
}
