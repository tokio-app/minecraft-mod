package com.ialfaro.tokio.connection;

/**
 * How a command went: whether it ran, and the game's feedback, or why it
 * failed. Tokio shows the message next to the command.
 */
public record CommandResult(boolean ok, String message) {
}
