package rxbonjour.discovery;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import rxbonjour.RxBonjourBase;
import rxbonjour.exc.DiscoveryFailed;
import rxbonjour.exc.TypeMalformedException;
import rxbonjour.internal.Backlog;
import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourListener;
import rxbonjour.model.BonjourService;
import rxbonjour.utils.JBUtils;

import static android.os.Build.VERSION_CODES.JELLY_BEAN;
import static android.os.Build.VERSION_CODES.LOLLIPOP;

/**
 * Bonjour implementation for Jelly-Bean and up, utilizing the NsdManager APIs.
 */
@TargetApi(JELLY_BEAN)
final class JBBonjourDiscovery extends BonjourDiscovery<JBUtils> {

	/**
	 * Number of subscribers listening to Bonjour events
	 */
	private static int subscriberCount = 0;
	private static NsdManager nsdManager;
	private static Backlog<NsdServiceInfo> resolveBacklog;

	private NsdManager.DiscoveryListener discoveryListener;

	/**
	 * Constructor
	 */
	public JBBonjourDiscovery() {
		super();
	}

	@Override protected JBUtils createUtils() {
		return JBUtils.get();
	}

	/* Begin private */

	/**
	 * Creates a new BonjourEvent instance from an Nsd Service info object.
	 *
	 * @param type        Type of event, either ADDED or REMOVED
	 * @param serviceInfo ServiceInfo containing information about the changed service
	 * @return A BonjourEvent containing the necessary information
	 */
	@TargetApi(LOLLIPOP) private BonjourEvent newBonjourEvent(BonjourEvent.Type type, NsdServiceInfo serviceInfo) {
		// Construct a new BonjourService
		BonjourService.Builder serviceBuilder = new BonjourService.Builder(serviceInfo.getServiceName(), serviceInfo.getServiceType());

		// Prepare TXT record Bundle (on Lollipop and up)
		if (Build.VERSION.SDK_INT >= LOLLIPOP) {
			Map<String, byte[]> attributes = serviceInfo.getAttributes();
			for (String key : attributes.keySet()) {
				serviceBuilder.addTxtRecord(key, new String(attributes.get(key), Charset.forName("UTF-8")));
			}
		}

		// Add host address and port
		serviceBuilder.addAddress(serviceInfo.getHost());
		serviceBuilder.setPort(serviceInfo.getPort());

		// Create and return an event wrapping the BonjourService
		return new BonjourEvent(type, serviceBuilder.build());
	}

	/* Begin overrides */

	@Override public void start(Context context, final String type, final BonjourListener listener) {
		// Verify type
		if (!RxBonjourBase.isBonjourType(type)) {
			listener.onBonjourError(new TypeMalformedException(type));
			return;
		}

		// Create the discovery listener
		this.discoveryListener = new NsdManager.DiscoveryListener() {
			@Override public void onStartDiscoveryFailed(String serviceType, int errorCode) {
				listener.onBonjourError(new DiscoveryFailed(JBBonjourDiscovery.class, serviceType, errorCode));
			}

			@Override public void onStopDiscoveryFailed(String serviceType, int errorCode) {
				listener.onBonjourError(new DiscoveryFailed(JBBonjourDiscovery.class, serviceType, errorCode));
			}

			@Override public void onDiscoveryStarted(String serviceType) {
			}

			@Override public void onDiscoveryStopped(String serviceType) {
			}

			@Override public void onServiceFound(NsdServiceInfo serviceInfo) {
				// Add the found service to the resolve backlog (it will be processed once the backlog gets to it)
				resolveBacklog.add(serviceInfo);
			}

			@Override public void onServiceLost(NsdServiceInfo serviceInfo) {
				listener.onBonjourEvent(newBonjourEvent(BonjourEvent.Type.REMOVED, serviceInfo));
			}
		};

		// Obtain the NSD manager
		try {
			nsdManager = utils.getManager(context);

		} catch (IOException e) {
			listener.onBonjourError(e);
			return;
		}

		// Create the resolver backlog upon registering the first listener
		if (resolveBacklog == null) {
			resolveBacklog = new Backlog<NsdServiceInfo>() {
				@Override public void onNext(Backlog<NsdServiceInfo> backlog, NsdServiceInfo info) {
					// Resolve this service info using the corresponding listener
					nsdManager.resolveService(info, new NsdManager.ResolveListener() {
						@Override public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
						}

						@Override public void onServiceResolved(NsdServiceInfo serviceInfo) {
							listener.onBonjourEvent(newBonjourEvent(BonjourEvent.Type.ADDED, serviceInfo));

							// Inform the backlog to continue processing
							if (resolveBacklog != null) {
								resolveBacklog.proceed();
							}
						}
					});
				}
			};
		}

		// Start discovery
		nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
		subscriberCount++;
	}

	@Override public synchronized void stop() {
		if (nsdManager != null) {
			try {
				nsdManager.stopServiceDiscovery(discoveryListener);
				subscriberCount--;

			} catch (Exception ignored) {
				// "Service discovery not active on discoveryListener", thrown if starting the service discovery was unsuccessful earlier

			} finally {
				if (subscriberCount <= 0) {
					resolveBacklog.quit();
					resolveBacklog = null;
					nsdManager = null;
				}
			}
		}
	}
}
