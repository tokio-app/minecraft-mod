package com.ialfaro.tokio.connection;

import java.util.concurrent.CompletableFuture;

/** Runs the commands Tokio sends, in the game. */
public interface Commands {
	/**
	 * Runs one command line, without a leading slash, and completes with how
	 * it went. Called from the connection's threads; never completes
	 * exceptionally.
	 */
	CompletableFuture<CommandResult> run(String commandLine);
}
