package rxbonjour.broadcast;

import android.content.Context;
import android.support.annotation.Nullable;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import rx.Observable;
import rxbonjour.exc.TypeMalformedException;
import rxbonjour.model.BonjourEvent;

public abstract class BonjourBroadcastBuilder {

	private static final String DEFAULT_NAME = "RxBonjour Service";
	private static final int DEFAULT_PORT = 80;

	private final String type;
	private Map<String, String> txtRecords;
	private String name = DEFAULT_NAME;
	private InetAddress address;
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

	/**
	 * Starts broadcasting the configured Bonjour service.
	 * <p/>
	 * A {@link TypeMalformedException} is emitted after subscribing if the input type does not obey Bonjour type specifications. If you intend
	 * to use this method with arbitrary types that can be provided by user input, it is highly encouraged to verify this input
	 * using {@link rxbonjour.RxBonjour#isBonjourType(String)} <b>before</b> calling this method!
	 *
	 * @param context Context used to start the broadcast
	 * @return An Observable for Bonjour events
	 */
	public final Observable<BonjourEvent> start(Context context) {
		return this.build().start(context);
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

	protected abstract BonjourBroadcast<?> build();
}
