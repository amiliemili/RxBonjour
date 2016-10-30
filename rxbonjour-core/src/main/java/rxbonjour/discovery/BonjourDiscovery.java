package rxbonjour.discovery;

import android.content.Context;
import android.os.Build;

import rxbonjour.model.BonjourListener;
import rxbonjour.utils.BonjourUtils;

import static android.os.Build.VERSION_CODES.JELLY_BEAN;

/**
 * Base interface for DNS-SD implementations
 */
public abstract class BonjourDiscovery<T extends BonjourUtils<?>> {

	protected final T utils;

	public BonjourDiscovery() {
		this.utils = createUtils();
	}

	protected abstract T createUtils();

	public abstract void start(Context context, String type, BonjourListener listener);
	public abstract void stop();

	public static BonjourDiscovery get(boolean forceNsdManager) {
		if (forceNsdManager && Build.VERSION.SDK_INT >= JELLY_BEAN) {
			return new JBBonjourDiscovery();
		} else {
			return new SupportBonjourDiscovery();
		}
	}
}
