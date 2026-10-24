package com.ialfaro.tokio.connection;

/**
 * What happens to the connection, called from the connection's threads with
 * the client's lock held: don't block, hand the work to the server thread.
 * Nothing comes after a {@link TokioClient#disconnect()} the game asked for.
 */
public interface ConnectionListener {
	/** The socket opened: Tokio counts the game as connected. */
	void connected();

	/**
	 * An open connection ended, with the close code and Tokio's reason (empty
	 * when there's none, as when the network drops).
	 */
	void closed(int code, String reason);

	/** A connect didn't open, with why, for the player. */
	void failed(String message);
}
