package com.ialfaro.tokio.game;

import java.net.InetAddress;
import java.util.regex.Pattern;

/** Where Tokio listens, as the player types it: {@code host[:port]}, IPv6 as {@code [::1]:37892}. */
public record Address(String host, int port) {
	public static final int DEFAULT_PORT = 37892;
	public static final Address DEFAULT = new Address("localhost", DEFAULT_PORT);

	private static final Pattern HOST = Pattern.compile("[A-Za-z0-9._-]+");
	private static final Pattern PORT = Pattern.compile("[0-9]{1,5}");

	/**
	 * Reads an address; blank is {@link #DEFAULT}, and a missing port is
	 * {@link #DEFAULT_PORT}.
	 *
	 * @throws IllegalArgumentException with a message for the player
	 */
	public static Address parse(String text) {
		String address = text.strip();
		if (address.isEmpty()) return DEFAULT;

		String host;
		String port = null;
		if (address.startsWith("[")) {
			int end = address.indexOf(']');
			if (end < 0) throw notAnAddress(address);
			host = address.substring(1, end);
			String rest = address.substring(end + 1);
			if (!rest.isEmpty()) {
				if (!rest.startsWith(":")) throw notAnAddress(address);
				port = rest.substring(1);
			}
			if (!isIpv6(host)) throw notAnAddress(address);
		} else if (address.indexOf(':') != address.lastIndexOf(':')) {
			// An IPv6 address without brackets can't have a port
			if (!isIpv6(address)) throw notAnAddress(address);
			host = address;
		} else {
			int colon = address.indexOf(':');
			host = colon < 0 ? address : address.substring(0, colon);
			if (colon >= 0) port = address.substring(colon + 1);
			if (!HOST.matcher(host).matches()) throw notAnAddress(address);
		}
		if (port == null) return new Address(host, DEFAULT_PORT);
		if (!PORT.matcher(port).matches()) throw notAnAddress(address);
		int number = Integer.parseInt(port);
		if (number < 1 || number > 65535) {
			throw new IllegalArgumentException(port + " isn't a port. Use a number from 1 to 65535.");
		}
		return new Address(host, number);
	}

	private static boolean isIpv6(String host) {
		if (!host.contains(":")) return false;
		try {
			// A literal only: no DNS lookup
			InetAddress.ofLiteral(host);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	private static IllegalArgumentException notAnAddress(String text) {
		return new IllegalArgumentException(text + " isn't an address. Type it as host:port, like 192.168.1.20:37892.");
	}

	/** {@code host:port}, with an IPv6 host in brackets. */
	@Override
	public String toString() {
		return (host.contains(":") ? "[" + host + "]" : host) + ":" + port;
	}
}
