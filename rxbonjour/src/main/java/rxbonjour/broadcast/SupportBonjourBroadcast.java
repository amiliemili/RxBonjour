package rxbonjour.broadcast;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

import rx.AsyncEmitter;
import rx.Observable;
import rx.functions.Action1;
import rxbonjour.RxBonjour;
import rxbonjour.exc.BroadcastFailed;
import rxbonjour.exc.StaleContextException;
import rxbonjour.exc.TypeMalformedException;
import rxbonjour.internal.BonjourSchedulers;
import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourService;
import rxbonjour.utils.SupportUtils;

final class SupportBonjourBroadcast extends BonjourBroadcast<SupportUtils> {

	/** Tag to associate with the multicast lock */
	private static final String LOCK_TAG = "RxBonjourBroadcast";

	protected SupportBonjourBroadcast(BonjourBroadcastBuilder builder) {
		super(builder);
	}

	@Override protected SupportUtils createUtils() {
		return SupportUtils.get();
	}

	@Override public Observable<BonjourEvent> start(Context context) {
		// Verify service type
		if (!RxBonjour.isBonjourType(this.type)) {
			return Observable.error(new TypeMalformedException(this.type));
		}

		// Create a weak reference to the incoming Context
		final WeakReference<Context> weakContext = new WeakReference<>(context);

		return Observable.fromEmitter(new Action1<AsyncEmitter<BonjourEvent>>() {
			@Override public void call(final AsyncEmitter<BonjourEvent> emitter) {
				Context context = weakContext.get();
				if (context == null) {
					emitter.onError(new StaleContextException());
					return;
				}

				// Obtain a multicast lock from the Wifi Manager and acquire it
				WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
				final WifiManager.MulticastLock lock = wifiManager.createMulticastLock(LOCK_TAG);
				lock.setReferenceCounted(true);
				lock.acquire();

				try {
					// Create a JmDNS service using the BonjourService information and register that
					final BonjourService bonjourService = createBonjourService(context);
					final ServiceInfo jmdnsService = createJmdnsService(bonjourService);
					final JmDNS jmdns = utils.getManager(context);

					emitter.setCancellation(new AsyncEmitter.Cancellable() {
						@Override public void cancel() throws Exception {
							jmdns.unregisterService(jmdnsService);
							utils.decrementSubscriberCount();
							lock.release();

							Observable<Boolean> cleanUpObservable = Observable.fromCallable(new Callable<Boolean>() {
								@Override public Boolean call() throws Exception {
									utils.closeIfNecessary();
									return true;
								}
							});

							cleanUpObservable
									.compose(BonjourSchedulers.cleanupSchedulers())
									.subscribe();
						}
					});

					jmdns.registerService(jmdnsService);
					utils.incrementSubscriberCount();
					emitter.onNext(new BonjourEvent(BonjourEvent.Type.ADDED, bonjourService));

				} catch (IOException e) {
					emitter.onError(new BroadcastFailed(SupportBonjourBroadcast.class, type));
				}
			}
		}, AsyncEmitter.BackpressureMode.BUFFER);
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

	/* Begin static */

	static BonjourBroadcastBuilder newBuilder(String type) {
		return new SupportBonjourBroadcastBuilder(type);
	}

	/* Begin inner classes */

	private static final class SupportBonjourBroadcastBuilder extends BonjourBroadcastBuilder {

		/** Suffix appended to input types */
		private static final String SUFFIX = ".local.";

		protected SupportBonjourBroadcastBuilder(String type) {
			super((type.endsWith(SUFFIX)) ? type : type + SUFFIX);
		}

		@Override public BonjourBroadcast build() {
			return new SupportBonjourBroadcast(this);
		}
	}
}
