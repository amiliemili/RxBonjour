package rxbonjour.discovery;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Message;

import org.junit.Test;
import org.powermock.core.classloader.annotations.PrepareForTest;

import java.net.InetAddress;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceListener;
import javax.jmdns.impl.DNSStatefulObject;

import rxbonjour.TestBonjourListener;
import rxbonjour.base.BaseTest;
import rxbonjour.exc.TypeMalformedException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@PrepareForTest({ JmDNS.class })
public class SupportBonjourDiscoveryTest extends BaseTest {

	private TestJmDNS jmdns;
	private TestBonjourListener listener;

	abstract class TestJmDNS extends JmDNS implements DNSStatefulObject {
	}

	@Override protected void setupMocks() throws Exception {
		this.listener = new TestBonjourListener();

		WifiManager wifiManager = mock(WifiManager.class);
		WifiInfo wifiInfo = mock(WifiInfo.class);
		WifiManager.MulticastLock lock = mock(WifiManager.MulticastLock.class);
		jmdns = mock(TestJmDNS.class);

		// Wire default return values
		when(context.getSystemService(Context.WIFI_SERVICE)).thenReturn(wifiManager);
		when(wifiManager.createMulticastLock(anyString())).thenReturn(lock);
		when(wifiInfo.getIpAddress()).thenReturn(0);
		when(wifiManager.getConnectionInfo()).thenReturn(wifiInfo);

		// Mock statics
		mockStatic(JmDNS.class);
		mockStatic(Message.class);

		// Wire statics
		given(JmDNS.create(any(InetAddress.class), anyString())).willReturn(jmdns);
	}

	private void setJmDNSMockClosed() {
		when(jmdns.isClosing()).thenReturn(true);
		when(jmdns.isClosed()).thenReturn(true);
	}

	@Test public void testAddAndRemoveOneCycle() throws Exception {
		BonjourDiscovery discovery = new SupportBonjourDiscovery();

		discovery.start(context, "_http._tcp", listener);

		listener.assertNoErrors();
		verify(jmdns, times(1)).addServiceListener(eq("_http._tcp.local."), any(ServiceListener.class));
		discovery.stop();
		verify(jmdns, times(1)).removeServiceListener(eq("_http._tcp.local."), any(ServiceListener.class));
		verify(jmdns, times(1)).close();
		setJmDNSMockClosed();
	}

	@Test public void testAddAndRemoveOneCycleWithLocalDomain() throws Exception {
		BonjourDiscovery discovery = new SupportBonjourDiscovery();

		discovery.start(context, "_http._tcp.local.", listener);

		listener.assertNoErrors();
		verify(jmdns, times(1)).addServiceListener(eq("_http._tcp.local."), any(ServiceListener.class));
		discovery.stop();
		verify(jmdns, times(1)).removeServiceListener(eq("_http._tcp.local."), any(ServiceListener.class));
		verify(jmdns, times(1)).close();
		setJmDNSMockClosed();
	}

	@Test public void testAddAndRemoveTwoCycle() throws Exception {
		BonjourDiscovery discovery = new SupportBonjourDiscovery();
		TestBonjourListener listener1 = new TestBonjourListener();
		TestBonjourListener listener2 = new TestBonjourListener();

		discovery.start(context, "_http._tcp", listener1);
		discovery.start(context, "_http._tcp", listener2);

		listener1.assertNoErrors();
		listener2.assertNoErrors();
		verify(jmdns, times(2)).addServiceListener(eq("_http._tcp.local."), any(ServiceListener.class));
		discovery.stop();
		verify(jmdns, times(1)).removeServiceListener(eq("_http._tcp.local."), any(ServiceListener.class));
		verify(jmdns, never()).close();
		discovery.stop();
		verify(jmdns, times(2)).removeServiceListener(eq("_http._tcp.local."), any(ServiceListener.class));
		verify(jmdns, times(1)).close();
		setJmDNSMockClosed();
	}

	@Test public void testAddAndRemoveTwoDifferentTypesCycle() throws Exception {
		BonjourDiscovery discovery = new SupportBonjourDiscovery();
		TestBonjourListener listener1 = new TestBonjourListener();
		TestBonjourListener listener2 = new TestBonjourListener();

		discovery.start(context, "_http._tcp", listener1);
		discovery.start(context, "_ssh._tcp", listener2);

		listener1.assertNoErrors();
		listener2.assertNoErrors();
		verify(jmdns, times(1)).addServiceListener(eq("_http._tcp.local."), any(ServiceListener.class));
		verify(jmdns, times(1)).addServiceListener(eq("_ssh._tcp.local."), any(ServiceListener.class));
		discovery.stop();
		verify(jmdns, times(1)).removeServiceListener(eq("_http._tcp.local."), any(ServiceListener.class));
		verify(jmdns, never()).close();
		discovery.stop();
		verify(jmdns, times(1)).removeServiceListener(eq("_ssh._tcp.local."), any(ServiceListener.class));
		verify(jmdns, times(1)).close();
		setJmDNSMockClosed();
	}

	@Test public void testRaisesExceptionOnMalformedType() throws Exception {
		BonjourDiscovery discovery = new SupportBonjourDiscovery();

		discovery.start(context, "not_a-type", listener);

		listener.assertError(TypeMalformedException.class);
	}

	// TODO Fill with more tests
}
