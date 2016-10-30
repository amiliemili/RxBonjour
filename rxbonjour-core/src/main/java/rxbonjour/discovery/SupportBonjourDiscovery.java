package rxbonjour.discovery;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.DNSIncoming;
import javax.jmdns.impl.constants.DNSRecordClass;
import javax.jmdns.impl.constants.DNSRecordType;

import rxbonjour.RxBonjourBase;
import rxbonjour.exc.DiscoveryFailed;
import rxbonjour.exc.TypeMalformedException;
import rxbonjour.model.BonjourEvent;
import rxbonjour.model.BonjourListener;
import rxbonjour.model.BonjourService;
import rxbonjour.utils.SupportUtils;

/**
 * Support implementation for Bonjour service discovery on pre-Jelly Bean devices,
 * utilizing Android's WifiManager and the JmDNS library for lookups.
 */
final class SupportBonjourDiscovery extends BonjourDiscovery<SupportUtils> {

	static {
		// Disable logging for some JmDNS classes, since those severely clutter log output
		Logger.getLogger(DNSIncoming.class.getName()).setLevel(Level.OFF);
		Logger.getLogger(DNSRecordType.class.getName()).setLevel(Level.OFF);
		Logger.getLogger(DNSRecordClass.class.getName()).setLevel(Level.OFF);
		Logger.getLogger(DNSIncoming.MessageInputStream.class.getName()).setLevel(Level.OFF);
	}

	/**
	 * Suffix appended to input types
	 */
	private static final String SUFFIX = ".local.";

	/**
	 * Tag to associate with the multicast lock
	 */
	private static final String LOCK_TAG = "RxBonjourDiscovery";

	private JmDNS jmdns;
	private String dnsType;
	private ServiceListener serviceListener;
	private WifiManager.MulticastLock lock;

	/**
	 * Constructor
	 */
	public SupportBonjourDiscovery() {
		super();
	}

	@Override protected SupportUtils createUtils() {
		return SupportUtils.get();
	}

	/* Begin private */

	/**
	 * Creates a new BonjourEvent instance from a JmDNS ServiceEvent.
	 *
	 * @param type  Type of event, either ADDED or REMOVED
	 * @param event Event containing information about the changed service
	 * @return A BonjourEvent containing the necessary information
	 */
	private BonjourEvent newBonjourEvent(BonjourEvent.Type type, ServiceEvent event) {
		// Construct a new BonjourService
		ServiceInfo info = event.getInfo();
		BonjourService.Builder serviceBuilder = new BonjourService.Builder(event.getName(), event.getType());

		// Prepare TXT record Bundle
		Enumeration<String> keys = info.getPropertyNames();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			serviceBuilder.addTxtRecord(key, info.getPropertyString(key));
		}

		// Add non-null host addresses and port
		InetAddress[] addresses = info.getInetAddresses();
		for (InetAddress address : addresses) {
			if (address == null) continue;
			serviceBuilder.addAddress(address);
		}
		serviceBuilder.setPort(info.getPort());

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

		// Append ".local." suffix in order to have JmDNS pick up on the services
		dnsType = (type.endsWith(SUFFIX)) ? type : type + SUFFIX;

		// Create the service listener
		serviceListener = new ServiceListener() {
			@Override public void serviceAdded(ServiceEvent event) {
				event.getDNS().requestServiceInfo(event.getType(), event.getName());
			}

			@Override public void serviceRemoved(ServiceEvent event) {
				listener.onBonjourEvent(newBonjourEvent(BonjourEvent.Type.REMOVED, event));
			}

			@Override public void serviceResolved(ServiceEvent event) {
				listener.onBonjourEvent(newBonjourEvent(BonjourEvent.Type.ADDED, event));
			}
		};

		// Obtain a multicast lock from the Wifi Manager and acquire it
		WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
		lock = wifiManager.createMulticastLock(LOCK_TAG);
		lock.setReferenceCounted(true);
		lock.acquire();

		// Obtain the current IP address and initialize JmDNS' discovery service with that
		try {
			jmdns = utils.getManager(context);

			// Start discovery
			jmdns.addServiceListener(dnsType, serviceListener);
			utils.incrementSubscriberCount();

		} catch (IOException e) {
			listener.onBonjourError(new DiscoveryFailed(SupportBonjourDiscovery.class, dnsType));
		}
	}

	@Override public void stop() {
		if (jmdns != null) {
			// Release the lock and clean up the JmDNS client
			jmdns.removeServiceListener(dnsType, serviceListener);
			utils.decrementSubscriberCount();

			// Perform clean-up in a separate thread
			new Thread() {
				@Override public void run() {
					super.run();

					// Release the held multicast lock
					lock.release();

					// Close the JmDNS instance if no more subscribers remain
					utils.closeIfNecessary();
				}
			}.start();
		}
	}
}
