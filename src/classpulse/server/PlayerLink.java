package classpulse.server;

import classpulse.common.Message.Response;

/**
 * How a GameRoom talks to one player (added in the phone upgrade). Two classes implement it:
 *   ClientHandler - a player in the Swing app, over a TCP socket;
 *   WebPlayer     - a player on a phone browser, over HTTP.
 * The room does not know or care which kind it is talking to (polymorphism), so phone and
 * app players can play in the same game.
 */
interface PlayerLink {
    /** Queues a message for the player. Must never block. */
    void send(Response message);

    /** Called when the room ends, so the player can join another one. */
    void clearRoom(GameRoom room);
}
