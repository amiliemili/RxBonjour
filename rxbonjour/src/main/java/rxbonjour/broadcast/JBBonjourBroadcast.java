package rxbonjour.broadcast;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import rx.AsyncEmitter;
import rx.Observable;
import rx.functions.Action1;
import rxbonjour.RxBonjour;
import rxbonjour.exc.BroadcastFailed;
import rxbonjour.exc.StaleContextException;
import rxbonjour.exc.TypeMalformedException;
import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourService;
import rxbonjour.utils.JBUtils;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
final class JBBonjourBroadcast extends BonjourBroadcast<JBUtils> {

	protected JBBonjourBroadcast(BonjourBroadcastBuilder builder) {
		super(builder);
	}

	@Override protected JBUtils createUtils() {
		return JBUtils.get();
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

				try {
					final BonjourService bonjourService = createBonjourService(context);
					final NsdServiceInfo nsdService = createServiceInfo(bonjourService);
					final NsdManager nsdManager = utils.getManager(context);

					final NsdManager.RegistrationListener listener = new NsdManager.RegistrationListener() {
						@Override
						public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
							emitter.onError(new BroadcastFailed(JBBonjourBroadcast.class,
									bonjourService.getName(), errorCode));
						}

						@Override
						public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
						}

						@Override
						public void onServiceRegistered(NsdServiceInfo info) {
							emitter.onNext(new BonjourEvent(BonjourEvent.Type.ADDED, bonjourService));
						}

						@Override
						public void onServiceUnregistered(NsdServiceInfo info) {
							emitter.onNext(new BonjourEvent(BonjourEvent.Type.REMOVED, mapNsdServiceInfo(info)));
						}
					};

					nsdManager.registerService(nsdService, NsdManager.PROTOCOL_DNS_SD, listener);

					emitter.setCancellation(new AsyncEmitter.Cancellable() {
						@Override public void cancel() throws Exception {
							try {
								nsdManager.unregisterService(listener);
							} catch (IllegalArgumentException ignored) {
								ignored.printStackTrace();
							}
						}
					});
				} catch (IOException e) {
					emitter.onError(new BroadcastFailed(JBBonjourBroadcast.class, type));
				}
			}
		}, AsyncEmitter.BackpressureMode.BUFFER);
	}

	private BonjourService mapNsdServiceInfo(NsdServiceInfo info) {
		BonjourService.Builder builder =
				new BonjourService.Builder(info.getServiceName(), info.getServiceType())
						.addAddress(info.getHost())
						.setPort(info.getPort());

		if (Build.VERSION.SDK_INT >= LOLLIPOP) {
			Map<String, byte[]> attrs = info.getAttributes();
			for (Map.Entry<String, byte[]> entry : attrs.entrySet()) {
				builder.addTxtRecord(entry.getKey(),
						new String(entry.getValue(), StandardCharsets.UTF_8));
			}
		}

		return builder.build();
	}

	private NsdServiceInfo createServiceInfo(BonjourService serviceInfo) throws IOException {
		NsdServiceInfo nsdService = new NsdServiceInfo();
		nsdService.setServiceType(serviceInfo.getType());
		nsdService.setServiceName(serviceInfo.getName());
		nsdService.setHost(serviceInfo.getHost());
		nsdService.setPort(serviceInfo.getPort());

		// Add TXT records on Lollipop and up
		if (Build.VERSION.SDK_INT >= LOLLIPOP) {
			Bundle txtRecordBundle = serviceInfo.getTxtRecords();
			Map<String, String> txtRecordMap = new HashMap<>(serviceInfo.getTxtRecordCount());
			for (String key : txtRecordBundle.keySet()) {
				txtRecordMap.put(key, txtRecordBundle.getString(key));
			}

			for (String key : txtRecordMap.keySet()) {
				nsdService.setAttribute(key, txtRecordMap.get(key));
			}
		}

		return nsdService;
	}

	/* Begin static */

	static BonjourBroadcastBuilder newBuilder(String type) {
		return new JBBonjourBroadcastBuilder(type);
	}

	/* Begin inner classes */

	private static final class JBBonjourBroadcastBuilder extends BonjourBroadcastBuilder {

		protected JBBonjourBroadcastBuilder(String type) {
			super(type);
		}

		@Override public BonjourBroadcast build() {
			return new JBBonjourBroadcast(this);
		}
	}
}
