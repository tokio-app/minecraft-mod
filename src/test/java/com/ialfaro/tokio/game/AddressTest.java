package com.ialfaro.tokio.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class AddressTest {
	@ParameterizedTest
	@CsvSource(delimiter = '|', value = {
		"''                   | localhost      | 37892 | localhost:37892",
		"'  '                 | localhost      | 37892 | localhost:37892",
		"localhost            | localhost      | 37892 | localhost:37892",
		"192.168.1.20:37892   | 192.168.1.20   | 37892 | 192.168.1.20:37892",
		"' 192.168.1.20:1 '   | 192.168.1.20   | 1     | 192.168.1.20:1",
		"tokio-app.local:8080 | tokio-app.local| 8080  | tokio-app.local:8080",
		"[::1]:37892          | ::1            | 37892 | [::1]:37892",
		"[::1]                | ::1            | 37892 | [::1]:37892",
		"::1                  | ::1            | 37892 | [::1]:37892",
		"[fe80::1]:65535      | fe80::1        | 65535 | [fe80::1]:65535",
	})
	void reads(String text, String host, int port, String shown) {
		Address address = Address.parse(text);
		assertEquals(new Address(host, port), address);
		assertEquals(shown, address.toString());
	}

	@ParameterizedTest
	@ValueSource(strings = {"local host", "host:", "[::1", "[::1]37892", "[::1]:", "[localhost]:1", "1:2:x", "ws://localhost", "a/b:1", ":37892", "host:port", "localhost:-1", "[::1]:x"})
	void refusesAddresses(String text) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Address.parse(text));
		assertEquals(text + " isn't an address. Type it as host:port, like 192.168.1.20:37892.", e.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = {"localhost:0", "localhost:65536", "localhost:99999", "[::1]:70000"})
	void refusesPorts(String text) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Address.parse(text));
		assertEquals(text.substring(text.lastIndexOf(':') + 1) + " isn't a port. Use a number from 1 to 65535.", e.getMessage());
	}

	@Test
	void namesTheBadAddress() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> Address.parse(" local host "));
		assertEquals("local host isn't an address. Type it as host:port, like 192.168.1.20:37892.", e.getMessage());
	}

	@Test
	void joinsChatSentences() {
		assertEquals(
				"Couldn't connect to Tokio at localhost:37892. Tokio isn't listening there. Turn on Minecraft in Tokio's Games page.",
				Chat.couldntConnect(Address.DEFAULT, "Tokio isn't listening there."));
		assertEquals(
				"Couldn't connect to Tokio at localhost:37892. Connection reset. Turn on Minecraft in Tokio's Games page.",
				Chat.couldntConnect(Address.DEFAULT, "Connection reset"));
		assertEquals("Disconnected from Tokio.", Chat.disconnected(""));
		assertEquals("Disconnected from Tokio. Another game connected to Tokio.", Chat.disconnected("Another game connected to Tokio."));
	}
}
