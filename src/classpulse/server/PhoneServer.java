package classpulse.server;

import classpulse.common.Validation;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Lets students play from a phone browser, with no app to install (added in the phone upgrade).
 *
 * It is a small web server built on Java's own com.sun.net.httpserver:
 *   GET  /            the game page (HTML, CSS and JavaScript in one file, see PhonePage)
 *   POST /api/join    join a room with a code and a name; returns a secret token
 *   GET  /api/poll    wait for new game events (long polling)
 *   POST /api/answer  answer the current question
 *   POST /api/leave   leave the room
 * Phone players are guests. They join the same GameRoom as app players through PlayerLink.
 */
final class PhoneServer implements AutoCloseable {
    private static final int MAX_BODY_BYTES = 4096;
    private static final int MAX_PHONE_PLAYERS = 1000;
    private static final long POLL_WAIT_MILLIS = 20_000;
    /** A phone that stops polling for this long has closed the page, so it leaves the room. */
    private static final long IDLE_MILLIS = 45_000;
    private static final long KEEP_AFTER_END_MILLIS = 5 * 60_000;

    private final ClassPulseServer server;
    private final HttpServer http;
    private final String host;
    private final Map<String, WebPlayer> players = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "phone-cleaner");
        thread.setDaemon(true);
        return thread;
    });

    private PhoneServer(ClassPulseServer server, HttpServer http, String host) {
        this.server = server;
        this.http = http;
        this.host = host;
    }

    /** Opens the port. host is the address phones should use; null means "find this computer's Wi-Fi address". */
    static PhoneServer start(ClassPulseServer server, int port, String host) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(port), 64);
        PhoneServer phone = new PhoneServer(server, http, host == null || host.isBlank() ? lanAddress() : host.strip());
        http.createContext("/", phone::handle);
        http.setExecutor(Executors.newVirtualThreadPerTaskExecutor()); // A waiting poll costs almost nothing.
        http.start();
        phone.cleaner.scheduleWithFixedDelay(phone::cleanUp, 10, 10, TimeUnit.SECONDS);
        return phone;
    }

    int port() {
        return http.getAddress().getPort();
    }

    String baseUrl() {
        return "http://" + host + ":" + port() + "/";
    }

    String linkFor(String roomCode) {
        return baseUrl() + "?code=" + roomCode;
    }

    @Override
    public void close() {
        cleaner.shutdownNow();
        http.stop(0);
    }

    // ----- Requests -----

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            switch (path) {
                case "/", "/index.html" -> {
                    if (!method.equals("GET") && !method.equals("HEAD")) {
                        reply(exchange, 405, "text/plain", "Method not allowed");
                    } else {
                        reply(exchange, 200, "text/html; charset=utf-8", PhonePage.HTML);
                    }
                }
                case "/api/join" -> requirePost(exchange, method, () -> join(exchange));
                case "/api/poll" -> poll(exchange);
                case "/api/answer" -> requirePost(exchange, method, () -> answer(exchange));
                case "/api/leave" -> requirePost(exchange, method, () -> leave(exchange));
                default -> reply(exchange, 404, "text/plain", "Not found");
            }
        } catch (RuntimeException e) {
            // Never let one bad request stop the web server.
            System.err.println("Phone request failed: " + e);
        }
    }

    @FunctionalInterface
    private interface Action {
        void run() throws IOException;
    }

    private static void requirePost(HttpExchange exchange, String method, Action action) throws IOException {
        if (!method.equals("POST")) {
            reply(exchange, 405, "text/plain", "Method not allowed");
        } else {
            action.run();
        }
    }

    private void join(HttpExchange exchange) throws IOException {
        Map<String, String> form = parse(readBody(exchange));
        String code;
        String name;
        try {
            code = Validation.roomCode(form.get("code"));
            name = Validation.displayName(form.get("name"));
        } catch (IllegalArgumentException e) {
            json(exchange, WebPlayer.object("ok", false, "error", e.getMessage()));
            return;
        }
        if (players.size() >= MAX_PHONE_PLAYERS) {
            json(exchange, WebPlayer.object("ok", false, "error", "Too many phones are connected. Try again soon."));
            return;
        }
        GameRoom room = server.findRoom(code);
        if (room == null) {
            json(exchange, WebPlayer.object("ok", false, "error", "No room has that code."));
            return;
        }
        WebPlayer player = new WebPlayer(newToken(), name);
        player.setRoom(room);
        players.put(player.token, player); // Before joining, so a fast first event is never lost.
        String problem = room.join(player, name, null, null);
        if (problem != null) {
            players.remove(player.token);
            json(exchange, WebPlayer.object("ok", false, "error", problem));
            return;
        }
        json(exchange, WebPlayer.object("ok", true, "token", player.token, "title", room.title(), "name", name));
    }

    private void poll(HttpExchange exchange) throws IOException {
        Map<String, String> query = parse(exchange.getRequestURI().getRawQuery());
        WebPlayer player = players.get(query.getOrDefault("token", ""));
        if (player == null) {
            json(exchange, WebPlayer.object("ok", false, "error", "gone"));
            return;
        }
        int after;
        try {
            after = Math.max(0, Integer.parseInt(query.getOrDefault("after", "0")));
        } catch (NumberFormatException e) {
            after = 0;
        }
        player.touch();
        List<String> events;
        try {
            events = player.eventsAfter(after, POLL_WAIT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        player.touch();
        String body = "{\"ok\":true,\"last\":" + (after + events.size()) + ",\"events\":[" + String.join(",", events) + "]}";
        json(exchange, body);
    }

    private void answer(HttpExchange exchange) throws IOException {
        Map<String, String> form = parse(readBody(exchange));
        WebPlayer player = players.get(form.getOrDefault("token", ""));
        GameRoom room = player == null ? null : player.room();
        if (room == null) {
            json(exchange, WebPlayer.object("ok", false, "error", "You are not in a room."));
            return;
        }
        try {
            room.answer(player, Integer.parseInt(form.getOrDefault("index", "-1")),
                    Integer.parseInt(form.getOrDefault("choice", "-1")));
        } catch (NumberFormatException e) {
            json(exchange, WebPlayer.object("ok", false, "error", "That is not a valid answer."));
            return;
        }
        player.touch();
        json(exchange, WebPlayer.object("ok", true)); // The result arrives as an "answer" event.
    }

    private void leave(HttpExchange exchange) throws IOException {
        Map<String, String> form = parse(readBody(exchange));
        WebPlayer player = players.remove(form.getOrDefault("token", ""));
        if (player != null && player.room() != null) {
            player.room().leave(player);
        }
        json(exchange, WebPlayer.object("ok", true));
    }

    /** Removes phones that closed the page, and players of games that ended a while ago. */
    private void cleanUp() {
        try {
            long now = System.currentTimeMillis();
            for (WebPlayer player : players.values()) {
                GameRoom room = player.room();
                if (room == null && player.endedMillis() > 0 && now - player.endedMillis() > KEEP_AFTER_END_MILLIS) {
                    players.remove(player.token);
                } else if (now - player.lastSeenMillis() > IDLE_MILLIS) {
                    players.remove(player.token);
                    if (room != null) {
                        room.leave(player);
                    }
                }
            }
        } catch (RuntimeException e) {
            System.err.println("Phone clean-up failed: " + e);
        }
    }

    int connectedPhones() {
        return players.size();
    }

    // ----- HTTP helpers -----

    private String newToken() {
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] body = in.readNBytes(MAX_BODY_BYTES + 1);
            if (body.length > MAX_BODY_BYTES) {
                throw new IOException("Request body is too large.");
            }
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    /** Parses "a=1&b=two" (URL-encoded form data or a query string). */
    static Map<String, String> parse(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> values = new HashMap<>();
        for (String pair : text.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                try {
                    values.put(URLDecoder.decode(pair.substring(0, equals), StandardCharsets.UTF_8),
                            URLDecoder.decode(pair.substring(equals + 1), StandardCharsets.UTF_8));
                } catch (IllegalArgumentException e) {
                    // A broken %-escape: ignore that pair.
                }
            }
        }
        return values;
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        reply(exchange, 200, "application/json; charset=utf-8", body);
    }

    private static void reply(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        boolean head = exchange.getRequestMethod().equalsIgnoreCase("HEAD");
        exchange.sendResponseHeaders(status, head ? -1 : bytes.length);
        if (!head) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    /**
     * The address phones on the same Wi-Fi can reach: the first private IPv4 address of a
     * network adapter that is up, skipping loopback and virtual adapters (VirtualBox, Hyper-V...).
     */
    static String lanAddress() {
        try {
            String fallback = null;
            for (NetworkInterface adapter : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!adapter.isUp() || adapter.isLoopback() || adapter.isVirtual()) {
                    continue;
                }
                String label = (adapter.getDisplayName() + " " + adapter.getName()).toLowerCase(Locale.ROOT);
                boolean virtual = label.contains("virtual") || label.contains("vmware") || label.contains("vethernet")
                        || label.contains("hyper-v") || label.contains("docker") || label.contains("wsl");
                for (InetAddress address : Collections.list(adapter.getInetAddresses())) {
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        if (!virtual) {
                            return address.getHostAddress();
                        }
                        if (fallback == null) {
                            fallback = address.getHostAddress();
                        }
                    }
                }
            }
            return fallback != null ? fallback : InetAddress.getLocalHost().getHostAddress();
        } catch (IOException e) {
            return "localhost";
        }
    }
}
