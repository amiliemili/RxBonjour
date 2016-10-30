package rxbonjour.broadcast;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import rxbonjour.exc.BroadcastFailed;
import rxbonjour.exc.TypeMalformedException;
import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourListener;
import rxbonjour.model.BonjourService;
import rxbonjour.RxBonjourBase;
import rxbonjour.utils.JBUtils;

import static android.os.Build.VERSION_CODES.LOLLIPOP;

@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
final class JBBonjourBroadcast extends BonjourBroadcast<JBUtils> {

	private NsdManager nsdManager;
	private NsdManager.RegistrationListener nsdListener;

	protected JBBonjourBroadcast(BonjourBroadcastBuilder builder) {
		super(builder);
	}

	@Override protected JBUtils createUtils() {
		return JBUtils.get();
	}

	@Override public void start(@NonNull Context context, @NonNull final BonjourListener listener) {
		// Verify service type
		if (!RxBonjourBase.isBonjourType(this.type)) {
			listener.onBonjourError(new TypeMalformedException(this.type));
			return;
		}

		try {
			final BonjourService bonjourService = createBonjourService(context);
			final NsdServiceInfo nsdService = createServiceInfo(bonjourService);

			nsdManager = utils.getManager(context);
			nsdListener = new NsdManager.RegistrationListener() {
				@Override
				public void onRegistrationFailed(NsdServiceInfo info, int errorCode) {
					listener.onBonjourError(new BroadcastFailed(JBBonjourBroadcast.class,
							bonjourService.getName(), errorCode));
				}

				@Override
				public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) {
				}

				@Override
				public void onServiceRegistered(NsdServiceInfo info) {
					listener.onBonjourEvent(new BonjourEvent(BonjourEvent.Type.ADDED, bonjourService));
				}

				@Override
				public void onServiceUnregistered(NsdServiceInfo info) {
					listener.onBonjourEvent(new BonjourEvent(BonjourEvent.Type.REMOVED, mapNsdServiceInfo(info)));
				}
			};

			nsdManager.registerService(nsdService, NsdManager.PROTOCOL_DNS_SD, nsdListener);

		} catch (IOException e) {
			listener.onBonjourError(new BroadcastFailed(JBBonjourBroadcast.class, type));
		}
	}

	@Override public void stop() {
		if (nsdManager != null) {
			try {
				nsdManager.unregisterService(nsdListener);
			} catch (IllegalArgumentException ignored) {
				ignored.printStackTrace();
			}
		}
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

	/* Begin inner classes */

	static class Builder extends BonjourBroadcastBuilder {

		Builder(String type) {
			super(type);
		}

		@Override public BonjourBroadcast build() {
			return new JBBonjourBroadcast(this);
		}
	}
}
