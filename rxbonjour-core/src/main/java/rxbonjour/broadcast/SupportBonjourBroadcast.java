package rxbonjour.broadcast;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import rxbonjour.exc.BroadcastFailed;
import rxbonjour.exc.TypeMalformedException;
import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourListener;
import rxbonjour.model.BonjourService;
import rxbonjour.RxBonjourBase;
import rxbonjour.utils.SupportUtils;

final class SupportBonjourBroadcast extends BonjourBroadcast<SupportUtils> {

	/** Tag to associate with the multicast lock */
	private static final String LOCK_TAG = "RxBonjourBroadcast";

	private JmDNS jmdns;
	private ServiceInfo jmdnsService;
	private WifiManager.MulticastLock lock;

	protected SupportBonjourBroadcast(BonjourBroadcastBuilder builder) {
		super(builder);
	}

	@Override protected SupportUtils createUtils() {
		return SupportUtils.get();
	}

	@Override public void start(@NonNull Context context, @NonNull final BonjourListener listener) {
		// Verify service type
		if (!RxBonjourBase.isBonjourType(this.type)) {
			listener.onBonjourError(new TypeMalformedException(this.type));
			return;
		}

		// Obtain a multicast lock from the Wifi Manager and acquire it
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		lock = wifiManager.createMulticastLock(LOCK_TAG);
		lock.setReferenceCounted(true);
		lock.acquire();

		try {
			// Create a JmDNS service using the BonjourService information and register that
			final BonjourService bonjourService = createBonjourService(context);
			jmdnsService = createJmdnsService(bonjourService);
			jmdns = utils.getManager(context);

			jmdns.registerService(jmdnsService);
			utils.incrementSubscriberCount();
			listener.onBonjourEvent(new BonjourEvent(BonjourEvent.Type.ADDED, bonjourService));

		} catch (IOException e) {
			listener.onBonjourError(new BroadcastFailed(SupportBonjourBroadcast.class, type));
		}
	}

	@Override public void stop() {
		if (jmdns != null) {
			jmdns.unregisterService(jmdnsService);
			utils.decrementSubscriberCount();
			lock.release();

			// Perform clean-up asynchronously
			new Thread() {
				@Override public void run() {
					super.run();

					utils.closeIfNecessary();
				}
			}.start();
		}
	}

	private ServiceInfo createJmdnsService(BonjourService serviceInfo) {
		int txtRecordCount = serviceInfo.getTxtRecordCount();
		Bundle txtRecordBundle = serviceInfo.getTxtRecords();
		Map<String, String> txtRecordMap = new HashMap<>(txtRecordCount);

		if (txtRecordBundle.size() > 0) {
			for (String key : txtRecordBundle.keySet()) {
				txtRecordMap.put(key, txtRecordBundle.getString(key));
			}
		}

		return ServiceInfo.create(
				serviceInfo.getType(),
				serviceInfo.getName(),
				serviceInfo.getPort(),
				0,
				0,
				true,
				txtRecordMap
		);
	}

	/* Begin inner classes */

	static class Builder extends BonjourBroadcastBuilder {

		/** Suffix appended to input types */
		private static final String SUFFIX = ".local.";

		Builder(String type) {
			super((type.endsWith(SUFFIX)) ? type : type + SUFFIX);
		}

		@Override public BonjourBroadcast build() {
			return new SupportBonjourBroadcast(this);
		}
	}
}
