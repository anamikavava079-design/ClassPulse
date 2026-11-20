package classpulse.server;

import classpulse.common.Message.AnswerReceived;
import classpulse.common.Message.GameOver;
import classpulse.common.Message.JoinResult;
import classpulse.common.Message.PlayersChanged;
import classpulse.common.Message.QuestionEnded;
import classpulse.common.Message.QuestionStarted;
import classpulse.common.Message.Response;
import classpulse.common.Message.RoomClosed;
import classpulse.common.Views.PersonalResult;
import classpulse.common.Views.ScoreEntry;
import java.util.ArrayList;
import java.util.List;

/**
 * A player on a phone browser (added in the phone upgrade). A phone cannot keep a Java
 * socket open, so the room's messages are turned into JSON and kept in a list. The phone
 * asks for new ones with "long polling": the request waits until something new arrives.
 */
final class WebPlayer implements PlayerLink {
    private static final int MAX_EVENTS = 2000;

    final String token;
    final String name;
    private volatile GameRoom room;
    private final List<String> events = new ArrayList<>();
    private volatile long lastSeenMillis = System.currentTimeMillis();
    private volatile long endedMillis;

    WebPlayer(String token, String name) {
        this.token = token;
        this.name = name;
    }

    GameRoom room() {
        return room;
    }

    void setRoom(GameRoom room) {
        this.room = room;
    }

    void touch() {
        lastSeenMillis = System.currentTimeMillis();
    }

    long lastSeenMillis() {
        return lastSeenMillis;
    }

    long endedMillis() {
        return endedMillis;
    }

    @Override
    public synchronized void send(Response message) {
        String json = toJson(message);
        if (json != null && events.size() < MAX_EVENTS) {
            events.add(json);
            notifyAll(); // Wakes a waiting poll at once.
        }
    }

    @Override
    public void clearRoom(GameRoom ended) {
        if (room == ended) {
            room = null;
            endedMillis = System.currentTimeMillis();
        }
    }

    /** Returns the events after number `after` (1-based), waiting up to waitMillis for one to arrive. */
    synchronized List<String> eventsAfter(int after, long waitMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + waitMillis;
        while (events.size() <= after) {
            long left = deadline - System.currentTimeMillis();
            if (left <= 0) {
                break;
            }
            wait(left);
        }
        int from = Math.max(0, Math.min(after, events.size()));
        return List.copyOf(events.subList(from, events.size()));
    }

    synchronized int eventCount() {
        return events.size();
    }

    // ----- JSON (written by hand: Java has no built-in JSON library) -----

    static String toJson(Response message) {
        return switch (message) {
            case JoinResult m -> object("type", "joined", "ok", m.ok(), "text", m.text(), "title", m.title());
            case PlayersChanged m -> object("type", "players", "count", m.names().size());
            case QuestionStarted m -> object("type", "question", "index", m.index(), "total", m.total(),
                    "text", m.text(), "options", m.options(), "seconds", m.seconds());
            case AnswerReceived m -> object("type", "answer", "accepted", m.accepted(), "text", m.text());
            case QuestionEnded m -> object("type", "ended", "index", m.index(), "total", m.total(),
                    "correctIndex", m.correctIndex(), "you", you(m.you()), "top", ranking(m.top()));
            case GameOver m -> object("type", "over", "you", you(m.you()), "correct", m.correct(),
                    "questions", m.questions(), "ranking", ranking(m.ranking()));
            case RoomClosed m -> object("type", "closed", "reason", m.reason());
            default -> null; // Teacher-only and account messages never go to a phone.
        };
    }

    private static Raw you(PersonalResult r) {
        if (r == null) {
            return new Raw("null");
        }
        return new Raw(object("answered", r.answered(), "correct", r.correct(), "points", r.pointsEarned(),
                "total", r.totalScore(), "rank", r.rank(), "streak", r.streak(), "bonus", r.streakBonus()));
    }

    private static Raw ranking(List<ScoreEntry> entries) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < entries.size(); i++) {
            ScoreEntry e = entries.get(i);
            json.append(i == 0 ? "" : ",").append(object("rank", e.rank(), "name", e.name(), "score", e.score()));
        }
        return new Raw(json.append("]").toString());
    }

    /** Already-built JSON that must not be quoted again. */
    record Raw(String json) {
    }

    static String object(Object... keysAndValues) {
        StringBuilder json = new StringBuilder("{");
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            json.append(i == 0 ? "" : ",").append(quote(String.valueOf(keysAndValues[i]))).append(':')
                    .append(value(keysAndValues[i + 1]));
        }
        return json.append('}').toString();
    }

    private static String value(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Raw raw) {
            return raw.json();
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof List<?> list) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                json.append(i == 0 ? "" : ",").append(value(list.get(i)));
            }
            return json.append(']').toString();
        }
        return quote(value.toString());
    }

    /** Escapes text for JSON, including < and > so it is also safe inside HTML. */
    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                case '&' -> out.append("\\u0026");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
