package rxbonjour;

import android.support.annotation.NonNull;

public abstract class RxBonjourBase {

	private static final String TYPE_PATTERN = "_[a-zA-Z0-9\\-_]+\\.(_tcp|_udp)(\\.[a-zA-Z0-9\\-]+\\.)?";

	/**
	 * Checks the provided type String against Bonjour specifications, and returns whether or not the type is valid.
	 *
	 * @param type Type of service to check
	 * @return True if the type refers to a valid Bonjour type, false otherwise
	 */
	public static boolean isBonjourType(@NonNull String type) {
		return type.matches(TYPE_PATTERN);
	}
}
