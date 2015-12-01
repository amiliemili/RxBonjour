package rxbonjour.broadcast;

import android.support.annotation.Nullable;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public abstract class BonjourBroadcastBuilder {

	private static final InetAddress DEFAULT_ADDRESS;
	private static final String DEFAULT_NAME = "RxBonjour Service";
	private static final int DEFAULT_PORT = 80;

	static {
		InetAddress defaultAddress = null;
		try {
			defaultAddress = InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			try {
				defaultAddress = InetAddress.getByName("127.0.0.1");
			} catch (UnknownHostException ignored) {
			}
		}
		DEFAULT_ADDRESS = defaultAddress;
	}

	private final String type;
	private Map<String, String> txtRecords;
	private String name = DEFAULT_NAME;
	private InetAddress address = DEFAULT_ADDRESS;
	private int port = DEFAULT_PORT;

	protected BonjourBroadcastBuilder(String type) {
		this.type = type;
	}

	public final BonjourBroadcastBuilder name(String name) {
		this.name = name;
		return this;
	}

	public final BonjourBroadcastBuilder address(InetAddress address) {
		this.address = address;
		return this;
	}

	public final BonjourBroadcastBuilder port(int port) {
		this.port = port;
		return this;
	}

	public final BonjourBroadcastBuilder addTxtRecord(String key, String value) {
		if (this.txtRecords == null) {
			this.txtRecords = new HashMap<>(4);
		}
		this.txtRecords.put(key, value);
		return this;
	}

	/* Begin package */

	@Nullable final InetAddress address() {
		return address;
	}

	@Nullable final Map<String, String> txtRecords() {
		return txtRecords;
	}

	final String type() {
		return type;
	}

	final String name() {
		return name;
	}

	final int port() {
		return port;
	}

	/* Begin abstract */

	public abstract BonjourBroadcast<?> build();
}
